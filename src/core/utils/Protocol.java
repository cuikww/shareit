package core.utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Protocol definition untuk file transfer di ShareIt
 * 
 * Message Format:
 * [1 byte: Operation] [4 bytes: Payload length] [Payload]
 */
public class Protocol {
    // ==================== Operation Types ====================
    public static final byte OP_HANDSHAKE = 0x01; // Initial handshake
    public static final byte OP_FILE_INFO = 0x02; // File metadata
    public static final byte OP_DATA = 0x03; // File data chunk
    public static final byte OP_ACK = 0x04; // Acknowledgement
    public static final byte OP_PAUSE = 0x05; // Pause transfer
    public static final byte OP_RESUME = 0x06; // Resume transfer
    public static final byte OP_CANCEL = 0x07; // Cancel transfer
    public static final byte OP_COMPLETE = 0x08; // Transfer complete
    public static final byte OP_ERROR = 0x09; // Error occurred

    // ==================== Constants ====================
    public static final int BUFFER_SIZE = 8192; // 8KB chunks
    public static final int HEADER_SIZE = 5; // 1 (op) + 4 (length)

    // ==================== Helper Classes ====================

    /**
     * Message untuk dikirim/diterima
     */
    public static class Message {
        public byte operation;
        public byte[] payload;

        public Message(byte operation, byte[] payload) {
            this.operation = operation;
            this.payload = payload != null ? payload : new byte[0];
        }

        @Override
        public String toString() {
            String opName = getOperationName(operation);
            return String.format("Message[%s, payloadLen=%d]", opName, payload.length);
        }
    }

    /**
     * Handshake message: USERNAME|VERSION
     */
    public static class HandshakeMessage {
        public String username;
        public String version;

        public HandshakeMessage(String username, String version) {
            this.username = username;
            this.version = version;
        }

        public byte[] toBytes() {
            String data = username + "|" + version;
            return data.getBytes();
        }

        public static HandshakeMessage fromBytes(byte[] data) {
            String str = new String(data);
            String[] parts = str.split("\\|");
            if (parts.length >= 2) {
                return new HandshakeMessage(parts[0], parts[1]);
            }
            return new HandshakeMessage(str, "1.0");
        }
    }

    /**
     * File info message: FILENAME|FILESIZE|MD5HASH
     * Contoh: "photo.jpg|1048576|5d41402abc4b2a76b9719d911017c592"
     */
    public static class FileInfoMessage {
        public String filename;
        public long fileSize;
        public String md5Hash;

        public FileInfoMessage(String filename, long fileSize, String md5Hash) {
            this.filename = filename;
            this.fileSize = fileSize;
            this.md5Hash = md5Hash;
        }

        public byte[] toBytes() {
            String data = filename + "|" + fileSize + "|" + md5Hash;
            return data.getBytes();
        }

        public static FileInfoMessage fromBytes(byte[] data) {
            String str = new String(data);
            String[] parts = str.split("\\|");
            if (parts.length >= 3) {
                return new FileInfoMessage(parts[0], Long.parseLong(parts[1]), parts[2]);
            }
            throw new IllegalArgumentException("Invalid FileInfoMessage format");
        }

        @Override
        public String toString() {
            return String.format("FileInfo[%s, %d bytes, md5=%s]",
                    filename, fileSize, md5Hash);
        }
    }

    /**
     * ACK message: TRANSFER_ID|BYTES_RECEIVED
     */
    public static class AckMessage {
        public String transferId;
        public long bytesReceived;

        public AckMessage(String transferId, long bytesReceived) {
            this.transferId = transferId;
            this.bytesReceived = bytesReceived;
        }

        public byte[] toBytes() {
            String data = transferId + "|" + bytesReceived;
            return data.getBytes();
        }

        public static AckMessage fromBytes(byte[] data) {
            String str = new String(data);
            String[] parts = str.split("\\|");
            if (parts.length >= 2) {
                return new AckMessage(parts[0], Long.parseLong(parts[1]));
            }
            return new AckMessage(str, 0);
        }
    }

    /**
     * Error message: ERROR_CODE|ERROR_MESSAGE
     */
    public static class ErrorMessage {
        public int errorCode;
        public String errorMessage;

        public ErrorMessage(int errorCode, String errorMessage) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public byte[] toBytes() {
            String data = errorCode + "|" + errorMessage;
            return data.getBytes();
        }

        public static ErrorMessage fromBytes(byte[] data) {
            String str = new String(data);
            String[] parts = str.split("\\|", 2); // Split max 2 parts
            if (parts.length >= 2) {
                return new ErrorMessage(Integer.parseInt(parts[0]), parts[1]);
            }
            return new ErrorMessage(1, str);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Baca message dari InputStream
     */
    public static Message readMessage(InputStream in) throws IOException {
        // Baca header (1 byte operation + 4 bytes length)
        byte[] header = new byte[HEADER_SIZE];
        int read = in.read(header);
        if (read != HEADER_SIZE) {
            throw new IOException("Premature end of stream");
        }

        byte operation = header[0];
        int payloadLength = ByteBuffer.wrap(header, 1, 4).getInt();

        if (payloadLength < 0 || payloadLength > 1024 * 1024 * 100) { // Max 100MB per message
            throw new IOException("Invalid payload length: " + payloadLength);
        }

        // Baca payload
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            read = in.read(payload);
            if (read != payloadLength) {
                throw new IOException("Incomplete payload read");
            }
        }

        return new Message(operation, payload);
    }

    /**
     * Tulis message ke OutputStream
     */
    public static void writeMessage(OutputStream out, Message message) throws IOException {
        // Siapkan header
        byte[] header = new byte[HEADER_SIZE];
        header[0] = message.operation;

        // Write payload length (big-endian)
        int payloadLength = message.payload.length;
        ByteBuffer.wrap(header, 1, 4).putInt(payloadLength);

        // Tulis header + payload
        out.write(header);
        if (payloadLength > 0) {
            out.write(message.payload);
        }
        out.flush();
    }

    /**
     * Get operation name untuk logging
     */
    public static String getOperationName(byte op) {
        switch (op) {
            case OP_HANDSHAKE:
                return "HANDSHAKE";
            case OP_FILE_INFO:
                return "FILE_INFO";
            case OP_DATA:
                return "DATA";
            case OP_ACK:
                return "ACK";
            case OP_PAUSE:
                return "PAUSE";
            case OP_RESUME:
                return "RESUME";
            case OP_CANCEL:
                return "CANCEL";
            case OP_COMPLETE:
                return "COMPLETE";
            case OP_ERROR:
                return "ERROR";
            default:
                return "UNKNOWN(0x" + String.format("%02X", op) + ")";
        }
    }

    // ==================== Error Codes ====================
    public static class ErrorCode {
        public static final int FILE_NOT_FOUND = 1001;
        public static final int INVALID_CHECKSUM = 1002;
        public static final int TRANSFER_CANCELLED = 1003;
        public static final int DISK_FULL = 1004;
        public static final int PERMISSION_DENIED = 1005;
        public static final int UNKNOWN_ERROR = 9999;
    }
}
