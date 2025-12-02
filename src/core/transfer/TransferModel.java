package core.transfer;

/**
 * Data model untuk tracking transfer progress
 * 
 * Diagram alur:
 * SenderTask/ReceiverTask → onTransferProgress()
 * → setBytesTransferred(currentBytes)
 * → calculatePercent()
 * → TransferListPanel.fireTableDataChanged()
 * → getProgress(), getSpeed(), etc.
 */
public class TransferModel {

    private String transferId;
    private String fileName;
    private long totalBytes;
    private long bytesTransferred = 0;
    private Direction direction = Direction.OUTGOING;
    private TransferStatus status;
    private long startTime;
    private long lastUpdateTime;
    private long lastBytesTransferred = 0;
    private double speedKBps = 0.0; // smoothed speed (KB/s)

    public enum TransferStatus {
        WAITING("Waiting"),
        TRANSFERRING("Transferring"),
        PAUSED("Paused"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        CANCELLED("Cancelled");

        private final String displayName;

        TransferStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Direction {
        INCOMING,
        OUTGOING
    }

    // ==================== Constructor ====================

    public TransferModel(String transferId, String fileName, long totalBytes, Direction direction) {
        this.transferId = transferId;
        this.fileName = fileName;
        this.totalBytes = totalBytes;
        this.direction = direction;
        this.status = TransferStatus.WAITING;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
    }

    public Direction getDirection() { return direction; }

    // ==================== Setters ====================

    public void setBytesTransferred(long bytes) {
        long now = System.currentTimeMillis();

        // Cap at totalBytes if totalBytes is known (>0), otherwise allow reporting
        if (totalBytes > 0) {
            this.bytesTransferred = Math.min(bytes, totalBytes);
        } else {
            this.bytesTransferred = bytes;
        }

        // compute instantaneous speed based on delta since last update
        long deltaBytes = this.bytesTransferred - this.lastBytesTransferred;
        long deltaMs = now - this.lastUpdateTime;
        if (deltaMs > 0 && deltaBytes >= 0) {
            double instKBps = (deltaBytes / 1024.0) / (deltaMs / 1000.0);
            // simple exponential smoothing to avoid jumps
            this.speedKBps = (this.speedKBps * 0.7) + (instKBps * 0.3);
        }

        this.lastUpdateTime = now;
        this.lastBytesTransferred = this.bytesTransferred;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
        // re-cap bytesTransferred in case it exceeded the new total
        if (this.bytesTransferred > this.totalBytes && this.totalBytes > 0) {
            this.bytesTransferred = this.totalBytes;
        }
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public void addBytesTransferred(long bytes) {
        setBytesTransferred(bytesTransferred + bytes);
    }

    // ==================== Getters ====================

    public String getTransferId() {
        return transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public TransferStatus getStatus() {
        return status;
    }

    // ==================== Calculations ====================

    /**
     * Calculate transfer progress percentage (0-100)
     */
    public int getProgress() {
        if (totalBytes == 0) {
            return 0;
        }
        return (int) ((bytesTransferred * 100) / totalBytes);
    }

    /**
     * Get transfer speed in KB/s
     */
    public double getSpeedKBps() {
        // return last computed/smoothed speed (fallback to total average if not yet computed)
        if (speedKBps > 0) return speedKBps;

        long elapsedMs = System.currentTimeMillis() - startTime;
        if (elapsedMs == 0) return 0;

        double totalKB = bytesTransferred / 1024.0;
        double elapsedSeconds = elapsedMs / 1000.0;
        return elapsedSeconds > 0 ? totalKB / elapsedSeconds : 0;
    }

    /**
     * Get transfer speed in human-readable format (KB/s or MB/s)
     */
    public String getSpeedFormatted() {
        double speedKBps = getSpeedKBps();

        if (speedKBps < 1024) {
            return String.format("%.2f KB/s", speedKBps);
        } else {
            double speedMBps = speedKBps / 1024;
            return String.format("%.2f MB/s", speedMBps);
        }
    }

    /**
     * Get remaining time in seconds
     */
    public long getRemainingSeconds() {
        double speedKBps = getSpeedKBps();
        if (speedKBps == 0) {
            return 0;
        }

        long remainingBytes = totalBytes - bytesTransferred;
        double remainingKB = remainingBytes / 1024.0;
        return (long) (remainingKB / speedKBps);
    }

    /**
     * Get remaining time in human-readable format
     */
    public String getRemainingTimeFormatted() {
        long seconds = getRemainingSeconds();

        if (seconds < 60) {
            return String.format("%d sec", seconds);
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return String.format("%d:%02d min", minutes, secs);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%d:%02d hr", hours, minutes);
        }
    }

    /**
     * Get bytes transferred formatted (KB, MB, GB)
     */
    public String getBytesTransferredFormatted() {
        return formatBytes(bytesTransferred);
    }

    /**
     * Get total bytes formatted (KB, MB, GB)
     */
    public String getTotalBytesFormatted() {
        return formatBytes(totalBytes);
    }

    /**
     * Helper method untuk format bytes
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Get elapsed time in seconds
     */
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    /**
     * Get elapsed time formatted
     */
    public String getElapsedTimeFormatted() {
        long seconds = getElapsedSeconds();

        if (seconds < 60) {
            return String.format("%d sec", seconds);
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return String.format("%d:%02d min", minutes, secs);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%d:%02d hr", hours, minutes);
        }
    }

    /**
     * Check if transfer is complete
     */
    public boolean isComplete() {
        return bytesTransferred >= totalBytes;
    }

    /**
     * Check if transfer is in progress
     */
    public boolean isInProgress() {
        return status == TransferStatus.TRANSFERRING;
    }

    /**
     * Get summary string untuk logging/display
     */
    @Override
    public String toString() {
        return String.format("%s: %s (%d%%) - %s/%s @ %s",
                fileName,
                status.getDisplayName(),
                getProgress(),
                getBytesTransferredFormatted(),
                getTotalBytesFormatted(),
                getSpeedFormatted());
    }
}
