package com.example.engine

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EndToEndCryptoTest {
    private val crypto = EndToEndCrypto(SecureRandom())

    @Test
    fun bothPeersDeriveSameSessionKeyAndTransferEncryptedPayload() {
        val sender = crypto.generateEphemeralKeyPair()
        val receiver = crypto.generateEphemeralKeyPair()
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        val context = "Aero-v1|sender|receiver|pairing-transcript".toByteArray()
        val senderKey = crypto.deriveSessionKey(sender.private, receiver.public, salt, context)
        val receiverKey = crypto.deriveSessionKey(receiver.private, sender.public, salt, context)
        val plaintext = "private file chunk".toByteArray()
        val aad = "transfer-123|file-0|chunk-0".toByteArray()

        try {
            assertArrayEquals(senderKey, receiverKey)
            val packet = crypto.encrypt(senderKey, plaintext, aad)
            assertFalse(plaintext.contentEquals(packet.ciphertextAndTag))
            assertArrayEquals(plaintext, crypto.decrypt(receiverKey, packet, aad))
        } finally {
            senderKey.fill(0)
            receiverKey.fill(0)
        }
    }

    @Test
    fun modifiedCiphertextOrAssociatedDataIsRejected() {
        val sender = crypto.generateEphemeralKeyPair()
        val receiver = crypto.generateEphemeralKeyPair()
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        val key = crypto.deriveSessionKey(
            sender.private,
            receiver.public,
            salt,
            "Aero-v1|sender|receiver".toByteArray()
        )
        try {
            val packet = crypto.encrypt(key, "secret".toByteArray(), "expected-metadata".toByteArray())
            assertThrows(AEADBadTagException::class.java) {
                crypto.decrypt(key, packet, "changed-metadata".toByteArray())
            }
            val changedCiphertext = packet.ciphertextAndTag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            assertThrows(AEADBadTagException::class.java) {
                crypto.decrypt(key, packet.copy(ciphertextAndTag = changedCiphertext), "expected-metadata".toByteArray())
            }
        } finally {
            key.fill(0)
        }
    }

    @Test
    fun invalidKeyAndOversizedPayloadAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            crypto.encrypt(ByteArray(8), byteArrayOf(1), byteArrayOf())
        }
        val key = ByteArray(EndToEndCrypto.AES_KEY_BYTES)
        assertThrows(IllegalArgumentException::class.java) {
            crypto.encrypt(key, ByteArray(EndToEndCrypto.MAX_PLAINTEXT_BYTES + 1), byteArrayOf())
        }
    }
}
