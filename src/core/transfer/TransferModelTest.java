package core.transfer;

public class TransferModelTest {

    public static void main(String[] args) throws Exception {
        TransferModel model = new TransferModel("t1", "file.bin", 10000, TransferModel.Direction.OUTGOING);

        model.setBytesTransferred(0);
        Thread.sleep(200);
        model.setBytesTransferred(2048);

        System.out.println("Progress: " + model.getProgress() + "%");
        System.out.println("Speed: " + model.getSpeedFormatted());

        if (model.getSpeedKBps() <= 0)
            throw new RuntimeException("Speed should be > 0 after data change");

        System.out.println("TransferModelTest OK");
    }
}
