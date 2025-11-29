package core.utils;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {

    /**
     * Prepare file untuk transfer.
     * Jika input adalah folder, akan di-zip dulu.
     * Jika input adalah file, langsung return.
     */
    public static File prepareFile(File inputPath) throws IOException {
        if (inputPath.isDirectory()) {
            return zipFolder(inputPath);
        } else {
            return inputPath;
        }
    }

    /**
     * Zip folder menjadi file .zip temporary
     */
    public static File zipFolder(File folder) throws IOException {
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("Input bukan folder: " + folder);
        }

        // Buat file zip temporary
        File tempZip = File.createTempFile(folder.getName() + "_", ".zip");
        tempZip.deleteOnExit(); // Auto delete saat aplikasi close

        System.out.println("[FileUtils] Zipping folder: " + folder.getName() + " -> " + tempZip.getName());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            zipFolderRecursive(folder, folder.getName(), zos);
        }

        System.out.println("[FileUtils] Zip completed: " + tempZip.getAbsolutePath() +
                " (" + formatFileSize(tempZip.length()) + ")");
        return tempZip;
    }

    /**
     * Recursive helper untuk zip folder
     */
    private static void zipFolderRecursive(File folder, String parentPath, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            String entryName = parentPath + "/" + file.getName();

            if (file.isDirectory()) {
                // Tambah entry untuk folder
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
                // Recursive
                zipFolderRecursive(file, entryName, zos);
            } else {
                // Tambah file
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * Hitung MD5 checksum dari file
     */
    public static String getMD5(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    md.update(buffer, 0, len);
                }
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new IOException("Error calculating MD5: " + e.getMessage(), e);
        }
    }

    /**
     * Format file size menjadi human-readable (KB, MB, GB)
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Validasi apakah file bisa dibaca
     */
    public static boolean isReadable(File file) {
        return file.exists() && file.canRead();
    }

    /**
     * Buat folder jika belum ada
     */
    public static void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }
}
