/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import java.awt.*;

/**
 * GitHub release üzerinden güncelleme kontrolü yapan sınıf
 * 
 * Özellikler:
 * - GitHub API kullanarak en son release'i kontrol eder
 * - PhotoViewer ile başlayan .msi dosyalarını bulur
 * - MSI dosyası mevcut olan herhangi bir release'i güncelleme olarak algılar (sürüm kıyaslaması yapmaz)
 * - Private repository senaryosunu destekler (404/403 hataları için özel mesajlar)
 * - Repository sadece yeni sürüm yayınlandığında public olur, sonra private'a döner
 * - Otomatik indirme ve kurulum özelliği ile progress bar desteği
 */
public class UpdateManager {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/yusufziyrek/DigitalPhotoTransfer/releases/latest";
    private static final String DOWNLOAD_URL_TEMPLATE = "https://github.com/yusufziyrek/DigitalPhotoTransfer/releases/download/{tag}/PhotoViewer-{version}.msi";
    private static final AppLogger logger = new AppLogger("UpdateManager");
    
    private final String currentVersion;
    private final JFrame parentFrame;
    
    public UpdateManager(String currentVersion, JFrame parentFrame) {
        this.currentVersion = currentVersion;
        this.parentFrame = parentFrame;
        logger.info("UpdateManager başlatıldı - Güncel sürüm: v" + currentVersion);
        logger.info("GitHub Repository: " + GITHUB_API_URL);
    }
    
    /**
     * Başlangıçta otomatik güncelleme kontrolü yapar (arka planda)
     */
    public void checkForUpdatesOnStartup() {
        logger.info("Otomatik güncelleme kontrolü başlatılıyor...");
        SwingUtilities.invokeLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 2 saniye bekle ki UI yüklensin
                    logger.info("UI yüklendi, güncelleme kontrolü yapılıyor...");
                    UpdateInfo updateInfo = checkForUpdates();
                    if (updateInfo != null && updateInfo.hasUpdate) {
                        // PhotoViewer MSI dosyası bulundu, kullanıcıya bildir
                        logger.success("PhotoViewer MSI dosyası bulundu! Release: v" + updateInfo.latestVersion);
                        SwingUtilities.invokeLater(() -> {
                            showUpdateNotification(updateInfo);
                        });
                    } else if (updateInfo != null && updateInfo.errorMessage != null) {
                        logger.warn("Otomatik güncelleme kontrolü sonucu: " + updateInfo.errorMessage);
                    } else if (updateInfo != null) {
                        logger.info("Otomatik güncelleme kontrolü tamamlandı. PhotoViewer MSI dosyası bulunamadı.");
                    } else {
                        logger.warn("Otomatik güncelleme kontrolü başarısız oldu");
                    }
                } catch (Exception e) {
                    logger.warn("Otomatik güncelleme kontrolü başarısız: " + e.getMessage());
                }
            }).start();
        });
    }
    
    /**
     * Manuel güncelleme kontrolü yapar (menüden çağrılır)
     */
    public void checkForUpdatesManual() {
        logger.info("Manuel güncelleme kontrolü başlatıldı");
        new Thread(() -> {
            try {
                // Progress dialog göster
                SwingUtilities.invokeLater(() -> {
                    JOptionPane optionPane = new JOptionPane("Güncellemeler kontrol ediliyor...", 
                                                           JOptionPane.INFORMATION_MESSAGE, 
                                                           JOptionPane.DEFAULT_OPTION, 
                                                           null, new Object[]{}, null);
                    JDialog dialog = optionPane.createDialog(parentFrame, "Güncelleme Kontrolü");
                    dialog.setModal(false);
                    dialog.setVisible(true);
                    
                    new Thread(() -> {
                        try {
                            UpdateInfo updateInfo = checkForUpdates();
                            SwingUtilities.invokeLater(() -> {
                                dialog.dispose();
                                if (updateInfo != null && updateInfo.hasUpdate) {
                                    logger.success("Manuel kontrol: PhotoViewer MSI dosyası bulundu! Release: v" + updateInfo.latestVersion);
                                    showUpdateDialog(updateInfo);
                                } else if (updateInfo != null) {
                                    String message;
                                    if (updateInfo.errorMessage != null) {
                                        logger.warn("Manuel kontrol sonucu: " + updateInfo.errorMessage);
                                        message = updateInfo.errorMessage;
                                    } else {
                                        logger.info("Manuel kontrol tamamlandı. PhotoViewer MSI dosyası bulunamadı.");
                                        message = "Mevcut release'de PhotoViewer MSI dosyası bulunamadı.";
                                    }
                                    JOptionPane.showMessageDialog(parentFrame, 
                                        message, 
                                        "Güncelleme Kontrolü", 
                                        updateInfo.errorMessage != null ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(parentFrame, 
                                        "Güncelleme kontrolü yapılamadı. İnternet bağlantınızı kontrol edin.", 
                                        "Hata", 
                                        JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        } catch (Exception e) {
                            SwingUtilities.invokeLater(() -> {
                                dialog.dispose();
                                logger.error("Manuel güncelleme kontrolü başarısız", e);
                                JOptionPane.showMessageDialog(parentFrame, 
                                    "Güncelleme kontrolü sırasında hata: " + e.getMessage(), 
                                    "Hata", 
                                    JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    }).start();
                });
            } catch (Exception e) {
                logger.error("Manuel güncelleme kontrolü başarısız", e);
            }
        }).start();
    }
    
    /**
     * GitHub API'den en son release bilgisini alır
     */
    private UpdateInfo checkForUpdates() {
        try {
            logger.info("Güncelleme kontrolü başlatılıyor: " + GITHUB_API_URL);
            
            URI uri = URI.create(GITHUB_API_URL);
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "PhotoViewer/" + currentVersion);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                return parseReleaseInfo(response.toString());
            } else if (responseCode == 404) {
                logger.warn("Repository veya release bulunamadı (404) - Repository şu anda private olabilir");
                return createNoUpdateInfo("Repository şu anda private durumda. Yeni sürüm duyurusu bekleyiniz.");
            } else if (responseCode == 403) {
                logger.warn("Repository'e erişim izni yok (403) - API rate limit veya private repository");
                return createNoUpdateInfo("Repository erişim sorunu. Lütfen daha sonra tekrar deneyin.");
            } else {
                logger.warn("GitHub API yanıt kodu: " + responseCode);
                // Hata mesajını da okumaya çalış
                try {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    logger.warn("GitHub API hata yanıtı: " + errorResponse.toString());
                } catch (Exception ignored) {}
                return null;
            }
        } catch (Exception e) {
            logger.error("Güncelleme kontrolü hatası", e);
            return null;
        }
    }
    
    /**
     * Güncelleme olmadığı durumunda bilgi mesajı ile UpdateInfo oluşturur
     */
    private UpdateInfo createNoUpdateInfo(String message) {
        UpdateInfo info = new UpdateInfo();
        info.latestVersion = currentVersion;
        info.hasUpdate = false;
        info.errorMessage = message;
        return info;
    }
    
    /**
     * GitHub API JSON yanıtını parse eder
     */
    private UpdateInfo parseReleaseInfo(String jsonResponse) {
        try {
            // Basit JSON parsing (JSON library kullanmadan)
            Pattern tagPattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
            Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
            Pattern bodyPattern = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]+)\"");
            // PhotoViewer ile başlayan .msi dosyalarını ara
            Pattern urlPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*PhotoViewer[^\"]*\\.msi[^\"]*?)\"");
            
            Matcher tagMatcher = tagPattern.matcher(jsonResponse);
            Matcher nameMatcher = namePattern.matcher(jsonResponse);
            Matcher bodyMatcher = bodyPattern.matcher(jsonResponse);
            
            if (tagMatcher.find()) {
                String latestTag = tagMatcher.group(1);
                String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
                
                UpdateInfo info = new UpdateInfo();
                info.latestVersion = latestVersion;
                info.tagName = latestTag;
                
                // İlk önce PhotoViewer MSI dosyasını bul
                String foundMsiUrl = null;
                Matcher urlMatcher = urlPattern.matcher(jsonResponse);
                if (urlMatcher.find()) {
                    foundMsiUrl = urlMatcher.group(1);
                    logger.info("PhotoViewer MSI dosyası bulundu: " + foundMsiUrl);
                } else {
                    // Assets içinde PhotoViewer MSI dosyasını arayalım
                    foundMsiUrl = findPhotoViewerMsiInAssets(jsonResponse, latestTag);
                    if (foundMsiUrl != null) {
                        logger.info("Assets içinde PhotoViewer MSI bulundu: " + foundMsiUrl);
                    }
                }
                
                // PhotoViewer MSI dosyası varsa her zaman güncelleme olarak değerlendir
                if (foundMsiUrl != null) {
                    info.downloadUrl = foundMsiUrl;
                    info.hasUpdate = true; // MSI varsa her zaman güncelleme var
                    logger.success("PhotoViewer MSI bulundu - Güncelleme mevcut olarak işaretlendi");
                } else {
                    // MSI bulunamadıysa template kullan ama güncelleme yok
                    info.downloadUrl = DOWNLOAD_URL_TEMPLATE
                        .replace("{tag}", latestTag)
                        .replace("{version}", latestVersion);
                    info.hasUpdate = false;
                    logger.warn("PhotoViewer MSI bulunamadı - Güncelleme yok olarak işaretlendi");
                }
                
                if (nameMatcher.find()) {
                    info.releaseName = nameMatcher.group(1);
                }
                if (bodyMatcher.find()) {
                    info.releaseNotes = bodyMatcher.group(1).replace("\\n", "\n").replace("\\r", "");
                }
                
                logger.info("Sürüm kontrolü tamamlandı - Güncel: " + currentVersion + ", Release: " + latestVersion + ", MSI mevcut: " + info.hasUpdate);
                return info;
            }
        } catch (Exception e) {
            logger.error("Release bilgisi parse hatası", e);
        }
        return null;
    }
    
    /**
     * Assets listesinde PhotoViewer ile başlayan MSI dosyasını arar
     */
    private String findPhotoViewerMsiInAssets(String jsonResponse, String tag) {
        try {
            // Assets array içindeki tüm download URL'leri bul
            Pattern assetsPattern = Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]");
            Matcher assetsMatcher = assetsPattern.matcher(jsonResponse);
            
            if (assetsMatcher.find()) {
                String assetsContent = assetsMatcher.group(1);
                
                // PhotoViewer ile başlayan ve .msi ile biten dosya isimlerini ara
                Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"(PhotoViewer[^\"]*\\.msi)\"");
                Pattern downloadUrlPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
                
                Matcher nameMatcher = namePattern.matcher(assetsContent);
                Matcher urlMatcher = downloadUrlPattern.matcher(assetsContent);
                
                // İsim ve URL'yi eşleştir
                while (nameMatcher.find() && urlMatcher.find()) {
                    String fileName = nameMatcher.group(1);
                    String downloadUrl = urlMatcher.group(1);
                    
                    // Dosya isminin PhotoViewer ile başladığını kontrol et
                    if (fileName.startsWith("PhotoViewer") && fileName.endsWith(".msi")) {
                        logger.info("PhotoViewer MSI dosyası bulundu: " + fileName + " -> " + downloadUrl);
                        return downloadUrl;
                    }
                }
                
                // Alternatif: Tüm browser_download_url'leri kontrol et
                Pattern allUrlsPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*PhotoViewer[^\"]*\\.msi[^\"]*?)\"");
                Matcher allUrlsMatcher = allUrlsPattern.matcher(assetsContent);
                if (allUrlsMatcher.find()) {
                    return allUrlsMatcher.group(1);
                }
            }
        } catch (Exception e) {
            logger.warn("Assets içinde PhotoViewer MSI arama hatası: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Başlangıçta gösterilen güncelleme bildirimi
     */
    private void showUpdateNotification(UpdateInfo updateInfo) {
        logger.info("PhotoViewer güncelleme bildirimi gösteriliyor - Release: v" + updateInfo.latestVersion);
        
        String message = "PhotoViewer güncellemesi mevcut!\n\n" +
                        "Mevcut release: v" + updateInfo.latestVersion + "\n" +
                        "MSI dosyası hazır\n\n" +
                        "Şimdi güncellemek ister misiniz?";
        
        int choice = JOptionPane.showConfirmDialog(
            parentFrame, 
            message, 
            "Güncelleme Mevcut", 
            JOptionPane.YES_NO_OPTION, 
            JOptionPane.INFORMATION_MESSAGE
        );
        
        if (choice == JOptionPane.YES_OPTION) {
            logger.info("Kullanıcı otomatik bildirimden güncellemeyi kabul etti");
            showUpdateDialog(updateInfo);
        } else {
            logger.info("Kullanıcı otomatik bildirimden güncellemeyi reddetti");
        }
    }
    
    /**
     * Detaylı güncelleme dialog'u gösterir
     */
    private void showUpdateDialog(UpdateInfo updateInfo) {
        logger.info("Detaylı güncelleme dialog'u açılıyor");
        logger.info("Gösterilecek sürüm bilgileri - Güncel: v" + currentVersion + ", Yeni: v" + updateInfo.latestVersion);
        logger.info("Release adı: " + (updateInfo.releaseName != null ? updateInfo.releaseName : "Belirtilmemiş"));
        logger.info("İndirme URL'i: " + updateInfo.downloadUrl);
        
        JDialog dialog = new JDialog(parentFrame, "PhotoViewer Güncellemesi", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(parentFrame);
        
        // Üst panel - Sürüm bilgileri
        JPanel topPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        
        JLabel currentLabel = new JLabel("Güncel Sürüm: v" + currentVersion);
        currentLabel.setFont(currentLabel.getFont().deriveFont(Font.BOLD));
        
        JLabel newLabel = new JLabel("Yeni Sürüm: v" + updateInfo.latestVersion);
        newLabel.setFont(newLabel.getFont().deriveFont(Font.BOLD));
        newLabel.setForeground(new Color(0, 150, 0));
        
        JLabel nameLabel = new JLabel(updateInfo.releaseName != null ? updateInfo.releaseName : "");
        
        topPanel.add(currentLabel);
        topPanel.add(newLabel);
        topPanel.add(nameLabel);
        
        // Orta panel - Release notes
        JTextArea notesArea = new JTextArea(updateInfo.releaseNotes != null ? updateInfo.releaseNotes : "Sürüm notları yüklenemedi.");
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBackground(dialog.getBackground());
        notesArea.setBorder(BorderFactory.createTitledBorder("Sürüm Notları"));
        
        JScrollPane scrollPane = new JScrollPane(notesArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));
        
        // Alt panel - Butonlar
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));
        
        JButton downloadButton = new JButton("İndir ve Yükle");
        downloadButton.addActionListener(e -> {
            logger.info("Kullanıcı 'İndir ve Yükle' butonunu tıkladı");
            dialog.dispose();
            downloadUpdate(updateInfo);
        });
        
        JButton laterButton = new JButton("Daha Sonra");
        laterButton.addActionListener(e -> {
            logger.info("Kullanıcı 'Daha Sonra' butonunu tıkladı, güncelleme ertelendi");
            dialog.dispose();
        });
        
        buttonPanel.add(downloadButton);
        buttonPanel.add(laterButton);
        
        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }
    
    /**
     * Güncellemeyi indirir ve yükleme işlemini başlatır
     */
    private void downloadUpdate(UpdateInfo updateInfo) {
        logger.info("Otomatik güncelleme indirme işlemi başlatıldı");
        logger.info("İndirilecek dosya: " + updateInfo.downloadUrl);
        logger.info("Hedef sürüm: v" + updateInfo.latestVersion + " (Güncel: v" + currentVersion + ")");
        
        // Progress dialog oluştur
        JDialog progressDialog = new JDialog(parentFrame, "PhotoViewer Güncelleniyor", true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.setSize(450, 180);
        progressDialog.setLocationRelativeTo(parentFrame);
        progressDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        JLabel statusLabel = new JLabel("İndirme hazırlanıyor...", JLabel.CENTER);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        
        // İptal butonu ekle
        JButton cancelButton = new JButton("İptal");
        final boolean[] cancelled = {false}; // Array to make it effectively final
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(cancelButton);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        progressDialog.add(panel);
        
        // İptal butonuna listener ekle
        cancelButton.addActionListener(e -> {
            cancelled[0] = true;
            progressDialog.dispose();
            logger.info("Kullanıcı indirme işlemini iptal etti");
        });
        
        // Dialog kapandığında da iptal et
        progressDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancelled[0] = true;
                logger.info("Progress dialog kapatıldı, indirme iptal ediliyor");
            }
        });
        
        // İndirme işlemini arka planda başlat (dialog gösterilmeden ÖNCE)
        Thread downloadThread = new Thread(() -> {
            try {
                downloadAndInstall(updateInfo, statusLabel, progressBar, progressDialog, cancelled);
            } catch (Exception e) {
                logger.error("Güncelleme indirme hatası", e);
                SwingUtilities.invokeLater(() -> {
                    if (!cancelled[0]) {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(
                            parentFrame,
                            "Güncelleme indirilemedi: " + e.getMessage(),
                            "Hata",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                });
            }
        });
        
        // Önce thread'i başlat, sonra dialog'u göster
        downloadThread.start();
        progressDialog.setVisible(true);
    }
    
    /**
     * MSI dosyasını indirir ve otomatik kurulumu başlatır
     */
    private void downloadAndInstall(UpdateInfo updateInfo, JLabel statusLabel, JProgressBar progressBar, JDialog progressDialog, boolean[] cancelled) throws Exception {
        logger.info("MSI dosyası indiriliyor: " + updateInfo.downloadUrl);
        
        // Geçici dosya oluştur
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File msiFile = new File(tempDir, "PhotoViewer-" + updateInfo.latestVersion + ".msi");
        
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Bağlantı kuruluyor...");
            progressBar.setIndeterminate(true);
        });
        
        // HTTP bağlantısı kur
        URI uri = URI.create(updateInfo.downloadUrl);
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "PhotoViewer/" + currentVersion);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP Error: " + responseCode);
        }
        
        long fileSize = connection.getContentLengthLong();
        logger.info("MSI dosya boyutu: " + (fileSize > 0 ? fileSize + " bytes" : "Bilinmiyor"));
        
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("İndiriliyor: " + msiFile.getName());
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
        });
        
        // Dosyayı indir
        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(msiFile)) {
            
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1 && !cancelled[0]) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                if (fileSize > 0) {
                    final int progress = (int) ((totalBytesRead * 100) / fileSize);
                    final long finalTotalBytes = totalBytesRead;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress);
                        progressBar.setString(progress + "% (" + formatBytes(finalTotalBytes) + "/" + formatBytes(fileSize) + ")");
                    });
                }
                
                // Thread.sleep kullanarak UI'ın donmasını önle
                if (totalBytesRead % (64 * 1024) == 0) { // Her 64KB'de bir
                    Thread.sleep(1);
                }
            }
            
            // İptal kontrolü
            if (cancelled[0]) {
                logger.info("İndirme işlemi kullanıcı tarafından iptal edildi");
                if (msiFile.exists()) {
                    msiFile.delete();
                }
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                });
                return;
            }
        }
        
        logger.success("MSI dosyası başarıyla indirildi: " + msiFile.getAbsolutePath());
        
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Kurulum başlatılıyor...");
            progressBar.setIndeterminate(true);
            progressBar.setString("Kurulum hazırlanıyor...");
        });
        
        // Kurulumu başlat
        startInstallation(msiFile, progressDialog);
    }
    
    /**
     * MSI kurulumunu başlatır ve uygulamayı kapatır
     */
    private void startInstallation(File msiFile, JDialog progressDialog) {
        try {
            logger.info("MSI kurulumu başlatılıyor: " + msiFile.getAbsolutePath());
            
            // Windows Installer komutunu hazırla
            String[] command = {
                "msiexec.exe",
                "/i",
                msiFile.getAbsolutePath(),
                "/qb",  // Basic UI ile kurulum
                "/passive"  // Kullanıcı müdahalesi gerektirmez
            };
            
            logger.info("Kurulum komutu: " + String.join(" ", command));
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(msiFile.getParentFile());
            
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                
                String message = "PhotoViewer güncelleme kurulumu başlatıldı.\n\n" +
                               "Kurulum otomatik olarak devam edecek.\n" +
                               "Eski uygulama şimdi kapatılacak.\n\n" +
                               "Kurulum tamamlandıktan sonra yeni PhotoViewer'ı başlatabilirsiniz.";
                
                JOptionPane.showMessageDialog(
                    parentFrame,
                    message,
                    "Kurulum Başlatıldı",
                    JOptionPane.INFORMATION_MESSAGE
                );
            });
            
            // Kurulumu başlat
            Process process = processBuilder.start();
            logger.success("MSI kurulum süreci başlatıldı - PID: " + process.pid());
            
            // Kurulum başladığına emin olduktan sonra uygulamayı kapat
            Thread.sleep(2000); // 2 saniye bekle ki kurulum başlasın
            
            logger.info("Kurulum başladı, eski uygulama kapatılıyor...");
            
            SwingUtilities.invokeLater(() -> {
                try {
                    // Tüm pencereleri kapat
                    if (parentFrame != null) {
                        parentFrame.dispose();
                    }
                    // JVM'i kapat
                    System.exit(0);
                } catch (Exception e) {
                    logger.error("Uygulama kapatma hatası", e);
                }
            });
            
        } catch (Exception e) {
            logger.error("MSI kurulum hatası", e);
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                JOptionPane.showMessageDialog(
                    parentFrame,
                    "Kurulum başlatılamadı: " + e.getMessage() + "\n\n" +
                    "Lütfen MSI dosyasını manuel olarak çalıştırın:\n" + msiFile.getAbsolutePath(),
                    "Kurulum Hatası",
                    JOptionPane.ERROR_MESSAGE
                );
            });
        }
    }
    
    /**
     * Byte sayısını okunabilir formata çevirir
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Güncelleme bilgilerini tutan iç sınıf
     */
    private static class UpdateInfo {
        String latestVersion;
        String tagName;
        String releaseName;
        String releaseNotes;
        String downloadUrl;
        String errorMessage;
        boolean hasUpdate;
    }
}
