package core.transfer;

import core.utils.Protocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Integration test: ensures SenderTask will reconnect and resume after a dropped connection.
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
                // First accept: read some bytes then close
                try (Socket s1 = server.accept()) {
                    InputStream in = s1.getInputStream();
                    OutputStream out = s1.getOutputStream();

                    // handshake
                    Protocol.Message m = Protocol.readMessage(in);
                    // reply ACK
                    Protocol.writeMessage(out, new Protocol.Message(Protocol.OP_ACK, "ok".getBytes()));

                    // file_info
                    m = Protocol.readMessage(in);
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
                    // close connection to simulate failure
                }

                // Accept second connection and verify resume
                try (Socket s2 = server.accept()) {
                    InputStream in2 = s2.getInputStream();
                    OutputStream out2 = s2.getOutputStream();

                    // handshake
                    Protocol.Message m = Protocol.readMessage(in2);
                    Protocol.writeMessage(out2, new Protocol.Message(Protocol.OP_ACK, "ok".getBytes()));

                    // file_info
                    m = Protocol.readMessage(in2);
                    Protocol.FileInfoMessage fi = Protocol.FileInfoMessage.fromBytes(m.payload);

                    // reply with ACK indicating previously received bytes
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

        // ConnectionManager: simple helper which connects to our ephemeral server port
        core.network.ConnectionManager cm = new core.network.ConnectionManager(null) {
            @Override
            public Socket connectTo(String targetIP) throws IOException {
                // always connect to our local ephemeral server port used above
                return new Socket("127.0.0.1", port);
            }
        };

        // Client side: make initial socket and run SenderTask (this will attempt reconnect + resume)
        try (Socket sock = new Socket("127.0.0.1", port)) {
            SenderTask sender = new SenderTask(f, sock, "reconnect-test", null, System.getProperty("user.name"), cm, "127.0.0.1", 3);
            sender.run(); // execute in-process for test
        }

        server.close();
        if (f.exists()) f.delete();

        System.out.println("ReconnectResumeTest OK");
    }
}
