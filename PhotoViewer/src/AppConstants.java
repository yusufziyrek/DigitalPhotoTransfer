/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */

/**
 * Uygulama genelinde kullanılan sabit değerler
 */
public final class AppConstants {
    
    // Versiyon ve Uygulama Bilgileri
    public static final String VERSION = "1.0.8";
    public static final String APP_TITLE = "PhotoViewer - Wyndham Grand Istanbul Europe";
    public static final String CONTACT_EMAIL = "yusufziyrek1@gmail.com";
    public static final String COPYRIGHT = "© 2025 Yusuf Ziyrek";
    
    // Network Configuration
    public static final int DEFAULT_PORT = 5000;
    public static final int SOCKET_TIMEOUT_MS = 10000;
    public static final int CONNECTION_TIMEOUT_MS = 10000;
    public static final int READ_TIMEOUT_MS = 15000; // PhotoSender ile uyumlu hale getirildi
    public static final int PUSHBACK_BUFFER_SIZE = 8192;
    
    // Time Constants for compatibility with PhotoSender
    public static final int HOUR_IN_SECONDS = 3600;
    public static final int DAY_IN_SECONDS = 24 * HOUR_IN_SECONDS;
    public static final int WEEK_IN_SECONDS = 7 * DAY_IN_SECONDS;
    public static final int MONTH_IN_SECONDS = 30 * DAY_IN_SECONDS;
    
    // File I/O Configuration
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int OPTIMAL_BUFFER_SIZE_MIN = 8192;
    public static final int OPTIMAL_BUFFER_SIZE_MAX = 65536;
    public static final int STREAM_TO_FILE_THRESHOLD = 50 * 1024 * 1024; // 50 MB
    
    // UI Configuration
    public static final int CURSOR_IDLE_MS = 3000;
    public static final int UI_READY_DELAY_MS = 500;
    public static final int CLOCK_UPDATE_INTERVAL_MS = 1000;
    public static final int TIME_FONT_SIZE = 45;
    public static final int DATE_FONT_SIZE = 20;
    public static final int INFO_FONT_SIZE = 48;
    public static final int CLOCK_MARGIN = 18;
    
    // Colors
    public static final java.awt.Color INFO_BACKGROUND_COLOR = new java.awt.Color(230, 230, 230);
    public static final java.awt.Color INFO_TEXT_COLOR = java.awt.Color.DARK_GRAY;
    public static final java.awt.Color CLOCK_COLOR = new java.awt.Color(30, 144, 255); // DodgerBlue
    public static final java.awt.Color IMAGE_BACKGROUND_COLOR = java.awt.Color.BLACK;
    
    // File Extensions
    public static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".gif"};
    
    // Configuration Keys
    public static final String CONFIG_MODE_KEY = "mode";
    public static final String CONFIG_SAVED_IMAGE_PATH_KEY = "savedImagePath";
    public static final String CONFIG_AUTO_FOLDER_KEY = "autoFolder";
    public static final String CONFIG_COMMENT = "PhotoViewer settings";
    
    // Configuration Values
    public static final String MODE_PROMPT = "PROMPT";
    public static final String MODE_MANUAL = "MANUAL";
    public static final String MODE_AUTO = "AUTO";
    
    // Protocol Commands
    public static final String COMMAND_SHOW_DEFAULT = "SHOW_DEFAULT";
    public static final String COMMAND_SEND_PHOTO = "SEND_PHOTO:";
    public static final String COMMAND_SEND_PHOTO_WITH_TIMER = "SEND_PHOTO_WITH_TIMER:";
    public static final String COMMAND_GET_STATUS = "GET_STATUS";
    public static final String COMMAND_GET_SCREENSHOT = "GET_SCREENSHOT";
    public static final String COMMAND_GET_SCREENSHOT_HD = "GET_SCREENSHOT_HD"; // Ultra kalite için
    public static final String RESPONSE_OK = "OK\n";
    public static final String RESPONSE_ERROR = "ERR\n";
    
    // Status Response Values
    public static final String STATUS_DEFAULT = "DEFAULT";
    public static final String STATUS_MEETING = "MEETING";
    public static final String STATUS_CUSTOM = "CUSTOM";
    
    // GitHub API Configuration
    public static final String GITHUB_API_URL = "https://api.github.com/repos/yusufziyrek/DigitalPhotoTransfer/releases/latest";
    public static final String DOWNLOAD_URL_TEMPLATE = "https://github.com/yusufziyrek/DigitalPhotoTransfer/releases/download/{tag}/PhotoViewer-{version}.msi";
    public static final String USER_AGENT_TEMPLATE = "PhotoViewer/%s";
    
    // Update System Configuration
    public static final int UPDATE_CONNECTION_TIMEOUT_MS = 15000; // 15 saniye
    public static final int UPDATE_READ_TIMEOUT_MS = 30000; // 30 saniye
    public static final int UPDATE_MAX_RETRIES = 3;
    public static final long UPDATE_RETRY_DELAY_MS = 2000; // 2 saniye
    public static final long UPDATE_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 dakika
    public static final int UPDATE_BUFFER_SIZE = 64 * 1024; // 64 KB
    public static final long MIN_MSI_SIZE_BYTES = 1024 * 1024; // 1 MB minimum
    public static final long MAX_MSI_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB maximum
    public static final String MSI_TEMP_PREFIX = "PhotoViewer-Update-";
    public static final String MSI_TEMP_SUFFIX = ".msi";
    public static final int UPDATE_EXIT_DELAY_SECONDS = 3; // Kurulum sonrası kapanma gecikmesi
    
    // MSI Installation Configuration
    public static final String[] MSI_INSTALL_COMMAND_STANDARD = {"msiexec.exe", "/i"}; // + msi_path
    public static final String[] MSI_INSTALL_COMMAND_SILENT = {"msiexec.exe", "/i", "/quiet"}; // + msi_path  
    public static final String[] MSI_INSTALL_COMMAND_PASSIVE = {"msiexec.exe", "/i", "/passive"}; // + msi_path
    public static final int MSI_INSTALL_DELAY_MS = 3000; // 3 saniye bekle
    
    // Default Messages
    public static final String DEFAULT_INFO_MESSAGE = "Wyndham Grand Istanbul Europe";
    public static final String PHOTO_LOAD_ERROR_MESSAGE = "Fotoğraf yüklenemedi.";
    public static final String INVALID_PHOTO_DATA_MESSAGE = "Geçersiz veya eksik fotoğraf verisi.";
    public static final String NO_DEFAULT_PHOTO_MESSAGE = "Default fotoğraf yok.";
    
    // Time Formatting
    public static final String TIME_FORMAT_PATTERN = "HH:mm";
    public static final String DATE_FORMAT_PATTERN = "dd.MM.yyyy";
    public static final String LOG_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String LOG_FILE_DATE_PATTERN = "yyyy-MM-dd";
    
    // Private constructor to prevent instantiation
    private AppConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
