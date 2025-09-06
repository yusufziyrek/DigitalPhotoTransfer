/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public class PhotoSenderApp extends JFrame {
    public static final String VERSION = AppConstants.VERSION; // GitHub release ile senkronize
    private static final AppLogger logger = new AppLogger("PhotoSender");
    
    private IpList ipList;
    private DefaultListModel<IpList.IpEntry> ipListModel;
    private JList<IpList.IpEntry> ipJList;
    private JButton addIpButton, selectPhotoButton, sendAllButton, sendSingleButton, sendWithTimerButton;
    private File selectedPhoto;
    private static final File IP_LIST_FILE = getAppDataIpListFile();
    
    // Otomatik durum yenileme için timer - 10 saniyede bir tarar
    private javax.swing.Timer statusRefreshTimer;
    private static final int STATUS_REFRESH_INTERVAL = 10000; // 10 saniye
    
    // Canlı ekran izleme için
    private javax.swing.Timer liveViewTimer;
    private JDialog liveViewDialog;
    private JLabel liveImageLabel;
    private String currentLiveViewIp;
    private String currentLiveViewName;

    private static File getAppDataIpListFile() {
        try {
            String appdata = System.getenv("APPDATA");
            if (appdata == null || appdata.isEmpty()) {
                // Fallback to user.home but validate it
                appdata = System.getProperty("user.home");
                if (appdata == null || appdata.isEmpty()) {
                    logger.warn("Neither APPDATA nor user.home available, using current directory");
                    return new File("iplist.txt");
                }
            }
            
            // Path normalization ve validation
            File appdataDir = new File(appdata).getCanonicalFile();
            if (!appdataDir.exists() || !appdataDir.isDirectory()) {
                logger.warn("Invalid APPDATA directory: " + appdata + ", using current directory");
                return new File("iplist.txt");
            }
            
            File dir = new File(appdataDir, "PhotoSender");
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Cannot create PhotoSender directory, using current directory");
                return new File("iplist.txt");
            }
            
            logger.info("IP list file path: " + new File(dir, "iplist.txt").getAbsolutePath());
            return new File(dir, "iplist.txt");
        } catch (IOException | SecurityException e) {
            logger.error("Path resolution failed, using current directory", e);
            return new File("iplist.txt");
        }
    }

    public PhotoSenderApp() {
        
    JMenuBar menuBar = new JMenuBar();
    JMenu helpMenu = new JMenu("Yardım");
    JMenuItem aboutItem = new JMenuItem("Hakkında");
    JMenuItem updateCheckItem = new JMenuItem("Güncellemeleri Denetle");
        JMenuItem usageGuideItem = new JMenuItem("Kullanım Kılavuzu");
    helpMenu.add(usageGuideItem);
    helpMenu.add(updateCheckItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

    usageGuideItem.addActionListener(_ -> {
            String usageGuide = "PhotoSender Kullanım Kılavuzu\n\n" +
                "1. IP ADRESİ YÖNETİMİ:\n" +
                "   • 'IP Ekle' butonu ile yeni IP adresi ekleyin\n" +
                "   • Listeden IP seçip sağ tık yaparak düzenleme/silme/default gösterme işlemleri yapın\n\n" +
                "2. FOTOĞRAF SEÇİMİ:\n" +
                "   • 'Fotoğraf Seç' butonu ile göndermek istediğiniz resmi seçin\n" +
                "   • Desteklenen formatlar: JPG, JPEG, PNG, BMP, GIF\n\n" +
                "3. FOTOĞRAF GÖNDERİMİ:\n" +
                "   • 'Hepsine Gönder': Listedeki tüm IP'lere fotoğraf gönderir\n" +
                "   • 'Seçilene Gönder': Sadece seçili IP'ye fotoğraf gönderir\n" +
                "   • 'Zamanlı Gönder': Belirli bir tarihe kadar fotoğraf gösterir\n\n" +
                "4. ZAMANLI GÖNDERİM:\n" +
                "   • Tarih ve saat girişi ile tam kontrolü sizde\n" +
                "   • Bitiş tarihi/saati: gg.aa.yyyy ss:dd formatında\n" +
                "   • Fotoğraf belirlenen tarih/saatte otomatik olarak kaybolur\n\n" +
                "5. EKRAN GÖRÜNTÜSÜ ALMA:\n" +
                "   • İlgili ip üzerinden sağ tık ile anlık veya canlı görüntü alabilirsiniz.\n\n";
            JTextArea textArea = new JTextArea(usageGuide);
            textArea.setEditable(false);
            textArea.setFont(new Font("Arial", Font.PLAIN, 12));
            textArea.setBackground(getBackground());
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(500, 400));
            
            JOptionPane.showMessageDialog(this, scrollPane, 
                "Kullanım Kılavuzu", JOptionPane.INFORMATION_MESSAGE);
        });

    aboutItem.addActionListener(_ -> {
            JOptionPane.showMessageDialog(this,
                "PhotoSender v" + VERSION + "\n" +
                "Bu yazılım Yusuf Ziyrek'e aittir.\n" +
                "İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.\n" +
                "Tüm hakları saklıdır. © 2025 Yusuf Ziyrek",
                "Hakkında", JOptionPane.INFORMATION_MESSAGE);
        });
        // Manuel güncelleme kontrol menü maddesi
        updateCheckItem.addActionListener(_ -> UpdateManager.manualCheck(this));
        ipList = new IpList();
        setTitle("Photo Sender - Wyndham Grand Istanbul Europe");
        setSize(600, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Window closing event için cleanup
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (statusRefreshTimer != null) {
                    statusRefreshTimer.stop();
                }
                // Canlı izlemeyi durdur
                stopLiveView();
                logger.info("Uygulama kapatılıyor, timer durduruldu");
            }
        });
        
        // Ana pencereye click listener ekle (genel seçim kaldırma için)
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // Ana pencereye tıklandığında IP seçimini kaldır
                if (ipJList != null) {
                    ipJList.clearSelection();
                }
            }
        });
        
    logger.info("PhotoSender UI başlatıldı");
    // Uygulama açılışında sessiz (yeni sürüm varsa soran) kontrol
    UpdateManager.autoCheckForUpdates(this);

        ipListModel = new DefaultListModel<>();
        ipJList = new JList<>(ipListModel);
        ipJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Özel renderer durum göstermek için
        ipJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (value instanceof IpList.IpEntry) {
                    IpList.IpEntry entry = (IpList.IpEntry) value;
                    String status = entry.getStatus();
                    String displayText = entry.getName() + " (" + entry.getIp() + ") - " + status;
                    setText(displayText);
                    
                    // Durum renkleri
                    if (!isSelected) {
                        switch (status) {
                            case "Toplantı Var":
                                setBackground(new Color(255, 230, 230)); // Açık kırmızı
                                break;
                            case "Default":
                                setBackground(new Color(230, 255, 230)); // Açık yeşil
                                break;
                            case "Bağlantı Hatası":
                            case "Zaman Aşımı":
                            case "Bağlantı Reddedildi":
                            case "Yanıt Yok":
                                setBackground(new Color(255, 255, 200)); // Açık sarı
                                break;
                            case "Bilinmiyor":
                                setBackground(new Color(240, 240, 240)); // Açık gri
                                break;
                            default:
                                setBackground(Color.WHITE);
                        }
                    }
                }
                return this;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(ipJList);
        add(scrollPane, BorderLayout.CENTER);

        // Boş yere tıklama için mouse listener ekle
        ipJList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // Boş yere tıklandıysa seçimi kaldır
                int index = ipJList.locationToIndex(e.getPoint());
                if (index == -1 || !ipJList.getCellBounds(index, index).contains(e.getPoint())) {
                    ipJList.clearSelection();
                }
            }
        });
        
        // Scroll pane'e de click listener ekle (liste dışı alan için)
        scrollPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // ScrollPane'in kendisine tıklandıysa seçimi kaldır
                ipJList.clearSelection();
            }
        });

        // Sağ tık menüsü ekle
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem updateItem = new JMenuItem("Güncelle");
        JMenuItem deleteItem = new JMenuItem("Sil");
            JMenuItem defaultItem = new JMenuItem("Default");
        JMenuItem screenshotItem = new JMenuItem("Ekranı Gör");
        JMenuItem liveViewItem = new JMenuItem("Canlı İzle");
        popupMenu.add(updateItem);
        popupMenu.add(deleteItem);
            popupMenu.add(defaultItem);
        popupMenu.addSeparator();
        popupMenu.add(screenshotItem);
        popupMenu.add(liveViewItem);

        ipJList.setComponentPopupMenu(popupMenu);
        ipJList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = ipJList.locationToIndex(e.getPoint());
                    ipJList.setSelectedIndex(index);
                }
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = ipJList.locationToIndex(e.getPoint());
                    ipJList.setSelectedIndex(index);
                }
            }
        });

            // Default Ayarı Koy işlemi
            defaultItem.addActionListener(_ -> {
                IpList.IpEntry selected = ipJList.getSelectedValue();
                if (selected == null) return;
                String ip = selected.getIp();
                int port = AppConstants.DEFAULT_PORT;
                try (Socket socket = new Socket(ip, port);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));) {
                    writer.write(AppConstants.COMMAND_SHOW_DEFAULT + "\n");
                    writer.flush();
                    JOptionPane.showMessageDialog(this, "İşlem Başarılı " + ip);
                    
                    // Default gösterildikten sonra durumu güncelle
                    ipList.updateStatus(ip, "Default");
                    ipJList.repaint();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "SHOW_DEFAULT gönderilemedi: " + ex.getMessage());
                }
            });

        // Ekran görüntüsü alma işlemi
        screenshotItem.addActionListener(_ -> {
            IpList.IpEntry selected = ipJList.getSelectedValue();
            if (selected == null) return;
            
            String ip = selected.getIp();
            String name = selected.getName();
            
            logger.info("Ultra kalite ekran görüntüsü istendi - Hedef: " + name + " (" + ip + ")");
            
            // HD Screenshot alma işlemini background thread'de yap
            SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
                @Override
                protected BufferedImage doInBackground() throws Exception {
                    logger.info("Yüksek kalite ekran görüntüsü alma işlemi başlatıldı: " + ip);
                    return requestScreenshot(ip);
                }
                
                @Override
                protected void done() {
                    try {
                        BufferedImage screenshot = get();
                        if (screenshot != null) {
                            logger.info("Ultra kalite ekran görüntüsü başarıyla alındı: " + name + " (" + ip + ") - Boyut: " + 
                                      screenshot.getWidth() + "x" + screenshot.getHeight() + " (HD Quality)");
                            showScreenshotDialog(name + " (" + ip + ")", screenshot);
                        } else {
                            logger.warn("Ultra kalite ekran görüntüsü alınamadı (null response): " + ip);
                            JOptionPane.showMessageDialog(PhotoSenderApp.this, 
                                "Ultra kalite ekran görüntüsü alınamadı: " + ip + "\nBağlantı problemi olabilir.", 
                                "HD Ekran Görüntüsü Hatası", 
                                JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        logger.error("Ultra kalite ekran görüntüsü alma işlemi başarısız: " + ip + " - " + ex.getMessage(), ex);
                        JOptionPane.showMessageDialog(PhotoSenderApp.this, 
                            "HD ekran görüntüsü hatası: " + ex.getMessage() + "\nIP: " + ip, 
                            "HD Bağlantı Hatası", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        });

        // Canlı ekran izleme işlemi
        liveViewItem.addActionListener(_ -> {
            IpList.IpEntry selected = ipJList.getSelectedValue();
            if (selected == null) return;
            
            String ip = selected.getIp();
            String name = selected.getName();
            
            logger.info("Canlı ekran izleme başlatıldı - Hedef: " + name + " (" + ip + ")");
            startLiveView(ip, name);
        });

        // Güncelleme işlemi
    updateItem.addActionListener(_ -> {
            IpList.IpEntry selected = ipJList.getSelectedValue();
            if (selected == null) return;
            JTextField ipField = new JTextField(selected.getIp());
            JTextField nameField = new JTextField(selected.getName());
            Object[] fields = {"IP Adresi:", ipField, "İsim:", nameField};
            int result = JOptionPane.showConfirmDialog(this, fields, "IP ve İsim Güncelle", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String newIp = ipField.getText().trim();
                String newName = nameField.getText().trim();
                if (!newIp.isEmpty() && !newName.isEmpty()) {
                    int idx = ipJList.getSelectedIndex();
                    ipList.getIpEntries().set(idx, new IpList.IpEntry(newIp, newName));
                    ipListModel.set(idx, new IpList.IpEntry(newIp, newName));
                    try {
                        ipList.saveToFile(IP_LIST_FILE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, AppMessages.formatIpSaveError(ex.getMessage()));
                    }
                }
            }
        });

        // Silme işlemi
    deleteItem.addActionListener(_ -> {
            int idx = ipJList.getSelectedIndex();
            if (idx >= 0) {
                ipList.getIpEntries().remove(idx);
                ipListModel.remove(idx);
                try {
                    ipList.saveToFile(IP_LIST_FILE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, AppMessages.formatIpSaveError(ex.getMessage()));
                }
            }
        });

        // IP listesini dosyadan yükle
        if (IP_LIST_FILE.exists()) {
            try {
                ipList.loadFromFile(IP_LIST_FILE);
                for (IpList.IpEntry entry : ipList.getIpEntries()) {
                    ipListModel.addElement(entry);
                }
                
                // IP'ler yüklendikten sonra durumları güncelle
                // İlk başlatmada PhotoViewer'ların başlaması için biraz bekle
                if (ipList.getIpEntries().size() > 0) {
                    logger.info(ipList.getIpEntries().size() + " IP yüklendi, durum sorgulaması başlatılıyor...");
                    
                    // Hemen bir durum güncellemesi yap
                    SwingUtilities.invokeLater(() -> updateAllIPStatuses());
                    
                    // 5 saniye sonra tekrar kontrol et (PhotoViewer'ların başlaması için)
                    javax.swing.Timer delayedCheck = new javax.swing.Timer(5000, _ -> {
                        logger.info("Gecikmiş durum kontrolü başlatılıyor...");
                        updateAllIPStatuses();
                    });
                    delayedCheck.setRepeats(false);
                    delayedCheck.start();
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, AppMessages.formatIpReadError(ex.getMessage()));
            }
        }

        JPanel panel = new JPanel();
        addIpButton = new JButton("IP Ekle");
        selectPhotoButton = new JButton("Fotoğraf Seç");
        sendAllButton = new JButton("Toplu Gönder");
        sendSingleButton = new JButton("Seçiliye Gönder");
        
        // Zamanlı gönderim butonu
        sendWithTimerButton = new JButton("Zamanlı Gönder");
        
        // Durum güncelleme butonu
        JButton refreshStatusButton = new JButton("Durum Güncelle");
        
        panel.add(addIpButton);
        panel.add(selectPhotoButton);
        panel.add(sendAllButton);
        panel.add(sendSingleButton);
        panel.add(sendWithTimerButton);
        panel.add(refreshStatusButton);
        add(panel, BorderLayout.SOUTH);

    addIpButton.addActionListener(_ -> {
            JTextField ipField = new JTextField();
            JTextField nameField = new JTextField();
            Object[] fields = {"IP Adresi:", ipField, "İsim:", nameField};
            int result = JOptionPane.showConfirmDialog(this, fields, "IP ve İsim Ekle", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String ip = ipField.getText().trim();
                String name = nameField.getText().trim();
                if (!ip.isEmpty() && !name.isEmpty()) {
                    ipList.addIp(ip, name);
                    IpList.IpEntry newEntry = new IpList.IpEntry(ip, name);
                    ipListModel.addElement(newEntry);
                    logger.info("Yeni IP eklendi: " + name + " (" + ip + ")");
                    
                    // Yeni eklenen IP'nin durumunu hemen sorgula
                    SwingWorker<Void, Void> statusWorker = new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            String status = queryIpStatus(ip);
                            ipList.updateStatus(ip, status);
                            logger.info("Yeni IP durum sorgulaması: " + name + " (" + ip + ") -> " + status);
                            return null;
                        }
                        
                        @Override
                        protected void done() {
                            ipJList.repaint();
                        }
                    };
                    statusWorker.execute();
                    
                    // IP listesini dosyaya kaydet
                    try {
                        ipList.saveToFile(IP_LIST_FILE);
                        logger.info("IP listesi kaydedildi");
                    } catch (IOException ex) {
                        logger.error("IP listesi kaydedilemedi", ex);
                        JOptionPane.showMessageDialog(this, "IP listesi kaydedilemedi: " + ex.getMessage());
                    }
                }
            }
        });

    selectPhotoButton.addActionListener(_ -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) return true;
                    String name = f.getName().toLowerCase();
                    return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                           name.endsWith(".png") || name.endsWith(".bmp") ||
                           name.endsWith(".gif");
                }
                @Override
                public String getDescription() {
                    return "Resim Dosyaları (*.jpg, *.jpeg, *.png, *.bmp, *.gif)";
                }
            });
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".bmp") ||
                    name.endsWith(".gif")) {
                    selectedPhoto = file;
                    logger.info("Fotoğraf seçildi: " + file.getName() + " (" + (file.length() / 1024) + " KB)");
                } else {
                    JOptionPane.showMessageDialog(this, AppMessages.INFO_PLEASE_SELECT_PHOTO);
                    selectedPhoto = null;
                    logger.warn("Geçersiz dosya türü seçildi: " + file.getName());
                }
            }
        });

    sendAllButton.addActionListener(_ -> {
            if (selectedPhoto == null) {
                JOptionPane.showMessageDialog(this, AppMessages.ERROR_NO_PHOTO_SELECTED);
                return;
            }
            logger.info("Toplu gönderim başlatıldı - " + ipList.getIpEntries().size() + " hedef");
            sendPhotoToIps(selectedPhoto, ipList.getIpEntries());
        });

    sendSingleButton.addActionListener(_ -> {
            if (selectedPhoto == null) {
                JOptionPane.showMessageDialog(this, AppMessages.ERROR_NO_PHOTO_SELECTED);
                return;
            }
            IpList.IpEntry selected = ipJList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, AppMessages.ERROR_NO_IP_SELECTED);
                return;
            }
            sendPhotoToIps(selectedPhoto, List.of(selected));
        });

    sendWithTimerButton.addActionListener(_ -> {
            if (selectedPhoto == null) {
                JOptionPane.showMessageDialog(this, "Önce fotoğraf seçin.");
                return;
            }
            
            IpList.IpEntry selected = ipJList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Listeden bir IP seçin.");
                return;
            }
            
            // Tarih ve saat seçim dialogu
            showDateTimeDialog(selected);
        });

        // Durum güncelleme butonu
        refreshStatusButton.addActionListener(_ -> {
            updateAllIPStatuses();
        });
        
        // Otomatik durum yenileme timer'ı - her 10 saniyede bir
        statusRefreshTimer = new javax.swing.Timer(STATUS_REFRESH_INTERVAL, _ -> {
            if (ipList.getIpEntries().size() > 0) {
                updateAllIPStatuses();
            }
        });
        statusRefreshTimer.start();
        
        logger.info("Otomatik durum yenileme başlatıldı (10 saniye aralıklarla)");
    }

    private void sendPhotoToIps(File photo, List<IpList.IpEntry> entries) {
        // Enhanced input validation
        if (photo == null || !photo.exists()) {
            JOptionPane.showMessageDialog(this, AppMessages.ERROR_IMAGE_INVALID, AppMessages.TITLE_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!photo.canRead()) {
            JOptionPane.showMessageDialog(this, AppMessages.ERROR_IMAGE_READ, AppMessages.TITLE_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (photo.length() == 0) {
            JOptionPane.showMessageDialog(this, AppMessages.ERROR_FILE_EMPTY, AppMessages.TITLE_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (entries == null || entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, AppMessages.ERROR_NO_IP_TO_SEND, AppMessages.TITLE_WARNING, JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Disable UI while sending
        addIpButton.setEnabled(false);
        selectPhotoButton.setEnabled(false);
        sendAllButton.setEnabled(false);
        sendSingleButton.setEnabled(false);
        sendWithTimerButton.setEnabled(false);

        final int CONNECT_TIMEOUT_MS = AppConstants.CONNECTION_TIMEOUT_MS;
        final int READ_TIMEOUT_MS = AppConstants.READ_TIMEOUT_MS;
        final int MAX_RETRIES = AppConstants.MAX_RETRIES;
        final int BUFFER_SIZE = AppConstants.BUFFER_SIZE;
        final long READ_ONCE_THRESHOLD = AppConstants.READ_ONCE_THRESHOLD;

    // Progress dialog (create first so worker can reference it)
    final JDialog progressDialog = new JDialog(this, "Gönderiliyor...", true);
    final JProgressBar progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    progressDialog.setLayout(new BorderLayout());
    progressDialog.add(new JLabel("Lütfen bekleyin..."), BorderLayout.NORTH);
    progressDialog.add(progressBar, BorderLayout.CENTER);
    JPanel p = new JPanel();
    progressDialog.add(p, BorderLayout.SOUTH);
    progressDialog.setSize(350, 120);
    progressDialog.setLocationRelativeTo(this);

    // Holder so cancel button can reference the worker (avoids final capture issues)
    final SwingWorker<?, ?>[] workerHolder = new SwingWorker[1];

    // holders for photo bytes and mode so worker lambda can access/mutate them
    final java.util.concurrent.atomic.AtomicReference<byte[]> photoBytesRef = new java.util.concurrent.atomic.AtomicReference<>();
    final java.util.concurrent.atomic.AtomicBoolean useByteArrayRef = new java.util.concurrent.atomic.AtomicBoolean(photo.length() <= READ_ONCE_THRESHOLD);

    SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            private final List<String> failures = Collections.synchronizedList(new ArrayList<>());

            @Override
            protected Void doInBackground() throws Exception {
                // attempt to preload photo into memory if small enough
                if (useByteArrayRef.get()) {
                    try {
                        photoBytesRef.set(Files.readAllBytes(photo.toPath()));
                    } catch (OutOfMemoryError | IOException e) {
                        // fallback to streaming per-target
                        photoBytesRef.set(null);
                        useByteArrayRef.set(false);
                        System.out.println("Fotoğraf belleğe yüklenemedi, akışla gönderilecek: " + e.getMessage());
                    }
                }

                int total = entries.size();
                int poolSize = Math.min(total, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
                ExecutorService pool = Executors.newFixedThreadPool(poolSize);
                CountDownLatch latch = new CountDownLatch(total);
                AtomicInteger completed = new AtomicInteger(0);

                for (IpList.IpEntry entry : entries) {
                    pool.submit(() -> {
                        String ip = entry.getIp();
                        String name = entry.getName();
                        boolean success = false;
                        for (int attempt = 1; attempt <= MAX_RETRIES + 1 && !success; attempt++) {
                            Socket socket = null;
                            try {
                                socket = new Socket();
                                socket.connect(new InetSocketAddress(ip, AppConstants.DEFAULT_PORT), CONNECT_TIMEOUT_MS);
                                socket.setSoTimeout(READ_TIMEOUT_MS);
                                OutputStream os = socket.getOutputStream();
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                                long lengthToSend = (photoBytesRef.get() != null) ? photoBytesRef.get().length : photo.length();
                                // Send header with length so receiver can read exact bytes
                                writer.write(AppConstants.COMMAND_SEND_PHOTO + lengthToSend + "\n");
                                writer.flush();

                                if (useByteArrayRef.get() && photoBytesRef.get() != null) {
                                    os.write(photoBytesRef.get());
                                } else {
                                    try (FileInputStream fis = new FileInputStream(photo)) {
                                        byte[] buffer = new byte[BUFFER_SIZE];
                                        int r;
                                        while ((r = fis.read(buffer)) != -1) {
                                            os.write(buffer, 0, r);
                                        }
                                    }
                                }
                                os.flush();
                                try { socket.shutdownOutput(); } catch (IOException ignored) {}
                                // Wait for ACK from receiver
                                String ack = null;
                                try {
                                    socket.setSoTimeout(READ_TIMEOUT_MS);
                                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "US-ASCII"));
                                    ack = br.readLine();
                                } catch (SocketTimeoutException ste) {
                                    System.out.println("ACK zaman aşımı: " + ip + " -> " + ste.getMessage());
                                } catch (IOException ioe) {
                                    System.out.println("ACK okuma hatası: " + ip + " -> " + ioe.getMessage());
                                }
                                if (AppConstants.RESPONSE_OK.equals(ack)) {
                                    success = true;
                                    logger.info("Photo sent successfully to " + name + " (" + ip + ")");
                                    System.out.println("Gönderildi ve onaylandı: " + name + " (" + ip + ")");
                                    
                                    // Fotoğraf gönderildikten sonra durumu güncelle
                                    SwingUtilities.invokeLater(() -> {
                                        ipList.updateStatus(ip, "Toplantı Var");
                                        ipJList.repaint();
                                    });
                                } else {
                                    logger.warn("Photo sent but no ACK from " + name + " (" + ip + ") ack=" + ack);
                                    System.out.println("Gönderildi fakat onay alınamadı: " + name + " (" + ip + ") ack=" + ack);
                                }
                            } catch (SocketTimeoutException ste) {
                                logger.warn("Socket timeout for " + ip + ": " + ste.getMessage());
                                System.out.println("Zaman aşımı: " + ip + " -> " + ste.getMessage());
                            } catch (IOException ioe) {
                                logger.warn("IO error for " + ip + ": " + ioe.getMessage());
                                System.out.println("IO hatası: " + ip + " -> " + ioe.getMessage());
                            } catch (Exception ex) {
                                logger.error("Unexpected error for " + ip, ex);
                                System.out.println("Genel hata: " + ip + " -> " + ex.getMessage());
                            } finally {
                                if (socket != null) {
                                    try { socket.close(); } catch (IOException ignored) {}
                                }
                            }

                            if (!success) {
                                // exponential backoff
                                try { Thread.sleep(AppConstants.RETRY_BACKOFF_BASE_MS * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                            }
                        }

                        if (!success) {
                            failures.add(name + " (" + ip + ")");
                        }

                        int done = completed.incrementAndGet();
                        publish((int) ((done / (double) total) * 100));
                        latch.countDown();
                    });
                }

                pool.shutdown();
                // Wait for all tasks or timeout
                try {
                    if (!latch.await(5, TimeUnit.MINUTES)) {
                        pool.shutdownNow();
                        failures.add("Zaman aşımı: Gönderim tamamlanamadı (süre aşıldı)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.add("Gönderim iptal edildi.");
                }

                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last);
            }

            @Override
            protected void done() {
                addIpButton.setEnabled(true);
                selectPhotoButton.setEnabled(true);
                sendAllButton.setEnabled(true);
                sendSingleButton.setEnabled(true);
                sendWithTimerButton.setEnabled(true);
                progressDialog.setVisible(false);
                progressDialog.dispose();

                if (failures.isEmpty()) {
                    JOptionPane.showMessageDialog(PhotoSenderApp.this, AppMessages.INFO_PHOTO_SENT);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Bazı hedeflere gönderilemedi:\n");
                    for (String f : failures) sb.append("- ").append(f).append("\n");
                    JOptionPane.showMessageDialog(PhotoSenderApp.this, sb.toString(), AppMessages.ERROR_NETWORK_SEND, JOptionPane.WARNING_MESSAGE);
                }
            }
        };

        // Cancel button that cancels the worker
        JButton cancelBtn = new JButton("İptal");
        cancelBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                SwingWorker<?, ?> wk = workerHolder[0];
                if (wk != null) wk.cancel(true);
            }
        });
        p.add(cancelBtn);

        // Start worker and show dialog
        workerHolder[0] = worker;
        worker.execute();
        progressDialog.setVisible(true);
    }

    private void sendPhotoWithTimer(File photo, List<IpList.IpEntry> entries, int durationSeconds) {
        // Quick checks
        if (entries == null || entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, AppMessages.ERROR_NO_IP_TO_SEND);
            return;
        }

        // Bitiş tarihi hesapla ve logla
        java.util.Calendar endTime = java.util.Calendar.getInstance();
        endTime.add(java.util.Calendar.SECOND, durationSeconds);
        String endTimeText = formatDateTime(endTime);
        String durationText = formatDuration(durationSeconds);
        
        logger.info("Zamanlı fotoğraf gönderimi başlıyor - Hedef sayısı: " + entries.size() + 
                   ", Bitiş: " + endTimeText + " (" + durationText + ")");

        // Disable UI while sending
        addIpButton.setEnabled(false);
        selectPhotoButton.setEnabled(false);
        sendAllButton.setEnabled(false);
        sendSingleButton.setEnabled(false);
        sendWithTimerButton.setEnabled(false);

        final int CONNECT_TIMEOUT_MS = AppConstants.CONNECTION_TIMEOUT_MS;
        final int READ_TIMEOUT_MS = AppConstants.READ_TIMEOUT_MS;
        final int MAX_RETRIES = AppConstants.MAX_RETRIES;
        final int BUFFER_SIZE = AppConstants.BUFFER_SIZE;
        final long READ_ONCE_THRESHOLD = AppConstants.READ_ONCE_THRESHOLD;

        // Progress dialog (create first so worker can reference it)
        final JDialog progressDialog = new JDialog(this, "Zamanlı Gönderiliyor...", true);
        final JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.add(new JLabel("Lütfen bekleyin..."), BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        JPanel p = new JPanel();
        progressDialog.add(p, BorderLayout.SOUTH);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(this);

        // Holder so cancel button can reference the worker (avoids final capture issues)
        final SwingWorker<?, ?>[] workerHolder = new SwingWorker[1];

        // holders for photo bytes and mode so worker lambda can access/mutate them
        final java.util.concurrent.atomic.AtomicReference<byte[]> photoBytesRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean useByteArrayRef = new java.util.concurrent.atomic.AtomicBoolean(photo.length() <= READ_ONCE_THRESHOLD);

        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            private final List<String> failures = Collections.synchronizedList(new ArrayList<>());

            @Override
            protected Void doInBackground() throws Exception {
                // attempt to preload photo into memory if small enough
                if (useByteArrayRef.get()) {
                    try {
                        photoBytesRef.set(Files.readAllBytes(photo.toPath()));
                    } catch (OutOfMemoryError | IOException e) {
                        // fallback to streaming per-target
                        photoBytesRef.set(null);
                        useByteArrayRef.set(false);
                        System.out.println("Fotoğraf belleğe yüklenemedi, akışla gönderilecek: " + e.getMessage());
                    }
                }

                int total = entries.size();
                int poolSize = Math.min(total, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
                ExecutorService pool = Executors.newFixedThreadPool(poolSize);
                CountDownLatch latch = new CountDownLatch(total);
                AtomicInteger completed = new AtomicInteger(0);

                for (IpList.IpEntry entry : entries) {
                    pool.submit(() -> {
                        String ip = entry.getIp();
                        String name = entry.getName();
                        boolean success = false;
                        for (int attempt = 1; attempt <= MAX_RETRIES + 1 && !success; attempt++) {
                            Socket socket = null;
                            try {
                                socket = new Socket();
                                socket.connect(new InetSocketAddress(ip, AppConstants.DEFAULT_PORT), CONNECT_TIMEOUT_MS);
                                socket.setSoTimeout(READ_TIMEOUT_MS);
                                OutputStream os = socket.getOutputStream();
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                                
                                long lengthToSend = (photoBytesRef.get() != null) ? photoBytesRef.get().length : photo.length();
                                
                                // Yeni protokol: Süre bilgisi ile gönder
                                writer.write(AppConstants.COMMAND_SEND_PHOTO_WITH_TIMER + lengthToSend + ":" + durationSeconds + "\n");
                                writer.flush();

                                if (useByteArrayRef.get() && photoBytesRef.get() != null) {
                                    os.write(photoBytesRef.get());
                                } else {
                                    try (FileInputStream fis = new FileInputStream(photo)) {
                                        byte[] buffer = new byte[BUFFER_SIZE];
                                        int r;
                                        while ((r = fis.read(buffer)) != -1) {
                                            os.write(buffer, 0, r);
                                        }
                                    }
                                }
                                os.flush();
                                try { socket.shutdownOutput(); } catch (IOException ignored) {}
                                
                                // Wait for ACK from receiver
                                String ack = null;
                                try {
                                    socket.setSoTimeout(READ_TIMEOUT_MS);
                                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "US-ASCII"));
                                    ack = br.readLine();
                                } catch (SocketTimeoutException ste) {
                                    System.out.println("ACK zaman aşımı: " + ip + " -> " + ste.getMessage());
                                } catch (IOException ioe) {
                                    System.out.println("ACK okuma hatası: " + ip + " -> " + ioe.getMessage());
                                }
                                if (AppConstants.RESPONSE_OK.equals(ack)) {
                                    success = true;
                                    // Bitiş tarihini hesapla ve logla
                                    java.util.Calendar endTime = java.util.Calendar.getInstance();
                                    endTime.add(java.util.Calendar.SECOND, durationSeconds);
                                    String endTimeText = formatDateTime(endTime);
                                    
                                    logger.success("Zamanlı fotoğraf başarıyla gönderildi: " + name + " (" + ip + ") - Bitiş: " + endTimeText);
                                    System.out.println("Zamanlı gönderildi ve onaylandı: " + name + " (" + ip + ") - Bitiş: " + endTimeText);
                                    
                                    // Zamanlı fotoğraf gönderildikten sonra durumu güncelle
                                    SwingUtilities.invokeLater(() -> {
                                        ipList.updateStatus(ip, "Toplantı Var");
                                        ipJList.repaint();
                                    });
                                } else {
                                    logger.warn("Zamanlı fotoğraf gönderildi ancak onay alınamadı: " + name + " (" + ip + ") ack=" + ack);
                                    System.out.println("Gönderildi fakat onay alınamadı: " + name + " (" + ip + ") ack=" + ack);
                                }
                            } catch (SocketTimeoutException ste) {
                                logger.warn("Zamanlı gönderim zaman aşımı: " + name + " (" + ip + ") - " + ste.getMessage());
                                System.out.println("Zaman aşımı: " + ip + " -> " + ste.getMessage());
                            } catch (IOException ioe) {
                                logger.warn("Zamanlı gönderim IO hatası: " + name + " (" + ip + ") - " + ioe.getMessage());
                                System.out.println("IO hatası: " + ip + " -> " + ioe.getMessage());
                            } catch (Exception ex) {
                                logger.error("Zamanlı gönderim beklenmeyen hata: " + name + " (" + ip + ")", ex);
                                System.out.println("Genel hata: " + ip + " -> " + ex.getMessage());
                            } finally {
                                if (socket != null) {
                                    try { socket.close(); } catch (IOException ignored) {}
                                }
                            }

                            if (!success) {
                                // exponential backoff
                                try { Thread.sleep(AppConstants.RETRY_BACKOFF_BASE_MS * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                            }
                        }

                        if (!success) {
                            failures.add(name + " (" + ip + ")");
                        }

                        int done = completed.incrementAndGet();
                        publish((int) ((done / (double) total) * 100));
                        latch.countDown();
                    });
                }

                pool.shutdown();
                // Wait for all tasks or timeout
                try {
                    if (!latch.await(5, TimeUnit.MINUTES)) {
                        pool.shutdownNow();
                        failures.add("Zaman aşımı: Gönderim tamamlanamadı (süre aşıldı)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.add("Gönderim iptal edildi.");
                }

                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last);
            }

            @Override
            protected void done() {
                addIpButton.setEnabled(true);
                selectPhotoButton.setEnabled(true);
                sendAllButton.setEnabled(true);
                sendSingleButton.setEnabled(true);
                sendWithTimerButton.setEnabled(true);
                progressDialog.setVisible(false);
                progressDialog.dispose();

                if (failures.isEmpty()) {
                    // Bitiş tarihi hesapla
                    java.util.Calendar endTime = java.util.Calendar.getInstance();
                    endTime.add(java.util.Calendar.SECOND, durationSeconds);
                    String endTimeText = formatDateTime(endTime);
                    String durationText = formatDuration(durationSeconds);
                    
                    JOptionPane.showMessageDialog(PhotoSenderApp.this, 
                        "Zamanlı fotoğraf başarıyla gönderildi!\n\n" +
                        "Bitiş tarihi: " + endTimeText + "\n" +
                        "Süre: " + durationText);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Bazı hedeflere gönderilemedi:\n");
                    for (String f : failures) sb.append("- ").append(f).append("\n");
                    JOptionPane.showMessageDialog(PhotoSenderApp.this, sb.toString(), "Gönderim Hataları", JOptionPane.WARNING_MESSAGE);
                }
            }
        };

        // Cancel button that cancels the worker
        JButton cancelBtn = new JButton("İptal");
        cancelBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                SwingWorker<?, ?> wk = workerHolder[0];
                if (wk != null) wk.cancel(true);
            }
        });
        p.add(cancelBtn);

        // Start worker and show dialog
        workerHolder[0] = worker;
        worker.execute();
        progressDialog.setVisible(true);
    }

    private void showDateTimeDialog(IpList.IpEntry targetEntry) {
        JDialog dialog = new JDialog(this, "Zamanlı Gönderim Ayarları", true);
        dialog.setSize(420, 280);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Ana panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));
        mainPanel.setBackground(Color.WHITE);

        // Başlık
        JLabel titleLabel = new JLabel("Fotoğraf hangi tarihe kadar gösterilsin?");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(new Color(51, 51, 51));
        mainPanel.add(titleLabel);
        
        mainPanel.add(Box.createVerticalStrut(8));
        
        // Hedef bilgisi
        JLabel targetLabel = new JLabel("Hedef: " + targetEntry.getName() + " (" + targetEntry.getIp() + ")");
        targetLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        targetLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        targetLabel.setForeground(new Color(102, 102, 102));
        mainPanel.add(targetLabel);
        
        mainPanel.add(Box.createVerticalStrut(20));

        // Tarih/Saat seçim paneli
        JPanel dateTimePanel = new JPanel(new GridBagLayout());
        dateTimePanel.setBackground(new Color(248, 249, 250));
        dateTimePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Şu anki zaman
        java.util.Calendar now = java.util.Calendar.getInstance();
        String currentTimeText = String.format("Şu an: %02d.%02d.%04d %02d:%02d",
            now.get(java.util.Calendar.DAY_OF_MONTH),
            now.get(java.util.Calendar.MONTH) + 1,
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE));
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        JLabel currentLabel = new JLabel(currentTimeText);
        currentLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        currentLabel.setForeground(new Color(102, 102, 102));
        currentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateTimePanel.add(currentLabel, gbc);
        
        gbc.gridwidth = 1;
        gbc.gridy = 1;

        // Tarih etiketleri ve kontroller
        gbc.gridx = 0;
        JLabel dateLabel = new JLabel("Tarih:");
        dateLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        dateTimePanel.add(dateLabel, gbc);

        // Tarih spinnerları
        int currentYear = now.get(java.util.Calendar.YEAR);
        int currentMonth = now.get(java.util.Calendar.MONTH) + 1;
        int currentDay = now.get(java.util.Calendar.DAY_OF_MONTH);
        
        gbc.gridx = 1;
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(currentDay, 1, 31, 1));
        styleSpinner(daySpinner, 50);
        dateTimePanel.add(daySpinner, gbc);
        
        gbc.gridx = 2;
        JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(currentMonth, 1, 12, 1));
        styleSpinner(monthSpinner, 50);
        dateTimePanel.add(monthSpinner, gbc);
        
        gbc.gridx = 3;
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(currentYear, currentYear, currentYear + 5, 1));
        styleSpinner(yearSpinner, 70);
        dateTimePanel.add(yearSpinner, gbc);

        // Saat kontrolleri
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel timeLabel = new JLabel("Saat:");
        timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        dateTimePanel.add(timeLabel, gbc);

        int defaultHour = (now.get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24;
        int currentMinute = now.get(java.util.Calendar.MINUTE);
        
        gbc.gridx = 1;
        JSpinner hourSpinner = new JSpinner(new SpinnerNumberModel(defaultHour, 0, 23, 1));
        styleSpinner(hourSpinner, 50);
        dateTimePanel.add(hourSpinner, gbc);
        
        gbc.gridx = 2;
        JLabel colonLabel = new JLabel(":");
        colonLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        colonLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateTimePanel.add(colonLabel, gbc);
        
        gbc.gridx = 3;
        JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(currentMinute, 0, 59, 1));
        styleSpinner(minuteSpinner, 50);
        dateTimePanel.add(minuteSpinner, gbc);

        mainPanel.add(dateTimePanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Önizleme etiketi
        JLabel previewLabel = new JLabel();
        previewLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        previewLabel.setForeground(new Color(25, 135, 84));
        previewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        mainPanel.add(previewLabel);

        // Buton paneli
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(Color.WHITE);
        
        JButton sendButton = new JButton("Gönder");
        JButton cancelButton = new JButton("İptal");
        
        // Buton stilleri
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setBackground(new Color(0, 123, 255));
        sendButton.setForeground(Color.WHITE);
        sendButton.setPreferredSize(new Dimension(80, 32));
        sendButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        sendButton.setFocusPainted(false);
        
        cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cancelButton.setBackground(new Color(248, 249, 250));
        cancelButton.setForeground(new Color(73, 80, 87));
        cancelButton.setPreferredSize(new Dimension(80, 32));
        cancelButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(206, 212, 218), 1),
            BorderFactory.createEmptyBorder(7, 15, 7, 15)
        ));
        cancelButton.setFocusPainted(false);

        buttonPanel.add(sendButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(cancelButton);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // Önizleme güncelleme fonksiyonu
        Runnable updatePreview = () -> {
            try {
                int day = (Integer) daySpinner.getValue();
                int month = (Integer) monthSpinner.getValue();
                int year = (Integer) yearSpinner.getValue();
                int hour = (Integer) hourSpinner.getValue();
                int minute = (Integer) minuteSpinner.getValue();
                
                String preview = String.format("➤ Bitiş: %02d.%02d.%04d %02d:%02d", day, month, year, hour, minute);
                previewLabel.setText(preview);
                previewLabel.setForeground(new Color(25, 135, 84));
            } catch (Exception e) {
                previewLabel.setText("⚠ Geçersiz tarih/saat");
                previewLabel.setForeground(new Color(220, 53, 69));
            }
        };
        
        // Spinner değişiklik dinleyicileri
        daySpinner.addChangeListener(_ -> updatePreview.run());
        monthSpinner.addChangeListener(_ -> updatePreview.run());
        yearSpinner.addChangeListener(_ -> updatePreview.run());
        hourSpinner.addChangeListener(_ -> updatePreview.run());
        minuteSpinner.addChangeListener(_ -> updatePreview.run());
        
        // İlk önizlemeyi ayarla
        updatePreview.run();

        // Özel tarih/saat gönder butonu
        sendButton.addActionListener(_ -> {
            int day = (Integer) daySpinner.getValue();
            int month = (Integer) monthSpinner.getValue();
            int year = (Integer) yearSpinner.getValue();
            int hour = (Integer) hourSpinner.getValue();
            int minute = (Integer) minuteSpinner.getValue();
            
            // Seçilen tarihi kontrol et
            java.util.Calendar targetCalendar = java.util.Calendar.getInstance();
            try {
                // Geçerli bir tarih mi kontrol et
                targetCalendar.setLenient(false); // Katı tarih kontrolü
                targetCalendar.set(year, month - 1, day, hour, minute, 0); // month 0-based
                targetCalendar.set(java.util.Calendar.MILLISECOND, 0);
                targetCalendar.getTime(); // Bu satır geçersiz tarih durumunda exception fırlatır
                
                java.util.Calendar currentCalendar = java.util.Calendar.getInstance();
                
                if (targetCalendar.before(currentCalendar) || targetCalendar.equals(currentCalendar)) {
                    JOptionPane.showMessageDialog(dialog, "Seçilen tarih/saat şu anki zamandan sonra olmalıdır!", 
                                                "Geçersiz Tarih/Saat", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                // Süreyi saniye cinsinden hesapla
                long durationMillis = targetCalendar.getTimeInMillis() - currentCalendar.getTimeInMillis();
                int durationSeconds = (int) (durationMillis / 1000);
                
                sendWithSpecificDateTime(targetEntry, targetCalendar, durationSeconds, dialog);
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(dialog, "Geçersiz tarih girişi! Lütfen geçerli bir tarih seçin.\n" +
                                            "Örnek: 29 Şubat geçersiz bir tarihtir (artık yıl değilse).", 
                                            "Hata", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(_ -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void styleSpinner(JSpinner spinner, int width) {
        spinner.setPreferredSize(new Dimension(width, 28));
        spinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        // Spinner text field'ını merkez hizala ve stil ver
        JTextField textField = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        textField.setHorizontalAlignment(JTextField.CENTER);
        textField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        textField.setBackground(Color.WHITE);
        
        // Yıl spinner'ı için özel formatting (binlik ayracını kaldır)
        if (spinner.getModel() instanceof SpinnerNumberModel) {
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
            // Eğer bu bir yıl spinner'ı ise (değer 2000'den büyükse)
            if (((Number) model.getValue()).intValue() >= 2000) {
                JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
                spinner.setEditor(editor);
                editor.getTextField().setHorizontalAlignment(JTextField.CENTER);
                editor.getTextField().setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                editor.getTextField().setBackground(Color.WHITE);
            }
        }
        
        // Spinner border'ını güzelleştir
        spinner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(206, 212, 218), 1),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
    }

    private void sendWithSpecificDateTime(IpList.IpEntry targetEntry, java.util.Calendar targetDateTime, int durationSeconds, JDialog dialog) {
        dialog.dispose();
        
        // Seçilen tarihi kullanıcıya göster
        String targetTimeText = formatDateTime(targetDateTime);
        String durationText = formatDuration(durationSeconds);
        logger.info("Zamanlı gönderim seçildi (belirli tarih): " + targetTimeText + " -> " + targetEntry.getName() + " (" + targetEntry.getIp() + ")");
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Fotoğraf " + targetTimeText + " tarihine kadar " + targetEntry.getName() + " (" + targetEntry.getIp() + ") adresinde gösterilecek.\n" +
            "(" + durationText + " süreyle)\n\nDevam etmek istiyor musunuz?",
            "Onay", JOptionPane.YES_NO_OPTION);
            
        if (confirm == JOptionPane.YES_OPTION) {
            logger.info("Zamanlı gönderim onaylandı - bitiş: " + targetTimeText);
            sendPhotoWithTimer(selectedPhoto, List.of(targetEntry), durationSeconds);
        } else {
            logger.info("Zamanlı gönderim iptal edildi");
        }
    }

    private String formatDateTime(java.util.Calendar calendar) {
        return String.format("%02d.%02d.%04d %02d:%02d",
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.MONTH) + 1, // Calendar.MONTH is 0-based
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE));
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + " saniye";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            return minutes + " dakika";
        } else if (seconds < 86400) {
            int hours = seconds / 3600;
            int remainingMinutes = (seconds % 3600) / 60;
            if (remainingMinutes == 0) {
                return hours + " saat";
            } else {
                return hours + " saat " + remainingMinutes + " dakika";
            }
        } else {
            int days = seconds / 86400;
            int remainingHours = (seconds % 86400) / 3600;
            if (remainingHours == 0) {
                return days + " gün";
            } else {
                return days + " gün " + remainingHours + " saat";
            }
        }
    }

    // Eski jar tabanlı güncelleme kodu kaldırıldı. Yeni MSI tabanlı sistem için UpdateManager kullanılmaktadır.

    // Bir IP'nin durumunu sorgula - geliştirilmiş versiyon
    private String queryIpStatus(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, AppConstants.DEFAULT_PORT), AppConstants.CONNECTION_TIMEOUT_MS);
            socket.setSoTimeout(AppConstants.READ_TIMEOUT_MS);
            
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                
                writer.write(AppConstants.COMMAND_GET_STATUS + "\n");
                writer.flush();
                
                String response = reader.readLine();
                if (response != null && response.startsWith("STATUS:")) {
                    String status = response.substring(7).trim();
                    
                    // Status mapping - PhotoViewer'dan gelen yanıtları normalize et
                    if (status.isEmpty() || "Default".equalsIgnoreCase(status) || "DEFAULT".equals(status)) {
                        return "Default";
                    } else if ("Toplantı Var".equalsIgnoreCase(status) || "MEETING".equalsIgnoreCase(status) || "CUSTOM".equalsIgnoreCase(status)) {
                        return "Toplantı Var";
                    } else {
                        // Bilinmeyen durum için default kabul et
                        logger.warn("Bilinmeyen durum yanıtı: " + status + " (IP: " + ip + ")");
                        return status; // Orjinal yanıtı göster
                    }
                } else {
                    logger.warn("Geçersiz durum yanıtı: " + response + " (IP: " + ip + ")");
                    return "Yanıt Yok";
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            return "Zaman Aşımı";
        } catch (java.net.ConnectException e) {
            return "Bağlantı Reddedildi";
        } catch (Exception e) {
            return "Bağlantı Hatası";
        }
    }

    // Tüm IP'lerin durumunu güncelle - geliştirilmiş versiyon
    private void updateAllIPStatuses() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<IpList.IpEntry> entries = ipList.getIpEntries();
                
                if (entries.isEmpty()) {
                    return null;
                }
                
                // Paralel işleme için ExecutorService kullan
                ExecutorService executor = Executors.newFixedThreadPool(Math.min(5, entries.size()));
                List<Future<Void>> futures = new ArrayList<>();
                
                for (IpList.IpEntry entry : entries) {
                    Future<Void> future = executor.submit(() -> {
                        try {
                            String oldStatus = entry.getStatus();
                            String newStatus = queryIpStatus(entry.getIp());
                            
                            if (!newStatus.equals(oldStatus)) {
                                logger.info("IP " + entry.getIp() + " (" + entry.getName() + ") durum değişti: " + oldStatus + " -> " + newStatus);
                            }
                            
                            ipList.updateStatus(entry.getIp(), newStatus);
                        } catch (Exception e) {
                            logger.error("IP " + entry.getIp() + " durum güncelleme hatası", e);
                        }
                        return null;
                    });
                    futures.add(future);
                }
                
                // Tüm görevlerin tamamlanmasını bekle
                for (Future<Void> future : futures) {
                    try {
                        future.get(AppConstants.CONNECTION_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        // Sessizce devam et
                    }
                }
                
                executor.shutdown();
                return null;
            }
            
            @Override
            protected void done() {
                // UI'ı güncelle
                SwingUtilities.invokeLater(() -> {
                    ipJList.repaint();
                    ipJList.revalidate();
                });
            }
        };
        worker.execute();
    }

    // Screenshot isteme metodu
    private BufferedImage requestScreenshot(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, AppConstants.DEFAULT_PORT), AppConstants.CONNECTION_TIMEOUT_MS);
            socket.setSoTimeout(AppConstants.READ_TIMEOUT_MS);
            
            // OutputStream'i al
            OutputStream outputStream = socket.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            
            // Screenshot isteği gönder
            writer.write(AppConstants.COMMAND_GET_SCREENSHOT + "\n");
            writer.flush();
            
            // InputStream'i direkt al (BufferedReader kullanma!)
            InputStream inputStream = socket.getInputStream();
            
            // Header oku - satır satır okuma için
            StringBuilder headerBuilder = new StringBuilder();
            int c;
            while ((c = inputStream.read()) != -1 && c != '\n') {
                if (c != '\r') { // CR karakterini atla
                    headerBuilder.append((char)c);
                }
            }
            
            String response = headerBuilder.toString();
            
            if (response.startsWith("SCREENSHOT:")) {
                int dataSize = Integer.parseInt(response.substring(11).trim());
                
                // Binary veriyi oku
                byte[] imageData = new byte[dataSize];
                int totalRead = 0;
                while (totalRead < dataSize) {
                    int bytesRead = inputStream.read(imageData, totalRead, dataSize - totalRead);
                    if (bytesRead == -1) break;
                    totalRead += bytesRead;
                }
                
                if (totalRead != dataSize) {
                    logger.warn("Screenshot veri boyutu uyumsuz - Beklenen: " + dataSize + ", Okunan: " + totalRead);
                }
                
                // Byte array'den BufferedImage'e çevir
                ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
                BufferedImage result = ImageIO.read(bais);
                
                if (result != null) {
                } else {
                    logger.error("Screenshot decode hatası - Image null");
                }
                
                return result;
            } else {
                logger.error("Geçersiz screenshot yanıtı: " + response);
            }
            return null;
        } catch (Exception e) {
            logger.error("Screenshot alma bağlantı hatası: " + ip + " - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    // Screenshot gösterme dialog'u - Dinamik boyut ve en iyi kalite
    // Screenshot gösterme dialog'u - Otomatik en iyi kalite boyutlandırması
    private void showScreenshotDialog(String title, BufferedImage screenshot) {
        logger.info("Screenshot dialog açılıyor: " + title + " - Orijinal boyut: " + 
                   screenshot.getWidth() + "x" + screenshot.getHeight());
        
        JDialog dialog = new JDialog(this, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Ekran boyutunu al
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        // Otomatik en iyi boyutlandırma - resmin kalitesine göre
        // Eğer resim zaten yüksek çözünürlüklüyse, ekranın %90'ını kullan
        // Düşük çözünürlüklüyse, orijinal boyutunu koru ama maksimum sınır koy
        
        double screenRatio = 0.9; // Ekranın %90'ını kullan
        int maxDisplayWidth = (int)(screenSize.width * screenRatio);
        int maxDisplayHeight = (int)(screenSize.height * screenRatio) - 100; // Title bar ve butonlar için
        
        // Resmin boyutuna göre otomatik scaling
        double scaleX = (double)maxDisplayWidth / screenshot.getWidth();
        double scaleY = (double)maxDisplayHeight / screenshot.getHeight();
        double scale = Math.min(scaleX, scaleY);
        
        // Eğer resim çok küçükse büyütme (1.0'dan küçükse), ama max 2x büyütme
        if (scale > 2.0) scale = 2.0;
        
        // En az %25 ölçeklendirme (çok büyük resimler için)
        if (scale < 0.25) scale = 0.25;
        
        int displayWidth = (int)(screenshot.getWidth() * scale);
        int displayHeight = (int)(screenshot.getHeight() * scale);
        
        logger.info("Otomatik boyutlandırma: " + screenshot.getWidth() + "x" + screenshot.getHeight() + 
                   " -> " + displayWidth + "x" + displayHeight + " (ölçek: " + String.format("%.2f", scale) + ")");
        
        // En yüksek kaliteli ölçeklendirme
        BufferedImage scaledImage = new BufferedImage(displayWidth, displayHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaledImage.createGraphics();
        
        // En iyi kalite ayarları
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        
        g2d.drawImage(screenshot, 0, 0, displayWidth, displayHeight, null);
        g2d.dispose();
        
        // Resmi göster
        JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        // Sadece Kapat butonu
        JButton closeButton = new JButton("Kapat");
        closeButton.addActionListener(_ -> dialog.dispose());
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // Dialog boyutunu içeriğe göre ayarla
        int windowWidth = displayWidth + 50; // Margin için
        int windowHeight = displayHeight + 100; // Buton paneli için
        
        // Minimum boyut kontrolü
        windowWidth = Math.max(400, Math.min(windowWidth, screenSize.width));
        windowHeight = Math.max(300, Math.min(windowHeight, screenSize.height));
        
        dialog.setSize(windowWidth, windowHeight);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        
        logger.info("Screenshot dialog kapatıldı: " + title);
    }

    // Canlı ekran izleme başlat - Otomatik en iyi kalite boyutlandırması
    private void startLiveView(String ip, String name) {
        // Eğer başka bir canlı izleme açıksa kapat
        stopLiveView();
        
        currentLiveViewIp = ip;
        currentLiveViewName = name;
        
        // Canlı izleme dialog'u oluştur
        liveViewDialog = new JDialog(this, "Canlı İzleme: " + name + " (" + ip + ")", true);
        liveViewDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        // Otomatik en iyi boyutlandırma - canlı izleme için ekranın %80'i
        Dimension screenSizeLive = Toolkit.getDefaultToolkit().getScreenSize();
        final int windowWidth = Math.max(800, (int)(screenSizeLive.width * 0.8));
        final int windowHeight = Math.max(600, (int)(screenSizeLive.height * 0.8));
        
        logger.info("Canlı izleme otomatik boyutlandırma: " + windowWidth + "x" + windowHeight);
        
        // Image label oluştur
        liveImageLabel = new JLabel("Canlı görüntü yükleniyor...", JLabel.CENTER);
        liveImageLabel.setHorizontalAlignment(JLabel.CENTER);
        liveImageLabel.setVerticalAlignment(JLabel.CENTER);
        
        // ScrollPane ekle
        JScrollPane liveScrollPane = new JScrollPane(liveImageLabel);
        liveScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        liveScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        liveViewDialog.add(liveScrollPane, BorderLayout.CENTER);
        
        // Sadece Durdur butonu
        JButton stopButton = new JButton("Durdur");
        stopButton.addActionListener(_ -> {
            logger.info("Canlı izleme durduruldu: " + name + " (" + ip + ")");
            stopLiveView();
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(stopButton);
        liveViewDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // Dialog boyutu ve pozisyon
        liveViewDialog.setSize(windowWidth, windowHeight);
        liveViewDialog.setLocationRelativeTo(this);
        liveViewDialog.setResizable(true);
        
        // Kapanma işlemi için listener
        liveViewDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopLiveView();
            }
        });
        
        // Timer başlat (2 saniye aralık)
        liveViewTimer = new javax.swing.Timer(2000, _ -> updateLiveView());
        liveViewTimer.start();
        
        logger.info("Canlı ekran izleme başlatıldı - Güncelleme aralığı: 2 saniye");
        
        // İlk screenshot'ı hemen al
        updateLiveView();
        
        // Dialog'u göster
        liveViewDialog.setVisible(true);
    }
    
    // Canlı izlemeyi durdur
    private void stopLiveView() {
        if (liveViewTimer != null && liveViewTimer.isRunning()) {
            liveViewTimer.stop();
            logger.debug("Canlı izleme timer durduruldu");
        }
        
        if (liveViewDialog != null) {
            liveViewDialog.dispose();
            liveViewDialog = null;
            logger.debug("Canlı izleme dialog kapatıldı");
        }
        
        liveImageLabel = null;
        currentLiveViewIp = null;
        currentLiveViewName = null;
    }
    
    // Canlı görüntüyü güncelle - Otomatik en iyi kalite boyutlandırması
    private void updateLiveView() {
        if (currentLiveViewIp == null || liveImageLabel == null) {
            return;
        }
        
        // Background thread'de screenshot al
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return requestScreenshot(currentLiveViewIp);
            }
            
            @Override
            protected void done() {
                try {
                    BufferedImage screenshot = get();
                    if (screenshot != null && liveImageLabel != null) {
                        // Otomatik en iyi kalite boyutlandırması
                        Dimension currentDialogSize = liveViewDialog.getSize();
                        
                        // Dialog'da kullanılabilir alan hesapla
                        int availableWidth = currentDialogSize.width - 40; // Margin için
                        int availableHeight = currentDialogSize.height - 100; // Buton paneli için
                        
                        // Resmin boyutuna göre otomatik scaling
                        double scaleX = (double)availableWidth / screenshot.getWidth();
                        double scaleY = (double)availableHeight / screenshot.getHeight();
                        double scale = Math.min(scaleX, scaleY);
                        
                        // Minimum %25, maksimum %200 ölçeklendirme
                        if (scale > 2.0) scale = 2.0;
                        if (scale < 0.25) scale = 0.25;
                        
                        int scaledWidth = (int)(screenshot.getWidth() * scale);
                        int scaledHeight = (int)(screenshot.getHeight() * scale);
                        
                        // En yüksek kaliteli scaling - canlı izleme için optimize
                        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = scaledImage.createGraphics();
                        
                        // Yüksek kalite ayarları
                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                        
                        g2d.drawImage(screenshot, 0, 0, scaledWidth, scaledHeight, null);
                        g2d.dispose();
                        
                        ImageIcon icon = new ImageIcon(scaledImage);
                        
                        SwingUtilities.invokeLater(() -> {
                            if (liveImageLabel != null) {
                                liveImageLabel.setIcon(icon);
                                liveImageLabel.setText(""); // Text'i kaldır
                            }
                        });
                        
                        logger.info("Canlı görüntü güncellendi: " + currentLiveViewName + " - " + 
                                   scaledWidth + "x" + scaledHeight + " (ölçek: " + String.format("%.2f", scale) + ")");
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            if (liveImageLabel != null) {
                                liveImageLabel.setIcon(null);
                                liveImageLabel.setText("Bağlantı hatası");
                            }
                        });
                        logger.warn("Canlı izleme - screenshot alınamadı: " + currentLiveViewIp);
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        if (liveImageLabel != null) {
                            liveImageLabel.setIcon(null);
                            liveImageLabel.setText("Hata: " + ex.getMessage());
                        }
                    });
                    logger.error("Canlı izleme güncelleme hatası: " + currentLiveViewIp + " - " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PhotoSenderApp app = new PhotoSenderApp();
            app.setVisible(true);
            
            // Uygulama tamamen yüklendikten 10 saniye sonra bir kez daha durum kontrolü yap
            // Bu, PhotoViewer'ların tam olarak başlaması için zaman tanır
            javax.swing.Timer startupStatusCheck = new javax.swing.Timer(10000, _ -> {
                if (app.ipList.getIpEntries().size() > 0) {
                    logger.info("Başlangıç durum kontrolü başlatılıyor...");
                    app.updateAllIPStatuses();
                }
            });
            startupStatusCheck.setRepeats(false);
            startupStatusCheck.start();
        });
    }
}
