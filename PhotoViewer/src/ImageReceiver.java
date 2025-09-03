import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Fotoğraf alma yardımcı sınıfı
 */
public class ImageReceiver {
    public static BufferedImage receiveImage(PushbackInputStream in, int length) {
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
        } catch (Exception ex) {
            System.out.println("Fotoğraf alma hatası: " + ex.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception ignored) {}
            }
        }
        
        return image;
    }
}
