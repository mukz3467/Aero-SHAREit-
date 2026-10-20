package com.example.engine

import android.content.Context
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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class AeroTransferClient(
    private val context: Context,
    private val onProgressUpdate: (TransferRecord) -> Unit,
    private val onTransferFinished: (VerifiedReceipt) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var clientJob: Job? = null
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isCancelled = false

    @Volatile
    private var isPaused = false

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
        isCancelled = false
        isPaused = false

        clientJob = scope.launch {
            try {
                socket = Socket().apply {
                    tcpNoDelay = true
                    soTimeout = AeroTransferProtocol.SOCKET_TIMEOUT_MS
                    connect(InetSocketAddress(targetIp, targetPort), 8000)
                }

                val output = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream(), AeroTransferProtocol.TURBO_CHUNK_SIZE))
                val input = DataInputStream(BufferedInputStream(socket!!.getInputStream(), AeroTransferProtocol.TURBO_CHUNK_SIZE))

                val totalBytes = files.sumOf { it.sizeBytes }
                val sessionId = UUID.randomUUID().toString()

                output.writeInt(AeroTransferProtocol.MAGIC_HEADER)
                output.writeUTF(localDeviceName)
                output.writeInt(files.size)
                output.writeLong(totalBytes)
                output.flush()

                val accepted = input.readBoolean()
                if (!accepted) {
                    onError("Transfer request rejected by peer.")
                    return@launch
                }

                var transferredSoFar = 0L
                val startTime = System.currentTimeMillis()
                val receiptItems = mutableListOf<FileReceiptItem>()

                for (f in files) {
                    if (isCancelled) break

                    val stream = if (f.uri != null) {
                        context.contentResolver.openInputStream(f.uri)
                    } else {
                        null
                    }

                    val sampleBytes = stream?.readBytes() ?: "AeroShare_Offline_Payload_${f.name}".toByteArray()
                    val checksum = FileIntegrityEngine.calculateSha256(sampleBytes)

                    output.writeUTF(f.name)
                    output.writeLong(sampleBytes.size.toLong())
                    output.writeUTF(checksum)
                    output.flush()

                    var fileOffset = 0
                    val buffer = ByteArray(AeroTransferProtocol.TURBO_CHUNK_SIZE)
                    while (fileOffset < sampleBytes.size) {
                        while (isPaused && !isCancelled) {
                            Thread.sleep(100)
                        }
                        if (isCancelled) break

                        val len = Math.min(buffer.size, sampleBytes.size - fileOffset)
                        System.arraycopy(sampleBytes, fileOffset, buffer, 0, len)
                        output.write(buffer, 0, len)
                        output.flush()

                        fileOffset += len
                        transferredSoFar += len

                        val elapsedSec = Math.max(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
                        val speed = ((transferredSoFar * 8f) / (elapsedSec * 1024 * 1024)).toFloat()

                        val record = TransferRecord(
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
                        onProgressUpdate(record)
                    }

                    val verifiedByPeer = input.readBoolean()
                    receiptItems.add(
                        FileReceiptItem(
                            fileName = f.name,
                            fileSizeBytes = sampleBytes.size.toLong(),
                            sha256Checksum = checksum,
                            verified = verifiedByPeer
                        )
                    )
                }

                val totalSec = Math.max(1, ((System.currentTimeMillis() - startTime) / 1000).toInt())
                val avgSpeed = ((totalBytes * 8f) / (totalSec * 1024 * 1024)).toFloat()

                val receipt = VerifiedReceipt(
                    id = sessionId,
                    sessionId = sessionId,
                    peerDeviceName = targetIp,
                    peerIpAddress = targetIp,
                    totalBytesTransferred = totalBytes,
                    totalFilesCount = files.size,
                    durationSeconds = totalSec,
                    averageSpeedMbps = avgSpeed,
                    isOutgoing = true,
                    fileReceiptItems = receiptItems
                )
                onTransferFinished(receipt)

            } catch (ex: Exception) {
                if (!isCancelled) onError("Transfer failed: ${ex.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }
}
