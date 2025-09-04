import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
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
    private static final String REPO = AppConstants.GITHUB_REPO;
    private static final String LATEST_API = AppConstants.GITHUB_API_URL;
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    
    // Caching for API responses
    private static long lastCheckTime = 0;
    private static String cachedResponse = null;
    private static final long CACHE_DURATION = AppConstants.UPDATE_CACHE_DURATION_MS;
    
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
                    String json = httpGetWithRetry(LATEST_API, AppConstants.UPDATE_MAX_RETRIES);
                    if (json == null || json.trim().isEmpty()) {
                        error = "GitHub API yanıtı boş";
                        logger.error("GitHub API yanıtı boş veya null");
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
                                AppMessages.TITLE_INFO, JOptionPane.INFORMATION_MESSAGE);
                        }
                        System.out.println("Private repository - güncelleme kontrolü atlandı");
                        return;
                    } else {
                        // Diğer hatalar
                        logger.error("Güncelleme kontrolü başarısız: " + error);
                        if (!silentIfUpToDate) {
                            JOptionPane.showMessageDialog(parent, 
                                AppMessages.withDetails(AppMessages.ERROR_UPDATE_CHECK, error), 
                                AppMessages.TITLE_UPDATE, JOptionPane.WARNING_MESSAGE);
                        }
                        return;
                    }
                }
                
                if (latestTag == null) {
                    logger.warn("Release tag bulunamadı");
                    if (!silentIfUpToDate) {
                        JOptionPane.showMessageDialog(parent, "Release tag bulunamadı.", AppMessages.TITLE_UPDATE, JOptionPane.INFORMATION_MESSAGE);
                    }
                    return;
                }
                
                // MSI kontrolü - Repository sadece yeni sürüm varken public olur
                // Bu yüzden MSI varsa kesinlikle yeni sürüm var demektir
                if (msiUrl == null) {
                    logger.info("PhotoSender MSI asset bulunamadı - Repository private veya MSI henüz hazır değil (Tag: " + latestTag + ")");
                    if (!silentIfUpToDate) {
                        JOptionPane.showMessageDialog(parent, 
                            "Şu anda güncel sürümü kullanıyorsunuz.\n\n" +
                            "Repository private durumda veya PhotoSender MSI henüz hazırlanmadı.\n" +
                            "Yeni sürüm çıktığında otomatik olarak bildirilecektir.",
                            AppMessages.TITLE_INFO, JOptionPane.INFORMATION_MESSAGE);
                    }
                    return;
                }
                
                // MSI bulundu = Yeni sürüm mevcut
                logger.info("YENİ SÜRÜM BULUNDU! PhotoSender MSI mevcut - Tag: " + latestTag);
                logger.info("MSI download URL: " + msiUrl);
                System.out.println("Yeni sürüm mevcut: " + latestTag);
                int res = JOptionPane.showConfirmDialog(parent,
                        "Yeni PhotoSender sürümü mevcut: " + latestTag + "\n\n" +
                        "MSI dosyası indirilerek kurulum başlatılacak.\n" +
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

    private static String httpGetWithRetry(String urlStr, int retryCount) throws IOException {
        // Cache kontrolü
        long now = System.currentTimeMillis();
        if (cachedResponse != null && (now - lastCheckTime) < CACHE_DURATION && retryCount == 0) {
            System.out.println("Cache'den yanıt kullanılıyor");
            return cachedResponse;
        }
        
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                java.net.URL url = java.net.URI.create(urlStr).toURL();
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setRequestProperty("Accept", "application/vnd.github.v3+json");
                c.setRequestProperty("User-Agent", String.format(AppConstants.USER_AGENT_TEMPLATE, PhotoSenderApp.VERSION));
                
                c.setConnectTimeout(AppConstants.UPDATE_CONNECTION_TIMEOUT_MS);
                c.setReadTimeout(AppConstants.UPDATE_READ_TIMEOUT_MS);
                
                int code = c.getResponseCode();
                System.out.println("HTTP yanıt kodu: " + code + " (deneme: " + (attempt + 1) + ")");
                
                if (code >= 400) {
                    String err = readStream(c.getErrorStream());
                    IOException httpException = new IOException("HTTP " + code + (err == null ? "" : (": " + err)));
                    if (attempt < retryCount && isRetryableError(code)) {
                        lastException = httpException;
                        logger.warn("HTTP hatası, tekrar denenecek: " + code + " (deneme: " + (attempt + 1) + "/" + (retryCount + 1) + ")");
                        Thread.sleep(AppConstants.UPDATE_RETRY_DELAY_MS * (attempt + 1)); // Exponential backoff
                        continue;
                    } else {
                        throw httpException;
                    }
                }
                
                String result = readStream(c.getInputStream());
                
                // Başarılı, cache'e kaydet
                if (result != null && !result.trim().isEmpty()) {
                    lastCheckTime = now;
                    cachedResponse = result;
                    logger.info("GitHub API yanıtı başarıyla alındı ve cache'lendi (" + result.length() + " karakter)");
                }
                
                return result;
                
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("İşlem kesintiye uğradı", ie);
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryCount && isRetryableException(e)) {
                    logger.warn("Bağlantı hatası, tekrar denenecek: " + e.getMessage() + " (deneme: " + (attempt + 1) + "/" + (retryCount + 1) + ")");
                    try {
                        Thread.sleep(AppConstants.UPDATE_RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("İşlem kesintiye uğradı", ie);
                    }
                } else {
                    if (e instanceof IOException) {
                        throw (IOException) e;
                    } else {
                        throw new IOException("Güncelleme kontrolü hatası: " + e.getMessage(), e);
                    }
                }
            }
        }
        
        // Tüm denemeler başarısız
        if (lastException instanceof IOException) {
            throw (IOException) lastException;
        } else {
            throw new IOException("Güncelleme kontrolü başarısız (tüm denemeler tükendi): " + lastException.getMessage(), lastException);
        }
    }
    
    private static boolean isRetryableError(int httpCode) {
        // 5xx server errors ve bazı 4xx client errors retry edilebilir
        return (httpCode >= 500) || httpCode == 408 || httpCode == 429;
    }
    
    private static boolean isRetryableException(Exception e) {
        // Network timeout, connection refused vs. retry edilebilir
        String message = e.getMessage().toLowerCase();
        return message.contains("timeout") || 
               message.contains("connection") || 
               message.contains("socket") ||
               message.contains("network") ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof java.net.ConnectException ||
               e instanceof java.net.SocketException;
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
                    
                    // Güvenli dosya adı oluştur
                    String fileName = AppConstants.MSI_TEMP_PREFIX + normalizeVersion(version) + AppConstants.MSI_TEMP_SUFFIX;
                    tempMsi = new File(System.getProperty("java.io.tmpdir"), fileName);
                    logger.info("Geçici dosya yolu: " + tempMsi.getAbsolutePath());
                    
                    // Eski dosyayı temizle
                    if (tempMsi.exists()) {
                        tempMsi.delete();
                    }
                    
                    downloadMsiWithRetry(url, tempMsi);
                    
                } catch (Exception ex) {
                    logger.error("MSI indirme hatası", ex);
                    System.err.println("MSI download failed: " + ex.getMessage());
                    ex.printStackTrace(); // Stack trace for debugging
                    error = ex.getMessage();
                    publish("Hata: " + error);
                }
                return null;
            }
            
            private void downloadMsiWithRetry(String url, File tempMsi) throws Exception {
                Exception lastException = null;
                
                for (int attempt = 0; attempt < AppConstants.UPDATE_MAX_RETRIES; attempt++) {
                    try {
                        java.net.URL u = java.net.URI.create(url).toURL();
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                        c.setRequestProperty("User-Agent", String.format(AppConstants.USER_AGENT_TEMPLATE, PhotoSenderApp.VERSION));
                        c.setConnectTimeout(AppConstants.UPDATE_CONNECTION_TIMEOUT_MS);
                        c.setReadTimeout(AppConstants.UPDATE_READ_TIMEOUT_MS);
                        
                        int code = c.getResponseCode();
                        logger.info("MSI indirme HTTP yanıt kodu: " + code + " (deneme: " + (attempt + 1) + ")");
                        System.out.println("MSI indirme HTTP yanıt kodu: " + code + " (deneme: " + (attempt + 1) + ")");
                        System.out.println("MSI indirme URL: " + url);
                        
                        if (code >= 400) {
                            String err = readStream(c.getErrorStream());
                            IOException httpException = new IOException("HTTP " + code + (err == null ? "" : (": " + err)));
                            
                            if (attempt < AppConstants.UPDATE_MAX_RETRIES - 1 && isRetryableError(code)) {
                                lastException = httpException;
                                logger.warn("MSI indirme HTTP hatası, tekrar denenecek: " + code + " (deneme: " + (attempt + 1) + "/" + AppConstants.UPDATE_MAX_RETRIES + ")");
                                Thread.sleep(AppConstants.UPDATE_RETRY_DELAY_MS * (attempt + 1));
                                continue;
                            } else {
                                throw httpException;
                            }
                        }
                        
                        // Dosya boyutunu kontrol et
                        long fileSize = c.getContentLengthLong();
                        logger.info("MSI dosya boyutu: " + (fileSize > 0 ? (fileSize / (1024 * 1024)) + " MB" : "bilinmiyor"));
                        
                        if (fileSize > 0) {
                            if (fileSize < AppConstants.MIN_MSI_SIZE_BYTES) {
                                throw new IOException("MSI dosyası çok küçük: " + fileSize + " bytes");
                            }
                            if (fileSize > AppConstants.MAX_MSI_SIZE_BYTES) {
                                throw new IOException("MSI dosyası çok büyük: " + (fileSize / (1024 * 1024)) + " MB");
                            }
                        }
                        
                        publish("Dosya boyutu: " + (fileSize > 0 ? (fileSize / (1024 * 1024)) + " MB" : "Bilinmiyor"));
                        
                        // Dosyayı indir
                        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tempMsi)) {
                            byte[] buf = new byte[AppConstants.UPDATE_BUFFER_SIZE];
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
                        
                        // İndirme tamamlandı, dosya boyutunu kontrol et
                        if (!tempMsi.exists() || tempMsi.length() == 0) {
                            throw new IOException("İndirilen dosya boş veya oluşturulamadı");
                        }
                        
                        if (fileSize > 0 && tempMsi.length() != fileSize) {
                            throw new IOException("İndirilen dosya boyutu uyuşmuyor. Beklenen: " + fileSize + ", Gerçek: " + tempMsi.length());
                        }
                        
                        logger.info("MSI indirme tamamlandı - Dosya boyutu: " + (tempMsi.length() / (1024 * 1024)) + " MB");
                        publish("İndirme tamamlandı!");
                        return; // Başarılı
                        
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("İndirme kesintiye uğradı", ie);
                    } catch (Exception e) {
                        lastException = e;
                        if (attempt < AppConstants.UPDATE_MAX_RETRIES - 1 && isRetryableException(e)) {
                            logger.warn("MSI indirme hatası, tekrar denenecek: " + e.getMessage() + " (deneme: " + (attempt + 1) + "/" + AppConstants.UPDATE_MAX_RETRIES + ")");
                            try {
                                Thread.sleep(AppConstants.UPDATE_RETRY_DELAY_MS * (attempt + 1));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new IOException("İndirme kesintiye uğradı", ie);
                            }
                        } else {
                            throw e;
                        }
                    }
                }
                
                // Tüm denemeler başarısız
                throw new IOException("MSI indirme başarısız (tüm denemeler tükendi): " + lastException.getMessage(), lastException);
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
                    JOptionPane.showMessageDialog(parent, AppMessages.withDetails(AppMessages.ERROR_UPDATE_DOWNLOAD, error), AppMessages.TITLE_UPDATE, JOptionPane.ERROR_MESSAGE);
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
            AppMessages.formatUpdateInstallConfirm((int)(msiFile.length() / (1024 * 1024))),
            AppMessages.TITLE_CONFIRM,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            try {
                logger.info("Standart MSI kurulum komutu çalıştırılıyor...");
                System.out.println("MSI kurulumu başlatılıyor: " + msiFile.getAbsolutePath());
                
                // Standart MSI kurulum komutunu kullan
                String[] command = new String[AppConstants.MSI_INSTALL_COMMAND_STANDARD.length + 1];
                System.arraycopy(AppConstants.MSI_INSTALL_COMMAND_STANDARD, 0, command, 0, AppConstants.MSI_INSTALL_COMMAND_STANDARD.length);
                command[command.length - 1] = msiFile.getAbsolutePath();
                
                ProcessBuilder pb = new ProcessBuilder(command);
                logger.info("ProcessBuilder komutu: " + String.join(" ", pb.command()));
                
                // Kurulum başlat
                pb.start();
                
                logger.info("MSI kurulum süreci başlatıldı - Uygulama " + AppConstants.UPDATE_EXIT_DELAY_SECONDS + " saniye sonra kapanacak");
                
                // Standart bekleme süresi ile güvenli şekilde uygulamayı kapat
                Timer timer = new Timer(AppConstants.UPDATE_EXIT_DELAY_SECONDS * 1000, _ -> {
                    logger.info("Uygulama kapatılıyor - MSI kurulumu devam ediyor");
                    System.out.println("Kurulum başlatıldı - Uygulama kapatılıyor");
                    System.exit(0);
                });
                timer.setRepeats(false);
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                        parentFrame,
                        AppMessages.formatMsiInstallStarted(AppConstants.UPDATE_EXIT_DELAY_SECONDS),
                        AppMessages.TITLE_UPDATE,
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    timer.start();
                });
                
            } catch (Exception e) {
                logger.error("MSI kurulum başlatma hatası", e);
                System.err.println("MSI installation failed: " + e.getMessage());
                e.printStackTrace();
                
                // Hata durumunda manuel kurulum seçeneği sun
                int manualChoice = JOptionPane.showConfirmDialog(
                    parentFrame,
                    AppMessages.withException(AppMessages.ERROR_UPDATE_INSTALL, e) + "\n\n" +
                    AppMessages.formatMsiManualInstall(msiFile.getAbsolutePath()),
                    AppMessages.TITLE_ERROR,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE
                );
                
                if (manualChoice == JOptionPane.YES_OPTION) {
                    try {
                        // Dosya gezginini aç
                        Desktop.getDesktop().open(msiFile.getParentFile());
                    } catch (Exception ex) {
                        logger.error("Dosya gezgini açma hatası", ex);
                    }
                }
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
        
        // Public repo için önce browser_download_url'i tercih edelim (PhotoSender specific)
        Pattern dlPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+PhotoSender-[^\"]+\\.msi)\"");
        Matcher dlMatcher = dlPattern.matcher(json);
        if (dlMatcher.find()) {
            String dlUrl = dlMatcher.group(1);
            System.out.println("PhotoSender asset download url (browser_download_url): " + dlUrl);
            return dlUrl;
        } else {
            // Debug: Tüm MSI dosyalarını logla
            Pattern allMsiPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.msi)\"");
            Matcher allMsiMatcher = allMsiPattern.matcher(json);
            System.out.println("Bulunan MSI dosyaları:");
            while (allMsiMatcher.find()) {
                String foundMsi = allMsiMatcher.group(1);
                System.out.println("  - " + foundMsi);
                if (foundMsi.contains("PhotoViewer")) {
                    System.out.println("    ^ Bu PhotoViewer MSI'sı - PhotoSender için uygun değil");
                }
            }
        }
        
        // Fallback: API URL (PhotoSender specific assets only)
        // First, find assets that contain PhotoSender in the name
        Pattern assetsPattern = Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher assetsMatcherFallback = assetsPattern.matcher(json);
        if (assetsMatcherFallback.find()) {
            String assetsContent = assetsMatcherFallback.group(1);
            
            // Look for PhotoSender MSI assets with both name and URL
            Pattern photoSenderAssetPattern = Pattern.compile("\\{[^}]*\"name\"\\s*:\\s*\"(PhotoSender[^\"]*\\.msi)\"[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}");
            Matcher photoSenderAssetMatcher = photoSenderAssetPattern.matcher(assetsContent);
            if (photoSenderAssetMatcher.find()) {
                String fileName = photoSenderAssetMatcher.group(1);
                String apiUrl = photoSenderAssetMatcher.group(2);
                System.out.println("PhotoSender asset API URL bulundu: " + fileName + " -> " + apiUrl);
                return apiUrl;
            }
        }
        
        System.out.println("PhotoSender için uygun MSI asset bulunamadı");
        return null;
    }
}
