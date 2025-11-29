# Testing Phase 2: Connection Handshake

## Fitur yang Sudah Diimplementasi

### 1. ConnectionManager Updates

- ✅ `connectTo(targetIP)` - Establish TCP connection sebagai client
- ✅ Callback `onConnectionEstablished()` - Notifikasi saat koneksi berhasil
- ✅ Callback `onConnectionFailed()` - Notifikasi saat koneksi gagal
- ✅ Socket timeout 30 detik

### 2. TransferManager Updates

- ✅ Integration dengan ConnectionManager
- ✅ Establish connection sebelum transfer
- ✅ Error handling untuk connection failure

### 3. Flow Sequence

```
User → DeviceListPanel → MainController → ConnectionManager → TransferManager
                                              ↓
                                         new Socket(targetIP)
                                              ↓
                                    onConnectionEstablished()
                                              ↓
                                         sendFile(socket)
```

## Cara Testing

### 1. Setup - Jalankan 2 Instance

**Terminal 1 (Receiver):**

```bash
javac -d bin -sourcepath src src/app/Main.java
java -cp bin app.Main
```

Catat IP yang muncul di status bar, misal: `192.168.1.5`

**Terminal 2 (Sender):**

```bash
java -cp bin app.Main
```

### 2. Test Connection Handshake

**Di Sender:**

1. Tunggu sampai device Receiver muncul di list kiri
2. Klik device tersebut (highlight)
3. Klik "📄 Kirim File"
4. Pilih file test

**Expected Console Output (Sender):**

```
[Controller] Sending file: test.txt to User-123 (192.168.1.5)
[TransferManager] Preparing to send: test.txt (1.23 KB) to User-123 (192.168.1.5)
[ConnectionManager] Connecting to 192.168.1.5:1234
[ConnectionManager] Connected to 192.168.1.5
[Controller] Connection established to: 192.168.1.5
[TransferManager] Connection established, ready to transfer
[TransferManager] Socket: /192.168.1.5:1234
[TransferManager] Transfer task created: 1234567890_test.txt_192.168.1.5
```

**Expected Console Output (Receiver):**

```
[Server] Koneksi baru dari: /192.168.1.10
[Controller] Client connected from: /192.168.1.10
```

### 3. Test Connection Failure

**Scenario 1: Device Offline**

1. Pilih device yang sudah timeout (hilang dari list)
2. Klik "Kirim File"
3. Harus muncul error dialog: "Gagal terhubung ke 192.168.1.5"

**Scenario 2: Wrong Port**

1. Stop receiver
2. Coba kirim file
3. Harus muncul error: "Connection refused"

### 4. Verify Socket Properties

Console output harus menunjukkan:

- ✅ Socket berhasil dibuat
- ✅ Remote IP & port benar
- ✅ Timeout 30 detik
- ✅ Socket ready untuk I/O

## Architecture Flow

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Sender    │         │  ConnectionMgr   │         │    Receiver     │
│  (Client)   │         │                  │         │   (Server)      │
└──────┬──────┘         └────────┬─────────┘         └────────┬────────┘
       │                         │                            │
       │  1. connectTo(IP)       │                            │
       ├────────────────────────>│                            │
       │                         │                            │
       │                         │  2. new Socket(IP, 1234)   │
       │                         ├───────────────────────────>│
       │                         │                            │
       │                         │  3. accept() & handshake   │
       │                         │<───────────────────────────┤
       │                         │                            │
       │  4. onConnectionEstablished()                        │
       │<────────────────────────┤                            │
       │                         │                            │
       │  5. sendFile(socket)    │                            │
       │─────────────────────────┼───────────────────────────>│
       │                         │                            │
```

## Next Steps

Phase 3 (Folder Preparation) sudah done ✅

Phase 4 akan implementasi:

- **Protocol** - Define message format (HANDSHAKE, FILE_INFO, DATA, ACK)
- **SenderTask** - Actual file transfer dengan protocol
- **ReceiverTask** - Receive & save file
- **Progress tracking** - Real-time progress & speed calculation

## Notes

- Connection timeout: 30 detik
- TCP port: 1234 (dari Config)
- Socket reuse: Setiap transfer buat connection baru
- Error handling: Auto-notify UI via callback
