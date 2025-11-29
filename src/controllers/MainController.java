package controllers;

import core.network.ConnectionManager;
import core.network.DiscoveryService;
import core.network.Device;
import core.transfer.TransferManager;
import ui.frames.MainFrame;
import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.io.File;
import java.net.Socket;

public class MainController implements ConnectionManager.ServerListener,
        DiscoveryService.DiscoveryListener,
        TransferManager.TransferListener {

    private final MainFrame mainFrame;
    private final ConnectionManager connectionManager;
    private final DiscoveryService discoveryService;
    private final TransferManager transferManager;

    public MainController() {
        // 1. Siapkan UI
        this.mainFrame = new MainFrame(this);

        // 2. Siapkan Network Manager
        this.connectionManager = new ConnectionManager(this);

        // 3. Siapkan Discovery Service
        this.discoveryService = new DiscoveryService(this);

        // 4. Siapkan Transfer Manager
        this.transferManager = new TransferManager(this);
    }

    public void startApp() {
        // Tampilkan GUI
        SwingUtilities.invokeLater(() -> {
            mainFrame.setVisible(true);
        });

        // Nyalakan Server di Background
        connectionManager.startServer();

        // Nyalakan Discovery Service
        discoveryService.start();
    }

    // --- Callback dari ConnectionManager (ServerListener) ---

    @Override
    public void onServerStarted(String ip, int port) {
        // Update UI harus di thread Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            mainFrame.setServerStatus(ip, port, true);
        });
    }

    @Override
    public void onServerError(String error) {
        System.err.println("Server Error: " + error);
        SwingUtilities.invokeLater(() -> {
            mainFrame.setServerStatus("Error", 0, false);
        });
    }

    @Override
    public void onClientConnected(Socket socket) {
        // Ini akan kita isi di Phase 2 (Minggu depan)
        // Nanti kita akan panggil TransferManager di sini
        System.out.println("[Controller] Klien terhubung, siap untuk handshake...");
    }

    // --- Callback dari DiscoveryService (DiscoveryListener) ---

    @Override
    public void onDeviceDiscovered(Device device) {
        System.out.println("[Controller] Device discovered: " + device);
        SwingUtilities.invokeLater(() -> {
            mainFrame.addDevice(device);
        });
    }

    @Override
    public void onDeviceRemoved(Device device) {
        System.out.println("[Controller] Device removed: " + device);
        SwingUtilities.invokeLater(() -> {
            mainFrame.removeDevice(device);
        });
    }

    // --- Callback dari TransferManager (TransferListener) ---

    @Override
    public void onTransferStarted(String transferId, String fileName) {
        System.out.println("[Controller] Transfer started: " + fileName);
        // TODO: Update UI transfer list
    }

    @Override
    public void onTransferProgress(String transferId, long bytesTransferred, long totalBytes) {
        // TODO: Update progress bar
    }

    @Override
    public void onTransferCompleted(String transferId) {
        System.out.println("[Controller] Transfer completed: " + transferId);
        // TODO: Update UI
    }

    @Override
    public void onTransferFailed(String transferId, String error) {
        System.err.println("[Controller] Transfer failed: " + error);
        // TODO: Show error dialog
    }

    // --- Action Handlers ---

    public void handleSendFile() {
        Device selectedDevice = mainFrame.getSelectedDevice();
        if (selectedDevice == null) {
            mainFrame.showError("Pilih device tujuan terlebih dahulu!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Pilih File untuk Dikirim");

        int result = fileChooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            System.out.println("[Controller] Sending file: " + selectedFile.getName() + " to " + selectedDevice);
            transferManager.sendFile(selectedFile, selectedDevice);
        }
    }

    public void handleSendFolder() {
        Device selectedDevice = mainFrame.getSelectedDevice();
        if (selectedDevice == null) {
            mainFrame.showError("Pilih device tujuan terlebih dahulu!");
            return;
        }

        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("Pilih Folder untuk Dikirim");

        int result = folderChooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();
            System.out.println("[Controller] Sending folder: " + selectedFolder.getName() + " to " + selectedDevice);
            transferManager.sendFile(selectedFolder, selectedDevice);
        }
    }
}