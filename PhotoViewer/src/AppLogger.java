/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Uygulama için loglama sınıfı
 * APPDATA klasörü altında tarihli log dosyaları oluşturur
 */
public class AppLogger {
    private final String appName;
    private final File logFile;
    private final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern(AppConstants.LOG_TIMESTAMP_PATTERN);
    private final DateTimeFormatter fileFormat = DateTimeFormatter.ofPattern(AppConstants.LOG_FILE_DATE_PATTERN);
    
    /**
     * AppLogger constructor
     * @param appName Uygulama adı (PhotoSender veya PhotoViewer)
     */
    public AppLogger(String appName) {
        this.appName = appName;
        this.logFile = createLogFile();
        
        // Başlangıç log'u
        log("INFO", "Uygulama başlatıldı - " + appName);
    }
    
    /**
     * Log dosyası oluşturur
     * @return Log dosyası
     */
    private File createLogFile() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isEmpty()) {
            appdata = System.getProperty("user.home");
        }
        
        File logDir = new File(appdata, appName + "/logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        String fileName = appName + "_" + LocalDate.now().format(fileFormat) + ".log";
        return new File(logDir, fileName);
    }
    
    /**
     * Ana log metodu
     * @param level Log seviyesi (INFO, WARN, ERROR, SUCCESS)
     * @param message Log mesajı
     */
    public void log(String level, String message) {
        try {
            String timestamp = LocalDateTime.now().format(timestampFormat);
            String logEntry = String.format("[%s] %s - %s - %s%n", 
                                           timestamp, level, appName, message);
            
            // Dosyaya yaz
            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(logEntry);
            }
            
            // Konsola da yaz
            System.out.print(logEntry);
            
        } catch (IOException e) {
            System.err.println("Log yazma hatası: " + e.getMessage());
        }
    }
    
    /**
     * INFO seviyesinde log
     */
    public void info(String message) {
        log("INFO", message);
    }
    
    /**
     * DEBUG seviyesinde log (detaylı bilgiler için)
     */
    public void debug(String message) {
        log("DEBUG", message);
    }
    
    /**
     * WARN seviyesinde log
     */
    public void warn(String message) {
        log("WARN", message);
    }
    
    /**
     * ERROR seviyesinde log
     */
    public void error(String message) {
        log("ERROR", message);
    }
    
    /**
     * ERROR seviyesinde log (exception ile)
     */
    public void error(String message, Exception e) {
        log("ERROR", message + " - " + e.getMessage());
    }
    
    /**
     * SUCCESS seviyesinde log
     */
    public void success(String message) {
        log("SUCCESS", message);
    }
}
