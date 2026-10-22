package com.example

import com.example.engine.AeroCryptoEngine
import com.example.engine.AeroSecuritySanitizer
import com.example.engine.FileIntegrityEngine
import com.example.engine.SafeDropManager
import com.example.engine.StorageSmartManager
import com.example.model.FileCategory
import com.example.model.FileItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class AeroEngineUnitTest {

    @Test
    fun testEcdhKeyExchangeAndAesGcm() {
        // Peer A and Peer B generate ECDH keypairs
        val aliceKeyPair = AeroCryptoEngine.generateEcdhKeyPair()
        val bobKeyPair = AeroCryptoEngine.generateEcdhKeyPair()

        // Compute shared secrets
        val aliceShared = AeroCryptoEngine.computeSharedSecret(aliceKeyPair.private, bobKeyPair.public)
        val bobShared = AeroCryptoEngine.computeSharedSecret(bobKeyPair.private, aliceKeyPair.public)

        assertArrayEquals("ECDH shared secrets must match", aliceShared, bobShared)

        // Session salt
        val salt = "AeroSessionSalt2026".toByteArray(StandardCharsets.UTF_8)
        val aliceKey = AeroCryptoEngine.deriveAesKey(aliceShared, salt, "AeroDataTransfer")
        val bobKey = AeroCryptoEngine.deriveAesKey(bobShared, salt, "AeroDataTransfer")

        assertArrayEquals("Derived AES keys must match", aliceKey.encoded, bobKey.encoded)

        // SAS verification code
        val aliceSas = AeroCryptoEngine.generateSasCode(aliceShared, salt)
        val bobSas = AeroCryptoEngine.generateSasCode(bobShared, salt)
        assertEquals("SAS pairing codes must be identical", aliceSas, bobSas)
        assertEquals(6, aliceSas.length)

        // Encrypt & Decrypt chunk with AAD
        val originalMessage = "AeroShare high-speed P2P encrypted payload".toByteArray(StandardCharsets.UTF_8)
        val aad = "file_index_0_offset_0".toByteArray(StandardCharsets.UTF_8)

        val encrypted = AeroCryptoEngine.encryptChunk(originalMessage, 0, originalMessage.size, aliceKey, aad)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > originalMessage.size)

        val decrypted = AeroCryptoEngine.decryptChunk(encrypted, bobKey, aad)
        assertArrayEquals("Decrypted payload must match original", originalMessage, decrypted)
    }

    @Test
    fun testAesGcmRejectsTamperedCiphertext() {
        val alice = AeroCryptoEngine.generateEcdhKeyPair()
        val bob = AeroCryptoEngine.generateEcdhKeyPair()
        val salt = ByteArray(32) { it.toByte() }
        val shared = AeroCryptoEngine.computeSharedSecret(alice.private, bob.public)
        val key = AeroCryptoEngine.deriveAesKey(shared, salt, "AeroDataTransfer")
        val aad = "file=0;offset=0".toByteArray(StandardCharsets.UTF_8)
        val plaintext = "private payload".toByteArray(StandardCharsets.UTF_8)
        val encrypted = AeroCryptoEngine.encryptChunk(plaintext, 0, plaintext.size, key, aad)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        var rejected = false
        try {
            AeroCryptoEngine.decryptChunk(encrypted, key, aad)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("AES-GCM must reject modified ciphertext", rejected)
    }

    @Test
    fun testAesGcmRejectsWrongAdditionalAuthenticatedData() {
        val alice = AeroCryptoEngine.generateEcdhKeyPair()
        val bob = AeroCryptoEngine.generateEcdhKeyPair()
        val salt = ByteArray(32) { (it * 3).toByte() }
        val shared = AeroCryptoEngine.computeSharedSecret(alice.private, bob.public)
        val key = AeroCryptoEngine.deriveAesKey(shared, salt, "AeroDataTransfer")
        val plaintext = "authenticated chunk".toByteArray(StandardCharsets.UTF_8)
        val encrypted = AeroCryptoEngine.encryptChunk(
            plaintext, 0, plaintext.size, key, "file=1;offset=0".toByteArray(StandardCharsets.UTF_8)
        )

        var rejected = false
        try {
            AeroCryptoEngine.decryptChunk(
                encrypted, key, "file=1;offset=4096".toByteArray(StandardCharsets.UTF_8)
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("AES-GCM must reject changed AAD", rejected)
    }

    @Test
    fun testSha256ChecksumIntegrity() {
        val payload = "Hello AeroShare P2P Offline Engine".toByteArray(StandardCharsets.UTF_8)
        val hash = FileIntegrityEngine.calculateSha256(payload)

        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 produces 64 hex characters
        assertTrue(FileIntegrityEngine.verifyChecksum(hash, hash.uppercase()))
        assertFalse(FileIntegrityEngine.verifyChecksum(hash, "wrong_hash_value"))
    }

    @Test
    fun testSecuritySanitizerPathTraversal() {
        val maliciousRaw1 = "../../../../etc/shadow"
        val sanitized1 = AeroSecuritySanitizer.sanitizeFilename(maliciousRaw1)
        assertEquals("shadow", sanitized1)

        val maliciousRaw2 = "my..photo/\\:*?\"<>|.png"
        val sanitized2 = AeroSecuritySanitizer.sanitizeFilename(maliciousRaw2)
        assertFalse("Must not contain directory traversal slashes", sanitized2.contains("/"))
        assertFalse("Must not contain backslashes", sanitized2.contains("\\"))

        val emptyName = "   "
        val sanitized3 = AeroSecuritySanitizer.sanitizeFilename(emptyName)
        assertTrue(sanitized3.startsWith("received_file_"))
    }

    @Test
    fun testResolveUniqueFileCollisions() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "aerotest_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val file1 = File(tempDir, "document.pdf")
            file1.writeText("first version")

            val uniqueTarget = AeroSecuritySanitizer.resolveUniqueFile(tempDir, "document.pdf")
            assertEquals("document_(1).pdf", uniqueTarget.name)
            assertFalse(uniqueTarget.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testStorageSmartManagerFormatting() {
        assertEquals("0 B", StorageSmartManager.formatBytes(0L))
        assertEquals("500 B", StorageSmartManager.formatBytes(500L))
        assertEquals("1.0 KB", StorageSmartManager.formatBytes(1024L))
        assertEquals("1.0 MB", StorageSmartManager.formatBytes(1024L * 1024L))
        assertEquals("1.5 GB", StorageSmartManager.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testSafeDropTrust() {
        SafeDropManager.clearTrustedDevices()
        val peer = "Pixel-Fold-Peer"
        assertFalse(SafeDropManager.isDeviceTrusted(peer))

        SafeDropManager.addTrustedDevice(peer)
        assertTrue(SafeDropManager.isDeviceTrusted(peer))

        SafeDropManager.removeTrustedDevice(peer)
        assertFalse(SafeDropManager.isDeviceTrusted(peer))
    }

    @Test
    fun testFileDuplicateDetection() {
        val existingNames = setOf("existing.mp4")
        val candidateFiles = listOf(
            FileItem(
                id = "1",
                name = "existing.mp4",
                sizeBytes = 500L,
                sizeFormatted = "500 B",
                mimeType = "video/mp4",
                category = FileCategory.VIDEOS
            ),
            FileItem(
                id = "2",
                name = "unique.mp3",
                sizeBytes = 300L,
                sizeFormatted = "300 B",
                mimeType = "audio/mpeg",
                category = FileCategory.AUDIO
            ),
            FileItem(
                id = "3",
                name = "unique.mp3",
                sizeBytes = 300L,
                sizeFormatted = "300 B",
                mimeType = "audio/mpeg",
                category = FileCategory.AUDIO
            )
        )

        val duplicates = FileIntegrityEngine.findDuplicates(candidateFiles, existingNames)
        assertEquals(2, duplicates.size)
        assertTrue(duplicates.any { it.id == "1" })
        assertTrue(duplicates.any { it.id == "3" })
    }

    @Test
    fun testCryptoRejectsMalformedChunkBoundsAndShortCiphertext() {
        val alice = AeroCryptoEngine.generateEcdhKeyPair()
        val bob = AeroCryptoEngine.generateEcdhKeyPair()
        val shared = AeroCryptoEngine.computeSharedSecret(alice.private, bob.public)
        val key = AeroCryptoEngine.deriveAesKey(shared, ByteArray(32) { it.toByte() }, "AeroDataTransfer")
        var shortCiphertextRejected = false
        try {
            AeroCryptoEngine.decryptChunk(ByteArray(12), key, byteArrayOf())
        } catch (_: IllegalArgumentException) {
            shortCiphertextRejected = true
        }
        assertTrue("Short encrypted packets must be rejected before parsing", shortCiphertextRejected)

        var invalidBoundsRejected = false
        try {
            AeroCryptoEngine.encryptChunk(byteArrayOf(1, 2, 3), 2, 2, key, byteArrayOf())
        } catch (_: IllegalArgumentException) {
            invalidBoundsRejected = true
        }
        assertTrue("Invalid plaintext slice bounds must be rejected", invalidBoundsRejected)
    }

    @Test
    fun testSasCodeUsesSixDigits() {
        val pairA = AeroCryptoEngine.generateEcdhKeyPair()
        val pairB = AeroCryptoEngine.generateEcdhKeyPair()
        val shared = AeroCryptoEngine.computeSharedSecret(pairA.private, pairB.public)
        val code = AeroCryptoEngine.generateSasCode(shared, ByteArray(32) { (it + 1).toByte() })
        assertTrue("SAS must always be six decimal digits", code.matches(Regex("\\d{6}")))
    }
}
