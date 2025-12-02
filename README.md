## BreezeShare — Getting Started

This repository is the BreezeShare peer-to-peer file sharing sample (previously 'ShareIt' in earlier drafts). The UI now uses a pastel theme and the app displays the username you enter on the landing page — peers will see that name when discovered on the network.

Welcome to the VS Code Java world. Here is a guideline to help you get started to write Java code in Visual Studio Code.

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources
- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

> If you want to customize the folder structure, open `.vscode/settings.json` and update the related settings there.

## Dependency Management

The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found [here](https://github.com/microsoft/vscode-java-dependency#manage-dependencies).

## Troubleshooting transfer errors

If you see errors like:

- "Error during transfer: Incomplete payload read" (on receiver)
- "An established connection was aborted by the software in your host machine" (on sender, Windows)

Root causes and fixes included in this codebase:

- Protocol message reads used a single InputStream.read(...) call which is not guaranteed to return all requested bytes. This could cause the receiver to see "Incomplete payload read". Fixed by implementing a readFully utility which loops until the entire header/payload is received.
- The receiver previously stopped reading when the accumulated bytes matched the expected file size, which could leave a final OP_COMPLETE message unread on the stream. Receiver now continues reading until it receives OP_COMPLETE (and validates byte counts).
- To detect remote disconnects faster and avoid hanging reads, sender/receiver tasks now set a socket read timeout (30s) so SocketTimeoutException is raised instead of blocking indefinitely.

How to validate locally:

1. Compile the project:

```powershell
$files = Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d bin $files
```

2. Run the protocol partial-read tests (these simulate partial TCP reads):

```powershell
java -cp bin core.utils.ProtocolTest
```

If the tests pass you should see "All tests good." — this confirms the readFully and header/payload behavior are fixed.

If you still experience the connection-aborted error in full transfers, check:

- Windows firewall or antivirus that may be closing sockets.
- Network instability between sender and receiver.
- Ensure both peers are running compatible versions of the application.

If you'd like, I can also add an optional integration test that sets up a local ServerSocket and runs a full send/receive cycle using the SenderTask/ReceiverTask classes.

## Resume / Retry support and progress fixes

I've added resume support and improved progress/speed reporting:

- The receiver now reports how many bytes it already has (ACK payload includes bytesReceived).
- The sender will parse that ACK and resume sending starting at that offset (skips already-sent bytes).
- TransferManager keeps metadata for outgoing transfers so the UI "Resume" button can restart a transfer after a failure.
- TransferModel speed calculation was updated to use short-window delta + smoothing (avoids speed freezing) and totalBytes is now correctly set when the first progress event arrives.

New tests added:

- core.utils.ProtocolTest (checks partial-read safety)
- core.transfer.ResumeTest (integration-style test: verify sender respects resume offset)
- core.transfer.TransferModelTest (basic check for speed calculation)

How to run the new tests:

```powershell
javac -d bin src/core/utils/Protocol.java src/core/utils/ProtocolTest.java
java -cp bin core.utils.ProtocolTest

javac -d bin src/core/transfer/ResumeTest.java src/core/transfer/SenderTask.java src/core/utils/Protocol.java
java -cp bin core.transfer.ResumeTest

javac -d bin src/core/transfer/TransferModelTest.java src/core/transfer/TransferModel.java
java -cp bin core.transfer.TransferModelTest
```
