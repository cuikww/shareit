package core.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ProtocolTest {

    /**
     * InputStream which returns at most maxChunk bytes per read to simulate partial network reads.
     */
    private static class PartialInputStream extends InputStream {
        private final byte[] data;
        private final int maxChunk;
        private int pos = 0;

        public PartialInputStream(byte[] data, int maxChunk) {
            this.data = data;
            this.maxChunk = Math.max(1, maxChunk);
        }

        @Override
        public int read() throws IOException {
            if (pos >= data.length) return -1;
            return data[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= data.length) return -1;
            int chunk = Math.min(len, Math.min(maxChunk, data.length - pos));
            System.arraycopy(data, pos, b, off, chunk);
            pos += chunk;
            return chunk;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Running Protocol partial-read tests...");

        // Prepare payload
        byte[] payload = new byte[15000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 256);

        // Prepare header (OP_DATA + length)
        byte[] header = new byte[Protocol.HEADER_SIZE];
        ByteBuffer.wrap(header).put(Protocol.OP_DATA).putInt(payload.length);

        // Combine header + payload
        byte[] combined = new byte[header.length + payload.length];
        System.arraycopy(header, 0, combined, 0, header.length);
        System.arraycopy(payload, 0, combined, header.length, payload.length);

        // Test with small chunk size to force partial reads
        InputStream in = new PartialInputStream(combined, 512);

        Protocol.Message msg = Protocol.readMessage(in);
        if (msg == null) throw new RuntimeException("Message was null");
        if (msg.operation != Protocol.OP_DATA) throw new RuntimeException("Unexpected operation");
        if (!Arrays.equals(msg.payload, payload)) throw new RuntimeException("Payload mismatch");

        System.out.println("Partial payload read test passed.");

        // Test header split across reads: first 2 bytes, then rest
        byte[] header2 = new byte[Protocol.HEADER_SIZE];
        ByteBuffer.wrap(header2).put(Protocol.OP_DATA).putInt(10);
        byte[] combined2 = new byte[header2.length + 10];
        System.arraycopy(header2, 0, combined2, 0, header2.length);
        for (int i = 0; i < 10; i++) combined2[header.length + i] = (byte) i;

        InputStream in2 = new PartialInputStream(combined2, 2); // header will be split
        Protocol.Message msg2 = Protocol.readMessage(in2);
        if (msg2.operation != Protocol.OP_DATA) throw new RuntimeException("Unexpected op2");
        if (msg2.payload.length != 10) throw new RuntimeException("Unexpected payload length for test2");

        System.out.println("Partial header read test passed.");

        System.out.println("All tests good.");
    }
}
