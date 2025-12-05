package core.transfer;

import core.utils.FileUtils;
import core.utils.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class SenderTask extends TransferTask {

    private final File file;
    private final String transferId;
    private final TransferManager.TransferListener listener;
    private final String username;
    private String remotePeerName = "Unknown";

    private long totalBytes;
    private long bytesTransferred = 0;
     private long lastBytesNotify = 0; // Tambahkan variabel ini di class level jika belum ada
    
    // Variabel untuk membatasi update UI (Throttling)
    private long lastNotifyTime = 0;

    public SenderTask(File file, Socket socket, String transferId, TransferManager.TransferListener listener, String username) {
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
        FileInputStream fis = null;

        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            try { socket.setSoTimeout(30_000); } catch (Exception ignored) {}

            log("Starting file transfer: " + file.getName() + " -> " + remotePeerName);

            totalBytes = file.length();

            // 1. Handshake
            Protocol.HandshakeMessage hs = new Protocol.HandshakeMessage(username, "1.0");
            Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_HANDSHAKE, hs.toBytes()));

            Protocol.Message resp = Protocol.readMessage(in);
            if (resp.operation != Protocol.OP_ACK) throw new IOException("Expected ACK after HANDSHAKE");

            // 2. File Info
            String md5 = FileUtils.getMD5(file);
            Protocol.FileInfoMessage fi = new Protocol.FileInfoMessage(file.getName(), totalBytes, md5);
            Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_FILE_INFO, fi.toBytes()));

            resp = Protocol.readMessage(in);
            if (resp.operation != Protocol.OP_ACK) throw new IOException("Expected ACK after FILE_INFO");

            // 3. Resume Check
            long resumeOffset = 0;
            try { 
                resumeOffset = Protocol.AckMessage.fromBytes(resp.payload).bytesReceived; 
            } catch (Exception ignored) {}

            fis = new FileInputStream(file);
            if (resumeOffset > 0) {
                long skipped = skipFully(fis, resumeOffset);
                if (skipped != resumeOffset) throw new IOException("Unable to skip: " + resumeOffset);
                bytesTransferred = resumeOffset;
            } else {
                bytesTransferred = 0;
            }
            
            // Update awal (0%)
            notifyProgress(true); 

            // 4. Stream Data
            byte[] buf = new byte[Protocol.BUFFER_SIZE];
            int r;
            
            while ((r = fis.read(buf)) != -1) {
                checkPaused(); 
                checkCancelled();
                
                byte[] chunk = Arrays.copyOf(buf, r);
                Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_DATA, chunk));
                
                bytesTransferred += r;
                
                // --- PERBAIKAN UTAMA DI SINI (Throttling) ---
                // Hanya update UI jika sudah lewat 200ms
                notifyProgress(false); 
            }

            // Update terakhir (100%) sebelum complete
            notifyProgress(true);

            // 5. Complete
            Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_COMPLETE, transferId.getBytes()));

            resp = Protocol.readMessage(in);
            if (resp.operation != Protocol.OP_ACK) throw new IOException("Expected ACK after COMPLETE");

            log("Transfer completed: " + transferId);
            if (listener != null) listener.onTransferCompleted(transferId);

        } catch (InterruptedException ie) {
            log("Cancelled by user.");
            if (listener != null) listener.onTransferFailed(transferId, "Cancelled by user");
        } catch (Exception ex) {
            log("Error: " + ex.getMessage());
            if (listener != null) listener.onTransferFailed(transferId, ex.getMessage());
        } finally {
            try { if (fis != null) fis.close(); } catch (IOException ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }
    }

    private long skipFully(InputStream in, long toSkip) throws IOException {
        long remaining = toSkip;
        while (remaining > 0) {
            long s = in.skip(remaining);
            if (s <= 0) {
                if (in.read() == -1) break;
                s = 1;
            }
            remaining -= s;
        }
        return toSkip - remaining;
    }

    /**
     * Update progress dengan batasan waktu (Throttling) agar UI tidak macet.
     * @param force Jika true, paksa update (misal saat awal mulai atau selesai).
     */


    private void notifyProgress(boolean force) {
        long now = System.currentTimeMillis();
        long deltaBytes = bytesTransferred - lastBytesNotify;
        
        // Hitung persentase kenaikan sejak update terakhir
        boolean significantProgress = false;
        if (totalBytes > 0) {
            double percentDelta = (double) deltaBytes / totalBytes * 100.0;
            if (percentDelta >= 1.0) significantProgress = true; // Update setiap naik 1%
        }

        // Syarat update:
        // 1. Force (awal/akhir)
        // 2. Waktu > 50ms (agar terlihat realtime tapi tidak bikin lag)
        // 3. Progress naik signifikan (> 1%) agar tidak terlihat patah-patah pada file besar
        if (force || (now - lastNotifyTime > 50) || significantProgress) {
            if (listener != null) {
                listener.onTransferProgress(transferId, bytesTransferred, totalBytes);
            }
            lastNotifyTime = now;
            lastBytesNotify = bytesTransferred;
        }
    }

    private void log(String msg) { System.out.println("[SenderTask] " + msg); }
}