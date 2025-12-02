package core.transfer;

import core.transfer.TransferManager.TransferListener;
import core.utils.FileUtils;
import core.utils.Protocol;
import core.utils.Protocol.*;

import java.io.*;
import java.net.Socket;

/**
 * Task untuk menerima file via TCP socket dengan Protocol.java
 * 
 * Protocol Flow:
 * 1. Receive HANDSHAKE (username|version)
 * 2. Send ACK
 * 3. Receive FILE_INFO (filename|filesize|md5)
 * 4. Send ACK
 * 5. Receive DATA chunks (8KB) dan save to file
 * 6. Receive COMPLETE
 * 7. Verify MD5 checksum
 * 8. Send final ACK
 */
public class ReceiverTask extends TransferTask {

    private final String savePath;
    private final String transferId;
    private final TransferListener listener;
    private final TransferManager.IncomingTransferListener incomingTransferListener;
    
    // Progress tracking
    private long totalBytes;
    private long bytesReceived = 0;
    private File receivedFile;
    private FileOutputStream fileOut;

    public ReceiverTask(Socket socket, String savePath, String transferId, TransferListener listener, 
                        TransferManager.IncomingTransferListener incomingTransferListener) {
        super(socket);
        this.savePath = savePath;
        this.transferId = transferId;
        this.listener = listener;
        this.incomingTransferListener = incomingTransferListener;
    }

    @Override
    public void run() {
        InputStream in = null;
        OutputStream out = null;
        
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // Set socket read timeout so we can detect missing peers faster instead of blocking forever
            try {
                socket.setSoTimeout(30_000); // 30 seconds
            } catch (Exception ignored) { }
            
            log("ReceiverTask started, listening for incoming file...");
            
            // Step 1: Receive HANDSHAKE
            Message msg = Protocol.readMessage(in);
            if (msg.operation != Protocol.OP_HANDSHAKE) {
                throw new IOException("Expected HANDSHAKE, got " + 
                    Protocol.getOperationName(msg.operation));
            }
            
            HandshakeMessage handshake = HandshakeMessage.fromBytes(msg.payload);
            log("Received HANDSHAKE from " + handshake.username + " (version " + handshake.version + ")");
            
            // Check if we should ask user to confirm this incoming transfer
            if (incomingTransferListener != null) {
                boolean accepted = incomingTransferListener.onIncomingTransfer(transferId, handshake.username, socket);
                if (!accepted) {
                    log("Incoming transfer rejected by user");
                    socket.close();
                    return;
                }
            }
            
            // Step 2: Send ACK
            Message ack = new Message(Protocol.OP_ACK, transferId.getBytes());
            Protocol.writeMessage(out, ack);
            
            // Step 3: Receive FILE_INFO
            msg = Protocol.readMessage(in);
            if (msg.operation != Protocol.OP_FILE_INFO) {
                throw new IOException("Expected FILE_INFO, got " + 
                    Protocol.getOperationName(msg.operation));
            }
            
            FileInfoMessage fileInfo = FileInfoMessage.fromBytes(msg.payload);
            log("Received FILE_INFO: " + fileInfo);
            
            totalBytes = fileInfo.fileSize;
            
            // Notify listener
            if (listener != null) {
                listener.onTransferStarted(transferId, fileInfo.filename);
            }
            
            // Step 4: Send ACK. If a partial file already exists locally, include bytesReceived to allow resume.
            long existingBytes = 0;
            File possibleFile = new File(savePath, fileInfo.filename);
            if (possibleFile.exists()) {
                existingBytes = possibleFile.length();
                // Cap existingBytes to totalBytes
                if (existingBytes > totalBytes) existingBytes = totalBytes;
            }

            AckMessage ackMsgObj = new AckMessage(transferId, existingBytes);
            ack = new Message(Protocol.OP_ACK, ackMsgObj.toBytes());
            Protocol.writeMessage(out, ack);
            
            // Create output file
            receivedFile = new File(savePath, fileInfo.filename);
            
            // Ensure parent directory exists
            if (!receivedFile.getParentFile().exists()) {
                receivedFile.getParentFile().mkdirs();
            }
            
            log("Saving file to: " + receivedFile.getAbsolutePath());
            // If there is an existing partial file, open in append mode and continue
            if (existingBytes > 0 && receivedFile.exists()) {
                log("Resuming existing file at offset: " + existingBytes);
                fileOut = new FileOutputStream(receivedFile, true);
                bytesReceived = existingBytes;
            } else {
                fileOut = new FileOutputStream(receivedFile);
                bytesReceived = 0;
            }
            
            // Step 5: Receive DATA chunks
            // Keep reading until we receive an explicit COMPLETE message from sender.
            // Previously the code stopped when bytesReceived >= totalBytes which could leave
            // the trailing COMPLETE message in the stream and cause protocol misalignment.
            log("Waiting for data...");
            while (true) {
                // Check pause/cancel
                checkPaused();
                checkCancelled();
                
                msg = Protocol.readMessage(in);
                
                if (msg.operation == Protocol.OP_COMPLETE) {
                    log("Received COMPLETE message");

                    // If we get COMPLETE before receiving the expected number of bytes, that's an error
                    if (bytesReceived != totalBytes) {
                        throw new IOException("Received COMPLETE before all data was received (" + bytesReceived + "/" + totalBytes + ")");
                    }

                    break;
                }
                
                if (msg.operation != Protocol.OP_DATA) {
                    throw new IOException("Expected DATA or COMPLETE, got " + 
                        Protocol.getOperationName(msg.operation));
                }
                
                // Write chunk to file
                fileOut.write(msg.payload);
                bytesReceived += msg.payload.length;
                
                // Notify progress
                notifyProgress();
            }
            
            // Close file output
            if (fileOut != null) {
                fileOut.close();
                fileOut = null;
            }
            
            // Step 6: Verify MD5 checksum
            log("Verifying MD5 checksum...");
            String receivedMD5 = FileUtils.getMD5(receivedFile);
            
            if (!receivedMD5.equals(fileInfo.md5Hash)) {
                throw new IOException("MD5 checksum mismatch! Expected: " + fileInfo.md5Hash + 
                    ", Got: " + receivedMD5);
            }
            
            log("MD5 checksum verified successfully!");
            
            // Step 7: Send final ACK
            ack = new Message(Protocol.OP_ACK, transferId.getBytes());
            Protocol.writeMessage(out, ack);
            
            log("Transfer completed successfully! File: " + receivedFile.getAbsolutePath() + 
                " (" + FileUtils.formatFileSize(receivedFile.length()) + ")");
            
            if (listener != null) {
                listener.onTransferCompleted(transferId);
            }
            
        } catch (InterruptedException e) {
            log("Transfer interrupted: " + e.getMessage());
            if (listener != null) {
                listener.onTransferFailed(transferId, "Interrupted");
            }
            deleteReceivedFile();
        } catch (Exception e) {
            log("Error during transfer: " + e.getMessage());
            e.printStackTrace();
            
            // Try to send error message
            try {
                if (out != null) {
                    ErrorMessage errorMsg = new ErrorMessage(
                        Protocol.ErrorCode.UNKNOWN_ERROR, 
                        e.getMessage()
                    );
                    Message errMsg = new Message(Protocol.OP_ERROR, errorMsg.toBytes());
                    Protocol.writeMessage(out, errMsg);
                }
            } catch (Exception ignored) {}
            
            if (listener != null) {
                listener.onTransferFailed(transferId, e.getMessage());
            }
            deleteReceivedFile();
        } finally {
            // Cleanup
            try {
                if (fileOut != null) fileOut.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    // ==================== Helper Methods ====================
    
    private void notifyProgress() {
        if (listener != null) {
            listener.onTransferProgress(transferId, bytesReceived, totalBytes);
        }
    }
    
    private void deleteReceivedFile() {
        if (receivedFile != null && receivedFile.exists()) {
            if (receivedFile.delete()) {
                log("Incomplete file deleted: " + receivedFile.getAbsolutePath());
            }
        }
    }
    
    private void log(String message) {
        System.out.println("[ReceiverTask] " + message);
    }
}
