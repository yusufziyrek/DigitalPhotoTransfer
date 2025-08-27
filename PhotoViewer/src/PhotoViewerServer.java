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

public class PhotoViewerServer {
    public static void main(String[] args) {
        int port = 5000; // Dinlenecek port
        JFrame frame = new JFrame();
        // Hakkında menüsü ekle
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Yardım");
        JMenuItem aboutItem = new JMenuItem("Hakkında");
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        frame.setJMenuBar(menuBar);

        aboutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame,
                "PhotoViewer\n" +
                "Bu yazılım Yusuf Ziyrek'e aittir.\n" +
                "İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.\n" +
                "Tüm hakları saklıdır. © 2025 Yusuf Ziyrek",
                "Hakkında", JOptionPane.INFORMATION_MESSAGE);
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Özel panel: kalite bozulmadan fotoğraf gösterimi
        PhotoPanel photoPanel = new PhotoPanel();
        frame.setContentPane(photoPanel);

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
        fileChooser.setDialogTitle("Default fotoğrafı seçin");
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
                    InputStream in = clientSocket.getInputStream();
                    // Komut satırını oku (ilk satır)
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String command = reader.readLine();
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
                        // Komut satırı okunduktan sonra kalan veriyi doğrudan InputStream ile oku
                        System.out.println("Fotoğraf verisi alınıyor...");
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        // reader.readLine() komutu InputStream'den bir miktar veri okur, kalan veri resimdir
                        // Bu yüzden InputStream'i tekrar kullanmak için yeni bir referans oluşturmak gerekir
                        // Ancak Java'da InputStream'de okunan veri kaybolur, bu yüzden komut satırı ve kalan veri ayrılmalı
                        // Çözüm: Komut satırını doğrudan InputStream'den byte olarak oku
                        // Alternatif: Komut satırı ve kalan veri arasında bir ayraç (örneğin \n) olmalı
                        // Burada kalan veriyi doğrudan in'den okuyoruz
                        int available = clientSocket.getInputStream().available();
                        int b;
                        while ((b = in.read()) != -1) {
                            baos.write(b);
                        }
                        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                        BufferedImage image = ImageIO.read(bais);
                        if (image != null) {
                            System.out.println("Fotoğraf başarıyla alındı ve gösteriliyor.");
                        } else {
                            System.out.println("Geçersiz veya eksik fotoğraf verisi. Default gösteriliyor.");
                        }
                        if (image != null) {
                            photoPanel.setImage(image);
                        } else if (defaultImage != null) {
                            photoPanel.setImage(defaultImage);
                        } else {
                            photoPanel.setInfo("Geçersiz veya eksik fotoğraf verisi.");
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
}

// Fotoğrafı kalite bozulmadan gösteren özel panel
class PhotoPanel extends JPanel {
    private BufferedImage image = null;
    private String info = "Wyndham Grand Istanbul Europe";

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
