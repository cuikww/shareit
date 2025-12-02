# Implementation Order - BreezeShare

## ✅ Phase 1: Server Startup (DONE)

**Tujuan:** Setup aplikasi dasar dengan TCP server

**Komponen:**

- Main.java
- Config.java
- MainController.java
- ConnectionManager.java (server mode)
- MainFrame.java

**Flow:**

```
User → Main.main()
     → MainController()
     → ConnectionManager.startServer()
     → MainFrame.show()
```

**Output:**

- Server berjalan di port 1234
- GUI terbuka
- Status bar menampilkan IP & port

---

## ✅ Phase 1b: Device Discovery (DONE)

**Tujuan:** Auto-discover peers di network via UDP broadcast

**Komponen:**

- DiscoveryService.java
- Device.java
- DeviceListPanel.java

**Flow:**

```
DiscoveryService.start()
  ├─ Broadcaster Thread: broadcast "I_AM_HERE|Username" setiap 3 detik
  ├─ Listener Thread: terima broadcast dari device lain
  └─ Cleanup Thread: remove device yang timeout (>10 detik)

Device ditemukan → onDeviceDiscovered() → DeviceListPanel.addDevice()
```

**Output:**

- List device muncul di panel kiri
- Auto-update saat device online/offline

---

## ✅ Phase 2: Connection Handshake (DONE)

**Tujuan:** Establish TCP connection antara sender & receiver

**Komponen:**

- ConnectionManager.connectTo()
- TransferManager.setConnectionManager()
- MainController callbacks

**Flow:**

```
User klik "Kirim File"
  → MainController.handleSendFile()
  → TransferManager.sendFile()
  → ConnectionManager.connectTo(targetIP)
  → new Socket(targetIP, 1234)
  → onConnectionEstablished(socket)
```

**Output:**

```
[ConnectionManager] Connecting to 192.168.1.5:1234
[ConnectionManager] Connected to 192.168.1.5
[Controller] Connection established to: 192.168.1.5
[TransferManager] Socket: /192.168.1.5:1234
```

---

## ✅ Phase 3: Folder Preparation (DONE)

**Tujuan:** Handle file & folder (zip folder otomatis)

**Komponen:**

- FileUtils.java
  - prepareFile() - auto-detect file vs folder
  - zipFolder() - zip recursive
  - getMD5() - checksum
  - formatFileSize() - human-readable size

**Flow:**

```
TransferManager.sendFile(inputPath)
  → FileUtils.prepareFile(inputPath)
  → if (isDirectory) → zipFolder() → temp.zip
  → if (isFile) → return as-is
```

**Output:**

```
[FileUtils] Zipping folder: MyFolder -> MyFolder_123456.zip
[FileUtils] Zip completed: /tmp/MyFolder_123456.zip (2.45 MB)
```

---

## 🔄 Phase 4: File Transfer Protocol (NEXT)

**Tujuan:** Define protocol & implement actual transfer

**Komponen yang perlu dibuat:**

1. **Protocol.java** - Define message types

   ```java
   public class Protocol {
       public static final byte OP_HANDSHAKE = 0x01;
       public static final byte OP_FILE_INFO = 0x02;
       public static final byte OP_DATA = 0x03;
       public static final byte OP_ACK = 0x04;
       public static final byte OP_PAUSE = 0x05;
       public static final byte OP_RESUME = 0x06;
       public static final byte OP_CANCEL = 0x07;
       public static final byte OP_COMPLETE = 0x08;
   }
   ```

2. **SenderTask.java** - Kirim file via socket

   ```java
   public class SenderTask extends TransferTask {
       private File file;
       private String transferId;

       @Override
       public void run() {
           // 1. Send HANDSHAKE
           // 2. Send FILE_INFO (name, size, md5)
           // 3. Wait ACK
           // 4. Send DATA chunks
           // 5. Update progress
           // 6. Send COMPLETE
       }
   }
   ```

3. **ReceiverTask.java** - Terima file via socket
   ```java
   public class ReceiverTask extends TransferTask {
       private String savePath;

       @Override
       public void run() {
           // 1. Receive HANDSHAKE
           // 2. Receive FILE_INFO
           // 3. Send ACK
           // 4. Receive DATA chunks
           // 5. Save to file
           // 6. Verify MD5
           // 7. Send ACK COMPLETE
       }
   }
   ```

**Flow:**

```
Sender                          Receiver
  |                                |
  |--- HANDSHAKE ----------------->|
  |<-- ACK -----------------------|
  |                                |
  |--- FILE_INFO ----------------->|
  |    (name, size, md5)           |
  |<-- ACK -----------------------|
  |                                |
  |--- DATA chunk 1 -------------->|
  |--- DATA chunk 2 -------------->|
  |--- DATA chunk 3 -------------->|
  |    ...                         |
  |<-- ACK (progress) ------------|
  |                                |
  |--- COMPLETE ------------------>|
  |<-- ACK -----------------------|
```

---

## 📊 Phase 5: Progress UI (AFTER Phase 4)

**Tujuan:** Real-time progress bar & speed

**Komponen:**

- TransferModel.java - Data model
- TransferListPanel.java - UI panel
- Progress calculation & speed tracking

**Flow:**

```
SenderTask/ReceiverTask
  → onTransferProgress(bytesTransferred, totalBytes)
  → TransferModel.update()
  → TransferListPanel.repaint()
```

---

## 🎮 Phase 6: Pause/Resume/Cancel (FINAL)

**Tujuan:** Control transfer

**Flow:**

```
User klik "Pause"
  → TransferManager.pauseTransfer(id)
  → TransferTask.pause()
  → Send OP_PAUSE to peer
  → Wait for resume

User klik "Resume"
  → TransferManager.resumeTransfer(id)
  → TransferTask.resume()
  → Send OP_RESUME to peer
  → Continue transfer
```

---

## Summary

**Sudah selesai:**

- ✅ Phase 1: Server Startup
- ✅ Phase 1b: Device Discovery
- ✅ Phase 2: Connection Handshake
- ✅ Phase 3: Folder Preparation

**Belum:**

- ⏳ Phase 4: File Transfer Protocol (CRITICAL - ini yang bikin transfer jalan)
- ⏳ Phase 5: Progress UI
- ⏳ Phase 6: Pause/Resume/Cancel

**Next Action:**
Implementasi Phase 4 untuk actual file transfer!
