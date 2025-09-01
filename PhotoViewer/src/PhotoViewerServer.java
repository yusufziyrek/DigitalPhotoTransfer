/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;
import java.util.Properties;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PhotoViewerServer {
    private static final String VERSION = "1.0.1"; // Versiyon bilgisi eklendi
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

    try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Sunucu dinleniyor: " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Bağlantı alındı: " + clientSocket.getInetAddress());
            InputStream rawIn = clientSocket.getInputStream();
            // Use PushbackInputStream so we can unread one byte if needed when parsing lines
            PushbackInputStream in = new PushbackInputStream(rawIn, 8192);
            // Ensure we don't block forever waiting for data
            clientSocket.setSoTimeout(15000); // Bu değer sender ile eşleşmeli
clientSocket.setSoTimeout(10000); // PhotoSenderApp READ_TIMEOUT_MS ile eşleşsin
            // Komut satırını oku (ilk satır) - format: SHOW_DEFAULT OR SEND_PHOTO:<length>
                    String command = readAsciiLine(in);
                    System.out.println("Komut alındı: " + command);
                    if (command != null && command.equals("SHOW_DEFAULT")) {
                        // Default fotoğrafı göster
                        if (defaultImage != null) {
                            System.out.println("Default fotoğraf gösteriliyor.");
                            photoPanel.setImage(defaultImage);
                        } else {
                            photoPanel.setInfo("Default fotoğraf yok.");
                        }
                    } else {
                        // Yeni fotoğraf gelirse göster
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

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        BufferedImage image = null;
                        File tempFile = null;
                        try {
                            if (length > 0) {
                                // If the image is large, stream to a temp file to avoid OOM
                                final int STREAM_TO_FILE_THRESHOLD = 50 * 1024 * 1024; // 50 MB
                                if (length > STREAM_TO_FILE_THRESHOLD) {
                                    tempFile = File.createTempFile("received_image_", ".tmp");
                                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                                        byte[] buffer = new byte[8192];
                                        int remaining = length;
                                        while (remaining > 0) {
                                            int toRead = Math.min(buffer.length, remaining);
                                            int read = in.read(buffer, 0, toRead);
                                            if (read == -1) break;
                                            fos.write(buffer, 0, read);
                                            remaining -= read;
                                        }
                                    }
                                    image = ImageIO.read(tempFile);
                                } else {
                                    int remaining = length;
                                    byte[] buffer = new byte[8192];
                                    while (remaining > 0) {
                                        int toRead = Math.min(buffer.length, remaining);
                                        int read = in.read(buffer, 0, toRead);
                                        if (read == -1) break;
                                        baos.write(buffer, 0, read);
                                        remaining -= read;
                                    }
                                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                                    image = ImageIO.read(bais);
                                }
                            } else {
                                // fallback: read until EOF
                                int b;
                                while ((b = in.read()) != -1) baos.write(b);
                                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                                image = ImageIO.read(bais);
                            }

                            if (image != null) System.out.println("Fotoğraf başarıyla alındı ve gösteriliyor.");
                            else System.out.println("Geçersiz veya eksik fotoğraf verisi. Default gösteriliyor.");

                            if (image != null) {
                                photoPanel.setImage(image);
                            } else if (defaultImage != null) {
                                photoPanel.setImage(defaultImage);
                            } else {
                                photoPanel.setInfo("Geçersiz veya eksik fotoğraf verisi.");
                            }

                            // Send ACK back to sender — sadece görüntü başarıyla dekode edildiyse OK, aksi halde ERR
                            try {
                                OutputStream out = clientSocket.getOutputStream();
                                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                                String reply = (image != null) ? "OK\n" : "ERR\n";
                                bw.write(reply);
                                bw.flush();
                                // not closing bw/out here; let socket lifecycle manage streams
                            } catch (Exception ex) {
                                System.out.println("ACK gönderilemedi: " + ex.getMessage());
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            // On error, notify sender
                            try {
                                OutputStream out = clientSocket.getOutputStream();
                                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "US-ASCII"));
                                bw.write("ERR\n");
                                bw.flush();
                            } catch (Exception e) {
                                System.out.println("ERR gönderilemedi: " + e.getMessage());
                            }
                        } finally {
                            if (tempFile != null && tempFile.exists()) {
                                try { tempFile.delete(); } catch (Exception ignored) {}
                            }
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

        JPanel accessory = new JPanel(new BorderLayout());
        accessory.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Yardım / Hakkında butonu (eski hali)
        JButton helpBtn = new JButton("Yardım / Hakkında");
        helpBtn.setToolTipText("Lisans bilgisi ve kısa kullanım yardımı");
        helpBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                String license = "Bu yazılım Yusuf Ziyrek'e aittir.\n" +
                        "İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.\n" +
                        "Tüm hakları saklıdır. © 2025 Yusuf Ziyrek\n\n" +
                        "Kısa yardım:\n" +
                        "- Fotoğraf seçmek için dosya seçiciden bir resim seçin (jpg, jpeg, png, bmp).\n" +
                        "- Tam ekrandayken sağ tık ile çıkış yapabilir veya ESC ile çıkabilirsiniz. \n" +
                        "İletişim: yusufziyrek1@gmail.com";
                JOptionPane.showMessageDialog(frame, license, "Yardım / Lisans", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        accessory.add(helpBtn, BorderLayout.NORTH);
        fileChooser.setAccessory(accessory);
        return fileChooser;
    }

    // Startup preference dialog and handler removed: startup now directly prompts manual selection when needed.

}

// Fotoğrafı kalite bozulmadan gösteren özel panel
class PhotoPanel extends JPanel {
    private BufferedImage image = null;
    private String info = "Wyndham Grand Istanbul Europe";
    private JFrame parentFrame;

    // Clock & date overlay
    private volatile String timeText = "";
    private volatile String dateText = "";
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // Bigger, clearer fonts: large for time, smaller for date (increased per user request)
    private final Font timeFont = new Font("SansSerif", Font.PLAIN, 45);
    private final Font dateFont = new Font("SansSerif", Font.PLAIN, 20);
    private final Timer clockTimer;
    private boolean showClock = true; // can add setter if needed

    // Cursor auto-hide
    private Cursor defaultCursor;
    private Cursor blankCursor;
    private Timer hideCursorTimer;
    private final int CURSOR_IDLE_MS = 3000; // hide after 3 seconds
    private volatile boolean cursorHidden = false;

    // Sağ tık menüsü ve çıkış seçeneği
    private JPopupMenu popupMenu = new JPopupMenu();
    private JMenuItem clockToggleItem = new JMenuItem("Saat göster");
    private JMenuItem exitItem = new JMenuItem("Çıkış");

    {
        // Manuel seçim menü öğesi
        JMenuItem manualItem = new JMenuItem("Manuel Seç");
        manualItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                if (parentFrame == null) return;
                JFileChooser fc = PhotoViewerServer.createImageFileChooser(parentFrame);
                int r = fc.showOpenDialog(parentFrame);
                if (r == JFileChooser.APPROVE_OPTION) {
                    File sel = fc.getSelectedFile();
                    try {
                        BufferedImage img = ImageIO.read(sel);
                        if (img != null) {
                            setImage(img);
                            // Kaydet: photoviewer.properties içine mode=MANUAL ve savedImagePath yaz
                            try {
                                File configFile = PhotoViewerServer.getConfigFile();
                                Properties p = PhotoViewerServer.loadProperties(configFile);
                                p.setProperty("mode", "MANUAL");
                                p.setProperty("savedImagePath", sel.getAbsolutePath());
                                PhotoViewerServer.saveProperties(configFile, p);
                            } catch (Exception ignore) {}
                        } else {
                            JOptionPane.showMessageDialog(parentFrame, "Resim yüklenemedi.", "Hata", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(parentFrame, "Resim yüklenirken hata: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        popupMenu.add(manualItem);

        // Yeni: saat göster/gizle toggle as a simple menu item (no checkbox)
        updateClockToggleText();
        clockToggleItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                showClock = !showClock;
                updateClockToggleText();
                repaint();
            }
        });
        popupMenu.add(clockToggleItem);

        popupMenu.addSeparator();
        popupMenu.add(exitItem);
        exitItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (parentFrame != null) {
                    parentFrame.dispose();
                    System.exit(0);
                }
            }
        });
         this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // menü gösterilmeden önce toggle'ı güncelle (sadece fotoğraf varken etkin)
                    // update label text and enable state
                    updateClockToggleText();
                    clockToggleItem.setEnabled(image != null);
                    popupMenu.show(PhotoPanel.this, e.getX(), e.getY());
                }
            }
        });

        // Mouse movement: show cursor and restart hide timer
        this.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                // show cursor if hidden
                if (cursorHidden) {
                    setCursor(defaultCursor);
                    cursorHidden = false;
                }
                if (hideCursorTimer != null) hideCursorTimer.restart();
            }
        });

        // Başlangıçta saat & tarih metinlerini güncelle ve timer başlat
        updateTimeText();
        clockTimer = new Timer(1000, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                updateTimeText();
                // Her saniye güncelliyoruz (dakika değişiminde de repaint yeterli olur)
                repaint();
            }
        });
        clockTimer.setCoalesce(true);
        clockTimer.start();

        // Cursor hide timer and cursors (initialized after component created)
        defaultCursor = getCursor();
        blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB), new Point(0,0), "blank");
        hideCursorTimer = new Timer(CURSOR_IDLE_MS, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                SwingUtilities.invokeLater(() -> {
                    setCursor(blankCursor);
                    cursorHidden = true;
                });
            }
        });
        hideCursorTimer.setRepeats(false);
    }

    private void updateTimeText() {
        try {
            LocalTime lt = LocalTime.now();
            timeText = lt.format(timeFmt);
            dateText = LocalDate.now().format(dateFmt);
        } catch (Exception e) {
            timeText = "";
            dateText = "";
        }
    }

    private void updateClockToggleText() {
        if (clockToggleItem == null) return;
        if (showClock) {
            clockToggleItem.setText("Saati gizle");
        } else {
            clockToggleItem.setText("Saat göster");
        }
    }

    // Public API used by the outer class to control the panel
    public void setParentFrame(JFrame frame) {
        this.parentFrame = frame;
    }

    public void setInfo(String info) {
        // show info text instead of image
        this.image = null;
        this.info = (info != null) ? info : "";
        // ensure cursor visible when showing info
        if (defaultCursor == null) defaultCursor = getCursor();
        setCursor(defaultCursor);
        cursorHidden = false;
        if (hideCursorTimer != null) hideCursorTimer.stop();
        repaint();
    }

    public void setImage(BufferedImage img) {
        this.image = img;
        if (img != null) {
            // when an image is shown, restart hide timer so cursor will auto-hide
            if (defaultCursor == null) defaultCursor = getCursor();
            setCursor(defaultCursor);
            cursorHidden = false;
            if (hideCursorTimer != null) hideCursorTimer.restart();

            // ensure dark background while showing images (avoids light strips)
            setBackground(Color.BLACK);
        } else {
            // if clearing the image, stop hide timer and restore info-bg
            if (hideCursorTimer != null) hideCursorTimer.stop();
            setBackground(new Color(230, 230, 230));
        }
        repaint();
    }

    // Yeni yardımcı: kenar renklerini örnekleyip ortalayıp gradient için kullanacağız
    private Color averageEdgeColor(BufferedImage img, boolean top) {
        if (img == null) return Color.BLACK;
        int w = img.getWidth();
        int h = img.getHeight();
        int samples = Math.min(64, w);
        long r = 0, g = 0, b = 0, c = 0;
        for (int i = 0; i < samples; i++) {
            int x = (int) Math.round((i + 0.5) * w / (double) samples);
            if (x >= w) x = w - 1;
            int y = top ? 0 : (h - 1);
            try {
                int rgb = img.getRGB(x, y);
                Color col = new Color(rgb, true);
                r += col.getRed();
                g += col.getGreen();
                b += col.getBlue();
                c++;
            } catch (Exception ignored) {}
        }
        if (c == 0) return Color.BLACK;
        return new Color((int) (r / c), (int) (g / c), (int) (b / c));
    }

    private Color blendWithBlack(Color col, float factor) {
        int rr = (int) (col.getRed() * (1f - factor));
        int gg = (int) (col.getGreen() * (1f - factor));
        int bb = (int) (col.getBlue() * (1f - factor));
        return new Color(Math.max(0, Math.min(255, rr)),
                         Math.max(0, Math.min(255, gg)),
                         Math.max(0, Math.min(255, bb)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fill background with a subtle gradient sampled from the image edges
            Color topColor = averageEdgeColor(image, true);
            Color bottomColor = averageEdgeColor(image, false);
            // Slightly darken samples so they don't appear brighter than image
            topColor = blendWithBlack(topColor, 0.06f);
            bottomColor = blendWithBlack(bottomColor, 0.06f);
            java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, topColor, 0, getHeight(), bottomColor);
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            double panelRatio = (double) getWidth() / getHeight();
            double imgRatio = (double) imgWidth / imgHeight;
            int drawWidth, drawHeight;
            if (imgRatio > panelRatio) {
                drawWidth = getWidth();
                drawHeight = (int) Math.round(getWidth() / imgRatio);
            } else {
                drawHeight = getHeight();
                drawWidth = (int) Math.round(getHeight() * imgRatio);
            }
            int x = (getWidth() - drawWidth) / 2;
            int y = (getHeight() - drawHeight) / 2;
            g2d.drawImage(image, x, y, drawWidth, drawHeight, null);

            // Saat + tarih bindirmesini sağ üst köşeye çiz
            if (showClock && timeText != null && !timeText.isEmpty()) {
                int margin = 18; // köşeden uzaklık

                // Time
                g2d.setFont(timeFont);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics fmTime = g2d.getFontMetrics();
                int timeW = fmTime.stringWidth(timeText);
                int timeH = fmTime.getAscent();

                // Date (smaller)
                g2d.setFont(dateFont);
                FontMetrics fmDate = g2d.getFontMetrics();
                int dateW = fmDate.stringWidth(dateText);
                int dateH = fmDate.getAscent();

                // Position time at right margin, then center date under time
                int txTime = getWidth() - timeW - margin;
                int tyTime = margin + timeH;
                int centerX = txTime + timeW / 2;
                int txDate = (int) Math.round(centerX - dateW / 2.0);
                if (txDate < margin) txDate = margin; // don't go beyond left margin
                int tyDate = tyTime + 6 + dateH; // small gap between lines

                // Draw main text in blue (no shadow)
                Color mainBlue = new Color(30, 144, 255); // DodgerBlue
                g2d.setColor(mainBlue);
                g2d.setFont(timeFont);
                g2d.drawString(timeText, txTime, tyTime);
                g2d.setFont(dateFont);
                g2d.drawString(dateText, txDate, tyDate);
            }

        } else {
            setBackground(new Color(230, 230, 230));
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(info);
            int x = (getWidth() - textWidth) / 2;
            int y = getHeight() / 2;
            g.drawString(info, x, y);
        }
    }
}
