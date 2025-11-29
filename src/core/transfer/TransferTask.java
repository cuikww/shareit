package core.transfer;

import java.net.Socket;

/**
 * Abstract base class untuk transfer task (Sender & Receiver)
 */
public abstract class TransferTask implements Runnable {

    protected Socket socket;
    protected volatile boolean isPaused = false;
    protected volatile boolean isCancelled = false;
    protected Thread taskThread;

    public TransferTask(Socket socket) {
        this.socket = socket;
    }

    /**
     * Start task di thread terpisah
     */
    public void start() {
        if (taskThread == null || !taskThread.isAlive()) {
            taskThread = new Thread(this, "TransferTask-" + System.currentTimeMillis());
            taskThread.start();
        }
    }

    /**
     * Pause transfer
     */
    public void pause() {
        isPaused = true;
        System.out.println("[TransferTask] Paused");
    }

    /**
     * Resume transfer
     */
    public void resume() {
        isPaused = false;
        synchronized (this) {
            notifyAll();
        }
        System.out.println("[TransferTask] Resumed");
    }

    /**
     * Cancel transfer
     */
    public void cancel() {
        isCancelled = true;
        if (taskThread != null) {
            taskThread.interrupt();
        }
        System.out.println("[TransferTask] Cancelled");
    }

    /**
     * Check jika task sedang paused, tunggu sampai resume
     */
    protected void checkPaused() throws InterruptedException {
        while (isPaused && !isCancelled) {
            synchronized (this) {
                wait();
            }
        }
    }

    /**
     * Check jika task di-cancel
     */
    protected void checkCancelled() throws InterruptedException {
        if (isCancelled) {
            throw new InterruptedException("Task cancelled");
        }
    }

    @Override
    public abstract void run();
}
