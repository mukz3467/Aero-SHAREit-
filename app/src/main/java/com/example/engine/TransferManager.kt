package com.example.engine

import java.io.InputStream
import java.io.OutputStream

enum class TransferSessionStatus {
    IDLE,
    CONNECTING,
    TRANSFERRING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferProgressState(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val progress: Float,
    val speedMbps: Float,
    val status: TransferSessionStatus
)

object TransferManager {
    suspend fun sendFile(
        outputStream: OutputStream,
        fileStream: InputStream,
        totalBytes: Long,
        onProgress: (TransferProgressState) -> Unit
    ) {
        val buffer = ByteArray(AeroTransferProtocol.TURBO_CHUNK_SIZE)
        var transferred = 0L
        val startTime = System.currentTimeMillis()

        var bytesRead: Int
        while (fileStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            transferred += bytesRead
            val elapsed = Math.max(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
            val speed = ((transferred * 8f) / (elapsed * 1024 * 1024)).toFloat()

            onProgress(
                TransferProgressState(
                    bytesTransferred = transferred,
                    totalBytes = totalBytes,
                    progress = (transferred.toFloat() / Math.max(1L, totalBytes)).coerceIn(0f, 1f),
                    speedMbps = speed,
                    status = TransferSessionStatus.TRANSFERRING
                )
            )
        }
        outputStream.flush()
    }
}
