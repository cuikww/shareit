package core.network;

import app.Config;
import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveryService implements Runnable {

    private static final String PROTOCOL_PREFIX = "I_AM_HERE|";
    private static final int DEVICE_TIMEOUT = 10000; // 10 detik

    private DatagramSocket socket;
    private boolean isRunning;
    private Thread listenerThread;
    private Thread broadcasterThread;
    // apakah aplikasi sedang mengumumkan keberadaan (broadcast presence)
    private volatile boolean broadcastingEnabled = false;
    private Thread cleanupThread;

    // Menyimpan device yang ditemukan
    private final Map<String, Device> discoveredDevices = new ConcurrentHashMap<>();

    public interface DiscoveryListener {
        void onDeviceDiscovered(Device device);
        void onDeviceUpdated(Device device);

        void onDeviceRemoved(Device device);
    }

    private final DiscoveryListener listener;

    public DiscoveryService(DiscoveryListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (isRunning)
            return;
        isRunning = true;

        try {
            socket = new DatagramSocket(Config.UDP_PORT);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            System.err.println("[Discovery] Error membuka socket: " + e.getMessage());
            return;
        }

        // Thread untuk listening
        listenerThread = new Thread(this, "Discovery-Listener");
        listenerThread.start();

        // NOTE: broadcasting will start only when explicitly enabled by caller

        // Thread untuk cleanup device yang timeout
        cleanupThread = new Thread(this::cleanupLoop, "Discovery-Cleanup");
        cleanupThread.start();

        System.out.println("[Discovery] Service started on port " + Config.UDP_PORT);
    }

    /**
     * Trigger an immediate broadcast with the current username. Useful when the app changes
     * the username and we want peers to see the updated display name quickly.
     */
    public void announceNow() {
        // Best-effort; ensure socket/service has been started
        // Only announce presence if broadcasting is enabled (we only show as online when user opts-in)
        if (!isRunning || socket == null || socket.isClosed() || !broadcastingEnabled) return;
        new Thread(this::broadcastPresence, "Discovery-Announce").start();
    }

    /**
     * Turn on continuous broadcasting (announce presence periodically).
     */
    public void startBroadcasting() {
        if (!isRunning) return;
        if (broadcastingEnabled) return;
        broadcastingEnabled = true;

        broadcasterThread = new Thread(this::broadcastLoop, "Discovery-Broadcaster");
        broadcasterThread.start();
    }

    /**
     * Stop broadcasting presence.
     */
    public void stopBroadcasting() {
        broadcastingEnabled = false;
        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
            broadcasterThread = null;
        }
    }

    public void stop() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        // Listener thread - menerima broadcast dari device lain
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (isRunning) {
            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIP = packet.getAddress().getHostAddress();

                // Jangan proses broadcast dari diri sendiri
                if (isLocalAddress(senderIP)) {
                    continue;
                }

                parsePacket(message, senderIP);

            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("[Discovery] Error receiving: " + e.getMessage());
                }
            }
        }
    }

    private void broadcastLoop() {
        while (isRunning) {
            try {
                broadcastPresence();
                Thread.sleep(Config.BROADCAST_INTERVAL);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void broadcastPresence() {
        try {
            String message = PROTOCOL_PREFIX + Config.USERNAME;
            byte[] data = message.getBytes();

            // Broadcast ke subnet
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddress, Config.UDP_PORT);

            socket.send(packet);
            // System.out.println("[Discovery] Broadcast sent: " + message);

        } catch (IOException e) {
            System.err.println("[Discovery] Error broadcasting: " + e.getMessage());
        }
    }

    private void parsePacket(String message, String senderIP) {
        if (!message.startsWith(PROTOCOL_PREFIX)) {
            return;
        }

        String username = message.substring(PROTOCOL_PREFIX.length()).trim();

        Device device = discoveredDevices.get(senderIP);
        if (device == null) {
            // Device baru ditemukan
            device = new Device(senderIP, username);
            discoveredDevices.put(senderIP, device);

            System.out.println("[Discovery] Device found: " + device);
            if (listener != null) {
                listener.onDeviceDiscovered(device);
            }
        } else {
            // Update last seen and username if changed
            device.updateLastSeen();
            if (!device.getUsername().equals(username)) {
                System.out.println("[Discovery] Device updated name: " + device.getIpAddress() + " -> " + username);
                device.setUsername(username);
                if (listener != null) listener.onDeviceUpdated(device);
            }
        }
    }

    private void cleanupLoop() {
        while (isRunning) {
            try {
                Thread.sleep(5000); // Check setiap 5 detik

                long now = System.currentTimeMillis();
                discoveredDevices.entrySet().removeIf(entry -> {
                    Device device = entry.getValue();
                    if (now - device.getLastSeen() > DEVICE_TIMEOUT) {
                        System.out.println("[Discovery] Device timeout: " + device);
                        if (listener != null) {
                            listener.onDeviceRemoved(device);
                        }
                        return true;
                    }
                    return false;
                });

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private boolean isLocalAddress(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            InetAddress localHost = InetAddress.getLocalHost();

            // Check apakah IP sama dengan localhost
            if (addr.equals(localHost)) {
                return true;
            }

            // Check semua network interface
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localHost);
            if (networkInterface != null) {
                return networkInterface.getInterfaceAddresses().stream()
                        .anyMatch(ia -> ia.getAddress().equals(addr));
            }

        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    public Map<String, Device> getDiscoveredDevices() {
        return discoveredDevices;
    }
}
