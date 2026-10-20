package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.model.FileReceiptItem
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.model.VerifiedReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class AeroTransferServer(
    private val context: Context,
    private val port: Int = AeroTransferProtocol.DEFAULT_PORT,
    private val onIncomingRequest: (SafeDropRequest, (Boolean) -> Unit) -> Unit,
    private val onProgressUpdate: (TransferRecord) -> Unit,
    private val onTransferFinished: (VerifiedReceipt) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val connectionSemaphore = Semaphore(AeroTransferProtocol.MAX_CONCURRENT_CLIENTS)

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                    receiveBufferSize = AeroTransferProtocol.SOCKET_BUFFER_SIZE
                }
                isRunning = true

                while (isActive && !serverSocket!!.isClosed) {
                    val socket = serverSocket!!.accept()
                    scope.launch {
                        connectionSemaphore.acquire()
                        try {
                            handleClient(socket)
                        } catch (ex: Exception) {
                            onError("Incoming transfer failed: ${ex.message ?: "connection closed"}")
                        } finally {
                            connectionSemaphore.release()
                        }
                    }
                }
            } catch (ex: Exception) {
                if (isRunning) onError("Server error: ${ex.message}")
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            s.tcpNoDelay = true
            s.soTimeout = AeroTransferProtocol.SOCKET_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(s.getInputStream(), AeroTransferProtocol.TURBO_CHUNK_SIZE))
            val output = DataOutputStream(BufferedOutputStream(s.getOutputStream(), AeroTransferProtocol.TURBO_CHUNK_SIZE))

            // 1. Read Magic & Protocol
            val magic = input.readInt()
            if (magic != AeroTransferProtocol.MAGIC_HEADER) return

            val clientDeviceName = input.readUTF()
            val filesCount = input.readInt()
            val totalBytes = input.readLong()
            if (filesCount !in 1..1000 || totalBytes < 0L || totalBytes > 100L * 1024 * 1024 * 1024) {
                output.writeBoolean(false)
                output.flush()
                return
            }
            val clientIp = s.inetAddress.hostAddress ?: "unknown"

            val req = SafeDropRequest(
                peerName = clientDeviceName,
                peerModel = "Android Direct Peer",
                peerIp = clientIp,
                filesCount = filesCount,
                totalSizeBytes = totalBytes,
                totalSizeFormatted = StorageSmartManager.formatBytes(totalBytes)
            )

            val approval = CompletableDeferred<Boolean>()
            onIncomingRequest(req) { accepted -> approval.complete(accepted) }
            val isAccepted = withTimeoutOrNull(30_000L) { approval.await() } ?: false

            output.writeBoolean(isAccepted)
            output.flush()
            if (!isAccepted) return

            val receiptItems = mutableListOf<FileReceiptItem>()
            var transferredSoFar = 0L
            val startTime = System.currentTimeMillis()

            val tempDir = File(context.cacheDir, "transfers").apply { mkdirs() }
            val receivedDir = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "received")).apply {
                if (!exists() && !mkdirs()) throw IOException("Cannot create received-files folder.")
            }
            if (!receivedDir.isDirectory || !receivedDir.canWrite()) {
                throw IOException("Received-files folder is not writable.")
            }

            var declaredBytes = 0L
            for (i in 0 until filesCount) {
                val fileName = AeroSecuritySanitizer.sanitizeFilename(input.readUTF())
                val fileSize = input.readLong()
                val expectedSha = input.readUTF()
                if (fileSize < 0L || fileSize > totalBytes - declaredBytes) {
                    output.writeBoolean(false)
                    output.flush()
                    return
                }

                declaredBytes += fileSize
                val tempFile = File(tempDir, ".aero_${UUID.randomUUID()}.part").canonicalFile
                if (tempFile.parentFile != tempDir.canonicalFile) {
                    output.writeBoolean(false)
                    output.flush()
                    return
                }

                var fileTransferred = 0L
                tempFile.outputStream().use { fos ->
                    val buffer = ByteArray(AeroTransferProtocol.TURBO_CHUNK_SIZE)
                    while (fileTransferred < fileSize) {
                        val toRead = Math.min(buffer.size.toLong(), fileSize - fileTransferred).toInt()
                        val bytesRead = input.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        fos.write(buffer, 0, bytesRead)
                        fileTransferred += bytesRead
                        transferredSoFar += bytesRead

                        val elapsedSec = Math.max(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
                        val speed = ((transferredSoFar * 8f) / (elapsedSec * 1024 * 1024)).toFloat()

                        val record = TransferRecord(
                            id = req.requestId,
                            fileName = fileName,
                            fileSizeFormatted = StorageSmartManager.formatBytes(totalBytes),
                            totalBytes = totalBytes,
                            transferredBytes = transferredSoFar,
                            progress = (transferredSoFar.toFloat() / Math.max(1L, totalBytes)).coerceIn(0f, 1f),
                            speedMbps = speed,
                            isIncoming = true,
                            peerName = clientDeviceName,
                            status = TransferStatus.TRANSFERRING,
                            localFilePath = tempFile.absolutePath
                        )
                        onProgressUpdate(record)
                    }
                }

                // Commit only a complete file whose checksum matches.
                val complete = fileTransferred == fileSize
                val actualSha = if (complete) FileIntegrityEngine.calculateFileSha256(tempFile) else ""
                val checksumMatches = complete && FileIntegrityEngine.verifyChecksum(expectedSha, actualSha)
                val totalMatches = i != filesCount - 1 || declaredBytes == totalBytes
                val finalFile = AeroSecuritySanitizer.resolveUniqueFile(receivedDir, fileName)
                val commitTemp = File(receivedDir, ".aero_receive_${UUID.randomUUID()}.part")
                val isVerified = if (checksumMatches && totalMatches) {
                    try {
                        tempFile.copyTo(commitTemp, overwrite = false, bufferSize = AeroTransferProtocol.TURBO_CHUNK_SIZE)
                        val savedSha = FileIntegrityEngine.calculateFileSha256(commitTemp)
                        if (FileIntegrityEngine.verifyChecksum(expectedSha, savedSha) && commitTemp.renameTo(finalFile)) {
                            true
                        } else {
                            commitTemp.delete()
                            false
                        }
                    } catch (_: Exception) {
                        commitTemp.delete()
                        false
                    }
                } else {
                    false
                }
                tempFile.delete()

                receiptItems.add(
                    FileReceiptItem(
                        fileName = fileName,
                        fileSizeBytes = fileSize,
                        sha256Checksum = actualSha,
                        verified = isVerified,
                        localFilePath = if (isVerified) finalFile.absolutePath else null
                    )
                )

                output.writeBoolean(isVerified)
                output.flush()
            }

            val totalSec = Math.max(1, ((System.currentTimeMillis() - startTime) / 1000).toInt())
            val avgSpeed = ((totalBytes * 8f) / (totalSec * 1024 * 1024)).toFloat()

            val receipt = VerifiedReceipt(
                id = req.requestId,
                sessionId = req.requestId,
                peerDeviceName = clientDeviceName,
                peerIpAddress = clientIp,
                totalBytesTransferred = totalBytes,
                totalFilesCount = filesCount,
                durationSeconds = totalSec,
                averageSpeedMbps = avgSpeed,
                fileReceiptItems = receiptItems
            )
            onTransferFinished(receipt)
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
    }
}
