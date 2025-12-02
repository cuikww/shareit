package core.transfer;

import core.network.ConnectionManager;
import core.utils.FileUtils;
import core.utils.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

/**
 * SenderTask — reliable sender that supports resume and optional automatic reconnect/retry.
 */
public class SenderTask extends TransferTask {

    private final File file;
    private final String transferId;
    private final TransferManager.TransferListener listener;
    private final String username;

    // optional reconnect support
    private final ConnectionManager connectionManager; // may be null
    private final String targetIp; // null if no reconnect supported
    private final int maxRetries; // 0 means no automatic reconnect
    private final int baseRetryDelayMs = 2000;

    // progress
    private long totalBytes;
    private long bytesTransferred = 0;
    private String remotePeerName = "Unknown";

    // Legacy constructor (no reconnect)
    public SenderTask(File file, Socket socket, String transferId, TransferManager.TransferListener listener, String username) {
        this(file, socket, transferId, listener, username, null, null, 0);
    }

    // New constructor with optional reconnect retry parameters
    public SenderTask(File file, Socket socket, String transferId, TransferManager.TransferListener listener, String username,
                      ConnectionManager connectionManager, String targetIp, int maxRetries) {
        super(socket);
        this.file = file;
        this.transferId = transferId;
        this.listener = listener;
        this.username = username;
        this.connectionManager = connectionManager;
        this.targetIp = targetIp;
        this.maxRetries = Math.max(0, maxRetries);
    }

    public void setRemotePeerName(String name) { this.remotePeerName = name; }

    @Override
    public void run() {
        int attempt = 0;

        while (true) {
            InputStream in = null;
            OutputStream out = null;
            FileInputStream fis = null;

            try {
                attempt++;

                in = socket.getInputStream();
                out = socket.getOutputStream();

                try { socket.setSoTimeout(30_000); } catch (Exception ignored) {}

                log("Starting file transfer: " + file.getName() + " -> " + remotePeerName + " (attempt=" + attempt + ")");

                totalBytes = file.length();

                // Step 1: handshake
                Protocol.HandshakeMessage hs = new Protocol.HandshakeMessage(username, "1.0");
                Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_HANDSHAKE, hs.toBytes()));

                Protocol.Message resp = Protocol.readMessage(in);
                if (resp.operation != Protocol.OP_ACK) throw new IOException("Expected ACK after HANDSHAKE");

                // Step 2: send file info
                String md5 = FileUtils.getMD5(file);
                Protocol.FileInfoMessage fi = new Protocol.FileInfoMessage(file.getName(), totalBytes, md5);
                Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_FILE_INFO, fi.toBytes()));

                resp = Protocol.readMessage(in);
                if (resp.operation != Protocol.OP_ACK) throw new IOException("Expected ACK after FILE_INFO");

                long resumeOffset = 0;
                try { resumeOffset = Protocol.AckMessage.fromBytes(resp.payload).bytesReceived; } catch (Exception ignored) {}

                log("Receiver reports bytesReceived=" + resumeOffset);

                // Step 3: open file and skip to resume offset
                fis = new FileInputStream(file);
                if (resumeOffset > 0) {
                    long skipped = skipFully(fis, resumeOffset);
                    if (skipped != resumeOffset) throw new IOException("Unable to skip to resume offset: " + resumeOffset);
                    bytesTransferred = resumeOffset;
                    notifyProgress();
                } else {
                    bytesTransferred = 0;
                    notifyProgress();
                }

                // Step 4: stream data
                byte[] buf = new byte[Protocol.BUFFER_SIZE];
                int r;
                while ((r = fis.read(buf)) != -1) {
                    checkPaused(); checkCancelled();
                    byte[] chunk = Arrays.copyOf(buf, r);
                    Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_DATA, chunk));
                    bytesTransferred += r;
                    notifyProgress();
                }

                // Step 5: complete
                Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_COMPLETE, transferId.getBytes()));

                resp = Protocol.readMessage(in);
                if (resp.operation != Protocol.OP_ACK) throw new IOException("Expected ACK after COMPLETE, got " + Protocol.getOperationName(resp.operation));

                log("Transfer completed: " + transferId);
                if (listener != null) listener.onTransferCompleted(transferId);
                return; // success

            } catch (InterruptedException ie) {
                log("Transfer interrupted: " + ie.getMessage());
                if (listener != null) listener.onTransferFailed(transferId, "Interrupted");
                return;

            } catch (Exception ex) {
                log("Transfer error on attempt " + attempt + ": " + ex.getMessage());
                ex.printStackTrace();

                // if reconnect is not configured or we've exhausted retries -> fail
                if (connectionManager == null || targetIp == null || attempt >= Math.max(1, maxRetries)) {
                    // try to notify remote
                    try { if (out != null) {
                        Protocol.ErrorMessage err = new Protocol.ErrorMessage(Protocol.ErrorCode.UNKNOWN_ERROR, ex.getMessage());
                        Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_ERROR, err.toBytes()));
                    }} catch (Exception ignored) {}

                    if (listener != null) listener.onTransferFailed(transferId, ex.getMessage());
                    return;
                }

                // otherwise attempt reconnect + resume
                log("Attempting reconnect+resume (attempt " + attempt + ")");

                // close resources and backoff
                try { if (fis != null) { fis.close(); fis = null; } } catch (Exception ignored) {}
                try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}

                long backoff = baseRetryDelayMs * (1L << Math.max(0, attempt-1));
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { if (listener != null) listener.onTransferFailed(transferId, "Interrupted during backoff"); return; }

                try {
                    Socket newSock = connectionManager.connectTo(targetIp);
                    this.socket = newSock; // will loop and attempt again
                    log("Reconnected, will resume");
                    continue;
                } catch (Exception connEx) {
                    log("Reconnect failed: " + connEx.getMessage());
                    if (attempt >= maxRetries) {
                        if (listener != null) listener.onTransferFailed(transferId, "Reconnect failed: " + connEx.getMessage());
                        return;
                    }
                    // else, allow next loop iteration
                    continue;
                }

            } finally {
                try { if (fis != null) fis.close(); } catch (IOException ignored) {}
            }
        }
    }

    private long skipFully(InputStream in, long toSkip) throws IOException {
        long remaining = toSkip;
        while (remaining > 0) {
            long s = in.skip(remaining);
            if (s <= 0) {
                int b = in.read();
                if (b == -1) return toSkip - remaining;
                s = 1;
            }
            remaining -= s;
        }
        return toSkip;
    }

    private void notifyProgress() {
        if (listener != null) listener.onTransferProgress(transferId, bytesTransferred, totalBytes);
    }

    private void log(String msg) { System.out.println("[SenderTask] " + msg); }
}