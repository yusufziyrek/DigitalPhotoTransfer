/*
 * Bu yazılım Yusuf Ziyrek'e aittir.
 * İzinsiz kopyalanamaz, değiştirilemez, dağıtılamaz ve ticari olarak kullanılamaz.
 * Tüm hakları saklıdır. © 2025 Yusuf Ziyrek
 */

/**
 * Ortak hata mesajları ve kullanıcı bildirim mesajları
 * PhotoSender ve PhotoViewer uygulamaları için standart mesajlar
 */
public class AppMessages {
    
    // === PENCERE BAŞLIKLARI ===
    public static final String TITLE_ERROR = "Hata";
    public static final String TITLE_WARNING = "Uyarı";
    public static final String TITLE_INFO = "Bilgi";
    public static final String TITLE_UPDATE = "Güncelleme";
    public static final String TITLE_SUCCESS = "Başarılı";
    public static final String TITLE_CONFIRM = "Onay";
    
    // === DOSYA İŞLEMLERİ ===
    public static final String ERROR_FILE_READ = "Dosya okunamadı";
    public static final String ERROR_FILE_WRITE = "Dosya yazılamadı";
    public static final String ERROR_FILE_NOT_FOUND = "Dosya bulunamadı";
    public static final String ERROR_FILE_INVALID = "Geçersiz dosya";
    public static final String ERROR_FILE_EMPTY = "Dosya boş";
    public static final String ERROR_IMAGE_LOAD = "Resim yüklenemedi";
    public static final String ERROR_IMAGE_INVALID = "Geçersiz resim dosyası";
    public static final String ERROR_IMAGE_READ = "Resim dosyası okunamıyor";
    
    // === NETWORK İŞLEMLERİ ===
    public static final String ERROR_NETWORK_CONNECTION = "Bağlantı hatası";
    public static final String ERROR_NETWORK_TIMEOUT = "Bağlantı zaman aşımı";
    public static final String ERROR_NETWORK_SEND = "Gönderim hatası";
    public static final String ERROR_IP_INVALID = "Geçersiz IP adresi";
    public static final String ERROR_IP_NOT_FOUND = "IP adresi bulunamadı";
    public static final String ERROR_NO_IP_SELECTED = "Listeden bir IP seçin";
    public static final String ERROR_NO_IP_TO_SEND = "Gönderilecek IP adresi bulunamadı";
    
    // === GÜNCELLEME İŞLEMLERİ ===
    public static final String ERROR_UPDATE_CHECK = "Güncelleme kontrolü başarısız";
    public static final String ERROR_UPDATE_DOWNLOAD = "İndirme hatası";
    public static final String ERROR_UPDATE_INSTALL = "Kurulum başlatılamadı";
    public static final String ERROR_UPDATE_MSI = "MSI kurulum hatası";
    public static final String ERROR_GITHUB_API = "GitHub API hatası";
    public static final String ERROR_REPOSITORY_ACCESS = "Repository erişim hatası";
    public static final String ERROR_UPDATE_CONNECTION = "Güncelleme sunucusuna bağlanılamadı";
    public static final String ERROR_UPDATE_TIMEOUT = "Güncelleme zaman aşımı";
    public static final String ERROR_UPDATE_RETRY_FAILED = "Güncelleme tekrar denemesi başarısız";
    public static final String ERROR_MSI_SIZE_INVALID = "MSI dosya boyutu geçersiz";
    public static final String ERROR_MSI_DOWNLOAD_INCOMPLETE = "MSI dosyası tam olarak indirilemedi";
    public static final String ERROR_MSI_CORRUPTED = "MSI dosyası bozuk";
    
    public static final String INFO_UPDATE_NOT_AVAILABLE = "Güncelleme mevcut değil";
    public static final String INFO_UPDATE_AVAILABLE = "Yeni sürüm mevcut";
    public static final String INFO_UPDATE_CURRENT = "Mevcut sürüm güncel";
    public static final String INFO_REPOSITORY_PRIVATE = "Repository özel durumda";
    public static final String INFO_UPDATE_CHECKING = "Güncellemeler kontrol ediliyor...";
    public static final String INFO_UPDATE_DOWNLOADING = "Güncelleme indiriliyor...";
    public static final String INFO_UPDATE_RETRYING = "Tekrar deneniyor...";
    
    // === UYGULAMA İŞLEMLERİ ===
    public static final String ERROR_NO_PHOTO_SELECTED = "Önce fotoğraf seçin";
    public static final String ERROR_INVALID_INPUT = "Geçersiz veri girişi";
    public static final String ERROR_OPERATION_FAILED = "İşlem başarısız";
    public static final String ERROR_PERMISSION_DENIED = "Erişim reddedildi";
    public static final String ERROR_INVALID_TIME = "Geçersiz süre değeri";
    
    public static final String INFO_OPERATION_SUCCESS = "İşlem başarılı";
    public static final String INFO_PHOTO_SENT = "Fotoğraf gönderildi";
    public static final String INFO_PLEASE_SELECT_FILE = "Lütfen bir dosya seçin";
    public static final String INFO_PLEASE_SELECT_PHOTO = "Lütfen bir resim dosyası seçin";
    
    // === ONAY MESSAJları ===
    public static final String CONFIRM_EXIT = "Uygulamadan çıkmak istediğinizden emin misiniz?";
    public static final String CONFIRM_INSTALL_UPDATE = "Güncellemeyi şimdi kurmak istiyor musunuz?";
    public static final String CONFIRM_DOWNLOAD_UPDATE = "Güncellemeyi indirmek istiyor musunuz?";
    
    // === DETAYLI MESAJLAR ===
    public static final String MSG_TIME_INPUT_ERROR = "Lütfen geçerli bir süre girin.\n(En az 1 dakika olmalı)";
    public static final String MSG_IP_SAVE_ERROR = "IP listesi kaydedilemedi: %s";
    public static final String MSG_IP_READ_ERROR = "IP listesi okunamadı: %s";
    public static final String MSG_SEND_ERROR = "Gönderim hatası: %s";
    public static final String MSG_UPDATE_INSTALL_CONFIRM = "Güncelleme indirildi (%d MB).\n" +
            "Kurulumu başlatmak istiyor musunuz?\n\n" +
            "Not: Kurulum başladığında uygulama otomatik kapanacaktır.";
    
    // === MSI KURULUM MESAJLARI ===
    public static final String MSG_MSI_INSTALL_STARTING = "Güncelleme kurulumu başlatılıyor...\n\n" +
            "Standart Windows Installer arayüzü açılacak.\n" +
            "Kurulum talimatlarını takip ediniz.\n\n" +
            "Kurulum tamamlandıktan sonra yeni sürümü başlatabilirsiniz.";
    public static final String MSG_MSI_INSTALL_STARTED = "Kurulum başlatıldı.\n\n" +
            "Uygulama %d saniye sonra kapanacak.\n" +
            "Kurulum tamamlandıktan sonra yeni sürümü başlatabilirsiniz.";
    public static final String MSG_MSI_MANUAL_INSTALL = "Otomatik kurulum başarısız oldu.\n\n" +
            "Lütfen MSI dosyasını manuel olarak çalıştırın:\n%s";
    public static final String TITLE_MSI_INSTALL = "Güncelleme Kurulumu";
    public static final String TITLE_MSI_INSTALL_STARTED = "Kurulum Başlatıldı";
    public static final String TITLE_MSI_MANUAL_INSTALL = "Manuel Kurulum Gerekli";
    
    // === FORMAT HELPER METHODS ===
    public static String formatIpSaveError(String details) {
        return String.format(MSG_IP_SAVE_ERROR, details);
    }
    
    public static String formatIpReadError(String details) {
        return String.format(MSG_IP_READ_ERROR, details);
    }
    
    public static String formatSendError(String details) {
        return String.format(MSG_SEND_ERROR, details);
    }
    
    public static String formatUpdateInstallConfirm(int mbSize) {
        return String.format(MSG_UPDATE_INSTALL_CONFIRM, mbSize);
    }
    
    public static String formatMsiInstallStarted(int seconds) {
        return String.format(MSG_MSI_INSTALL_STARTED, seconds);
    }
    
    public static String formatMsiManualInstall(String msiPath) {
        return String.format(MSG_MSI_MANUAL_INSTALL, msiPath);
    }
    
    /**
     * Hata mesajı ile detay bilgiyi birleştirme
     */
    public static String withDetails(String message, String details) {
        if (details == null || details.trim().isEmpty()) {
            return message;
        }
        return message + ": " + details;
    }
    
    /**
     * Hata mesajı ile exception bilgiyi birleştirme
     */
    public static String withException(String message, Exception e) {
        if (e == null || e.getMessage() == null) {
            return message;
        }
        return withDetails(message, e.getMessage());
    }
}
