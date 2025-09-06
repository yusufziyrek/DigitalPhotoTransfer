/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;
import java.util.Properties;
import java.awt.Robot;

public class PhotoViewerServer {
    private static final AppLogger logger = new AppLogger("PhotoViewer");
    private static UpdateManager updateManager = null;
    
    // Properties dosyası thread-safe erişimi için kilit
    private static final Object CONFIG_LOCK = new Object();
    
    public static void main(String[] args) {
        int port = AppConstants.DEFAULT_PORT; // Dinlenecek port
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Özel panel: kalite bozulmadan fotoğraf gösterimi
        PhotoPanel photoPanel = new PhotoPanel();
        frame.setContentPane(photoPanel);
        photoPanel.setParentFrame(frame);

        // UpdateManager'ı erken başlat (UI oluşturulduktan hemen sonra)
        logger.info("UpdateManager başlatılıyor...");
        updateManager = new UpdateManager(AppConstants.VERSION, frame, logger);

        // ESC ile kapatma
        frame.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    // Timer'ları temizle
                    photoPanel.dispose();
                    frame.dispose();
                    System.exit(0);
                }
            }
        });

        // Config dosyası ve otomatik/manuel tercihlerin yönetimi
        File configFile = getConfigFile();
        File appDir = new File(System.getProperty("user.dir")); // Basitleştirildi

        BufferedImage defaultImage = null;

        Properties props = loadProperties(configFile);

        String mode = props.getProperty(AppConstants.CONFIG_MODE_KEY, AppConstants.MODE_PROMPT); // PROMPT, MANUAL, AUTO
        String savedImagePath = props.getProperty(AppConstants.CONFIG_SAVED_IMAGE_PATH_KEY, "");
        String autoFolder = props.getProperty(AppConstants.CONFIG_AUTO_FOLDER_KEY, "");

    // If there is no config (first run), ask user once at first start and save it.
    if (!configFile.exists()) {
            JFileChooser fc = createImageFileChooser(frame);
            int r = fc.showOpenDialog(frame);
            if (r == JFileChooser.APPROVE_OPTION) {
                File sel = fc.getSelectedFile();
                try {
                    BufferedImage img = ImageIO.read(sel);
                    if (img != null) {
                        defaultImage = img;
                        props.setProperty(AppConstants.CONFIG_MODE_KEY, AppConstants.MODE_MANUAL);
                        props.setProperty(AppConstants.CONFIG_SAVED_IMAGE_PATH_KEY, sel.getAbsolutePath());
                        saveProperties(configFile, props);
                    } else {
                        // leave defaultImage null and continue with existing flow
                    }
                } catch (IOException ignored) {
                }
            }
            // reload savedImagePath in case it was written
            savedImagePath = props.getProperty(AppConstants.CONFIG_SAVED_IMAGE_PATH_KEY, "");
            mode = props.getProperty(AppConstants.CONFIG_MODE_KEY, mode);
        }

        // Eğer kaydedilmiş manuel resim yolu varsa yüklemeyi dene
        if (AppConstants.MODE_MANUAL.equalsIgnoreCase(mode) && !savedImagePath.isEmpty()) {
            File f = new File(savedImagePath);
            if (f.exists()) {
                try { defaultImage = ImageIO.read(f); } catch (IOException ignored) {}
            }
        }

        // Eğer AUTO mod ise tarama yap
        if (defaultImage == null && AppConstants.MODE_AUTO.equalsIgnoreCase(mode)) {
            File folderToScan = autoFolder.isEmpty() ? appDir : new File(autoFolder);
            System.out.println("AUTO scan: primary -> " + folderToScan.getAbsolutePath());
            defaultImage = scanForDefaultImage(folderToScan);
            // Eğer bulunamadıysa çalışma dizinini (IDE'de genellikle proje kökü) de tara
            if (defaultImage == null) {
                File workDir = new File(System.getProperty("user.dir"));
                if (!workDir.equals(folderToScan)) {
                    System.out.println("AUTO scan: fallback working dir -> " + workDir.getAbsolutePath());
                    defaultImage = scanForDefaultImage(workDir);
                }
            }
            // Hala yoksa son bir deneme olarak appDir'i tara (jar çalıştırılırken farklı olabilir)
            if (defaultImage == null && appDir != null && !appDir.equals(folderToScan)) {
                System.out.println("AUTO scan: final fallback appDir -> " + appDir.getAbsolutePath());
                defaultImage = scanForDefaultImage(appDir);
            }
        }

        // PROMPT: do not force repeated chooser; if still no default, show info
        if (defaultImage == null && AppConstants.MODE_PROMPT.equalsIgnoreCase(mode)) {
            photoPanel.setInfo(AppConstants.DEFAULT_INFO_MESSAGE);
        }

        // Eğer halen defaultImage null ise, file chooser fallback veya bilgi göster
        if (defaultImage == null && !AppConstants.MODE_MANUAL.equalsIgnoreCase(mode)) {
            // otomatik modda bulunamadıysa fallback bilgi
            photoPanel.setInfo(AppConstants.DEFAULT_INFO_MESSAGE);
        } else if (defaultImage != null) {
            photoPanel.setImage(defaultImage, true); // Default olarak işaretle
        } else {
            // MANUAL modu ama kaydedilmiş yok -> gösterici ile manuel seçim
            try {
                JFileChooser fileChooser = createImageFileChooser(frame);
                int result = fileChooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    try {
                        defaultImage = ImageIO.read(selectedFile);
                        if (defaultImage != null) {
                            photoPanel.setImage(defaultImage, true); // Default olarak işaretle
                            logger.info("Manuel resim seçimi başarılı: " + selectedFile.getName());
                        } else {
                            photoPanel.setInfo(AppConstants.PHOTO_LOAD_ERROR_MESSAGE);
                            logger.warn("Seçilen dosya okunamadı: " + selectedFile.getName());
                        }
                    } catch (IOException ex) {
                        photoPanel.setInfo(AppConstants.PHOTO_LOAD_ERROR_MESSAGE);
                        logger.error("Resim yükleme hatası: " + ex.getMessage());
                    }
                } else {
                    photoPanel.setInfo(AppConstants.DEFAULT_INFO_MESSAGE);
                    logger.info("Kullanıcı resim seçimini iptal etti");
                }
            } catch (Exception ex) {
                photoPanel.setInfo(AppConstants.DEFAULT_INFO_MESSAGE);
                logger.error("FileChooser hatası: " + ex.getMessage());
            }
        }

        frame.setVisible(true);
        logger.info("PhotoViewer UI başlatıldı, port: " + port);

        // UpdateManager otomatik kontrolü kaldırıldı - sadece file chooser'da manuel kontrol
        // updateManager.checkForUpdatesOnStartup(); // Commented out

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.success("Sunucu başarıyla başlatıldı - Port: " + port);
            System.out.println("Sunucu dinleniyor: " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    logger.info("Yeni bağlantı alındı: " + clientIP);
                    System.out.println("Bağlantı alındı: " + clientSocket.getInetAddress());
            InputStream rawIn = clientSocket.getInputStream();
            // Use PushbackInputStream so we can unread one byte if needed when parsing lines
            PushbackInputStream in = new PushbackInputStream(rawIn, AppConstants.PUSHBACK_BUFFER_SIZE);
            // Ensure we don't block forever waiting for data
            clientSocket.setSoTimeout(AppConstants.SOCKET_TIMEOUT_MS); // PhotoSenderApp READ_TIMEOUT_MS ile eşleşsin
            // Komut satırını oku (ilk satır) - format: SHOW_DEFAULT OR SEND_PHOTO:<length>
                    String command = readAsciiLine(in);
                    logger.info("Komut alındı: " + command + " (Kaynak: " + clientIP + ")");
                    System.out.println("Komut alındı: " + command);
                    if (command != null && command.equals(AppConstants.COMMAND_SHOW_DEFAULT)) {
                        // Default fotoğrafı göster
                        if (defaultImage != null) {
                            logger.success("Default fotoğraf gösteriliyor");
                            System.out.println("Default fotoğraf gösteriliyor.");
                            photoPanel.setImage(defaultImage, true); // Default olarak işaretle
                        } else {
                            logger.warn("Default fotoğraf bulunamadı");
                            photoPanel.setInfo(AppConstants.NO_DEFAULT_PHOTO_MESSAGE);
                        }
                    } else if (command != null && command.equals(AppConstants.COMMAND_GET_STATUS)) {
                        // Durum sorgulama
                        try {
                            String status = photoPanel.getStatusDetails();
                            OutputStream out = clientSocket.getOutputStream();
                            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                            bw.write("STATUS:" + status + "\n");
                            bw.flush();
                            logger.info("Durum bilgisi gönderildi: " + status + " (Kaynak: " + clientIP + ")");
                            System.out.println("Durum sorgulandı: " + status);
                        } catch (Exception ex) {
                            logger.error("Durum bilgisi gönderilemedi", ex);
                            System.out.println("Durum bilgisi gönderilemedi: " + ex.getMessage());
                        }
                    } else if (command != null && command.equals(AppConstants.COMMAND_GET_SCREENSHOT)) {
                        // Screenshot sorgulama
                        try {
                            logger.info("Screenshot isteği alındı - Kaynak: " + clientIP);
                            long startTime = System.currentTimeMillis();
                            
                            byte[] screenshotData = captureScreenshot();
                            long captureTime = System.currentTimeMillis() - startTime;
                            
                            logger.debug("Screenshot yakalandı: " + screenshotData.length + " byte (" + 
                                       (screenshotData.length / 1024) + " KB) - Süre: " + captureTime + "ms");
                            
                            OutputStream out = clientSocket.getOutputStream();
                            
                            // Önce boyutu gönder
                            String header = "SCREENSHOT:" + screenshotData.length + "\n";
                            logger.debug("Screenshot header gönderiliyor: " + header.trim());
                            out.write(header.getBytes("UTF-8"));
                            out.flush();
                            
                            // Sonra screenshot verisini gönder
                            long transferStart = System.currentTimeMillis();
                            out.write(screenshotData);
                            out.flush();
                            long transferTime = System.currentTimeMillis() - transferStart;
                            
                            long totalTime = System.currentTimeMillis() - startTime;
                            logger.success("Screenshot başarıyla gönderildi - Boyut: " + (screenshotData.length / 1024) + " KB, " +
                                         "Yakalama: " + captureTime + "ms, Transfer: " + transferTime + "ms, Toplam: " + totalTime + "ms");
                        } catch (Exception ex) {
                            logger.error("Screenshot gönderim hatası - Kaynak: " + clientIP + " - " + ex.getClass().getSimpleName() + ": " + ex.getMessage(), ex);
                        }
                    } else if (command != null && command.equals(AppConstants.COMMAND_GET_SCREENSHOT_HD)) {
                        // Ultra yüksek kaliteli screenshot sorgulama
                        try {
                            logger.info("Ultra kalite screenshot isteği alındı - Kaynak: " + clientIP);
                            long startTime = System.currentTimeMillis();
                            
                            byte[] screenshotData = captureHighQualityScreenshot();
                            long captureTime = System.currentTimeMillis() - startTime;
                            
                            logger.debug("Ultra kalite screenshot yakalandı: " + screenshotData.length + " byte (" + 
                                       (screenshotData.length / 1024) + " KB) - Süre: " + captureTime + "ms");
                            
                            OutputStream out = clientSocket.getOutputStream();
                            
                            // Önce boyutu gönder
                            String header = "SCREENSHOT:" + screenshotData.length + "\n";
                            logger.debug("Ultra kalite header gönderiliyor: " + header.trim());
                            out.write(header.getBytes("UTF-8"));
                            out.flush();
                            
                            // Sonra screenshot verisini gönder
                            long transferStart = System.currentTimeMillis();
                            out.write(screenshotData);
                            out.flush();
                            long transferTime = System.currentTimeMillis() - transferStart;
                            
                            long totalTime = System.currentTimeMillis() - startTime;
                            logger.success("Ultra kalite screenshot başarıyla gönderildi - Boyut: " + (screenshotData.length / 1024) + " KB, " +
                                         "Yakalama: " + captureTime + "ms, Transfer: " + transferTime + "ms, Toplam: " + totalTime + "ms");
                        } catch (Exception ex) {
                            logger.error("Ultra kalite screenshot gönderim hatası - Kaynak: " + clientIP + " - " + ex.getClass().getSimpleName() + ": " + ex.getMessage(), ex);
                        }
                    } else if (command != null && command.startsWith(AppConstants.COMMAND_SEND_PHOTO_WITH_TIMER)) {
                        // Yeni protokol: SEND_PHOTO_WITH_TIMER:boyut:süre
                        System.out.println("Zamanlı fotoğraf verisi alınıyor...");
                        String[] parts = command.split(":", 3);
                        if (parts.length >= 3) {
                            try {
                                int length = Integer.parseInt(parts[1]);
                                long durationSeconds = Long.parseLong(parts[2]);
                                
                                // Fotoğrafı al
                                BufferedImage image = ImageReceiver.receiveImage(in, length);
                                
                                if (image != null) {
                                    photoPanel.setImageWithTimer(image, durationSeconds, defaultImage);
                                    String durStr = AppUtils.formatDuration(durationSeconds);
                                    logger.success("Zamanlı fotoğraf alındı: " + durStr + " gösterilecek (Kaynak: " + clientIP + ")");
                                    System.out.println("Zamanlı fotoğraf alındı: " + durStr + " gösterilecek");
                                    
                                    // Send ACK back to sender
                                    try {
                                        OutputStream out = clientSocket.getOutputStream();
                                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                                        bw.write(AppConstants.RESPONSE_OK);
                                        bw.flush();
                                    } catch (Exception ex) {
                                        logger.error("ACK gönderilemedi", ex);
                                        System.out.println("ACK gönderilemedi: " + ex.getMessage());
                                    }
                                } else {
                                    logger.error("Zamanlı fotoğraf alınamadı (Kaynak: " + clientIP + ")");
                                    System.out.println("Zamanlı fotoğraf alınamadı");
                                    try {
                                        OutputStream out = clientSocket.getOutputStream();
                                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                                        bw.write(AppConstants.RESPONSE_ERROR);
                                        bw.flush();
                                    } catch (Exception ex) {
                                        logger.error("ERR gönderilemedi", ex);
                                        System.out.println("ERR gönderilemedi: " + ex.getMessage());
                                    }
                                }
                            } catch (NumberFormatException ex) {
                                System.out.println("Zamanlı fotoğraf header parse hatası: " + ex.getMessage());
                            }
                        }
                    } else {
                        // Eski protokol: Normal fotoğraf gelirse göster
                        // Beklenen format: SEND_PHOTO:<length>\n followed by exactly <length> bytes
                        System.out.println("Fotoğraf verisi alınıyor...");
                        int length = -1;
                        if (command != null && command.startsWith(AppConstants.COMMAND_SEND_PHOTO)) {
                            try {
                                String[] sp = command.split(":", 2);
                                length = Integer.parseInt(sp[1]);
                            } catch (Exception ex) {
                                System.out.println("Header parse hatası: " + ex.getMessage());
                            }
                        }

                        BufferedImage image = ImageReceiver.receiveImage(in, length);
                        
                        if (image != null) {
                            logger.success("Normal fotoğraf alındı ve gösteriliyor (Kaynak: " + clientIP + ")");
                            System.out.println("Fotoğraf başarıyla alındı ve gösteriliyor.");
                            photoPanel.setImage(image);
                        } else {
                            logger.warn("Geçersiz veya eksik fotoğraf verisi (Kaynak: " + clientIP + ")");
                            System.out.println("Geçersiz veya eksik fotoğraf verisi. Default gösteriliyor.");
                            if (defaultImage != null) {
                                photoPanel.setImage(defaultImage, true); // Default olarak işaretle
                            } else {
                                photoPanel.setInfo(AppConstants.INVALID_PHOTO_DATA_MESSAGE);
                            }
                        }

                        // Send ACK back to sender — sadece görüntü başarıyla dekode edildiyse OK, aksi halde ERR
                        try {
                            OutputStream out = clientSocket.getOutputStream();
                            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                            String reply = (image != null) ? AppConstants.RESPONSE_OK : AppConstants.RESPONSE_ERROR;
                            bw.write(reply);
                            bw.flush();
                        } catch (Exception ex) {
                            System.out.println("ACK gönderilemedi: " + ex.getMessage());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Read an ASCII line terminated by \n (handles \r\n and \n). Returns null on EOF.
    private static String readAsciiLine(PushbackInputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                // peek next byte
                int next = in.read();
                if (next != '\n' && next != -1) {
                    in.unread(next);
                }
                break;
            } else if (c == '\n') {
                break;
            } else {
                baos.write(c);
            }
        }
        if (c == -1 && baos.size() == 0) return null;
        return baos.toString("US-ASCII");
    }

    // Yardımcılar -------------------------------------------------------

    public static Properties loadProperties(File configFile) {
        synchronized (CONFIG_LOCK) {
            Properties p = new Properties();
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    p.load(fis);
                } catch (IOException ignored) {}
            }
            return p;
        }
    }

    public static void saveProperties(File configFile, Properties p) {
        synchronized (CONFIG_LOCK) {
            try {
                File parent = configFile.getAbsoluteFile().getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(configFile)) {
                    p.store(fos, AppConstants.CONFIG_COMMENT);
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * Determine a writable location for photoviewer.properties.
     * Prefer the application directory when writable, otherwise fall back to %APPDATA%/PhotoViewer
     * or the user's home directory.
     */
    public static File getConfigFile() {
        // Öncelik: %APPDATA%/PhotoViewer
        String appdata = System.getenv("APPDATA");
        File candidate;
        if (appdata != null && !appdata.isEmpty()) {
            candidate = new File(appdata, "PhotoViewer");
        } else {
            // Eğer APPDATA yoksa kullanıcı dizinine gizli bir klasör
            candidate = new File(System.getProperty("user.home"), ".photoviewer");
        }
        // Ensure directory exists and is writable
        try {
            if (!candidate.exists()) candidate.mkdirs();
        } catch (Exception ignored) {}
        return new File(candidate, "photoviewer.properties");
    }

    // Recursively scan a folder for an image file (jpg/jpeg/png/bmp) and return the first readable image.
    public static BufferedImage scanForDefaultImage(File folder) {
        if (folder == null || !folder.exists()) return null;
        try {
            File[] list = folder.listFiles();
            if (list == null) return null;
            // First pass: files in this directory
            for (File f : list) {
                if (f.isFile() && AppUtils.isSupportedImageFile(f.getName())) {
                    BufferedImage img = AppUtils.readImageSafely(f);
                    if (img != null) return img;
                }
            }
            // Second pass: recurse into subdirectories
            for (File f : list) {
                if (f.isDirectory()) {
                    BufferedImage img = scanForDefaultImage(f);
                    if (img != null) return img;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static JFileChooser createImageFileChooser(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(AppConstants.APP_TITLE + " - v" + AppConstants.VERSION);
        fileChooser.setAcceptAllFileFilterUsed(false);
        
        // File chooser açıldığında otomatik güncelleme kontrolü başlat
        if (updateManager != null && updateManager.isReady()) {
            updateManager.checkForUpdatesOnFileChooser();
        }
        
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                for (String ext : AppConstants.SUPPORTED_IMAGE_EXTENSIONS) {
                    if (name.endsWith(ext)) return true;
                }
                return false;
            }
            @Override
            public String getDescription() {
                return "Resim Dosyaları (*.jpg, *.jpeg, *.png, *.bmp)";
            }
        });

        JPanel accessory = new JPanel(new GridLayout(3, 1, 0, 8));
        accessory.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Ayrı "Yardım" butonu - detaylı kullanım kılavuzu
        JButton helpBtn = new JButton("Yardım");
        helpBtn.setToolTipText("Detaylı kullanım kılavuzu");
        helpBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
        String usage = "PhotoViewer - Kullanım Kılavuzu\n\n" +
            "1) Dosya Seçici Kullanımı:\n" +
            "   - Bu pencereden jpg/jpeg/png/bmp uzantılı dosyaları seçin.\n" +
            "   - Seçilen resim uygulamanın varsayılan resmidir (MANUAL moda kaydedilebilir).\n\n" +
            "2) Modlar:\n" +
            "   - PROMPT: İlk çalışmada resim seçimi istenir; yoksa bilgi ekranı gösterilir.\n" +
            "   - MANUAL: Kaydedilmiş bir resim varsa uygulama onu varsayılan olarak gösterir.\n" +
            "   - AUTO: Belirtilen klasörde veya uygulama dizininde otomatik tarama yapar.\n" +
            "     (Örnek: photoviewer.properties içindeki autoFolder)\n\n" +
            "3) Zamanlı Gösterim:\n" +
            "   - Gönderici zaman (saniye) verdiğinde resim o süre boyunca gösterilir, sonra varsayılan resme veya bilgi ekranına döner.\n\n" +
            "4) Kullanıcı Arayüzü ve Kısayollar:\n" +
            "   - Tam ekran modunda ESC tuşu uygulamayı kapatır.\n" +
            "   - Fotoğraf görüntülenirken fare imleci 3 saniye sonra gizlenir; hareket edince geri gelir.\n" +
            "   - Fotoğraf olduğu durumda sağ tıklayarak menüden Saati göster/gizle ve Manuel Seç seçeneklerine erişebilirsiniz.\n\n" +
            "5) Sorun Giderme:\n" +
            "   - Eğer resim gösterilmiyorsa gönderici ile READ_TIMEOUT veya header formatını kontrol edin.\n" +
            "   - photoviewer.properties dosyası bulunamazsa uygulama %APPDATA% veya kullanıcı dizinine yazmaya çalışır.\n\n" +
            "İletişim: " + AppConstants.CONTACT_EMAIL + "\n" +
            "Sürüm: " + AppConstants.VERSION + "\n";
                JTextArea ta = new JTextArea(usage);
                ta.setEditable(false);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                ta.setCaretPosition(0);
                JScrollPane sp = new JScrollPane(ta);
                sp.setPreferredSize(new Dimension(480, 360));
                JOptionPane.showMessageDialog(frame, sp, "Yardım - Kullanım Kılavuzu", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Ayrı "Hakkında" butonu - lisans ve versiyon bilgisi
        JButton aboutBtn = new JButton("Hakkında");
        aboutBtn.setToolTipText("Lisans bilgisi ve kısa uygulama bilgisi");
        aboutBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                String license = AppConstants.COPYRIGHT + "\n" +
                        "İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.\n" +
                        "Tüm hakları saklıdır. " + AppConstants.COPYRIGHT + "\n\n" +
                        "Sürüm: " + AppConstants.VERSION + "\n\n" +
                        "Kısa yardım: Fotoğraf seçmek için dosya seçiciden bir resim seçin (jpg, jpeg, png, bmp).\n" +
                        "Tam ekrandayken sağ tık veya ESC ile çıkabilirsiniz.\n\n" +
                        "İletişim: " + AppConstants.CONTACT_EMAIL;
                JOptionPane.showMessageDialog(frame, license, "Hakkında / Lisans", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Güncelleme kontrolü butonu
        JButton updateBtn = new JButton("Güncellemeleri Kontrol Et");
        updateBtn.setToolTipText("GitHub'dan yeni sürüm kontrolü yapar");
        updateBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                if (updateManager != null && updateManager.isReady()) {
                    updateManager.checkForUpdatesManual();
                } else {
                    JOptionPane.showMessageDialog(frame, 
                        "Güncelleme yöneticisi henüz hazır değil. Lütfen birkaç saniye bekleyin.", 
                        "Bilgi", 
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        accessory.add(helpBtn);
        accessory.add(aboutBtn);
        accessory.add(updateBtn);
        fileChooser.setAccessory(accessory);
        return fileChooser;
    }

    // Screenshot alma metodu - Ultra yüksek kalite
    private static byte[] captureScreenshot() throws Exception {
        // Ekran görüntüsü al
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        logger.debug("Ekran boyutu: " + screenRect.width + "x" + screenRect.height);
        
        BufferedImage screenshot = robot.createScreenCapture(screenRect);
        logger.debug("Ham screenshot yakalandı: " + screenshot.getWidth() + "x" + screenshot.getHeight());
        
        // İyi kalite ve performans dengesi (1600px max - Full HD+ kalitesi)
        int maxWidth = 1600; // 1280'den artırıldı, 1920'den az
        int newWidth = Math.min(screenshot.getWidth(), maxWidth);
        int newHeight = (screenshot.getHeight() * newWidth) / screenshot.getWidth();
        
        logger.debug("Yeniden boyutlandırma (balanced): " + screenshot.getWidth() + "x" + screenshot.getHeight() + 
                    " -> " + newWidth + "x" + newHeight);
        
        // Kalite ve hız dengeli ayarlar
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        
        // Dengeli kalite ayarları - İyi görüntü ama hızlı
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); // Kaliteyi geri getir
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY); // Kaliteyi geri getir
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Kenar yumuşatma
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY); // Renk kalitesi
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        
        g.drawImage(screenshot, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        logger.debug("Görüntü işleme tamamlandı, PNG encoding başlıyor...");
        
        // PNG formatında encode et (kayıpsız kalite!)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // PNG - Kayıpsız sıkıştırma
        javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("png").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        
        javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
        
        writer.dispose();
        ios.close();
        
        byte[] result = baos.toByteArray();
        logger.debug("PNG encoding tamamlandı - Final boyut: " + result.length + " byte (" + (result.length / 1024) + " KB)");
        
        return result;
    }

    // Ultra yüksek kaliteli screenshot (tek görüntü için)
    private static byte[] captureHighQualityScreenshot() throws Exception {
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        logger.debug("Ultra kalite ekran boyutu: " + screenRect.width + "x" + screenRect.height);
        
        BufferedImage screenshot = robot.createScreenCapture(screenRect);
        logger.debug("Ultra kalite ham screenshot: " + screenshot.getWidth() + "x" + screenshot.getHeight());
        
        // Ultra yüksek çözünürlük (1920px max - 4K destekli)
        int maxWidth = 1920;
        int newWidth = Math.min(screenshot.getWidth(), maxWidth);
        int newHeight = (screenshot.getHeight() * newWidth) / screenshot.getWidth();
        
        logger.debug("Ultra kalite boyutlandırma: " + screenshot.getWidth() + "x" + screenshot.getHeight() + 
                    " -> " + newWidth + "x" + newHeight);
        
        // Ultra yüksek kaliteli resize
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        
        // En iyi kalite ayarları - Ultra mode
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        g.drawImage(screenshot, 0, 0, newWidth, newHeight, null);
        g.dispose();
        
        logger.debug("Ultra kalite görüntü işleme tamamlandı, PNG encoding...");
        
        // PNG formatında encode et (kayıpsız kalite!)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // PNG - Kayıpsız sıkıştırma
        javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("png").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        
        javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
        
        writer.dispose();
        ios.close();
        
        byte[] result = baos.toByteArray();
        logger.debug("Ultra kalite PNG encoding tamamlandı - Final boyut: " + result.length + " byte (" + (result.length / 1024) + " KB)");
        
        return result;
    }
}
