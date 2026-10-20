package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import java.io.File

data class CacheUsageReport(
    val totalCacheBytes: Long,
    val tempPartBytes: Long,
    val abandonedTransfersCount: Int,
    val totalCacheFilesCount: Int,
    val formattedTotalCache: String,
    val formattedTempParts: String
)

data class CleanupResult(
    val success: Boolean,
    val bytesFreed: Long,
    val filesRemovedCount: Int,
    val formattedBytesFreed: String,
    val message: String
)

data class FileDeletionResult(
    val recordId: String,
    val fileName: String,
    val wasDeletedFromDisk: Boolean,
    val bytesFreed: Long,
    val requiresSystemAuth: Boolean = false,
    val message: String
)

data class BatchDeletionResult(
    val totalAttempted: Int,
    val successfullyDeletedCount: Int,
    val bytesFreed: Long,
    val failedCount: Int,
    val formattedBytesFreed: String,
    val details: List<FileDeletionResult>
)

object FileManagerAndCleanerEngine {

    fun measureCacheUsage(context: Context): CacheUsageReport {
        var totalBytes = 0L
        var totalFiles = 0
        var tempPartBytes = 0L
        var abandonedCount = 0

        val dirs = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            File(context.cacheDir, "transfers"),
            File(context.filesDir, "transfers_temp")
        )

        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val length = file.length()
                        totalBytes += length
                        totalFiles++
                        val name = file.name.lowercase()
                        if (name.endsWith(".part") || name.endsWith(".tmp") || name.startsWith("aerobeam_") || file.parentFile?.name == "transfers") {
                            tempPartBytes += length
                            abandonedCount++
                        }
                    }
                }
            }
        }

        return CacheUsageReport(
            totalCacheBytes = totalBytes,
            tempPartBytes = tempPartBytes,
            abandonedTransfersCount = abandonedCount,
            totalCacheFilesCount = totalFiles,
            formattedTotalCache = StorageSmartManager.formatBytes(totalBytes),
            formattedTempParts = StorageSmartManager.formatBytes(tempPartBytes)
        )
    }

    fun cleanAppTemporaryCache(
        context: Context,
        activeTransferNames: Set<String> = emptySet(),
        cleanAllCache: Boolean = false
    ): CleanupResult {
        var bytesFreed = 0L
        var filesRemoved = 0

        val dirs = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            File(context.cacheDir, "transfers"),
            File(context.filesDir, "transfers_temp")
        )

        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue

            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val name = file.name
                    val isPart = name.endsWith(".part") || name.endsWith(".tmp")
                    val isTransfer = file.parentFile?.name == "transfers"

                    val isProtected = activeTransferNames.any { active ->
                        name.startsWith(active) || name.contains(active)
                    }

                    if (!isProtected && (cleanAllCache || isPart || isTransfer)) {
                        val size = file.length()
                        if (file.delete()) {
                            bytesFreed += size
                            filesRemoved++
                        }
                    }
                } else if (file.isDirectory && file.name == "transfers") {
                    file.listFiles()?.forEach { subFile ->
                        val isProtected = activeTransferNames.any { active ->
                            subFile.name.startsWith(active) || subFile.name.contains(active)
                        }
                        if (!isProtected) {
                            val size = subFile.length()
                            if (subFile.delete()) {
                                bytesFreed += size
                                filesRemoved++
                            }
                        }
                    }
                }
            }
        }

        return CleanupResult(
            success = true,
            bytesFreed = bytesFreed,
            filesRemovedCount = filesRemoved,
            formattedBytesFreed = StorageSmartManager.formatBytes(bytesFreed),
            message = "Safely cleaned $filesRemoved cache file(s), freed ${StorageSmartManager.formatBytes(bytesFreed)}."
        )
    }

    fun deleteActualFile(context: Context, record: TransferRecord): FileDeletionResult {
        var bytesFreed = 0L

        // 1. Direct local file path check
        if (!record.localFilePath.isNullOrBlank()) {
            val file = File(record.localFilePath)
            if (file.exists() && file.isFile) {
                bytesFreed = file.length()
                val deleted = file.delete()
                if (deleted) {
                    return FileDeletionResult(
                        recordId = record.id,
                        fileName = record.fileName,
                        wasDeletedFromDisk = true,
                        bytesFreed = bytesFreed,
                        message = "File permanently deleted from device storage (${StorageSmartManager.formatBytes(bytesFreed)})."
                    )
                }
            }
        }

        // 2. Check transfer cache folder
        val cacheCandidate = File(context.cacheDir, "transfers/${record.fileName}")
        if (cacheCandidate.exists() && cacheCandidate.isFile) {
            bytesFreed = cacheCandidate.length()
            if (cacheCandidate.delete()) {
                return FileDeletionResult(
                    recordId = record.id,
                    fileName = record.fileName,
                    wasDeletedFromDisk = true,
                    bytesFreed = bytesFreed,
                    message = "File permanently deleted from cache storage (${StorageSmartManager.formatBytes(bytesFreed)})."
                )
            }
        }

        // 3. Content URI attempt (MediaStore / DocumentsProvider)
        if (!record.contentUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(record.contentUriString)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    return FileDeletionResult(
                        recordId = record.id,
                        fileName = record.fileName,
                        wasDeletedFromDisk = true,
                        bytesFreed = record.totalBytes,
                        message = "File deleted via system storage provider."
                    )
                }
            } catch (secEx: SecurityException) {
                return FileDeletionResult(
                    recordId = record.id,
                    fileName = record.fileName,
                    wasDeletedFromDisk = false,
                    bytesFreed = 0L,
                    requiresSystemAuth = true,
                    message = "Requires user confirmation in system dialog to modify or delete storage item."
                )
            } catch (_: Exception) {}
        }

        return FileDeletionResult(
            recordId = record.id,
            fileName = record.fileName,
            wasDeletedFromDisk = false,
            bytesFreed = 0L,
            message = "File not found on device storage or already removed."
        )
    }

    fun deleteMultipleActualFiles(context: Context, records: List<TransferRecord>): BatchDeletionResult {
        val details = mutableListOf<FileDeletionResult>()
        var successCount = 0
        var totalFreed = 0L
        var failCount = 0

        for (record in records) {
            val result = deleteActualFile(context, record)
            details.add(result)
            if (result.wasDeletedFromDisk) {
                successCount++
                totalFreed += result.bytesFreed
            } else {
                failCount++
            }
        }

        return BatchDeletionResult(
            totalAttempted = records.size,
            successfullyDeletedCount = successCount,
            bytesFreed = totalFreed,
            failedCount = failCount,
            formattedBytesFreed = StorageSmartManager.formatBytes(totalFreed),
            details = details
        )
    }
}
