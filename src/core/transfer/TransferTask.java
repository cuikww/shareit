package core.transfer;

import java.net.Socket;

public abstract class TransferTask implements Runnable {

    protected Socket socket;
    // Gunakan volatile agar perubahan nilai langsung terbaca di thread lain
    protected volatile boolean isPaused = false;
    protected volatile boolean isCancelled = false;
    protected Thread taskThread;

    public TransferTask(Socket socket) {
        this.socket = socket;
    }

    public void start() {
        if (taskThread == null || !taskThread.isAlive()) {
            taskThread = new Thread(this, "TransferTask-" + System.currentTimeMillis());
            taskThread.start();
        }
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            System.out.println("[TransferTask] Requesting PAUSE...");
        }
    }

    public void resume() {
        if (isPaused) {
            isPaused = false;
            System.out.println("[TransferTask] Requesting RESUME...");
            synchronized (this) {
                notifyAll(); // Bangunkan thread yang sedang wait()
            }
        }
    }

    public void cancel() {
        isCancelled = true;
        // Jika sedang dipause, kita harus resume dulu agar bisa stop loop dan close socket
        resume(); 
        if (taskThread != null) {
            taskThread.interrupt();
        }
        System.out.println("[TransferTask] Cancelled");
    }

    /**
     * Method ini WAJIB dipanggil di dalam loop pengiriman/penerimaan.
     * Jika isPaused = true, thread akan tidur (wait) di sini.
     */
    protected void checkPaused() throws InterruptedException {
        synchronized (this) {
            while (isPaused && !isCancelled) {
                System.out.println("[TransferTask] Thread is PAUSED. Waiting for resume...");
                wait(); // Thread tidur di sini sampai di-notify() oleh resume()
                System.out.println("[TransferTask] Thread RESUMED.");
            }
        }
    }

    protected void checkCancelled() throws InterruptedException {
        if (isCancelled) {
            throw new InterruptedException("Task cancelled");
        }
    }

    @Override
    public abstract void run();
}