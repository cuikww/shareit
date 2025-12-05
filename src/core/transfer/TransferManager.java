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
    // metadata for outgoing transfers so we can resume/retry if they fail
    private final Map<String, TransferInfo> transferMeta = new ConcurrentHashMap<>();
    private ConnectionManager connectionManager;

    public interface TransferListener {
        void onTransferStarted(String transferId, String fileName);

        void onTransferProgress(String transferId, long bytesTransferred, long totalBytes);

        void onTransferCompleted(String transferId);

        void onTransferFailed(String transferId, String error);
    }

    public interface IncomingTransferListener {
        /**
         * Called when incoming transfer is detected (after receiving handshake with sender's username).
         * Callback should return true to accept or false to reject.
         */
        boolean onIncomingTransfer(String transferId, String senderUsername, Socket socket);
    }

    private final TransferListener listener;
    private IncomingTransferListener incomingTransferListener;

    public TransferManager(TransferListener listener) {
        this.listener = listener;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void setIncomingTransferListener(IncomingTransferListener listener) {
        this.incomingTransferListener = listener;
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Kirim file/folder ke device target
     */
    public void sendFile(File inputPath, Device targetDevice, String username) {
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

                // 5. Store transfer meta and create sender task
                transferMeta.put(transferId, new TransferInfo(fileToSend, targetDevice, username));

                SenderTask task = new SenderTask(fileToSend, socket, transferId, listener,
                    username);
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
            System.out.println("[TransferManager] Executing PAUSE on task...");
            task.pause();
        }
    }

    /**
     * Resume transfer
     */
    public void resumeTransfer(String transferId) {
        TransferTask task = activeTasks.get(transferId);

        if (task != null) {
            // if task exists and thread is alive, just resume
            System.out.println("[TransferManager] Executing RESUME on task...");
            task.resume();
            return;
        }

        // If there is no active task (e.g., previous failed), try to re-create sender using saved metadata
        TransferInfo info = transferMeta.get(transferId);
        if (info == null) {
            System.err.println("[TransferManager] No transfer metadata found for resume: " + transferId);
            return;
        }

        executorService.submit(() -> {
            try {
                Socket socket = connectionManager.connectTo(info.device.getIpAddress());
                SenderTask newTask = new SenderTask(info.file, socket, transferId, listener, info.username);
                activeTasks.put(transferId, newTask);
                newTask.start();
                System.out.println("[TransferManager] Resuming transfer: " + transferId);
            } catch (IOException e) {
                System.err.println("[TransferManager] Failed to resume transfer: " + e.getMessage());
                if (listener != null) listener.onTransferFailed(transferId, "Resume failed: " + e.getMessage());
            }
        });
    }

    /**
     * Cancel transfer
     */
    public void cancelTransfer(String transferId) {
        TransferTask task = activeTasks.get(transferId);
        if (task != null) {
            System.out.println("[TransferManager] Executing CANCEL on task...");
            task.cancel();
            activeTasks.remove(transferId);
            transferMeta.remove(transferId);
        } else {
             System.err.println("[TransferManager] Cannot CANCEL: Task not found for ID " + transferId);
        }
    }

    /**
     * Generate unique transfer ID
     */
    private String generateTransferId(File file, Device device) {
        return System.currentTimeMillis() + "_" + file.getName() + "_" + device.getIpAddress();
    }

    private static class TransferInfo {
        final File file;
        final Device device;
        final String username;

        TransferInfo(File file, Device device, String username) {
            this.file = file;
            this.device = device;
            this.username = username;
        }
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
                ReceiverTask task = new ReceiverTask(clientSocket, savePath, transferId, listener, incomingTransferListener);
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
