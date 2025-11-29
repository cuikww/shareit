package controllers;

import core.network.ConnectionManager;
import core.network.DiscoveryService;
import core.network.Device;
import ui.frames.MainFrame;
import javax.swing.SwingUtilities;
import java.net.Socket;

public class MainController implements ConnectionManager.ServerListener, DiscoveryService.DiscoveryListener {

    private final MainFrame mainFrame;
    private final ConnectionManager connectionManager;
    private final DiscoveryService discoveryService;

    public MainController() {
        // 1. Siapkan UI
        this.mainFrame = new MainFrame();

        // 2. Siapkan Network Manager
        this.connectionManager = new ConnectionManager(this);

        // 3. Siapkan Discovery Service
        this.discoveryService = new DiscoveryService(this);
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
}