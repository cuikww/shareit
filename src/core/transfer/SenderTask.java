package core.transfer;

import core.transfer.TransferManager.TransferListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;

public class SenderTask extends TransferTask {

    private final File file;
    private final String transferId;
    private final TransferListener listener;

    // Protokol Constants (Definisikan di sini atau buat class Protocol terpisah)
    private static final int OP_REQ_SEND = 1;
    private static final int OP_RESUME = 2; // Sesuai diagram: response OP_RESUME

    public SenderTask(File file, Socket socket, String transferId, TransferListener listener) {
        super(socket); // Pass socket ke parent class TransferTask
        this.file = file;
        this.transferId = transferId;
        this.listener = listener;
    }

    @Override
    public void run() {
        try (
            // Menggunakan RandomAccessFile untuk fitur Seek (Lompat)
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            
            // --- BAGIAN 1: HANDSHAKE (Sesuai Diagram) ---
            System.out.println("[SenderTask] Sending header for: " + file.getName());
            
            // 1. Kirim Header: OpCode, Nama File, Ukuran File
            out.writeInt(OP_REQ_SEND);
            out.writeUTF(file.getName());
            out.writeLong(file.length());
            out.flush();

            // 2. Baca Respon dari Penerima
            int responseType = in.readInt();
            long offset = 0;

            // 3. Cek apakah Penerima minta RESUME?
            if (responseType == OP_RESUME) {
                offset = in.readLong(); // Baca offset (misal: 5000 byte yang sudah ada)
                System.out.println("[SenderTask] Resuming transfer from byte: " + offset);
            }

            // --- BAGIAN 2: LOGIKA RESUME ---
            // "Task -> RAF : seek(5000)"
            if (offset > 0) {
                raf.seek(offset); 
            }

            // Update UI bahwa transfer dimulai (atau dilanjutkan)
            if (listener != null) {
                // listener.onTransferStarted dipanggil di Manager, tapi update progress awal penting
                listener.onTransferProgress(transferId, offset, file.length());
            }

            // --- BAGIAN 3: SENDING LOOP ---
            byte[] buffer = new byte[8192]; // 8KB Buffer
            int bytesRead;
            long totalSent = offset;

            while ((bytesRead = raf.read(buffer)) != -1) {
                
                // 1. Cek Pause/Cancel (Fitur dari Parent Class TransferTask)
                // Ini akan memblokir thread jika isPaused = true (wait)
                // Atau melempar exception jika isCancelled = true
                checkPaused(); 
                checkCancelled();

                // 2. Kirim Data ke Network
                out.write(buffer, 0, bytesRead);
                
                // 3. Update Progress ke UI
                totalSent += bytesRead;
                if (listener != null) {
                    listener.onTransferProgress(transferId, totalSent, file.length());
                }
            }

            out.flush();
            System.out.println("[SenderTask] Transfer completed.");
            
            if (listener != null) {
                listener.onTransferCompleted(transferId);
            }

        } catch (InterruptedException e) {
            System.out.println("[SenderTask] Transfer cancelled/interrupted.");
            if (listener != null) listener.onTransferFailed(transferId, "Cancelled");
            
        } catch (IOException e) {
            System.err.println("[SenderTask] Error: " + e.getMessage());
            if (listener != null) {
                listener.onTransferFailed(transferId, e.getMessage());
            }
        } finally {
            // Tutup socket setelah selesai
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
}