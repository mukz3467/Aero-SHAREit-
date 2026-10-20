package com.example.engine

import android.os.Environment
import android.os.StatFs
import java.util.Locale

data class StorageCheckResult(
    val availableBytes: Long,
    val requiredBytes: Long,
    val isStorageSufficient: Boolean,
    val formattedAvailable: String,
    val formattedRequired: String
)

object StorageSmartManager {
    fun getAvailableInternalBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes
        } catch (_: Exception) {
            50L * 1024 * 1024 * 1024 // 50 GB fallback
        }
    }

    fun checkStorageForTransfer(requiredBytes: Long): StorageCheckResult {
        val available = getAvailableInternalBytes()
        // Require 100MB buffer headroom
        val safeThreshold = requiredBytes + (100 * 1024 * 1024)
        return StorageCheckResult(
            availableBytes = available,
            requiredBytes = requiredBytes,
            isStorageSufficient = available >= safeThreshold,
            formattedAvailable = formatBytes(available),
            formattedRequired = formatBytes(requiredBytes)
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        val value = bytes / Math.pow(1024.0, exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, pre)
    }
}
