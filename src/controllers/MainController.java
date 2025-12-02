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
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import java.io.File;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import app.Config;

public class MainController implements ConnectionManager.ServerListener,
        DiscoveryService.DiscoveryListener,
        TransferManager.TransferListener {

    private final MainFrame mainFrame;
    private final ConnectionManager connectionManager;
    private final DiscoveryService discoveryService;
    private final TransferManager transferManager;
    private final TransferListPanel transferListPanel;
    private final Map<String, TransferModel> activeTransfers = new ConcurrentHashMap<>();
    private String username = System.getProperty("user.name");

    public MainController() {
        // 1. Siapkan UI
        this.mainFrame = new MainFrame(this);
        // show current username in main UI (default) and set discovery username
        Config.USERNAME = username;
        this.mainFrame.setUser(username);
        this.mainFrame.setLandingName(username);
        this.transferListPanel = mainFrame.getTransferListPanel();

        // 2. Siapkan Network Manager
        this.connectionManager = new ConnectionManager(this);

        // 3. Siapkan Discovery Service
        this.discoveryService = new DiscoveryService(this);

        // 4. Siapkan Transfer Manager
        this.transferManager = new TransferManager(this);
        this.transferManager.setConnectionManager(this.connectionManager);
        
        // Set incoming transfer confirmation listener
        this.transferManager.setIncomingTransferListener((transferId, senderUsername, socket) -> {
            final boolean[] accept = new boolean[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    int res = JOptionPane.showConfirmDialog(mainFrame,
                            "Incoming transfer from " + senderUsername + ". Accept?",
                            "Incoming transfer",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    accept[0] = (res == JOptionPane.YES_OPTION);
                });
            } catch (Exception e) {
                accept[0] = false;
            }
            return accept[0];
        });
        
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

        // Nyalakan Discovery Service (listener + cleanup). Broadcasting of presence
        // is a separate action that happens only when user explicitly enters Receive mode.
        discoveryService.start();
    }

    /**
     * Enable or disable "Receive" mode. When enabled the app will announce its
     * presence on the local network so other peers can see this device in the
     * peers list (i.e. act as a receiver). When disabled the device will not
     * broadcast and will not be listed by others.
     */
    public void enableReceiveMode(boolean enabled) {
        if (discoveryService == null) return;
        if (enabled) {
            discoveryService.startBroadcasting();
            // announce immediately so peers see us right away
            discoveryService.announceNow();
        } else {
            discoveryService.stopBroadcasting();
        }
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
        // Ask user for confirmation before accepting incoming transfer
        // The confirmation happens inside ReceiverTask which calls the incomingTransferListener
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
    public void onDeviceUpdated(Device device) {
        System.out.println("[Controller] Device updated: " + device);
        SwingUtilities.invokeLater(() -> mainFrame.updateDevice(device));
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
        // Determine direction: incoming transfers are prefixed with "incoming_"
        core.transfer.TransferModel.Direction dir = transferId != null && transferId.startsWith("incoming_")
            ? core.transfer.TransferModel.Direction.INCOMING
            : core.transfer.TransferModel.Direction.OUTGOING;
        TransferModel model = new TransferModel(transferId, fileName, 0, dir);
        model.setStatus(TransferStatus.TRANSFERRING);
        activeTransfers.put(transferId, model);

        // Update UI - add ke transfer list panel
        SwingUtilities.invokeLater(() -> {
            transferListPanel.updateTransfer(model);
        });

        // Ensure overall progress UI appears for outgoing transfers
        recomputeAndUpdateOverallProgress();
    }

    public void setUsername(String name) {
        this.username = name;
        // Update global broadcast username so discovery messages reflect the chosen name
        Config.USERNAME = name;
        SwingUtilities.invokeLater(() -> mainFrame.setUser(name));
        // Ask discovery service to re-announce with the new name immediately
        if (discoveryService != null) discoveryService.announceNow();
    }

    @Override
    public void onTransferProgress(String transferId, long bytesTransferred, long totalBytes) {
        TransferModel model = activeTransfers.get(transferId);
        if (model != null) {
            // If totalBytes not known previously, set it (first progress update usually contains totalBytes)
            if (model.getTotalBytes() == 0 && totalBytes > 0) {
                model.setTotalBytes(totalBytes);
            }
            model.setBytesTransferred(bytesTransferred);

            // Update UI real-time (dengan SwingUtilities.invokeLater untuk thread-safe)
            SwingUtilities.invokeLater(() -> {
                transferListPanel.updateTransfer(model);
            });

            // Update aggregated overall sending progress UI (outgoing transfers only)
            recomputeAndUpdateOverallProgress();
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

        // transfer finished - refresh overall progress UI
        recomputeAndUpdateOverallProgress();
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

        // update overall progress UI on failure
        recomputeAndUpdateOverallProgress();
    }

    /**
     * Compute totals for outgoing transfers and update the overall progress UI.
     */
    private void recomputeAndUpdateOverallProgress() {
        long totalBytesSum = 0;
        long transferredSum = 0;
        double combinedKBps = 0.0;

        boolean hasOutgoingActive = false;
        boolean unknownSize = false;

        for (TransferModel m : activeTransfers.values()) {
            if (m.getDirection() == TransferModel.Direction.OUTGOING && !m.getStatus().equals(TransferModel.TransferStatus.COMPLETED)
                    && m.getStatus() != TransferModel.TransferStatus.FAILED && m.getStatus() != TransferModel.TransferStatus.CANCELLED) {
                hasOutgoingActive = true;

                long t = m.getTotalBytes();
                if (t <= 0) {
                    unknownSize = true;
                } else {
                    totalBytesSum += t;
                }

                transferredSum += m.getBytesTransferred();
                combinedKBps += m.getSpeedKBps();
            }
        }

        if (mainFrame == null) return;

        final boolean shouldShow = hasOutgoingActive;
        final long totalBytesSumF = totalBytesSum;
        final long transferredSumF = transferredSum;
        final double combinedKBpsF = combinedKBps;
        final boolean unknownSizeF = unknownSize;

        SwingUtilities.invokeLater(() -> {
            mainFrame.setOverallProgressVisible(shouldShow);
            if (!shouldShow) return;

            if (unknownSizeF || totalBytesSumF <= 0) {
                // set indeterminate
                mainFrame.updateOverallProgress(transferredSumF, 0, combinedKBpsF);
            } else {
                mainFrame.updateOverallProgress(transferredSumF, totalBytesSumF, combinedKBpsF);
            }
        });
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
            transferManager.sendFile(selectedFile, selectedDevice, username);
        }
    }

    /**
     * Show a file chooser and return selected files, or null if cancelled.
     */
    public java.util.List<java.io.File> chooseFilesToAdd() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle("Pilih file untuk ditambahkan");
        int result = fileChooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File[] selected = fileChooser.getSelectedFiles();
            java.util.List<java.io.File> list = new java.util.ArrayList<>();
            for (java.io.File f : selected) list.add(f);
            return list;
        }
        return null;
    }

    /**
     * Show a folder chooser and return the selected folder, or null if cancelled
     */
    public java.io.File chooseFolder() {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("Pilih Folder untuk ditambahkan");

        int result = folderChooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            return folderChooser.getSelectedFile();
        }
        return null;
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
            // If selected item is a folder, TransferManager.sendFile will detect and zip before sending
            System.out.println("[Controller] Sending folder: " + selectedFolder.getName() + " to " + selectedDevice);
            transferManager.sendFile(selectedFolder, selectedDevice, username);
        }
    }

    /**
     * Called by SendPanel when user clicks Send after choosing files. Uses currently selected device.
     */
    public void handleSendFiles(java.util.List<java.io.File> files) {
        Device selectedDevice = mainFrame.getSelectedDevice();
        if (selectedDevice == null) {
            mainFrame.showError("Pilih device tujuan terlebih dahulu!");
            return;
        }

        // send each file individually for now (could package into one transfer)
        for (java.io.File f : files) {
            transferManager.sendFile(f, selectedDevice, username);
        }
    }
}