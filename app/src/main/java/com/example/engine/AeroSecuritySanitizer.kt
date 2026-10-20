package com.example.engine

import java.io.File

object AeroSecuritySanitizer {
    fun sanitizeFilename(rawName: String): String {
        var clean = File(rawName).name
        clean = clean.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        if (clean.isBlank()) clean = "received_file_${System.currentTimeMillis()}"
        return clean
    }

    fun resolveUniqueFile(directory: File, baseName: String): File {
        val sanitized = sanitizeFilename(baseName)
        var target = File(directory, sanitized)
        if (!target.exists()) return target

        val nameWithoutExt = sanitized.substringBeforeLast('.', sanitized)
        val ext = if (sanitized.contains('.')) ".${sanitized.substringAfterLast('.')}" else ""

        var counter = 1
        while (target.exists()) {
            target = File(directory, "${nameWithoutExt}_($counter)$ext")
            counter++
        }
        return target
    }
}
