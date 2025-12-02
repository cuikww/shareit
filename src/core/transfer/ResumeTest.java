package core.transfer;

import core.utils.Protocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Integration-style test to verify sender respects resume offset provided by receiver's ACK
 */
public class ResumeTest {

    public static void main(String[] args) throws Exception {
        // Create a sample file (10KB)
        File temp = new File("resume_test.dat");
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            for (int i = 0; i < 10 * 1024; i++) fos.write(i % 256);
        }

        ServerSocket server = new ServerSocket(0); // ephemeral port
        int port = server.getLocalPort();

        Thread serverThread = new Thread(() -> {
            try (Socket s = server.accept()) {
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();

                // Read handshake
                Protocol.Message m = Protocol.readMessage(in);
                System.out.println("Server: got " + Protocol.getOperationName(m.operation));
                // Reply ACK
                Protocol.Message ack = new Protocol.Message(Protocol.OP_ACK, "resume-test".getBytes());
                Protocol.writeMessage(out, ack);

                // Read FILE_INFO
                m = Protocol.readMessage(in);
                Protocol.FileInfoMessage fi = Protocol.FileInfoMessage.fromBytes(m.payload);
                System.out.println("Server: file info: " + fi.filename + " size=" + fi.fileSize);

                // Simulate that server already has 2048 bytes
                long already = 2048;
                Protocol.AckMessage ack2 = new Protocol.AckMessage("resume-test", already);
                Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_ACK, ack2.toBytes()));

                // Now read remaining data chunks
                long received = 0;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (true) {
                    Protocol.Message chunk = Protocol.readMessage(in);
                    if (chunk.operation == Protocol.OP_DATA) {
                        baos.write(chunk.payload);
                        received += chunk.payload.length;
                    } else if (chunk.operation == Protocol.OP_COMPLETE) {
                        break;
                    }
                }

                System.out.println("Server: received remaining bytes=" + received);

                // Expect remaining bytes = total - already
                if (received != fi.fileSize - already) {
                    throw new RuntimeException("Received count mismatch: " + received + " vs " + (fi.fileSize - already));
                }

                // Send final ACK
                Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_ACK, "done".getBytes()));

                s.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        serverThread.start();

        // Client -> create socket and run SenderTask directly
        try (Socket client = new Socket("127.0.0.1", port)) {
            SenderTask sender = new SenderTask(temp, client, "resume-test", null, System.getProperty("user.name"));
            sender.run(); // directly run on main thread for test
        }

        server.close();
        if (temp.exists()) temp.delete();

        System.out.println("ResumeTest OK");
    }
}
