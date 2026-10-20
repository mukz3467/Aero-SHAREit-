package com.example.engine

import com.example.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileIntegrityEngine {
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyChecksum(expected: String, actual: String): Boolean {
        return expected.trim().equals(actual.trim(), ignoreCase = true)
    }

    fun findDuplicates(files: List<FileItem>, existingFileNames: Set<String>): List<FileItem> {
        val seen = mutableSetOf<String>()
        val duplicates = mutableListOf<FileItem>()
        for (f in files) {
            val key = "${f.name}_${f.sizeBytes}"
            if (existingFileNames.contains(f.name) || seen.contains(key)) {
                duplicates.add(f)
            } else {
                seen.add(key)
            }
        }
        return duplicates
    }
}
