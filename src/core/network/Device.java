package core.network;

public class Device {
    private final String ipAddress;
    private String username;
    private long lastSeen;

    public Device(String ipAddress, String username) {
        this.ipAddress = ipAddress;
        this.username = username;
        this.lastSeen = System.currentTimeMillis();
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUsername() {
        return username;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Device))
            return false;
        Device other = (Device) obj;
        return ipAddress.equals(other.ipAddress);
    }

    @Override
    public int hashCode() {
        return ipAddress.hashCode();
    }

    @Override
    public String toString() {
        return username + " (" + ipAddress + ")";
    }
}
