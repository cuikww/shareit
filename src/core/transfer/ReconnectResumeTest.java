package core.transfer;

import core.utils.Protocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Integration test: ensures SenderTask will resume after a dropped connection.
 * Since SenderTask no longer handles auto-reconnect internally, 
 * this test simulates the application logic restarting the task.
 */
public class ReconnectResumeTest {

    public static void main(String[] args) throws Exception {
        // Prepare sample file (20KB)
        File f = new File("reconnect_test.dat");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            for (int i = 0; i < 20 * 1024; i++) fos.write(i % 256);
        }

        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();

        // server state
        final long alreadyReceivedByServer[] = {0};

        Thread serverThread = new Thread(() -> {
            try {
                // First accept: read some bytes then close (Simulate Drop)
                try (Socket s1 = server.accept()) {
                    InputStream in = s1.getInputStream();
                    OutputStream out = s1.getOutputStream();

                    // handshake
                    Protocol.readMessage(in);
                    Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_ACK, "ok".getBytes()));

                    // file_info
                    Protocol.Message m = Protocol.readMessage(in);
                    Protocol.FileInfoMessage fi = Protocol.FileInfoMessage.fromBytes(m.payload);

                    // send ACK claiming 0 bytes present
                    Protocol.AckMessage ack = new Protocol.AckMessage(fi.filename, 0);
                    Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_ACK, ack.toBytes()));

                    // read a small amount then simulate a drop
                    long received = 0;
                    while (true) {
                        m = Protocol.readMessage(in);
                        if (m.operation == Protocol.OP_DATA) {
                            received += m.payload.length;
                            if (received >= 4096) break; // read 4KB then drop
                        } else if (m.operation == Protocol.OP_COMPLETE) break;
                    }

                    alreadyReceivedByServer[0] = received; // 4KB
                    System.out.println("Server: Connection 1 dropped after " + received + " bytes");
                }

                // Accept second connection and verify resume
                try (Socket s2 = server.accept()) {
                    InputStream in2 = s2.getInputStream();
                    OutputStream out2 = s2.getOutputStream();

                    // handshake
                    Protocol.readMessage(in2);
                    Protocol.writeMessage(out2, new Protocol.Message(Protocol.OP_ACK, "ok".getBytes()));

                    // file_info
                    Protocol.Message m = Protocol.readMessage(in2);
                    Protocol.FileInfoMessage fi = Protocol.FileInfoMessage.fromBytes(m.payload);

                    // reply with ACK indicating previously received bytes (Resume logic)
                    Protocol.AckMessage ack2 = new Protocol.AckMessage(fi.filename, alreadyReceivedByServer[0]);
                    Protocol.writeMessage(out2, new Protocol.Message(Protocol.OP_ACK, ack2.toBytes()));

                    // read remaining data
                    long remaining = 0;
                    while (true) {
                        m = Protocol.readMessage(in2);
                        if (m.operation == Protocol.OP_DATA) {
                            remaining += m.payload.length;
                        } else if (m.operation == Protocol.OP_COMPLETE) {
                            break;
                        }
                    }

                    System.out.println("Server: Connection 2 finished. Remaining bytes read: " + remaining);

                    if (remaining != fi.fileSize - alreadyReceivedByServer[0]) {
                        throw new RuntimeException("Mismatch remaining bytes: " + remaining + " expected " + (fi.fileSize - alreadyReceivedByServer[0]));
                    }

                    // final ack
                    Protocol.writeMessage(out2, new Protocol.Message(Protocol.OP_ACK, "done".getBytes()));
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        serverThread.start();

        // Client side: Attempt 1 (Will fail because server drops it)
        System.out.println("Client: Starting attempt 1...");
        try (Socket sock = new Socket("127.0.0.1", port)) {
            // Gunakan constructor standar yang baru
            SenderTask sender = new SenderTask(f, sock, "reconnect-test", null, System.getProperty("user.name"));
            sender.run(); 
        } catch (Exception e) {
            System.out.println("Client: Attempt 1 failed as expected (simulated drop)");
        }

        // Client side: Attempt 2 (Resume)
        System.out.println("Client: Starting attempt 2 (Resume)...");
        try (Socket sock = new Socket("127.0.0.1", port)) {
            // Buat SenderTask baru untuk melanjutkan
            SenderTask sender = new SenderTask(f, sock, "reconnect-test", null, System.getProperty("user.name"));
            sender.run();
        }

        server.close();
        if (f.exists()) f.delete();

        System.out.println("ReconnectResumeTest OK");
    }
}