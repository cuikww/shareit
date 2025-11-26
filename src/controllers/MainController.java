package controllers;

import core.network.ConnectionManager;
import ui.frames.MainFrame;
import javax.swing.SwingUtilities;
import java.net.Socket;

public class MainController implements ConnectionManager.ServerListener {

    private final MainFrame mainFrame;
    private final ConnectionManager connectionManager;

    public MainController() {
        // 1. Siapkan UI
        this.mainFrame = new MainFrame();
        
        // 2. Siapkan Network Manager
        this.connectionManager = new ConnectionManager(this); // 'this' adalah listener
    }

    public void startApp() {
        // Tampilkan GUI
        SwingUtilities.invokeLater(() -> {
            mainFrame.setVisible(true);
        });

        // Nyalakan Server di Background
        connectionManager.startServer();
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
}