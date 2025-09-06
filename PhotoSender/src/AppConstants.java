/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */

/**
 * PhotoSender uygulaması için sabit değerler
 * PhotoViewer ile uyumlu protocol sabitlerini içerir
 */
public final class AppConstants {
    
    // Versiyon ve Uygulama Bilgileri
    public static final String VERSION = "1.0.8";
    public static final String APP_TITLE = "PhotoSender - Wyndham Grand Istanbul Europe";
    public static final String CONTACT_EMAIL = "yusufziyrek1@gmail.com";
    public static final String COPYRIGHT = "© 2025 Yusuf Ziyrek";
    
    // Network Configuration - PhotoViewer ile uyumlu
    public static final int DEFAULT_PORT = 5000;
    public static final int SOCKET_TIMEOUT_MS = 10000;
    public static final int CONNECTION_TIMEOUT_MS = 8000; // Artırıldı
    public static final int READ_TIMEOUT_MS = 15000; // PhotoViewer ile uyumlu hale getirildi
    public static final int MAX_RETRIES = 2;
    
    // File I/O Configuration
    public static final int BUFFER_SIZE = 8 * 1024;
    public static final long READ_ONCE_THRESHOLD = 20L * 1024 * 1024; // 20 MB
    
    // Threading Configuration
    public static final int MIN_THREAD_POOL_SIZE = 2;
    public static final long RETRY_BACKOFF_BASE_MS = 500L;
    public static final long PROGRESS_DIALOG_TIMEOUT_MINUTES = 5;
    
    // Protocol Commands - PhotoViewer ile uyumlu
    public static final String COMMAND_SHOW_DEFAULT = "SHOW_DEFAULT";
    public static final String COMMAND_SEND_PHOTO = "SEND_PHOTO:";
    public static final String COMMAND_SEND_PHOTO_WITH_TIMER = "SEND_PHOTO_WITH_TIMER:";
    public static final String COMMAND_GET_STATUS = "GET_STATUS";
    public static final String COMMAND_GET_SCREENSHOT = "GET_SCREENSHOT";
    public static final String RESPONSE_OK = "OK";
    public static final String RESPONSE_ERROR = "ERR";
    
    // Status Response Values
    public static final String STATUS_DEFAULT = "DEFAULT";
    public static final String STATUS_MEETING = "MEETING";
    public static final String STATUS_CUSTOM = "CUSTOM";
    
    // GitHub API Configuration
    public static final String GITHUB_REPO = "yusufziyrek/DigitalPhotoTransfer";
    public static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    public static final String USER_AGENT_TEMPLATE = "PhotoSender/%s (Java)";
    
    // Update System Configuration
    public static final int UPDATE_CONNECTION_TIMEOUT_MS = 15000; // 15 saniye
    public static final int UPDATE_READ_TIMEOUT_MS = 30000; // 30 saniye
    public static final int UPDATE_MAX_RETRIES = 3;
    public static final long UPDATE_RETRY_DELAY_MS = 2000; // 2 saniye
    public static final long UPDATE_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 dakika
    public static final int UPDATE_BUFFER_SIZE = 64 * 1024; // 64 KB
    public static final long MIN_MSI_SIZE_BYTES = 1024 * 1024; // 1 MB minimum
    public static final long MAX_MSI_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB maximum
    public static final String MSI_TEMP_PREFIX = "PhotoSender-Update-";
    public static final String MSI_TEMP_SUFFIX = ".msi";
    public static final int UPDATE_EXIT_DELAY_SECONDS = 3; // Kurulum sonrası kapanma gecikmesi
    
    // MSI Installation Configuration
    public static final String[] MSI_INSTALL_COMMAND_STANDARD = {"msiexec.exe", "/i"}; // + msi_path
    public static final String[] MSI_INSTALL_COMMAND_SILENT = {"msiexec.exe", "/i", "/quiet"}; // + msi_path  
    public static final String[] MSI_INSTALL_COMMAND_PASSIVE = {"msiexec.exe", "/i", "/passive"}; // + msi_path
    public static final int MSI_INSTALL_DELAY_MS = 3000; // 3 saniye bekle
    
    // Supported File Extensions
    public static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".gif"};
    
    // Time Configuration
    public static final int HOUR_IN_SECONDS = 3600;
    public static final int DAY_IN_SECONDS = 24 * HOUR_IN_SECONDS;
    public static final int WEEK_IN_SECONDS = 7 * DAY_IN_SECONDS;
    public static final int MONTH_IN_SECONDS = 30 * DAY_IN_SECONDS;
    
    // Logging Configuration
    public static final String LOG_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String LOG_FILE_DATE_PATTERN = "yyyy-MM-dd";
    
    // Private constructor to prevent instantiation
    private AppConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
