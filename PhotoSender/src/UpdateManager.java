import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Release üzerinden MSI tabanlı güncelleme yöneticisi.
 * - Uygulama sürümünü GitHub 'latest release' tag_name alanı ile karşılaştırır.
 * - Yeni sürüm varsa ilgili .msi asset'ini indirip msiexec ile kurulum başlatır.
 * - Public repository için tasarlanmıştır.
 */
public class UpdateManager {
    private static final String REPO = "yusufziyrek/DigitalPhotoTransfer"; // Doğru repository adı
    private static final String LATEST_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSETS_BLOCK = Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    
    // Caching for API responses
    private static long lastCheckTime = 0;
    private static String cachedResponse = null;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 dakika
    
    // Logger instance
    private static final AppLogger logger = new AppLogger("PhotoSender");

    /**
     * Başlangıçta otomatik güncelleme kontrolü (sessiz: sadece yeni sürüm varsa sorar).
     * Repository private ise sessizce geçer.
     */
    public static void autoCheckForUpdates(Component parent) {
        logger.info("Otomatik güncelleme kontrolü başlatıldı");
        SwingUtilities.invokeLater(() -> checkForUpdates(parent, true));
    }

    /**
     * Menüden manuel tetiklenen güncelleme kontrolü.
     * Repository private ise kullanıcıya bilgi verir.
     */
    public static void manualCheck(Component parent) {
        logger.info("Manuel güncelleme kontrolü başlatıldı");
        checkForUpdates(parent, false);
    }

    private static void checkForUpdates(Component parent, boolean silentIfUpToDate) {
        logger.info("Güncelleme kontrolü başlatılıyor - Repository: " + REPO);
        System.out.println("Güncelleme kontrolü başlatılıyor...");
        System.out.println("Repository: " + REPO);
        
        new SwingWorker<Void, Void>() {
            String latestTag;
            String msiUrl;
            String error;

            @Override
            protected Void doInBackground() {
                try {
                    logger.info("GitHub API çağrısı yapılıyor: " + LATEST_API);
                    String json = httpGet(LATEST_API);
                    if (json == null) {
                        error = "GitHub API yanıtı alınamadı";
                        logger.error("GitHub API yanıtı null");
                        return null;
                    }
                    logger.info("GitHub API yanıtı başarıyla alındı (" + json.length() + " karakter)");
                    System.out.println("GitHub API yanıtı alındı (✓)");
                    Matcher mTag = TAG_PATTERN.matcher(json);
                    if (mTag.find()) {
                        latestTag = mTag.group(1).trim();
                        logger.info("Latest tag bulundu: " + latestTag);
                        System.out.println("Latest tag bulundu: " + latestTag);
                    } else {
                        logger.warn("Tag name bulunamadı API yanıtında");
                    }
                    // findLatestMsiUrl fonksiyonunu kullan
                    msiUrl = findLatestMsiUrl(json);
                    if (msiUrl != null) {
                        logger.info("MSI download URL bulundu: " + msiUrl);
                    }
                } catch (Exception ex) {
                    // 404 hatası = Repository private veya release yok
                    if (ex.getMessage().contains("404")) {
                        logger.warn("Repository private veya release bulunamadı (HTTP 404)");
                        System.out.println("Repository şu anda private veya henüz release yok");
                        error = "Repository erişilebilir değil (private olabilir)";
                    } else {
                        logger.error("Güncelleme kontrolü hatası", ex);
                        System.err.println("Update check failed: " + ex.getMessage());
                        error = ex.getMessage();
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                String current = PhotoSenderApp.VERSION; // mevcut sürüm
                logger.info("Güncelleme kontrolü tamamlandı - Mevcut sürüm: " + current);
                
                if (error != null) {
                    if (error.contains("Repository erişilebilir değil")) {
                        // Private repository durumu
                        logger.info("Repository private durumda - güncelleme kontrolü atlandı");
                        if (!silentIfUpToDate) {
                            JOptionPane.showMessageDialog(parent, 
                                "Güncelleme kontrolü yapılamıyor.\n" +
                                "Repository şu anda private durumda.\n\n" +
                                "Yeni sürüm çıktığında geliştirici tarafından duyurulacaktır.",
                                "Güncelleme Bilgisi", JOptionPane.INFORMATION_MESSAGE);
                        }
                        System.out.println("Private repository - güncelleme kontrolü atlandı");
                        return;
                    } else {
                        // Diğer hatalar
                        logger.error("Güncelleme kontrolü başarısız: " + error);
                        if (!silentIfUpToDate) {
                            JOptionPane.showMessageDialog(parent, 
                                "Güncelleme kontrolü başarısız: " + error, 
                                "Güncelleme", JOptionPane.WARNING_MESSAGE);
                        }
                        return;
                    }
                }
                
                if (latestTag == null) {
                    logger.warn("Release tag bulunamadı");
                    if (!silentIfUpToDate) {
                        JOptionPane.showMessageDialog(parent, "Release tag bulunamadı.", "Güncelleme", JOptionPane.INFORMATION_MESSAGE);
                    }
                    return;
                }
                
                if (normalizeVersion(latestTag).equals(normalizeVersion(current))) {
                    logger.info("Sürüm güncel - herhangi bir güncelleme gerekli değil");
                    if (!silentIfUpToDate) {
                        JOptionPane.showMessageDialog(parent, "Güncel sürümü kullanıyorsunuz (" + current + ").", "Güncelleme", JOptionPane.INFORMATION_MESSAGE);
                    }
                    System.out.println("Sürüm güncel: " + current);
                    return;
                }
                
                if (msiUrl == null) {
                    logger.warn("Yeni sürüm bulundu ama MSI asset yok - Tag: " + latestTag);
                    JOptionPane.showMessageDialog(parent, "Yeni sürüm bulundu (" + latestTag + ") ancak MSI asset yok.", "Güncelleme", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                // Yeni sürüm bulundu!
                logger.info("YENİ SÜRÜM BULUNDU! Mevcut: " + current + " → Yeni: " + latestTag);
                logger.info("MSI download URL: " + msiUrl);
                System.out.println("Yeni sürüm mevcut: " + latestTag + " (mevcut: " + current + ")");
                int res = JOptionPane.showConfirmDialog(parent,
                        "Yeni sürüm bulundu: " + latestTag + "\n" +
                        "Mevcut sürüm: " + current + "\n\n" +
                        "Kurulumu başlatmak ister misiniz?",
                        "Yeni Sürüm Mevcut", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                        
                if (res == JOptionPane.YES_OPTION) {
                    logger.info("Kullanıcı güncellemeyi onayladı - İndirme başlatılıyor");
                    downloadAndInstallMsi(parent, latestTag, msiUrl);
                } else {
                    logger.info("Kullanıcı güncellemeyi reddetti");
                }
            }
        }.execute();
    }

    private static String normalizeVersion(String v) {
        if (v == null) return "";
        return v.startsWith("v") ? v.substring(1) : v;
    }

    private static String httpGet(String urlStr) throws IOException {
        // Cache kontrolü
        long now = System.currentTimeMillis();
        if (cachedResponse != null && (now - lastCheckTime) < CACHE_DURATION) {
            System.out.println("Cache'den yanıt kullanılıyor");
            return cachedResponse;
        }
        
        java.net.URL url = java.net.URI.create(urlStr).toURL();
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
        c.setRequestProperty("Accept", "application/vnd.github.v3+json");
        
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        int code = c.getResponseCode();
        System.out.println("HTTP yanıt kodu: " + c.getResponseCode());
        if (code >= 400) {
            String err = readStream(c.getErrorStream());
            throw new IOException("HTTP " + code + (err == null ? "" : (": " + err)));
        }
        
        String result = readStream(c.getInputStream());
        
        // Cache'e kaydet
        lastCheckTime = now;
        cachedResponse = result;
        
        return result;
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(8192); // Initial capacity
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static void downloadAndInstallMsi(Component parent, String version, String url) {
        logger.info("MSI indirme başlatıldı - Sürüm: " + version + ", URL: " + url);
        
        // Progress dialog oluştur
        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), "Güncelleme İndiriliyor", true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        JLabel statusLabel = new JLabel("İndirme başlatılıyor...");
        
        progressDialog.setLayout(new BorderLayout());
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel);
        progressDialog.setSize(400, 120);
        progressDialog.setLocationRelativeTo(parent);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            File tempMsi;
            String error;

            @Override
            protected Void doInBackground() {
                try {
                    logger.info("MSI dosyası indiriliyor...");
                    publish("İndirme başlatılıyor...");
                    String fileName = "PhotoSender-" + normalizeVersion(version) + ".msi";
                    tempMsi = new File(System.getProperty("java.io.tmpdir"), fileName);
                    logger.info("Geçici dosya yolu: " + tempMsi.getAbsolutePath());
                    
                    java.net.URL u = java.net.URI.create(url).toURL();
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    
                    int code = c.getResponseCode();
                    logger.info("MSI indirme HTTP yanıt kodu: " + code);
                    System.out.println("MSI indirme HTTP yanıt kodu: " + code);
                    System.out.println("MSI indirme URL: " + url);
                    if (code >= 400) {
                        String err = readStream(c.getErrorStream());
                        logger.error("MSI indirme HTTP hatası: " + code + " - " + err);
                        System.out.println("MSI indirme hatası: " + err);
                        throw new IOException("HTTP " + code + (err == null ? "" : (": " + err)));
                    }
                    
                    // Dosya boyutunu al
                    long fileSize = c.getContentLengthLong();
                    logger.info("MSI dosya boyutu: " + (fileSize > 0 ? (fileSize / (1024 * 1024)) + " MB" : "bilinmiyor"));
                    publish("Dosya boyutu: " + (fileSize > 0 ? (fileSize / (1024 * 1024)) + " MB" : "Bilinmiyor"));
                    
                    try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tempMsi)) {
                        byte[] buf = new byte[8192];
                        long totalRead = 0;
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            out.write(buf, 0, r);
                            totalRead += r;
                            
                            // Progress güncelle
                            if (fileSize > 0) {
                                int progress = (int) ((totalRead * 100) / fileSize);
                                setProgress(progress);
                                publish("İndirilen: " + (totalRead / (1024 * 1024)) + " MB / " + (fileSize / (1024 * 1024)) + " MB");
                            } else {
                                publish("İndirilen: " + (totalRead / (1024 * 1024)) + " MB");
                            }
                        }
                    }
                    logger.info("MSI indirme tamamlandı - Dosya boyutu: " + (tempMsi.length() / (1024 * 1024)) + " MB");
                    publish("İndirme tamamlandı!");
                } catch (Exception ex) {
                    logger.error("MSI indirme hatası", ex);
                    System.err.println("MSI download failed: " + ex.getMessage());
                    ex.printStackTrace(); // Stack trace for debugging
                    error = ex.getMessage();
                    publish("Hata: " + error);
                }
                return null;
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    String latestStatus = chunks.get(chunks.size() - 1);
                    statusLabel.setText(latestStatus);
                    System.out.println("Progress: " + latestStatus);
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose(); // Progress dialog'u kapat
                
                if (error != null) {
                    logger.error("MSI indirme başarısız: " + error);
                    JOptionPane.showMessageDialog(parent, "İndirme hatası: " + error, "Güncelleme", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                logger.info("MSI indirme başarılı - Kurulum onayı bekleniyor");
                int res = JOptionPane.showConfirmDialog(parent,
                        "İndirildi: " + tempMsi.getAbsolutePath() + 
                        "\nDosya boyutu: " + (tempMsi.length() / (1024 * 1024)) + " MB" +
                        "\nKurulumu şimdi başlatmak ister misiniz? (UAC sorabilir)",
                        "MSI Kurulumu", JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) {
                    logger.info("Kullanıcı MSI kurulumunu onayladı");
                    installMsi(tempMsi, (JFrame) SwingUtilities.getWindowAncestor(parent));
                } else {
                    logger.info("Kullanıcı MSI kurulumunu iptal etti");
                }
            }
        };
        
        // Progress bar güncellemesi için listener ekle
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        // Worker'ı başlat
        worker.execute();
        
        // Progress dialog'u göster
        progressDialog.setVisible(true);
    }

    private static void installMsi(File msiFile, JFrame parentFrame) {
        logger.info("MSI kurulumu başlatılıyor: " + msiFile.getAbsolutePath() + " (" + (msiFile.length() / (1024 * 1024)) + " MB)");
        
        int choice = JOptionPane.showConfirmDialog(
            parentFrame,
            "Güncelleme indirildi (" + (msiFile.length() / (1024 * 1024)) + " MB).\n" +
            "Kurulumu başlatmak istiyor musunuz?\n\n" +
            "Not: Kurulum başladığında uygulama otomatik kapanacaktır.",
            "Kurulum Onayı",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            try {
                logger.info("MSI kurulum komutu çalıştırılıyor...");
                System.out.println("MSI kurulumu başlatılıyor: " + msiFile.getAbsolutePath());
                
                // Kurulum komutunu hazırla
                ProcessBuilder pb = new ProcessBuilder(
                    "msiexec", "/i", msiFile.getAbsolutePath(), "/passive"
                );
                
                logger.info("ProcessBuilder komutu: " + String.join(" ", pb.command()));
                
                // Kurulum başlat
                pb.start(); // Process referansını tutmaya gerek yok
                
                logger.info("MSI kurulum süreci başlatıldı - Uygulama 3 saniye sonra kapanacak");
                
                // 3 saniye bekleyip güvenli şekilde uygulamayı kapat
                Timer timer = new Timer(3000, _ -> {
                    logger.info("Uygulama kapatılıyor - MSI kurulumu devam ediyor");
                    System.out.println("Kurulum başlatıldı - Uygulama kapatılıyor");
                    System.exit(0);
                });
                timer.setRepeats(false);
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                        parentFrame,
                        "Kurulum başlatıldı. Uygulama 3 saniye sonra kapanacak.\n" +
                        "Kurulum tamamlandıktan sonra yeni sürümü başlatabilirsiniz.",
                        "Uygulama Kapanıyor",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    timer.start(); // Timer'ı dialog kapatıldıktan sonra başlat
                });
                
            } catch (IOException e) {
                logger.error("MSI kurulum başlatma hatası", e);
                System.err.println("MSI installation failed: " + e.getMessage());
                e.printStackTrace(); // Stack trace for debugging
                JOptionPane.showMessageDialog(
                    parentFrame,
                    "Kurulum başlatılamadı: " + e.getMessage(),
                    "Hata",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } else {
            logger.info("Kullanıcı MSI kurulumunu iptal etti");
        }
    }

    private static String findLatestMsiUrl(String json) {
        Matcher tagMatcher = TAG_PATTERN.matcher(json);
        if (!tagMatcher.find()) {
            System.out.println("Tag name bulunamadı");
            return null;
        }
        String tagName = tagMatcher.group(1);
        System.out.println("Tag name: " + tagName);
        
        // Public repo için önce browser_download_url'i tercih edelim (daha basit)
        Pattern dlPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+PhotoSender-[^\"]+\\.msi)\"");
        Matcher dlMatcher = dlPattern.matcher(json);
        if (dlMatcher.find()) {
            String dlUrl = dlMatcher.group(1);
            System.out.println("Asset download url (browser_download_url - public repo için ideal): " + dlUrl);
            return dlUrl;
        }
        
        // Fallback: API URL (public repo'da da çalışır ama gereksiz)  
        Pattern apiUrlPattern = Pattern.compile("\"url\"\\s*:\\s*\"(https://api\\.github\\.com/repos/[^\"]+/releases/assets/\\d+)\"");
        Matcher apiMatcher = apiUrlPattern.matcher(json);
        if (apiMatcher.find()) {
            String apiUrl = apiMatcher.group(1);
            System.out.println("Asset API URL bulundu (fallback): " + apiUrl);
            return apiUrl;
        }
        
        // Eğer bulunamazsa, assets bloğunu detaylı inceleyelim
        Matcher assetsMatcher = ASSETS_BLOCK.matcher(json);
        if (!assetsMatcher.find()) {
            System.out.println("Assets bloğu bulunamadı");
            return null;
        }
        String assetsJson = assetsMatcher.group(1);
        System.out.println("Assets JSON bloğu: " + assetsJson);
        
        // Asset name ve API URL'ini birlikte arayalım
        Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"(PhotoSender-[^\"]+\\.msi)\"");
        Matcher nameMatcher = namePattern.matcher(assetsJson);
        if (nameMatcher.find()) {
            String name = nameMatcher.group(1);
            System.out.println("Asset name: " + name);
            
            // Aynı asset object'inde API URL'ini arayalım
            Pattern urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"(https://api\\.github\\.com/repos/[^\"]+/releases/assets/\\d+)\"");
            Matcher urlMatcher = urlPattern.matcher(assetsJson);
            if (urlMatcher.find()) {
                String apiUrl = urlMatcher.group(1);
                System.out.println("Asset API URL bulundu: " + apiUrl);
                return apiUrl;
            }
            
            // API URL bulunamazsa manuel URL oluştur
            String dlUrl = "https://github.com/" + REPO + "/releases/download/" + tagName + "/" + name;
            System.out.println("Manuel oluşturulan download URL: " + dlUrl);
            return dlUrl;
        }
        
        System.out.println("MSI asset bulunamadı");
        return null;
    }
}
