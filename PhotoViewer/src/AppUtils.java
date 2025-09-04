/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.io.IOException;

/**
 * Uygulama genelinde kullanılan yardımcı metodlar
 */
public final class AppUtils {
    
    /**
     * Dosya uzantısının desteklenen resim formatlarında olup olmadığını kontrol eder
     * @param fileName Dosya adı
     * @return Destekleniyorsa true
     */
    public static boolean isSupportedImageFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String lowerCase = fileName.toLowerCase();
        for (String ext : AppConstants.SUPPORTED_IMAGE_EXTENSIONS) {
            if (lowerCase.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Güvenli bir şekilde resim dosyası okur
     * @param file Resim dosyası
     * @return BufferedImage veya null
     */
    public static BufferedImage readImageSafely(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        
        if (!isSupportedImageFile(file.getName())) {
            return null;
        }
        
        try {
            return ImageIO.read(file);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
    
    /**
     * Byte sayısını okunabilir formata çevirir
     * @param bytes Byte sayısı
     * @return Formatlanmış string
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Süreyi okunabilir formata çevirir
     * @param durationSeconds Saniye cinsinden süre
     * @return Formatlanmış string
     */
    public static String formatDuration(long durationSeconds) {
        long days = durationSeconds / AppConstants.DAY_IN_SECONDS;
        long hours = (durationSeconds % AppConstants.DAY_IN_SECONDS) / AppConstants.HOUR_IN_SECONDS;
        long minutes = (durationSeconds % AppConstants.HOUR_IN_SECONDS) / 60;
        long seconds = durationSeconds % 60;
        return String.format("%d gün, %d saat, %d dakika, %d saniye", days, hours, minutes, seconds);
    }
    
    /**
     * Null veya boş string kontrolü
     * @param str Kontrol edilecek string
     * @return Null veya boşsa true
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Güvenli Thread.sleep
     * @param millis Uyku süresi (milisaniye)
     */
    public static void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // Private constructor to prevent instantiation
    private AppUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
