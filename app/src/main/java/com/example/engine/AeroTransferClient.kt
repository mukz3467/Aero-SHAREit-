package com.example.engine

import android.content.Context
import android.os.StatFs
import com.example.model.FileItem
import com.example.model.FileReceiptItem
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.model.VerifiedReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class AeroTransferClient(
    private val context: Context,
    private val onProgressUpdate: (TransferRecord) -> Unit,
    private val onTransferFinished: (VerifiedReceipt) -> Unit,
    private val onError: (String) -> Unit = {},
    private val liteModeProvider: () -> Boolean = { false }
) {
    private var clientJob: Job? = null
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isCancelled = false

    @Volatile
    private var isPaused = false

    @Volatile
    private var transferGeneration = 0L

    fun cancel() {
        isCancelled = true
        try {
            socket?.close()
        } catch (_: Exception) {}
        clientJob?.cancel()
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun startTransfer(
        targetIp: String,
        targetPort: Int = AeroTransferProtocol.DEFAULT_PORT,
        localDeviceName: String,
        files: List<FileItem>
    ) {
        if (files.isEmpty() || files.size > 1000) {
            onError("Select between 1 and 1000 files for a transfer.")
            return
        }
        if (clientJob?.isActive == true) {
            onError("A transfer is already active. Finish or cancel it before starting another.")
            return
        }
        isCancelled = false
        isPaused = false
        transferGeneration += 1
        val generation = transferGeneration
        val transferChunkSize = if (liteModeProvider()) LITE_CHUNK_SIZE else AeroTransferProtocol.TURBO_CHUNK_SIZE

        clientJob = scope.launch {
            var activeSocket: Socket? = null
            try {
                val connection = Socket().apply {
                    tcpNoDelay = true
                    soTimeout = AeroTransferProtocol.SOCKET_TIMEOUT_MS
                    connect(InetSocketAddress(targetIp, targetPort), 8000)
                }
                activeSocket = connection
                socket = connection

                val output = DataOutputStream(BufferedOutputStream(connection.getOutputStream(), transferChunkSize))
                val input = DataInputStream(BufferedInputStream(connection.getInputStream(), transferChunkSize))

                val sessionId = UUID.randomUUID().toString()
                val stagedFiles = mutableListOf<Pair<FileItem, File>>()
                var totalBytes = 0L
                var transferredSoFar = 0L
                var startTime = System.currentTimeMillis()
                val receiptItems = mutableListOf<FileReceiptItem>()
                var stagedTotalBytes = 0L
                var lastStorageCheckBytes = 0L
                try {
                    // Stage selected content to disk first so metadata and byte counts match
                    // the actual content and large files are never loaded fully into RAM.
                    for (item in files) {
                        if (isCancelled) throw IOException("Transfer cancelled.")
                        val uri = item.uri ?: throw IOException("Cannot open the selected file: ${item.name}")
                        val source = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("Cannot read the selected file: ${item.name}")
                        val staged = File.createTempFile("aero_send_", ".part", context.cacheDir)
                        try {
                            source.use { inputStream ->
                                staged.outputStream().buffered(transferChunkSize).use { out ->
                                    val copyBuffer = ByteArray(transferChunkSize)
                                    while (true) {
                                        if (isCancelled) throw IOException("Transfer cancelled.")
                                        val count = inputStream.read(copyBuffer)
                                        if (count < 0) break
                                        if (stagedTotalBytes > 100L * 1024 * 1024 * 1024 - count) {
                                            throw IOException("Selected batch exceeds the 100 GiB transfer limit.")
                                        }
                                        if (stagedTotalBytes - lastStorageCheckBytes >= 8L * 1024 * 1024) {
                                            val available = try {
                                                StatFs(context.cacheDir.absolutePath).availableBytes
                                            } catch (_: Exception) {
                                                0L
                                            }
                                            if (available < 100L * 1024 * 1024) {
                                                throw IOException("Not enough free storage to stage files safely.")
                                            }
                                            lastStorageCheckBytes = stagedTotalBytes
                                        }
                                        out.write(copyBuffer, 0, count)
                                        stagedTotalBytes += count
                                    }
                                }
                            }
                            stagedFiles.add(item to staged)
                        } catch (ex: Exception) {
                            staged.delete()
                            throw ex
                        }
                    }

                    totalBytes = stagedTotalBytes
                    output.writeInt(AeroTransferProtocol.MAGIC_HEADER)
                    output.writeUTF(localDeviceName)
                    output.writeInt(stagedFiles.size)
                    output.writeLong(totalBytes)
                    output.flush()

                    val accepted = input.readBoolean()
                    if (!accepted) {
                        onError("Transfer request rejected by peer.")
                        return@launch
                    }

                    transferredSoFar = 0L
                    startTime = System.currentTimeMillis()
                    val buffer = ByteArray(AeroTransferProtocol.TURBO_CHUNK_SIZE)

                    for ((f, staged) in stagedFiles) {
                        if (isCancelled) break
                        val fileSize = staged.length()
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        staged.inputStream().buffered(transferChunkSize).use { fileInput ->
                            while (true) {
                                val count = fileInput.read(buffer)
                                if (count < 0) break
                                digest.update(buffer, 0, count)
                            }
                        }
                        val checksum = digest.digest().joinToString("") { "%02x".format(it) }

                        output.writeUTF(f.name)
                        output.writeLong(fileSize)
                        output.writeUTF(checksum)
                        output.flush()

                        var fileOffset = 0L
                        staged.inputStream().buffered(AeroTransferProtocol.TURBO_CHUNK_SIZE).use { fileInput ->
                            while (true) {
                                while (isPaused && !isCancelled) {
                                    kotlinx.coroutines.delay(100)
                                }
                                if (isCancelled) break
                                val count = fileInput.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                fileOffset += count
                                transferredSoFar += count

                                val elapsedSec = Math.max(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
                                val speed = ((transferredSoFar * 8f) / (elapsedSec * 1024 * 1024)).toFloat()
                                onProgressUpdate(
                                    TransferRecord(
                                        id = sessionId,
                                        fileName = f.name,
                                        fileSizeFormatted = StorageSmartManager.formatBytes(totalBytes),
                                        totalBytes = totalBytes,
                                        transferredBytes = transferredSoFar,
                                        progress = (transferredSoFar.toFloat() / Math.max(1L, totalBytes)).coerceIn(0f, 1f),
                                        speedMbps = speed,
                                        isIncoming = false,
                                        peerName = targetIp,
                                        status = TransferStatus.TRANSFERRING,
                                        contentUriString = f.uri?.toString()
                                    )
                                )
                            }
                        }
                        output.flush()
                        if (isCancelled) break

                        val verifiedByPeer = input.readBoolean()
                        receiptItems.add(
                            FileReceiptItem(
                                fileName = f.name,
                                fileSizeBytes = fileSize,
                                sha256Checksum = checksum,
                                verified = verifiedByPeer
                            )
                        )
                        if (!verifiedByPeer) {
                            throw IOException("Receiver could not verify ${f.name}; transfer is not complete.")
                        }
                    }
                } finally {
                    stagedFiles.forEach { (_, staged) -> staged.delete() }
                }

                if (isCancelled) throw IOException("Transfer cancelled.")
                if (receiptItems.size != stagedFiles.size || receiptItems.any { !it.verified }) {
                    throw IOException("Transfer ended before every file was verified.")
                }

                val totalSec = Math.max(1, ((System.currentTimeMillis() - startTime) / 1000).toInt())
                val avgSpeed = ((transferredSoFar * 8f) / (totalSec * 1024 * 1024)).toFloat()

                val receipt = VerifiedReceipt(
                    id = sessionId,
                    sessionId = sessionId,
                    peerDeviceName = targetIp,
                    peerIpAddress = targetIp,
                    totalBytesTransferred = transferredSoFar,
                    totalFilesCount = receiptItems.size,
                    durationSeconds = totalSec,
                    averageSpeedMbps = avgSpeed,
                    isOutgoing = true,
                    fileReceiptItems = receiptItems
                )
                onTransferFinished(receipt)

            } catch (ex: Exception) {
                if (!isCancelled && generation == transferGeneration) onError("Transfer failed: ${ex.message}")
            } finally {
                try { activeSocket?.close() } catch (_: Exception) {}
                if (socket === activeSocket) socket = null
            }
        }
    }
    companion object {
        private const val LITE_CHUNK_SIZE = 16 * 1024
    }
}
