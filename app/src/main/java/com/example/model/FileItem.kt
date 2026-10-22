package com.example.model

import android.net.Uri

enum class FileCategory(val displayName: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APPS("Apps"),
    ARCHIVES("Archives")
}

data class FileItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val mimeType: String,
    val uri: Uri? = null,
    val category: FileCategory = FileCategory.DOCUMENTS,
    val extension: String = "",
    val dateModified: Long = System.currentTimeMillis()
)

data class NearbyDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 8829,
    val is5Ghz: Boolean = true,
    val signalStrength: Int = 95,
    val deviceModel: String = "Android Device",
    val pairingCode: String = ""
)

enum class TransferStatus {
    WAITING_ACCEPT,
    HANDSHAKING,
    TRANSFERRING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferRecord(
    val id: String,
    val fileName: String,
    val fileSizeFormatted: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val progress: Float, // 0.0 to 1.0
    val speedMbps: Float,
    val isIncoming: Boolean,
    val peerName: String,
    val status: TransferStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val timeRemainingSeconds: Int = 0,
    val localFilePath: String? = null,
    val contentUriString: String? = null,
    val mimeType: String? = null,
    val sha256Checksum: String? = null,
    val isLocalFileDeleted: Boolean = false
)
