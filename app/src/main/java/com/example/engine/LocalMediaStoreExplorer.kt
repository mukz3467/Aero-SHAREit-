package com.example.engine

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.model.FileCategory
import com.example.model.FileItem

data class MediaStoreScanResult(
    val allFiles: List<FileItem>,
    val categoryCounts: Map<FileCategory, Int>,
    val categorySizes: Map<FileCategory, Long>,
    val hasPermission: Boolean
)

object LocalMediaStoreExplorer {

    fun queryAllCategorized(context: Context): MediaStoreScanResult {
        val files = mutableListOf<FileItem>()
        val counts = mutableMapOf<FileCategory, Int>()
        val sizes = mutableMapOf<FileCategory, Long>()

        queryCategory(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.PHOTOS, files, counts, sizes)
        queryCategory(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEOS, files, counts, sizes)
        queryCategory(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCategory.AUDIO, files, counts, sizes)

        val filesUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        queryFiles(context, filesUri, files, counts, sizes)

        return MediaStoreScanResult(
            allFiles = files,
            categoryCounts = counts,
            categorySizes = sizes,
            hasPermission = true
        )
    }

    fun queryGalleryOnly(context: Context): MediaStoreScanResult {
        val files = mutableListOf<FileItem>()
        val counts = mutableMapOf<FileCategory, Int>()
        val sizes = mutableMapOf<FileCategory, Long>()

        queryCategory(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.PHOTOS, files, counts, sizes)
        queryCategory(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEOS, files, counts, sizes)
        queryCategory(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCategory.AUDIO, files, counts, sizes)

        return MediaStoreScanResult(
            allFiles = files,
            categoryCounts = counts,
            categorySizes = sizes,
            hasPermission = true
        )
    }

    private fun queryCategory(
        context: Context,
        uri: android.net.Uri,
        category: FileCategory,
        outList: MutableList<FileItem>,
        counts: MutableMap<FileCategory, Int>,
        sizes: MutableMap<FileCategory, Long>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "file_$id"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val date = cursor.getLong(dateCol) * 1000L
                    val contentUri = ContentUris.withAppendedId(uri, id)

                    val item = FileItem(
                        id = "$category-$id",
                        name = name,
                        sizeBytes = size,
                        sizeFormatted = StorageSmartManager.formatBytes(size),
                        mimeType = mime,
                        uri = contentUri,
                        category = category,
                        extension = name.substringAfterLast('.', "").uppercase(),
                        dateModified = date
                    )
                    outList.add(item)
                    counts[category] = (counts[category] ?: 0) + 1
                    sizes[category] = (sizes[category] ?: 0L) + size
                }
            }
        } catch (_: Exception) {}
    }

    private fun queryFiles(
        context: Context,
        uri: android.net.Uri,
        outList: MutableList<FileItem>,
        counts: MutableMap<FileCategory, Int>,
        sizes: MutableMap<FileCategory, Long>
    ) {
        val docSelection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%'"
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        try {
            context.contentResolver.query(uri, projection, docSelection, null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "doc_$id"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "application/pdf"
                    val date = cursor.getLong(dateCol) * 1000L
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val ext = name.substringAfterLast('.', "").lowercase()

                    val cat = when (ext) {
                        "apk" -> FileCategory.APPS
                        "zip", "rar", "7z", "tar", "gz" -> FileCategory.ARCHIVES
                        else -> FileCategory.DOCUMENTS
                    }

                    val item = FileItem(
                        id = "$cat-$id",
                        name = name,
                        sizeBytes = size,
                        sizeFormatted = StorageSmartManager.formatBytes(size),
                        mimeType = mime,
                        uri = contentUri,
                        category = cat,
                        extension = ext.uppercase(),
                        dateModified = date
                    )
                    outList.add(item)
                    counts[cat] = (counts[cat] ?: 0) + 1
                    sizes[cat] = (sizes[cat] ?: 0L) + size
                }
            }
        } catch (_: Exception) {}
    }
}
