# Testing Phase 3: Folder Preparation

**Note:** Phase ini sebenarnya sudah include Phase 2 (Connection Handshake).
Lihat `TEST_PHASE2.md` untuk detail connection flow.

## Fitur yang Sudah Diimplementasi

### 1. FileUtils

- ✅ `prepareFile()` - Auto-detect file vs folder
- ✅ `zipFolder()` - Zip folder secara recursive
- ✅ `getMD5()` - Calculate MD5 checksum
- ✅ `formatFileSize()` - Format size (KB, MB, GB)

### 2. TransferManager

- ✅ `sendFile()` - Handle file/folder transfer
- ✅ `pauseTransfer()` - Pause transfer
- ✅ `resumeTransfer()` - Resume transfer
- ✅ `cancelTransfer()` - Cancel transfer

### 3. TransferTask (Abstract)

- ✅ Base class untuk SenderTask & ReceiverTask
- ✅ Pause/Resume/Cancel functionality

### 4. UI Updates

- ✅ Button "Kirim File"
- ✅ Button "Kirim Folder"
- ✅ File/Folder chooser dialog
- ✅ Error handling (pilih device dulu)

## Cara Testing

### 1. Jalankan Aplikasi

```bash
javac -d bin -sourcepath src src/app/Main.java
java -cp bin app.Main
```

### 2. Test Send File

1. Tunggu sampai ada device muncul di list (kiri)
2. Pilih device
3. Klik "📄 Kirim File"
4. Pilih file
5. Lihat console output:
   ```
   [Controller] Sending file: test.txt to User-123 (192.168.1.5)
   [TransferManager] Preparing to send: test.txt (1.23 KB) to User-123 (192.168.1.5)
   ```

### 3. Test Send Folder

1. Pilih device
2. Klik "📁 Kirim Folder"
3. Pilih folder
4. Lihat console output:
   ```
   [FileUtils] Zipping folder: MyFolder -> MyFolder_123456.zip
   [FileUtils] Zip completed: /tmp/MyFolder_123456.zip (2.45 MB)
   [TransferManager] Preparing to send: MyFolder_123456.zip (2.45 MB) to User-123
   ```

### 4. Test Error Handling

1. Jangan pilih device
2. Klik "Kirim File" atau "Kirim Folder"
3. Harus muncul error dialog: "Pilih device tujuan terlebih dahulu!"

## Integration Test (Phase 2 + 3)

### Test Full Flow: Select File → Connect → Prepare

1. Jalankan 2 instance aplikasi
2. Di Sender:
   - Pilih device dari list
   - Klik "📄 Kirim File"
   - Pilih file test

**Expected Output:**

```
[Controller] Sending file: test.txt to User-123 (192.168.1.5)
[TransferManager] Preparing to send: test.txt (1.23 KB) to User-123 (192.168.1.5)
[ConnectionManager] Connecting to 192.168.1.5:1234
[ConnectionManager] Connected to 192.168.1.5
[Controller] Connection established to: 192.168.1.5
[TransferManager] Connection established, ready to transfer
[TransferManager] Socket: /192.168.1.5:1234
```

3. Test dengan folder:
   - Klik "📁 Kirim Folder"
   - Pilih folder test

**Expected Output:**

```
[FileUtils] Zipping folder: MyFolder -> MyFolder_123456.zip
[FileUtils] Zip completed: /tmp/MyFolder_123456.zip (2.45 MB)
[TransferManager] Preparing to send: MyFolder_123456.zip (2.45 MB) to User-123
[ConnectionManager] Connecting to 192.168.1.5:1234
[ConnectionManager] Connected to 192.168.1.5
```

## Next Phase

**Phase 4 (CRITICAL)** akan implementasi:

- **Protocol.java** - Define message format (HANDSHAKE, FILE_INFO, DATA, ACK)
- **SenderTask.java** - Actual file transfer via TCP socket
- **ReceiverTask.java** - Receive & save file
- **Progress tracking** - Real-time progress & speed calculation

Tanpa Phase 4, file tidak akan benar-benar ter-transfer (masih placeholder).

## Notes

- Folder akan di-zip otomatis ke temporary file
- Temporary zip file akan auto-delete saat aplikasi close
- MD5 checksum ready untuk verifikasi (Phase 4)
- Connection handshake sudah terintegrasi (Phase 2)
