package app;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {
    // Port TCP untuk transfer file
    public static final int TCP_PORT = 1234;

    // Port UDP untuk discovery
    public static final int UDP_PORT = 9876;

    // Interval broadcast (ms)
    public static final int BROADCAST_INTERVAL = 3000;

    // Nama user default (Nanti bisa diambil dari input user)
    public static String USERNAME = "User-" + (int) (Math.random() * 1000);

    // Folder untuk menyimpan file yang diterima
    // System.getProperty("user.home") mengambil folder Home user (C:\Users\Nama)
    public static final Path SAVE_DIR = Paths.get(System.getProperty("user.home"), "Downloads", "ShareIt_Received");

    // Pastikan folder download ada
    static {
        File dir = SAVE_DIR.toFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created)
                System.out.println("Folder download dibuat: " + SAVE_DIR);
        }
    }
}