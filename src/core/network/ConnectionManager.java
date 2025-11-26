package core.network;

import app.Config;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectionManager implements Runnable {

    private ServerSocket serverSocket;
    private boolean isRunning;
    private Thread serverThread;

    // Interface sederhana agar Controller tahu status server
    public interface ServerListener {
        void onServerStarted(String ip, int port);
        void onServerError(String error);
        void onClientConnected(Socket socket); // Persiapan untuk Phase 2
    }

    private final ServerListener listener;

    public ConnectionManager(ServerListener listener) {
        this.listener = listener;
    }

    public void startServer() {
        if (isRunning) return;
        isRunning = true;
        serverThread = new Thread(this, "Server-Thread");
        serverThread.start();
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(Config.TCP_PORT);
            
            // Dapatkan IP lokal komputer ini untuk ditampilkan
            InetAddress localHost = InetAddress.getLocalHost();
            String ip = localHost.getHostAddress();

            // Beri tahu UI bahwa server sudah jalan
            if (listener != null) {
                listener.onServerStarted(ip, Config.TCP_PORT);
            }

            System.out.println("[Server] Berjalan di " + ip + ":" + Config.TCP_PORT);

            while (isRunning) {
                // Menunggu koneksi masuk (Blocking)
                Socket clientSocket = serverSocket.accept();
                
                // Ada klien masuk! Beri tahu Controller (Nanti di Phase 2 ini dipakai)
                System.out.println("[Server] Koneksi baru dari: " + clientSocket.getInetAddress());
                if (listener != null) {
                    listener.onClientConnected(clientSocket);
                }
            }

        } catch (IOException e) {
            if (isRunning) { // Jika error bukan karena distop manual
                if (listener != null) listener.onServerError(e.getMessage());
                e.printStackTrace();
            }
        }
    }
}