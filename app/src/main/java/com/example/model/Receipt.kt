package com.example.model

import java.util.UUID

data class FileReceiptItem(
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val verified: Boolean = true
)

data class VerifiedReceipt(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = id,
    val peerDeviceName: String,
    val peerIpAddress: String,
    val totalBytesTransferred: Long,
    val totalFilesCount: Int = 1,
    val durationSeconds: Int = 0,
    val averageSpeedMbps: Float = 0f,
    val integrityStatus: String = "Cryptographically Verified (SHA-256)",
    val isOutgoing: Boolean = false,
    val fileReceiptItems: List<FileReceiptItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
