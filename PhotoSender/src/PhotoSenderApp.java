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
    private static final String VERSION = "1.0.1"; // Versiyon bilgisi eklendi
    private IpList ipList;
    private DefaultListModel<IpList.IpEntry> ipListModel;
    private JList<IpList.IpEntry> ipJList;
    private JButton addIpButton, selectPhotoButton, sendAllButton, sendSingleButton;
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
        // Hakkında menüsü ekle
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Yardım");
        JMenuItem aboutItem = new JMenuItem("Hakkında");
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        aboutItem.addActionListener(e -> {
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
            defaultItem.addActionListener(e -> {
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
        updateItem.addActionListener(e -> {
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
        deleteItem.addActionListener(e -> {
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
        panel.add(addIpButton);
        panel.add(selectPhotoButton);
        panel.add(sendAllButton);
        panel.add(sendSingleButton);
        add(panel, BorderLayout.SOUTH);

        addIpButton.addActionListener(e -> {
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
                    // IP listesini dosyaya kaydet
                    try {
                        ipList.saveToFile(IP_LIST_FILE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "IP listesi kaydedilemedi: " + ex.getMessage());
                    }
                }
            }
        });

        selectPhotoButton.addActionListener(e -> {
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
                } else {
                    JOptionPane.showMessageDialog(this, "Lütfen bir resim dosyası seçin.");
                    selectedPhoto = null;
                }
            }
        });

        sendAllButton.addActionListener(e -> {
            if (selectedPhoto == null) {
                JOptionPane.showMessageDialog(this, "Önce fotoğraf seçin.");
                return;
            }
            sendPhotoToIps(selectedPhoto, ipList.getIpEntries());
        });

        sendSingleButton.addActionListener(e -> {
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PhotoSenderApp().setVisible(true);
        });
    }
}
