package core.transfer;

import core.network.Device;
import core.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferManager {

    private final ExecutorService executorService;
    private final Map<String, TransferTask> activeTasks = new ConcurrentHashMap<>();

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

    /**
     * Kirim file/folder ke device target
     */
    public void sendFile(File inputPath, Device targetDevice) {
        executorService.submit(() -> {
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

                // 4. Create sender task (akan diimplementasi di phase berikutnya)
                // SenderTask task = new SenderTask(fileToSend, targetDevice, listener);
                // activeTasks.put(transferId, task);
                // task.start();

                // Sementara ini placeholder
                System.out.println("[TransferManager] Transfer task created: " + transferId);
                System.out.println("[TransferManager] TODO: Implement SenderTask untuk actual transfer");

            } catch (IOException e) {
                System.err.println("[TransferManager] Error preparing file: " + e.getMessage());
                if (listener != null) {
                    listener.onTransferFailed("unknown", e.getMessage());
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
     * Shutdown manager
     */
    public void shutdown() {
        executorService.shutdown();
        activeTasks.values().forEach(TransferTask::cancel);
    }
}
