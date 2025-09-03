/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */
import javax.swing.*;
import java.awt.*;
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

public class PhotoSenderApp extends JFrame {
    private static final String VERSION = "1.0.4";
    private static final AppLogger logger = new AppLogger("PhotoSender");
    
    private IpList ipList;
    private DefaultListModel<IpList.IpEntry> ipListModel;
    private JList<IpList.IpEntry> ipJList;
    private JButton addIpButton, selectPhotoButton, sendAllButton, sendSingleButton, sendWithTimerButton;
    private File selectedPhoto;
    private static final File IP_LIST_FILE = getAppDataIpListFile();

    private static File getAppDataIpListFile() {
        String appdata = System.getenv("APPDATA"); // %appdata%
        if (appdata == null || appdata.isEmpty()) {
            appdata = System.getProperty("user.home"); // fallback
        }
        File dir = new File(appdata, "PhotoSender");
        if (!dir.exists()) {
            try {
                dir.mkdirs();
            } catch (SecurityException ignored) {
                // Eğer klasör oluşturulamazsa fallback olarak çalışma dizinine dön
                return new File("iplist.txt");
            }
        }
        return new File(dir, "iplist.txt");
    }

    public PhotoSenderApp() {
        //checkForUpdateAndInstall(); // Uygulama başlatıldığında otomatik güncelleme kontrolü
        // Hakkında menüsü ekle
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Yardım");
        JMenuItem aboutItem = new JMenuItem("Hakkında");
        JMenuItem usageGuideItem = new JMenuItem("Kullanım Kılavuzu");
        helpMenu.add(usageGuideItem);
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
                "   • Desteklenen formatlar: JPG, JPEG, PNG, BMP\n\n" +
                "3. FOTOĞRAF GÖNDERİMİ:\n" +
                "   • 'Hepsine Gönder': Listedeki tüm IP'lere fotoğraf gönderir\n" +
                "   • 'Seçilene Gönder': Sadece seçili IP'ye fotoğraf gönderir\n" +
                "   • 'Zamanlı Gönder': Belirlenen süre boyunca fotoğraf gösterir\n\n" +
                "4. ZAMANLI GÖNDERİM:\n" +
                "   • Hızlı seçenekler: 1 Saat, 6 Saat, 1 Gün, 3 Gün, 1 Hafta, 1 Ay\n" +
                "   • Özel süre: Gün, saat ve dakika kombinasyonu ile\n" +
                "   • Fotoğraf belirlenen süre sonunda otomatik olarak kaybolur\n\n" +
                "5. İPUÇLARI:\n" +
                "   • Büyük fotoğraflar otomatik olarak optimize edilir\n" +
                "   • Gönderim sırasında ilerleme çubuğu gösterilir\n" +
                "   • Hata durumlarında detaylı bilgi verilir\n" +
                "   • Loglar APPDATA/PhotoSender klasöründe saklanır";
                
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
        ipList = new IpList();
        setTitle("Photo Sender - Wyndham Grand Istanbul Europe");
        setSize(600, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        logger.info("PhotoSender UI başlatıldı");

        ipListModel = new DefaultListModel<>();
        ipJList = new JList<>(ipListModel);
        ipJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(ipJList), BorderLayout.CENTER);

        // Sağ tık menüsü ekle
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem updateItem = new JMenuItem("Güncelle");
        JMenuItem deleteItem = new JMenuItem("Sil");
            JMenuItem defaultItem = new JMenuItem("Default");
        popupMenu.add(updateItem);
        popupMenu.add(deleteItem);
            popupMenu.add(defaultItem);

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
                int port = 5000; // Port bilgisini buradan güncelleyebilirsiniz
                try (Socket socket = new Socket(ip, port);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));) {
                    writer.write("SHOW_DEFAULT\n");
                    writer.flush();
                    JOptionPane.showMessageDialog(this, "İşlem Başarılı " + ip);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "SHOW_DEFAULT gönderilemedi: " + ex.getMessage());
                }
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
                        JOptionPane.showMessageDialog(this, "IP listesi kaydedilemedi: " + ex.getMessage());
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
                    JOptionPane.showMessageDialog(this, "IP listesi kaydedilemedi: " + ex.getMessage());
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
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "IP listesi okunamadı: " + ex.getMessage());
            }
        }

        JPanel panel = new JPanel();
        addIpButton = new JButton("IP Ekle");
        selectPhotoButton = new JButton("Fotoğraf Seç");
        sendAllButton = new JButton("Toplu Gönder");
        sendSingleButton = new JButton("Seçiliye Gönder");
        
        // Zamanlı gönderim butonu
        sendWithTimerButton = new JButton("Zamanlı Gönder");
        
        panel.add(addIpButton);
        panel.add(selectPhotoButton);
        panel.add(sendAllButton);
        panel.add(sendSingleButton);
        panel.add(sendWithTimerButton);
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
                    ipListModel.addElement(new IpList.IpEntry(ip, name));
                    logger.info("Yeni IP eklendi: " + name + " (" + ip + ")");
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
                    JOptionPane.showMessageDialog(this, "Lütfen bir resim dosyası seçin.");
                    selectedPhoto = null;
                    logger.warn("Geçersiz dosya türü seçildi: " + file.getName());
                }
            }
        });

    sendAllButton.addActionListener(_ -> {
            if (selectedPhoto == null) {
                JOptionPane.showMessageDialog(this, "Önce fotoğraf seçin.");
                return;
            }
            logger.info("Toplu gönderim başlatıldı - " + ipList.getIpEntries().size() + " hedef");
            sendPhotoToIps(selectedPhoto, ipList.getIpEntries());
        });

    sendSingleButton.addActionListener(_ -> {
            if (selectedPhoto == null) {
                JOptionPane.showMessageDialog(this, "Önce fotoğraf seçin.");
                return;
            }
            IpList.IpEntry selected = ipJList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Listeden bir IP seçin.");
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
    }

    private void sendPhotoToIps(File photo, List<IpList.IpEntry> entries) {
        // Quick checks
        if (entries == null || entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Gönderecek IP yok.");
            return;
        }

        // Disable UI while sending
        addIpButton.setEnabled(false);
        selectPhotoButton.setEnabled(false);
        sendAllButton.setEnabled(false);
        sendSingleButton.setEnabled(false);
        sendWithTimerButton.setEnabled(false);

        final int CONNECT_TIMEOUT_MS = 5000;
        final int READ_TIMEOUT_MS = 10000;
        final int MAX_RETRIES = 2;
        final int BUFFER_SIZE = 8 * 1024;
        final long READ_ONCE_THRESHOLD = 20L * 1024 * 1024; // 20 MB

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
                                socket.connect(new InetSocketAddress(ip, 5000), CONNECT_TIMEOUT_MS);
                                socket.setSoTimeout(READ_TIMEOUT_MS);
                                OutputStream os = socket.getOutputStream();
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                                long lengthToSend = (photoBytesRef.get() != null) ? photoBytesRef.get().length : photo.length();
                                // Send header with length so receiver can read exact bytes
                                writer.write("SEND_PHOTO:" + lengthToSend + "\n");
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
                                    socket.setSoTimeout(15000); // 15s ack timeout
                                    // Bu değer READ_TIMEOUT_MS ile uyumlu olmalı
                                    socket.setSoTimeout(READ_TIMEOUT_MS); // Düzeltme
                                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "US-ASCII"));
                                    ack = br.readLine();
                                } catch (SocketTimeoutException ste) {
                                    System.out.println("ACK zaman aşımı: " + ip + " -> " + ste.getMessage());
                                } catch (IOException ioe) {
                                    System.out.println("ACK okuma hatası: " + ip + " -> " + ioe.getMessage());
                                }
                                if ("OK".equals(ack)) {
                                    success = true;
                                    System.out.println("Gönderildi ve onaylandı: " + name + " (" + ip + ")");
                                } else {
                                    System.out.println("Gönderildi fakat onay alınamadı: " + name + " (" + ip + ") ack=" + ack);
                                }
                            } catch (SocketTimeoutException ste) {
                                System.out.println("Zaman aşımı: " + ip + " -> " + ste.getMessage());
                            } catch (IOException ioe) {
                                System.out.println("IO hatası: " + ip + " -> " + ioe.getMessage());
                            } catch (Exception ex) {
                                System.out.println("Genel hata: " + ip + " -> " + ex.getMessage());
                            } finally {
                                if (socket != null) {
                                    try { socket.close(); } catch (IOException ignored) {}
                                }
                            }

                            if (!success) {
                                // exponential backoff
                                try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
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
                    JOptionPane.showMessageDialog(PhotoSenderApp.this, "Fotoğraf gönderildi.");
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

    private void sendPhotoWithTimer(File photo, List<IpList.IpEntry> entries, int durationSeconds) {
        // Quick checks
        if (entries == null || entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Gönderecek IP yok.");
            return;
        }

        // Disable UI while sending
        addIpButton.setEnabled(false);
        selectPhotoButton.setEnabled(false);
        sendAllButton.setEnabled(false);
        sendSingleButton.setEnabled(false);
        sendWithTimerButton.setEnabled(false);

        final int CONNECT_TIMEOUT_MS = 5000;
        final int READ_TIMEOUT_MS = 10000;
        final int MAX_RETRIES = 2;
        final int BUFFER_SIZE = 8 * 1024;
        final long READ_ONCE_THRESHOLD = 20L * 1024 * 1024; // 20 MB

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
                                socket.connect(new InetSocketAddress(ip, 5000), CONNECT_TIMEOUT_MS);
                                socket.setSoTimeout(READ_TIMEOUT_MS);
                                OutputStream os = socket.getOutputStream();
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                                
                                long lengthToSend = (photoBytesRef.get() != null) ? photoBytesRef.get().length : photo.length();
                                
                                // Yeni protokol: Süre bilgisi ile gönder
                                writer.write("SEND_PHOTO_WITH_TIMER:" + lengthToSend + ":" + durationSeconds + "\n");
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
                                if ("OK".equals(ack)) {
                                    success = true;
                                    System.out.println("Zamanlı gönderildi ve onaylandı: " + name + " (" + ip + ") - " + (durationSeconds / 3600) + " saat");
                                } else {
                                    System.out.println("Gönderildi fakat onay alınamadı: " + name + " (" + ip + ") ack=" + ack);
                                }
                            } catch (SocketTimeoutException ste) {
                                System.out.println("Zaman aşımı: " + ip + " -> " + ste.getMessage());
                            } catch (IOException ioe) {
                                System.out.println("IO hatası: " + ip + " -> " + ioe.getMessage());
                            } catch (Exception ex) {
                                System.out.println("Genel hata: " + ip + " -> " + ex.getMessage());
                            } finally {
                                if (socket != null) {
                                    try { socket.close(); } catch (IOException ignored) {}
                                }
                            }

                            if (!success) {
                                // exponential backoff
                                try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
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
                    String durationText = formatDuration(durationSeconds);
                    JOptionPane.showMessageDialog(PhotoSenderApp.this, 
                        "Zamanlı fotoğraf gönderildi. " + durationText + " sonra otomatik olarak default fotoğrafa dönecek.");
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
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // Başlık
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel("<html><center><b>Fotoğraf ne kadar süre gösterilsin?</b><br>" + 
                                     "<small>Hedef: " + targetEntry.getName() + " (" + targetEntry.getIp() + ")</small></center></html>");
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        mainPanel.add(titleLabel, gbc);

        // Hızlı seçenekler
        gbc.gridwidth = 1; gbc.gridy++;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel quickPanel = new JPanel(new GridLayout(2, 3, 8, 8));
        
        JButton btn1Hour = new JButton("<html><center>1 Saat</center></html>");
        JButton btn6Hours = new JButton("<html><center>6 Saat</center></html>");
        JButton btn1Day = new JButton("<html><center>1 Gün</center></html>");
        JButton btn3Days = new JButton("<html><center>3 Gün</center></html>");
        JButton btn1Week = new JButton("<html><center>1 Hafta</center></html>");
        JButton btn1Month = new JButton("<html><center>1 Ay</center></html>");
        
        // Butonları daha güzel hale getir
        Font buttonFont = new Font("Arial", Font.BOLD, 12);
        Dimension buttonSize = new Dimension(100, 40);
        
        for (JButton btn : new JButton[]{btn1Hour, btn6Hours, btn1Day, btn3Days, btn1Week, btn1Month}) {
            btn.setFont(buttonFont);
            btn.setPreferredSize(buttonSize);
            btn.setBackground(new Color(230, 240, 250));
            btn.setBorder(BorderFactory.createRaisedBevelBorder());
        }
        
        quickPanel.add(btn1Hour);
        quickPanel.add(btn6Hours);
        quickPanel.add(btn1Day);
        quickPanel.add(btn3Days);
        quickPanel.add(btn1Week);
        quickPanel.add(btn1Month);

        gbc.gridx = 0; gbc.gridwidth = 2;
        mainPanel.add(quickPanel, gbc);

        // Özel süre girişi
        gbc.gridy++; gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Özel süre:"), gbc);
        
        JPanel customPanel = new JPanel(new FlowLayout());
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 365, 1));
        JSpinner hourSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 23, 1)); // Varsayılan 1 saat
        JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        
        // Spinner boyutlarını ayarla
        Dimension spinnerSize = new Dimension(60, 25);
        daySpinner.setPreferredSize(spinnerSize);
        hourSpinner.setPreferredSize(spinnerSize);
        minuteSpinner.setPreferredSize(spinnerSize);
        
        customPanel.add(daySpinner);
        customPanel.add(new JLabel("gün"));
        customPanel.add(hourSpinner);
        customPanel.add(new JLabel("saat"));
        customPanel.add(minuteSpinner);
        customPanel.add(new JLabel("dakika"));
        
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(customPanel, gbc);

        // Butonlar
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton sendButton = new JButton("Özel Süre ile Gönder");
        JButton cancelButton = new JButton("İptal");
        
        sendButton.setFont(new Font("Arial", Font.BOLD, 12));
        cancelButton.setFont(new Font("Arial", Font.PLAIN, 12));
        sendButton.setBackground(new Color(100, 200, 100));
        cancelButton.setBackground(new Color(240, 240, 240));
        
        buttonPanel.add(sendButton);
        buttonPanel.add(cancelButton);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // Hızlı butonların event'leri
    btn1Hour.addActionListener(_ -> sendWithDuration(targetEntry, 1 * 3600, dialog));
    btn6Hours.addActionListener(_ -> sendWithDuration(targetEntry, 6 * 3600, dialog));
    btn1Day.addActionListener(_ -> sendWithDuration(targetEntry, 24 * 3600, dialog));
    btn3Days.addActionListener(_ -> sendWithDuration(targetEntry, 3 * 24 * 3600, dialog));
    btn1Week.addActionListener(_ -> sendWithDuration(targetEntry, 7 * 24 * 3600, dialog));
    btn1Month.addActionListener(_ -> sendWithDuration(targetEntry, 30 * 24 * 3600, dialog));

        // Özel süre gönder butonu
    sendButton.addActionListener(_ -> {
            int days = (Integer) daySpinner.getValue();
            int hours = (Integer) hourSpinner.getValue();
            int minutes = (Integer) minuteSpinner.getValue();
            
            int totalSeconds = days * 24 * 3600 + hours * 3600 + minutes * 60;
            
            if (totalSeconds <= 0) {
                JOptionPane.showMessageDialog(dialog, "Lütfen geçerli bir süre girin.\n(En az 1 dakika olmalı)", 
                                            "Geçersiz Süre", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            sendWithDuration(targetEntry, totalSeconds, dialog);
        });

    cancelButton.addActionListener(_ -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void sendWithDuration(IpList.IpEntry targetEntry, int durationSeconds, JDialog dialog) {
        dialog.dispose();
        
        // Süreyi kullanıcıya göster
        String durationText = formatDuration(durationSeconds);
        logger.info("Zamanlı gönderim seçildi: " + durationText + " -> " + targetEntry.getName() + " (" + targetEntry.getIp() + ")");
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Fotoğraf " + durationText + " süreyle " + targetEntry.getName() + " (" + targetEntry.getIp() + ") adresine gönderilecek.\n\nDevam etmek istiyor musunuz?",
            "Onay", JOptionPane.YES_NO_OPTION);
            
        if (confirm == JOptionPane.YES_OPTION) {
            logger.info("Zamanlı gönderim onaylandı");
            sendPhotoWithTimer(selectedPhoto, List.of(targetEntry), durationSeconds);
        } else {
            logger.info("Zamanlı gönderim iptal edildi");
        }
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

    // --- GÜNCELLEME KONTROLÜ EKLE ---
    private static final String JAR_ASSET_NAME = "PhotoSenderApp.jar"; // Release'deki jar dosyasının adı
    private static final String ASSET_API_PREFIX = "https://api.github.com/repos/yusufziyrek/DigitalPhotoTransfer/releases/assets/";
    private static final String GITHUB_TOKEN = "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"; // Buraya kendi tokenınızı girin
    private static final String GITHUB_API_URL = "https://api.github.com/repos/yusufziyrek/DigitalPhotoTransfer/releases/latest"; // Latest release API endpoint

    private void checkForUpdateAndInstall() {
    final String[] latestVersion = {null};
    final String[] downloadUrl = {null};
    final String[] assetApiUrl = {null};
    SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    java.net.URL url = new java.net.URL(GITHUB_API_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN); // Token ekle
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    try (java.io.InputStream is = conn.getInputStream()) {
                        String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        // tag_name
                        java.util.regex.Matcher mTag = java.util.regex.Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                        if (mTag.find()) latestVersion[0] = mTag.group(1);

                        // assets array - find the assets block then each object
                        java.util.regex.Matcher mAssets = java.util.regex.Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(json);
                        if (mAssets.find()) {
                            String assetsBlock = mAssets.group(1);
                            java.util.regex.Matcher mObj = java.util.regex.Pattern.compile("\\{(.*?)\\}(,|$)", java.util.regex.Pattern.DOTALL).matcher(assetsBlock);
                            while (mObj.find()) {
                                String obj = mObj.group(1);
                                java.util.regex.Matcher mName = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
                                if (mName.find()) {
                                    String name = mName.group(1);
                                    if (JAR_ASSET_NAME.equals(name) || name.endsWith(JAR_ASSET_NAME)) {
                                        java.util.regex.Matcher mId = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(obj);
                                        if (mId.find()) {
                                            assetApiUrl[0] = ASSET_API_PREFIX + mId.group(1);
                                        }
                                        java.util.regex.Matcher mBrowser = java.util.regex.Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
                                        if (mBrowser.find()) {
                                            downloadUrl[0] = mBrowser.group(1);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("Güncelleme kontrol hatası: " + ex.getMessage());
                }
                System.out.println("Güncelleme kontrolü başlatıldı");
                return null;
            }
            @Override
            protected void done() {
                // Private repo ise assetApiUrl kullanılır, public repo ise downloadUrl
                String urlToDownload = assetApiUrl[0] != null ? assetApiUrl[0] : downloadUrl[0];
                if (latestVersion[0] != null && !VERSION.equals(latestVersion[0]) && urlToDownload != null) {
                    int result = JOptionPane.showConfirmDialog(PhotoSenderApp.this,
                        "Yeni sürüm mevcut: " + latestVersion[0] + "\nOtomatik güncelleme başlatılsın mı?",
                        "Güncelleme", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        downloadAndInstallUpdate(urlToDownload, assetApiUrl[0] != null);
                    }
                }
                System.out.println("Güncelleme kontrolü tamamlandı: latestVersion=" + latestVersion[0] + ", downloadUrl=" + downloadUrl[0]);
            }
        };
        worker.execute();
    }

    private void downloadAndInstallUpdate(String url, boolean isPrivateAssetApi) {
    File appDataDir = new File(System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home"), "PhotoSender");
    if (!appDataDir.exists()) try { appDataDir.mkdirs(); } catch (Exception ignored) {}
    File newJar = new File(appDataDir, "PhotoSenderApp_new.jar");
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL downloadURL = new java.net.URL(url);
            conn = (java.net.HttpURLConnection) downloadURL.openConnection();
            conn.setInstanceFollowRedirects(false); // handle redirects manually so we can drop auth on redirect

            if (isPrivateAssetApi) {
                conn.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);
                conn.setRequestProperty("Accept", "application/octet-stream");
            }

            int code = conn.getResponseCode();
            java.io.InputStream inStream = null;

            if (code == java.net.HttpURLConnection.HTTP_MOVED_PERM || code == java.net.HttpURLConnection.HTTP_MOVED_TEMP || code == 302) {
                // GitHub may redirect to an S3 URL; follow the Location header without Authorization
                String location = conn.getHeaderField("Location");
                if (location == null) throw new IOException("Redirect without Location header (code=" + code + ")");
                java.net.URL redirected = new java.net.URL(location);
                java.net.HttpURLConnection redirectedConn = (java.net.HttpURLConnection) redirected.openConnection();
                // drop Authorization header on redirected request
                redirectedConn.setInstanceFollowRedirects(true);
                int rc2 = redirectedConn.getResponseCode();
                if (rc2 >= 400) {
                    String err = readStream(redirectedConn.getErrorStream());
                    throw new IOException("HTTP " + rc2 + " " + redirectedConn.getResponseMessage() + " -> " + (err == null ? "" : err));
                }
                inStream = redirectedConn.getInputStream();
            } else if (code >= 400) {
                String err = readStream(conn.getErrorStream());
                throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + " -> " + (err == null ? "" : err));
            } else {
                inStream = conn.getInputStream();
            }

            try (java.io.InputStream in = inStream; java.io.FileOutputStream out = new java.io.FileOutputStream(newJar)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }

                    // Indirme tamamlandi - Program Files korumali olabilir. Kullaniciya konumu bildir.
            int choice = JOptionPane.showOptionDialog(this,
                "Güncelleme indirildi: " + newJar.getAbsolutePath() + "\nProgram Files altındaki kurulum nedeniyle otomatik olarak üzerine yazılamayabilir. Klasörü açmak veya yükseltilmiş olarak kopyalamayı denemek ister misiniz?",
                "Güncelleme indirildi",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                new String[]{"Klasörü Aç","Yükseltilmiş Kopyala","İptal"},
                "Klasörü Aç");

            if (choice == 0) {
                try { java.awt.Desktop.getDesktop().open(appDataDir); } catch (Exception ex) { /* ignore */ }
            } else if (choice == 1) {
                // Attempt elevated copy to the running jar location
                try {
                    String destPath = null;
                    try {
                        java.net.URI loc = PhotoSenderApp.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                        File current = new File(loc.getPath());
                        destPath = current.getAbsolutePath();
                        // if running from classes directory, dest may be a folder; try to locate jar name
                        if (current.isDirectory()) {
                            destPath = new File(System.getProperty("user.dir"), "PhotoSenderApp.jar").getAbsolutePath();
                        }
                    } catch (Exception ex) {
                        destPath = "C:\\Program Files\\PhotoSender\\PhotoSenderApp.jar";
                    }

                    String copyCmd = "Copy-Item -Force -Path '" + newJar.getAbsolutePath().replace("'","''") + "' -Destination '" + destPath.replace("'","''") + "'";
                    String psArg = "-NoProfile -ExecutionPolicy Bypass -Command \"Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"" + copyCmd + "\"'\"";
                    ProcessBuilder pb = new ProcessBuilder("powershell", psArg);
                    pb.inheritIO();
                    pb.start();
                    JOptionPane.showMessageDialog(this, "Yükseltilmiş kopyalama talebi başlatıldı. UAC onayı gerekebilir.");
                    System.exit(0);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Yükseltilmiş kopyalama yapılamadı: " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            // Provide richer error information to help debugging (HTTP status, body)
            String msg = ex.getMessage();
            String detail = msg != null ? msg : ex.toString();
            JOptionPane.showMessageDialog(this, "Güncelleme indirilemedi: " + url + "\nHata: " + detail);

            // If we used the asset API URL and it failed, attempt a best-effort fallback to releases/download URL
            if (isPrivateAssetApi && url != null && url.startsWith("https://api.github.com/")) {
                String fallbackUrl = url.replace("https://api.github.com/repos/yusufziyrek/DigitalPhotoTransfer/releases/assets/", "https://github.com/yusufziyrek/DigitalPhotoTransfer/releases/download/");
                try {
                    File newJarFallback = new File("PhotoSenderApp_new.jar");
                    java.net.URL fallbackDownloadURL = new java.net.URL(fallbackUrl);
                    java.net.HttpURLConnection fallbackConn = (java.net.HttpURLConnection) fallbackDownloadURL.openConnection();
                    int fc = fallbackConn.getResponseCode();
                    if (fc >= 400) {
                        String err = readStream(fallbackConn.getErrorStream());
                        throw new IOException("HTTP " + fc + " " + fallbackConn.getResponseMessage() + " -> " + (err == null ? "" : err));
                    }
                    try (java.io.InputStream in = fallbackConn.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(newJarFallback)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                    }
                    JOptionPane.showMessageDialog(this, "Güncelleme indirildi (fallback). Uygulama yeniden başlatılıyor...");
                    ProcessBuilder pb = new ProcessBuilder("java", "-jar", newJarFallback.getAbsolutePath());
                    pb.start();
                    System.exit(0);
                } catch (Exception ex2) {
                    String detail2 = ex2.getMessage() != null ? ex2.getMessage() : ex2.toString();
                    JOptionPane.showMessageDialog(this, "Güncelleme fallback ile de indirilemedi: " + fallbackUrl + "\nHata: " + detail2);
                }
            }
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private static String readStream(java.io.InputStream is) {
        if (is == null) return null;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PhotoSenderApp().setVisible(true);
        });
    }
}
