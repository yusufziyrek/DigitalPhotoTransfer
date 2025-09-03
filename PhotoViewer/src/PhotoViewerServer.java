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
import java.net.URISyntaxException;
import java.nio.file.Path;

public class PhotoViewerServer {
    private static final String VERSION = "1.0.3";
    private static final AppLogger logger = new AppLogger("PhotoViewer");
    
    public static void main(String[] args) {
        int port = 5000; // Dinlenecek port
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Özel panel: kalite bozulmadan fotoğraf gösterimi
        PhotoPanel photoPanel = new PhotoPanel();
        frame.setContentPane(photoPanel);
        photoPanel.setParentFrame(frame);

        // ESC ile kapatma
        frame.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    frame.dispose();
                    System.exit(0);
                }
            }
        });

        // Config dosyası ve otomatik/manuel tercihlerin yönetimi
    File appDir = getAppDirectory();
    if (appDir == null) appDir = new File(System.getProperty("user.dir"));
    File configFile = getConfigFile();

        BufferedImage defaultImage = null;

        Properties props = loadProperties(configFile);

        String mode = props.getProperty("mode", "PROMPT"); // PROMPT, MANUAL, AUTO
        String savedImagePath = props.getProperty("savedImagePath", "");
        String autoFolder = props.getProperty("autoFolder", "");

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
                        props.setProperty("mode", "MANUAL");
                        props.setProperty("savedImagePath", sel.getAbsolutePath());
                        saveProperties(configFile, props);
                    } else {
                        // leave defaultImage null and continue with existing flow
                    }
                } catch (IOException ignored) {
                }
            }
            // reload savedImagePath in case it was written
            savedImagePath = props.getProperty("savedImagePath", "");
            mode = props.getProperty("mode", mode);
        }

        // Eğer kaydedilmiş manuel resim yolu varsa yüklemeyi dene
        if ("MANUAL".equalsIgnoreCase(mode) && !savedImagePath.isEmpty()) {
            File f = new File(savedImagePath);
            if (f.exists()) {
                try { defaultImage = ImageIO.read(f); } catch (IOException ignored) {}
            }
        }

        // Eğer AUTO mod ise tarama yap
        if (defaultImage == null && "AUTO".equalsIgnoreCase(mode)) {
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
        if (defaultImage == null && "PROMPT".equalsIgnoreCase(mode)) {
            photoPanel.setInfo("Wyndham Grand Istanbul Europe");
        }

        // Eğer halen defaultImage null ise, file chooser fallback veya bilgi göster
        if (defaultImage == null && !"MANUAL".equalsIgnoreCase(mode)) {
            // otomatik modda bulunamadıysa fallback bilgi
            photoPanel.setInfo("Wyndham Grand Istanbul Europe");
        } else if (defaultImage != null) {
            photoPanel.setImage(defaultImage);
        } else {
            // MANUAL modu ama kaydedilmiş yok -> gösterici ile manuel seçim
            JFileChooser fileChooser = createImageFileChooser(frame);
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    defaultImage = ImageIO.read(selectedFile);
                    if (defaultImage != null) {
                        photoPanel.setImage(defaultImage);
                    } else {
                        photoPanel.setInfo("Fotoğraf yüklenemedi.");
                    }
                } catch (IOException ex) {
                    photoPanel.setInfo("Fotoğraf yüklenemedi.");
                }
            } else {
                photoPanel.setInfo("Wyndham Grand Istanbul Europe");
            }
        }

        frame.setVisible(true);
        logger.info("PhotoViewer UI başlatıldı, port: " + port);

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
            PushbackInputStream in = new PushbackInputStream(rawIn, 8192);
            // Ensure we don't block forever waiting for data
            clientSocket.setSoTimeout(15000); // Bu değer sender ile eşleşmeli
clientSocket.setSoTimeout(10000); // PhotoSenderApp READ_TIMEOUT_MS ile eşleşsin
            // Komut satırını oku (ilk satır) - format: SHOW_DEFAULT OR SEND_PHOTO:<length>
                    String command = readAsciiLine(in);
                    logger.info("Komut alındı: " + command + " (Kaynak: " + clientIP + ")");
                    System.out.println("Komut alındı: " + command);
                    if (command != null && command.equals("SHOW_DEFAULT")) {
                        // Default fotoğrafı göster
                        if (defaultImage != null) {
                            logger.success("Default fotoğraf gösteriliyor");
                            System.out.println("Default fotoğraf gösteriliyor.");
                            photoPanel.setImage(defaultImage);
                        } else {
                            logger.warn("Default fotoğraf bulunamadı");
                            photoPanel.setInfo("Default fotoğraf yok.");
                        }
                    } else if (command != null && command.startsWith("SEND_PHOTO_WITH_TIMER:")) {
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
                                    logger.success("Zamanlı fotoğraf alındı: " + (durationSeconds / 86400) + " gün, " + 
                                                 ((durationSeconds % 86400) / 3600) + " saat gösterilecek (Kaynak: " + clientIP + ")");
                                    System.out.println("Zamanlı fotoğraf alındı: " + (durationSeconds / 86400) + " gün gösterilecek");
                                    
                                    // Send ACK back to sender
                                    try {
                                        OutputStream out = clientSocket.getOutputStream();
                                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                                        bw.write("OK\n");
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
                                        bw.write("ERR\n");
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
                        if (command != null && command.startsWith("SEND_PHOTO:")) {
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
                                photoPanel.setImage(defaultImage);
                            } else {
                                photoPanel.setInfo("Geçersiz veya eksik fotoğraf verisi.");
                            }
                        }

                        // Send ACK back to sender — sadece görüntü başarıyla dekode edildiyse OK, aksi halde ERR
                        try {
                            OutputStream out = clientSocket.getOutputStream();
                            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                            String reply = (image != null) ? "OK\n" : "ERR\n";
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

    public static File getAppDirectory() {
        try {
            Path p = new File(PhotoViewerServer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
            File f = p.toFile();
            if (f.isFile()) return f.getParentFile();
            return f;
        } catch (URISyntaxException e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    public static Properties loadProperties(File configFile) {
        Properties p = new Properties();
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                p.load(fis);
            } catch (IOException ignored) {}
        }
        return p;
    }

    public static void saveProperties(File configFile, Properties p) {
        try {
            File parent = configFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                p.store(fos, "PhotoViewer settings");
            }
        } catch (IOException ignored) {}
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
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp")) {
                        try {
                            BufferedImage img = ImageIO.read(f);
                            if (img != null) return img;
                        } catch (IOException ignored) {}
                    }
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
        fileChooser.setDialogTitle("Photo Viewer - Wyndham Grand Istanbul Europe - "+ "v" +VERSION);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp");
            }
            @Override
            public String getDescription() {
                return "Resim Dosyaları (*.jpg, *.jpeg, *.png, *.bmp)";
            }
        });

        JPanel accessory = new JPanel(new GridLayout(2, 1, 0, 8));
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
            "İletişim: yusufziyrek1@gmail.com\n" +
            "Sürüm: " + VERSION + "\n";
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
                String license = "Bu yazılım Yusuf Ziyrek'e aittir.\n" +
                        "İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.\n" +
                        "Tüm hakları saklıdır. © 2025 Yusuf Ziyrek\n\n" +
                        "Sürüm: " + VERSION + "\n\n" +
                        "Kısa yardım: Fotoğraf seçmek için dosya seçiciden bir resim seçin (jpg, jpeg, png, bmp).\n" +
                        "Tam ekrandayken sağ tık veya ESC ile çıkabilirsiniz.\n\n" +
                        "İletişim: yusufziyrek1@gmail.com";
                JOptionPane.showMessageDialog(frame, license, "Hakkında / Lisans", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        accessory.add(helpBtn);
        accessory.add(aboutBtn);
        fileChooser.setAccessory(accessory);
        return fileChooser;
    }

}
