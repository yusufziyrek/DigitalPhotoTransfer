/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Resource yönetimi ve cleanup için yardımcı sınıf
 * Thread-safe resource management ve proper cleanup sağlar
 */
public final class ResourceUtils {
    
    private ResourceUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Socket'i güvenli şekilde kapatır
     * @param socket Kapatılacak socket
     */
    public static void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Quietly ignore close errors
            }
        }
    }
    
    /**
     * Closeable resource'u güvenli şekilde kapatır
     * @param resource Kapatılacak resource
     */
    public static void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException ignored) {
                // Quietly ignore close errors
            }
        }
    }
    
    /**
     * ExecutorService'i güvenli şekilde kapatır
     * @param executor Kapatılacak executor service
     * @param timeoutSeconds Timeout süresi (saniye)
     */
    public static void shutdownExecutor(ExecutorService executor, int timeoutSeconds) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate gracefully");
                }
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Thread'i güvenli şekilde interrupt eder
     * @param thread Interrupt edilecek thread
     */
    public static void interruptQuietly(Thread thread) {
        if (thread != null && !thread.isInterrupted()) {
            try {
                thread.interrupt();
            } catch (SecurityException ignored) {
                // Quietly ignore security exceptions
            }
        }
    }
    
    /**
     * Thread sleep işlemini güvenli şekilde yapar
     * @param millis Sleep süresi (milisaniye)
     * @return true if sleep completed normally, false if interrupted
     */
    public static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
