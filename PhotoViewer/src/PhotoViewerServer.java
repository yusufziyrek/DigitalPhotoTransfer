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
import java.io.PushbackInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;

public class PhotoViewerServer {
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

        // Default fotoğraf seçimi
        BufferedImage defaultImage = null;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Photo Viewer - Wyndham Grand Istanbul Europe");
        // Sadece fotoğraf uzantılarını gösteren filtre
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

        // Yardım/Hakkında/Lisans bilgisi için accessory panel eklendi
        JPanel accessory = new JPanel(new BorderLayout());
        accessory.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
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
                        "- Tam ekrandayken sağ tık ile çıkış yapabilir veya ESC ile çıkabilirsiniz.";
                JOptionPane.showMessageDialog(frame, license, "Yardım / Lisans", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        accessory.add(helpBtn, BorderLayout.NORTH);
        fileChooser.setAccessory(accessory);

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String name = selectedFile.getName().toLowerCase();
            if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp"))) {
                photoPanel.setInfo("Geçersiz dosya uzantısı. Sadece jpg, jpeg, png, bmp seçebilirsiniz.");
            } else {
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
            }
        } else {
            photoPanel.setInfo("Wyndham Grand Istanbul Europe");
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
            clientSocket.setSoTimeout(15000);
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

}

// Fotoğrafı kalite bozulmadan gösteren özel panel
class PhotoPanel extends JPanel {
    private BufferedImage image = null;
    private String info = "Wyndham Grand Istanbul Europe";
    private JFrame parentFrame;

    public void setImage(BufferedImage img) {
        this.image = img;
        this.info = null;
        setBackground(Color.BLACK);
        repaint();
    }

    public void setInfo(String info) {
        this.image = null;
        this.info = info;
        setBackground(new Color(230, 230, 230));
        repaint();
    }

    public void setParentFrame(JFrame frame) {
        this.parentFrame = frame;
    }

    // Sağ tık menüsü ve çıkış seçeneği
    private JPopupMenu popupMenu = new JPopupMenu();
    private JMenuItem exitItem = new JMenuItem("Çıkış");

    {
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
                    popupMenu.show(PhotoPanel.this, e.getX(), e.getY());
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            double panelRatio = (double) getWidth() / getHeight();
            double imgRatio = (double) imgWidth / imgHeight;
            int drawWidth, drawHeight;
            if (imgRatio > panelRatio) {
                drawWidth = getWidth();
                drawHeight = (int) (getWidth() / imgRatio);
            } else {
                drawHeight = getHeight();
                drawWidth = (int) (getHeight() * imgRatio);
            }
            int x = (getWidth() - drawWidth) / 2;
            int y = (getHeight() - drawHeight) / 2;
            g2d.drawImage(image, x, y, drawWidth, drawHeight, null);
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
