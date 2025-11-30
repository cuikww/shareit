package controllers;

import core.network.ConnectionManager;
import core.network.DiscoveryService;
import core.network.Device;
import core.transfer.TransferManager;
import core.transfer.TransferModel;
import core.transfer.TransferModel.TransferStatus;
import ui.frames.MainFrame;
import ui.panels.TransferListPanel;
import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.io.File;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainController implements ConnectionManager.ServerListener,
        DiscoveryService.DiscoveryListener,
        TransferManager.TransferListener {

    private final MainFrame mainFrame;
    private final ConnectionManager connectionManager;
    private final DiscoveryService discoveryService;
    private final TransferManager transferManager;
    private final TransferListPanel transferListPanel;
    private final Map<String, TransferModel> activeTransfers = new ConcurrentHashMap<>();

    public MainController() {
        // 1. Siapkan UI
        this.mainFrame = new MainFrame(this);
        this.transferListPanel = mainFrame.getTransferListPanel();

        // 2. Siapkan Network Manager
        this.connectionManager = new ConnectionManager(this);

        // 3. Siapkan Discovery Service
        this.discoveryService = new DiscoveryService(this);

        // 4. Siapkan Transfer Manager
        this.transferManager = new TransferManager(this);
        this.transferManager.setConnectionManager(this.connectionManager);
        // Wire UI control actions to TransferManager
        this.transferListPanel.setControlListener(new TransferListPanel.ControlListener() {
            @Override
            public void pauseTransfer(String transferId) {
                transferManager.pauseTransfer(transferId);
            }

            @Override
            public void resumeTransfer(String transferId) {
                transferManager.resumeTransfer(transferId);
            }

            @Override
            public void cancelTransfer(String transferId) {
                transferManager.cancelTransfer(transferId);
            }
        });
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
        System.out.println("[Controller] Client connected from: " + socket.getInetAddress());

        // Spawn ReceiverTask untuk handle incoming file transfer
        // Files akan di-save ke "received" folder
        String savePath = new File("received").getAbsolutePath();
        transferManager.receiveFile(socket, savePath);
    }

    @Override
    public void onConnectionEstablished(Socket socket, String remoteIP) {
        System.out.println("[Controller] Connection established to: " + remoteIP);
        // Connection berhasil, socket siap digunakan untuk transfer
    }

    @Override
    public void onConnectionFailed(String remoteIP, String error) {
        System.err.println("[Controller] Connection failed to " + remoteIP + ": " + error);
        SwingUtilities.invokeLater(() -> {
            mainFrame.showError("Gagal terhubung ke " + remoteIP + "\n" + error);
        });
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

        // Create TransferModel untuk track progress
        TransferModel model = new TransferModel(transferId, fileName, 0);
        model.setStatus(TransferStatus.TRANSFERRING);
        activeTransfers.put(transferId, model);

        // Update UI - add ke transfer list panel
        SwingUtilities.invokeLater(() -> {
            transferListPanel.updateTransfer(model);
        });
    }

    @Override
    public void onTransferProgress(String transferId, long bytesTransferred, long totalBytes) {
        TransferModel model = activeTransfers.get(transferId);
        if (model != null) {
            model.setBytesTransferred(bytesTransferred);

            // Update UI real-time (dengan SwingUtilities.invokeLater untuk thread-safe)
            SwingUtilities.invokeLater(() -> {
                transferListPanel.updateTransfer(model);
            });
        }
    }

    @Override
    public void onTransferCompleted(String transferId) {
        System.out.println("[Controller] Transfer completed: " + transferId);

        TransferModel model = activeTransfers.get(transferId);
        if (model != null) {
            model.setStatus(TransferStatus.COMPLETED);

            // Update UI
            SwingUtilities.invokeLater(() -> {
                transferListPanel.updateTransfer(model);
                // Bisa di-remove dari active list setelah beberapa waktu
                // atau biarkan tetap terlihat untuk history
            });
        }
    }

    @Override
    public void onTransferFailed(String transferId, String error) {
        System.err.println("[Controller] Transfer failed: " + error);

        TransferModel model = activeTransfers.get(transferId);
        if (model != null) {
            model.setStatus(TransferStatus.FAILED);

            // Update UI
            SwingUtilities.invokeLater(() -> {
                transferListPanel.updateTransfer(model);
                mainFrame.showError("Transfer failed: " + error);
            });
        }
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