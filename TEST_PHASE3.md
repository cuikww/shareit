# Testing Phase 3: Folder Preparation

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

## Next Phase

Phase 4 akan implementasi:

- SenderTask - Actual file transfer via TCP
- ReceiverTask - Receive file via TCP
- Protocol - Handshake & data transfer protocol
- Progress UI - Real-time progress bar & speed

## Notes

- Folder akan di-zip otomatis ke temporary file
- Temporary zip file akan auto-delete saat aplikasi close
- MD5 checksum ready untuk verifikasi (Phase 4)
