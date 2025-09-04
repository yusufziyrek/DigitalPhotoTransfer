import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import javax.imageio.ImageIO;

/**
 * Fotoğrafı kalite bozulmadan gösteren özel panel
 */
public class PhotoPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private transient BufferedImage image = null;
    private String info = AppConstants.DEFAULT_INFO_MESSAGE;
    private JFrame parentFrame;

    // Clock & date overlay
    private volatile String timeText = "";
    private volatile String dateText = "";
    private final transient DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern(AppConstants.TIME_FORMAT_PATTERN);
    private final transient DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(AppConstants.DATE_FORMAT_PATTERN);
    // Bigger, clearer fonts: large for time, smaller for date (increased per user request)
    private final Font timeFont = new Font("SansSerif", Font.PLAIN, AppConstants.TIME_FONT_SIZE);
    private final Font dateFont = new Font("SansSerif", Font.PLAIN, AppConstants.DATE_FONT_SIZE);
    private Timer clockTimer;
    private boolean showClock = true; // can add setter if needed

    // Zamanlı gösterim için timer
    private Timer autoReturnTimer;

    // Cursor auto-hide
    private Cursor defaultCursor;
    private Cursor blankCursor;
    private Timer hideCursorTimer;
    private volatile boolean cursorHidden = false;

    // Sağ tık menüsü ve çıkış seçeneği
    private JPopupMenu popupMenu = new JPopupMenu();
    private JMenuItem clockToggleItem = new JMenuItem("Saat göster");
    private JMenuItem exitItem = new JMenuItem("Çıkış");

    public PhotoPanel() {
        initializeComponents();
        setupEventHandlers();
        startClockTimer();
        initializeCursors();
    }

    private void initializeComponents() {
        // Manuel seçim menü öğesi
        JMenuItem manualItem = new JMenuItem("Manuel Seç");
        manualItem.addActionListener(_ -> {
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
                            p.setProperty(AppConstants.CONFIG_MODE_KEY, AppConstants.MODE_MANUAL);
                            p.setProperty(AppConstants.CONFIG_SAVED_IMAGE_PATH_KEY, sel.getAbsolutePath());
                            PhotoViewerServer.saveProperties(configFile, p);
                        } catch (Exception ignore) {}
                    } else {
                        JOptionPane.showMessageDialog(parentFrame, AppMessages.ERROR_IMAGE_LOAD, AppMessages.TITLE_ERROR, JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(parentFrame, AppMessages.withException(AppMessages.ERROR_IMAGE_LOAD, ex), AppMessages.TITLE_ERROR, JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        popupMenu.add(manualItem);

        // Yeni: saat göster/gizle toggle as a simple menu item (no checkbox)
        updateClockToggleText();
        clockToggleItem.addActionListener(_ -> {
            showClock = !showClock;
            updateClockToggleText();
            repaint();
        });
        popupMenu.add(clockToggleItem);

        popupMenu.addSeparator();
        popupMenu.add(exitItem);
        exitItem.addActionListener(_ -> {
            if (parentFrame != null) {
                parentFrame.dispose();
                System.exit(0);
            }
        });
    }

    private void setupEventHandlers() {
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
    }

    private void startClockTimer() {
        // Başlangıçta saat & tarih metinlerini güncelle ve timer başlat
        updateTimeText();
        clockTimer = new Timer(AppConstants.CLOCK_UPDATE_INTERVAL_MS, _ -> {
            updateTimeText();
            // Her saniye güncelliyoruz (dakika değişiminde de repaint yeterli olur)
            repaint();
        });
        clockTimer.setCoalesce(true);
        clockTimer.start();
    }

    private void initializeCursors() {
        // Cursor hide timer and cursors (initialized after component created)
        defaultCursor = getCursor();
        blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), 
            new Point(0, 0), "blank");
        hideCursorTimer = new Timer(AppConstants.CURSOR_IDLE_MS, _ -> {
            SwingUtilities.invokeLater(() -> {
                setCursor(blankCursor);
                cursorHidden = true;
            });
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
        // Önceki zamanlayıcıyı durdur
        if (autoReturnTimer != null) {
            autoReturnTimer.stop();
        }
        
        this.image = img;
        if (img != null) {
            // when an image is shown, restart hide timer so cursor will auto-hide
            if (defaultCursor == null) defaultCursor = getCursor();
            setCursor(defaultCursor);
            cursorHidden = false;
            if (hideCursorTimer != null) hideCursorTimer.restart();

            // ensure dark background while showing images (avoids light strips)
            setBackground(AppConstants.IMAGE_BACKGROUND_COLOR);
        } else {
            // if clearing the image, stop hide timer and restore info-bg
            if (hideCursorTimer != null) hideCursorTimer.stop();
            setBackground(AppConstants.INFO_BACKGROUND_COLOR);
        }
        repaint();
    }

    // Zamanlı fotoğraf gösterimi
    public void setImageWithTimer(BufferedImage img, long durationSeconds, BufferedImage defaultImg) {
        // Önceki zamanlayıcıyı durdur
        if (autoReturnTimer != null) {
            autoReturnTimer.stop();
        }
        
        this.image = img;
        if (img != null) {
            // when an image is shown, restart hide timer so cursor will auto-hide
            if (defaultCursor == null) defaultCursor = getCursor();
            setCursor(defaultCursor);
            cursorHidden = false;
            if (hideCursorTimer != null) hideCursorTimer.restart();

            // ensure dark background while showing images (avoids light strips)
            setBackground(AppConstants.IMAGE_BACKGROUND_COLOR);
            
            // Zamanlayıcıyı başlat
            if (durationSeconds > 0) {
                // Java Timer maksimum Integer.MAX_VALUE milisaniye destekler
                // Uzun süreler için özel işlem gerekebilir
                long millis = Math.min(durationSeconds * 1000L, Integer.MAX_VALUE);
                autoReturnTimer = new Timer((int) millis, _ -> {
                    if (defaultImg != null) {
                        setImage(defaultImg);
                        System.out.println("Otomatik olarak varsayılan resme döndü.");
                    } else {
                        setInfo(AppConstants.DEFAULT_INFO_MESSAGE);
                        System.out.println("Otomatik olarak bilgi ekranına döndü.");
                    }
                });
                autoReturnTimer.setRepeats(false);
                autoReturnTimer.start();
                
                System.out.println("Zamanlayıcı başlatıldı: " + durationSeconds + " saniye (" + 
                                   (durationSeconds / 86400) + " gün)");
            }
        } else {
            // if clearing the image, stop hide timer and restore info-bg
            if (hideCursorTimer != null) hideCursorTimer.stop();
            setBackground(AppConstants.INFO_BACKGROUND_COLOR);
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
                int margin = AppConstants.CLOCK_MARGIN; // köşeden uzaklık

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
                g2d.setColor(AppConstants.CLOCK_COLOR);
                g2d.setFont(timeFont);
                g2d.drawString(timeText, txTime, tyTime);
                g2d.setFont(dateFont);
                g2d.drawString(dateText, txDate, tyDate);
            }

        } else {
            setBackground(AppConstants.INFO_BACKGROUND_COLOR);
            g.setColor(AppConstants.INFO_TEXT_COLOR);
            g.setFont(new Font("Arial", Font.BOLD, AppConstants.INFO_FONT_SIZE));
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(info);
            int x = (getWidth() - textWidth) / 2;
            int y = getHeight() / 2;
            g.drawString(info, x, y);
        }
    }

    /**
     * Component kapatıldığında timer'ları temizler (Memory leak prevention)
     */
    public void dispose() {
        // Tüm timer'ları güvenli şekilde durdur
        if (clockTimer != null && clockTimer.isRunning()) {
            clockTimer.stop();
            clockTimer = null;
        }
        if (autoReturnTimer != null && autoReturnTimer.isRunning()) {
            autoReturnTimer.stop();
            autoReturnTimer = null;
        }
        if (hideCursorTimer != null && hideCursorTimer.isRunning()) {
            hideCursorTimer.stop();
            hideCursorTimer = null;
        }
    }
}
