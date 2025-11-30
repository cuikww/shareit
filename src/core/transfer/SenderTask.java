package core.transfer;

import core.transfer.TransferManager.TransferListener;
import core.utils.FileUtils;
import core.utils.Protocol;
import core.utils.Protocol.*;

import java.io.*;
import java.net.Socket;

/**
 * Task untuk mengirim file via TCP socket dengan Protocol.java
 * 
 * Protocol Flow:
 * 1. Send HANDSHAKE (username|version)
 * 2. Wait ACK
 * 3. Send FILE_INFO (filename|filesize|md5)
 * 4. Wait ACK
 * 5. Send DATA chunks (8KB)
 * 6. Send COMPLETE
 * 7. Wait ACK
 */
public class SenderTask extends TransferTask {

    private final File file;
    private final String transferId;
    private final TransferListener listener;
    private final String username;

    // Progress tracking
    private long totalBytes;
    private long bytesTransferred = 0;
    private String remotePeerName = "Unknown";

    public SenderTask(File file, Socket socket, String transferId, TransferListener listener, String username) {
        super(socket);
        this.file = file;
        this.transferId = transferId;
        this.listener = listener;
        this.username = username;
    }

    public void setRemotePeerName(String name) {
        this.remotePeerName = name;
    }

    @Override
    public void run() {
        InputStream in = null;
        OutputStream out = null;
        FileInputStream fileIn = null;

        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            log("Starting file transfer: " + file.getName() + " to " + remotePeerName);
            totalBytes = file.length();

            // Step 1: Send HANDSHAKE
            log("Sending HANDSHAKE...");
            HandshakeMessage handshake = new HandshakeMessage(username, "1.0");
            Message msg = new Message(Protocol.OP_HANDSHAKE, handshake.toBytes());
            Protocol.writeMessage(out, msg);

            // Step 2: Wait for ACK
            Message ackMsg = Protocol.readMessage(in);
            if (ackMsg.operation != Protocol.OP_ACK) {
                throw new IOException("Expected ACK after HANDSHAKE, got " +
                        Protocol.getOperationName(ackMsg.operation));
            }
            log("Received ACK for HANDSHAKE");

            // Step 3: Send FILE_INFO
            log("Sending FILE_INFO: " + file.getName() + " (" +
                    FileUtils.formatFileSize(totalBytes) + ")");
            String md5 = FileUtils.getMD5(file);
            FileInfoMessage fileInfo = new FileInfoMessage(file.getName(), totalBytes, md5);
            msg = new Message(Protocol.OP_FILE_INFO, fileInfo.toBytes());
            Protocol.writeMessage(out, msg);

            // Step 4: Wait for ACK
            ackMsg = Protocol.readMessage(in);
            if (ackMsg.operation != Protocol.OP_ACK) {
                throw new IOException("Expected ACK after FILE_INFO, got " +
                        Protocol.getOperationName(ackMsg.operation));
            }
            log("Received ACK for FILE_INFO");

            // Step 5: Send FILE DATA in chunks
            fileIn = new FileInputStream(file);
            byte[] buffer = new byte[Protocol.BUFFER_SIZE];
            int bytesRead;

            log("Sending file data...");
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                // Check pause/cancel
                checkPaused();
                checkCancelled();

                // Send data chunk
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                msg = new Message(Protocol.OP_DATA, chunk);
                Protocol.writeMessage(out, msg);

                // Update progress
                bytesTransferred += bytesRead;
                notifyProgress();
            }

            log("All data sent. Sending COMPLETE...");

            // Step 6: Send COMPLETE
            msg = new Message(Protocol.OP_COMPLETE, transferId.getBytes());
            Protocol.writeMessage(out, msg);

            // Step 7: Wait for final ACK
            ackMsg = Protocol.readMessage(in);
            if (ackMsg.operation != Protocol.OP_ACK) {
                throw new IOException("Expected ACK after COMPLETE, got " +
                        Protocol.getOperationName(ackMsg.operation));
            }

            log("Transfer completed successfully!");
            if (listener != null) {
                listener.onTransferCompleted(transferId);
            }

        } catch (InterruptedException e) {
            log("Transfer interrupted: " + e.getMessage());
            if (listener != null) {
                listener.onTransferFailed(transferId, "Interrupted");
            }
        } catch (Exception e) {
            log("Error during transfer: " + e.getMessage());
            e.printStackTrace();

            // Try to send error message
            try {
                if (out != null) {
                    ErrorMessage errorMsg = new ErrorMessage(
                            Protocol.ErrorCode.UNKNOWN_ERROR,
                            e.getMessage());
                    Message errMsg = new Message(Protocol.OP_ERROR, errorMsg.toBytes());
                    Protocol.writeMessage(out, errMsg);
                }
            } catch (Exception ignored) {
            }

            if (listener != null) {
                listener.onTransferFailed(transferId, e.getMessage());
            }
        } finally {
            // Cleanup
            try {
                if (fileIn != null)
                    fileIn.close();
                if (socket != null && !socket.isClosed())
                    socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ==================== Helper Methods ====================

    private void notifyProgress() {
        if (listener != null) {
            listener.onTransferProgress(transferId, bytesTransferred, totalBytes);
        }
    }

    private void log(String message) {
        System.out.println("[SenderTask] " + message);
    }
}