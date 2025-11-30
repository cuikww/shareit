package core.transfer;

import core.network.ConnectionManager;
import core.network.Device;
import core.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferManager {

    private final ExecutorService executorService;
    private final Map<String, TransferTask> activeTasks = new ConcurrentHashMap<>();
    private ConnectionManager connectionManager;

    public interface TransferListener {
        void onTransferStarted(String transferId, String fileName);

        void onTransferProgress(String transferId, long bytesTransferred, long totalBytes);

        void onTransferCompleted(String transferId);

        void onTransferFailed(String transferId, String error);
    }

    private final TransferListener listener;

    public TransferManager(TransferListener listener) {
        this.listener = listener;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Kirim file/folder ke device target
     */
    public void sendFile(File inputPath, Device targetDevice) {
        executorService.submit(() -> {
            Socket socket = null;
            try {
                // 1. Prepare file (zip jika folder)
                File fileToSend = FileUtils.prepareFile(inputPath);

                if (!FileUtils.isReadable(fileToSend)) {
                    throw new IOException("File tidak bisa dibaca: " + fileToSend);
                }

                // 2. Generate transfer ID
                String transferId = generateTransferId(fileToSend, targetDevice);

                // 3. Notify listener
                if (listener != null) {
                    listener.onTransferStarted(transferId, fileToSend.getName());
                }

                System.out.println("[TransferManager] Preparing to send: " + fileToSend.getName() +
                        " (" + FileUtils.formatFileSize(fileToSend.length()) + ") to " + targetDevice);

                // 4. Establish connection (Phase 2: Handshake)
                if (connectionManager == null) {
                    throw new IOException("ConnectionManager not set");
                }

                socket = connectionManager.connectTo(targetDevice.getIpAddress());

                System.out.println("[TransferManager] Connection established, ready to transfer");
                System.out.println("[TransferManager] Socket: " + socket.getInetAddress() + ":" + socket.getPort());

                // 5. Create sender task (akan diimplementasi di phase berikutnya)
                SenderTask task = new SenderTask(fileToSend, socket, transferId, listener,
                        System.getProperty("user.name"));
                activeTasks.put(transferId, task);
                task.start();

                // Sementara ini placeholder
                System.out.println("[TransferManager] Transfer task created: " + transferId);
                System.out.println("[TransferManager] TODO: Implement SenderTask untuk actual transfer");

            } catch (IOException e) {
                System.err.println("[TransferManager] Error: " + e.getMessage());
                if (listener != null) {
                    listener.onTransferFailed("unknown", e.getMessage());
                }
            } finally {
                // Cleanup socket jika error (nanti di SenderTask yang handle)
                if (socket != null && !socket.isClosed()) {
                    try {
                        // socket.close(); // Jangan close dulu, nanti di SenderTask
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        });
    }

    /**
     * Pause transfer
     */
    public void pauseTransfer(String transferId) {
        TransferTask task = activeTasks.get(transferId);
        if (task != null) {
            task.pause();
        }
    }

    /**
     * Resume transfer
     */
    public void resumeTransfer(String transferId) {
        TransferTask task = activeTasks.get(transferId);
        if (task != null) {
            task.resume();
        }
    }

    /**
     * Cancel transfer
     */
    public void cancelTransfer(String transferId) {
        TransferTask task = activeTasks.get(transferId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(transferId);
        }
    }

    /**
     * Generate unique transfer ID
     */
    private String generateTransferId(File file, Device device) {
        return System.currentTimeMillis() + "_" + file.getName() + "_" + device.getIpAddress();
    }

    /**
     * Terima file dari device lain (incoming connection)
     */
    public void receiveFile(Socket clientSocket, String savePath) {
        executorService.submit(() -> {
            try {
                // Generate transfer ID untuk incoming transfer
                String transferId = "incoming_" + System.currentTimeMillis();

                System.out.println("[TransferManager] Incoming transfer from: " + clientSocket.getInetAddress());

                // Create receiver task
                ReceiverTask task = new ReceiverTask(clientSocket, savePath, transferId, listener);
                activeTasks.put(transferId, task);
                task.start();

            } catch (Exception e) {
                System.err.println("[TransferManager] Error handling incoming transfer: " + e.getMessage());
                e.printStackTrace();

                try {
                    clientSocket.close();
                } catch (IOException ignored) {
                }

                if (listener != null) {
                    listener.onTransferFailed("unknown", "Error receiving file: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Shutdown manager
     */
    public void shutdown() {
        executorService.shutdown();
        activeTasks.values().forEach(TransferTask::cancel);
    }
}
