
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

        public IpEntry(String ip, String name) {
            this.ip = ip;
            this.name = name;
        }
        public String getIp() { return ip; }
        public String getName() { return name; }
        public String toString() { return name + " (" + ip + ")"; }
    }

    private List<IpEntry> ipEntries;

    public IpList() {
        ipEntries = new ArrayList<>();
    }

    public void addIp(String ip, String name) {
        ipEntries.add(new IpEntry(ip, name));
    }

    public List<IpEntry> getIpEntries() {
        return ipEntries;
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
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    addIp(parts[0], parts[1]);
                }
            }
        }
    }
}
