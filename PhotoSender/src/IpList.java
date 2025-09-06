
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class IpList {
    public static class IpEntry {
        private String ip;
        private String name;
        private String status = "Bilinmiyor";

        public IpEntry(String ip, String name) {
            this.ip = ip;
            this.name = name;
        }
        public String getIp() { return ip; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String toString() { return name + " (" + ip + ")"; }
    }

    private List<IpEntry> ipEntries;

    public IpList() {
        ipEntries = new ArrayList<>();
    }

    public void addIp(String ip, String name) {
        // Input validation
        if (ip == null || ip.trim().isEmpty()) {
            throw new IllegalArgumentException("IP adresi boş olamaz");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("İsim boş olamaz");
        }
        
        String trimmedIp = ip.trim();
        String trimmedName = name.trim();
        
        if (!isValidIP(trimmedIp)) {
            throw new IllegalArgumentException("Geçersiz IP adresi: " + trimmedIp);
        }
        
        // Duplicate check
        for (IpEntry entry : ipEntries) {
            if (entry.getIp().equals(trimmedIp)) {
                throw new IllegalArgumentException("Bu IP zaten mevcut: " + trimmedIp);
            }
        }
        
        ipEntries.add(new IpEntry(trimmedIp, trimmedName));
    }

    public List<IpEntry> getIpEntries() {
        return ipEntries;
    }

    // Belirli bir IP'nin durumunu güncelle
    public void updateStatus(String ip, String status) {
        for (IpEntry entry : ipEntries) {
            if (entry.getIp().equals(ip)) {
                entry.setStatus(status);
                break;
            }
        }
    }

    // Tüm IP'lerin durumunu güncelle
    public void updateAllStatuses(java.util.Map<String, String> statusMap) {
        for (IpEntry entry : ipEntries) {
            String status = statusMap.get(entry.getIp());
            if (status != null) {
                entry.setStatus(status);
            }
        }
    }

    // IP formatı kontrolü
    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void saveToFile(File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (IpEntry entry : ipEntries) {
                writer.write(entry.getIp() + "," + entry.getName());
                writer.newLine();
            }
        }
    }

    public void loadFromFile(File file) throws IOException {
        ipEntries.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Boş satırları ve yorum satırlarını atla
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    String ip = parts[0].trim();
                    String name = parts[1].trim();
                    
                    // Validation kontrolü
                    if (!isValidIP(ip)) {
                        System.err.println("Geçersiz IP (satır " + lineNumber + "): " + ip);
                        continue;
                    }
                    
                    if (name.isEmpty()) {
                        System.err.println("Boş isim (satır " + lineNumber + "): " + line);
                        continue;
                    }
                    
                    // Duplicate check (dosya yüklerken)
                    boolean isDuplicate = false;
                    for (IpEntry entry : ipEntries) {
                        if (entry.getIp().equals(ip)) {
                            System.err.println("Duplicate IP (satır " + lineNumber + "): " + ip);
                            isDuplicate = true;
                            break;
                        }
                    }
                    
                    if (!isDuplicate) {
                        ipEntries.add(new IpEntry(ip, name));
                    }
                } else {
                    System.err.println("Geçersiz format (satır " + lineNumber + "): " + line);
                }
            }
        }
    }
}
