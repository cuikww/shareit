package core.transfer;

import core.transfer.TransferManager.TransferListener;
import core.utils.FileUtils;
import core.utils.Protocol;
import core.utils.Protocol.*;

import java.io.*;
import java.net.Socket;

public class ReceiverTask extends TransferTask {

    private final String savePath;
    private final String transferId;
    private final TransferListener listener;
    private final TransferManager.IncomingTransferListener incomingTransferListener;
    
    private long totalBytes;
    private long bytesReceived = 0;
    private File receivedFile;
    private FileOutputStream fileOut;
    
    // Variabel Throttling
    private long lastNotifyTime = 0;
    private long lastBytesNotify = 0; // Tambahkan variabel ini di class level


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
            try { socket.setSoTimeout(30_000); } catch (Exception ignored) { }
            
            // 1. Handshake
            Message msg = Protocol.readMessage(in);
            if (msg.operation != Protocol.OP_HANDSHAKE) throw new IOException("Expected HANDSHAKE");
            HandshakeMessage handshake = HandshakeMessage.fromBytes(msg.payload);
            
            if (incomingTransferListener != null) {
                boolean accepted = incomingTransferListener.onIncomingTransfer(transferId, handshake.username, socket);
                if (!accepted) {
                    socket.close();
                    return;
                }
            }
            
            // 2. ACK Handshake
            Protocol.writeMessage(out, new Message(Protocol.OP_ACK, transferId.getBytes()));
            
            // 3. File Info
            msg = Protocol.readMessage(in);
            if (msg.operation != Protocol.OP_FILE_INFO) throw new IOException("Expected FILE_INFO");
            FileInfoMessage fileInfo = FileInfoMessage.fromBytes(msg.payload);
            totalBytes = fileInfo.fileSize;
            
            if (listener != null) listener.onTransferStarted(transferId, fileInfo.filename);
            
            // 4. Check Resume
            long existingBytes = 0;
            File possibleFile = new File(savePath, fileInfo.filename);
            if (possibleFile.exists()) {
                existingBytes = possibleFile.length();
                if (existingBytes > totalBytes) existingBytes = 0;
            }
            
            Protocol.writeMessage(out, new Message(Protocol.OP_ACK, new AckMessage(transferId, existingBytes).toBytes()));
            
            receivedFile = new File(savePath, fileInfo.filename);
            if (!receivedFile.getParentFile().exists()) receivedFile.getParentFile().mkdirs();
            
            if (existingBytes > 0 && receivedFile.exists()) {
                fileOut = new FileOutputStream(receivedFile, true);
                bytesReceived = existingBytes;
            } else {
                fileOut = new FileOutputStream(receivedFile);
                bytesReceived = 0;
            }
            
            // Update awal
            notifyProgress(true);

            // 5. Receive Data Loop
            while (true) {
                checkPaused();
                checkCancelled();
                
                msg = Protocol.readMessage(in);
                
                if (msg.operation == Protocol.OP_COMPLETE) {
                    break;
                }
                
                if (msg.operation != Protocol.OP_DATA) throw new IOException("Expected DATA");
                
                fileOut.write(msg.payload);
                bytesReceived += msg.payload.length;
                
                // --- THROTTLING DI SINI ---
                notifyProgress(false);
            }
            
            // Update akhir sebelum verify
            notifyProgress(true);

            if (fileOut != null) { fileOut.close(); fileOut = null; }
            
            // 6. Verify MD5
            String receivedMD5 = FileUtils.getMD5(receivedFile);
            if (!receivedMD5.equalsIgnoreCase(fileInfo.md5Hash)) throw new IOException("MD5 mismatch");
            
            // 7. Final ACK
            Protocol.writeMessage(out, new Message(Protocol.OP_ACK, transferId.getBytes()));
            
            if (listener != null) listener.onTransferCompleted(transferId);
            
        } catch (InterruptedException e) {
            if (listener != null) listener.onTransferFailed(transferId, "Cancelled");
        } catch (Exception e) {
            e.printStackTrace();
            try { if (out != null) Protocol.writeMessage(out, new Message(Protocol.OP_ERROR, new ErrorMessage(99, e.getMessage()).toBytes())); } catch (Exception ignored) {}
            if (listener != null) listener.onTransferFailed(transferId, e.getMessage());
        } finally {
            try { if (fileOut != null) fileOut.close(); if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }
    }
    
    private void notifyProgress(boolean force) {
        long now = System.currentTimeMillis();
        long deltaBytes = bytesReceived - lastBytesNotify;

        // Hitung persentase kenaikan
        boolean significantProgress = false;
        if (totalBytes > 0) {
            double percentDelta = (double) deltaBytes / totalBytes * 100.0;
            if (percentDelta >= 1.0) significantProgress = true;
        }

        // Update jika: Force OR (Waktu > 50ms) OR (Naik > 1%)
        if (force || (now - lastNotifyTime > 50) || significantProgress) {
            if (listener != null) {
                listener.onTransferProgress(transferId, bytesReceived, totalBytes);
            }
            lastNotifyTime = now;
            lastBytesNotify = bytesReceived;
        }
    }
}