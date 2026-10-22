package com.example.engine

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption primitives for Aero's sender-to-receiver session.
 *
 * Important: ECDH by itself does NOT authenticate the peer and does not prevent
 * man-in-the-middle attacks. The caller must authenticate/bind the exchanged
 * public keys using a verified QR/PIN pairing transcript or a compared
 * short-authentication string before trusting this session key.
 *
 * This class does not provide radio transport, pairing discovery, persistent
 * identity management, or a complete transfer protocol.
 */
class EndToEndCrypto(
    private val random: SecureRandom = SecureRandom()
) {
    data class EncryptedPacket(
        val nonce: ByteArray,
        val ciphertextAndTag: ByteArray
    )

    fun generateEphemeralKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(EC_KEY_SIZE, random)
        return generator.generateKeyPair()
    }

    fun decodePeerPublicKey(encoded: ByteArray): PublicKey {
        require(encoded.isNotEmpty() && encoded.size <= MAX_PUBLIC_KEY_BYTES) {
            "Invalid public key length."
        }
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    /**
     * Derives a session key from ephemeral ECDH and HKDF-SHA-256.
     * The context should bind protocol version, sender/receiver roles, and the
     * authenticated pairing transcript. Do not use a short PIN as this context
     * or as the encryption key.
     */
    fun deriveSessionKey(
        localPrivateKey: java.security.PrivateKey,
        peerPublicKey: PublicKey,
        salt: ByteArray,
        context: ByteArray
    ): ByteArray {
        require(salt.size in MIN_SALT_BYTES..MAX_SALT_BYTES) { "Invalid session salt." }
        require(context.size in 1..MAX_CONTEXT_BYTES) { "Invalid key context." }

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPrivateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()
        return try {
            hkdfSha256(sharedSecret, salt, context, AES_KEY_BYTES)
        } finally {
            sharedSecret.fill(0)
        }
    }

    fun encrypt(
        sessionKey: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): EncryptedPacket {
        validateKey(sessionKey)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Payload exceeds the safe limit." }
        require(associatedData.size <= MAX_AAD_BYTES) { "Associated data exceeds the safe limit." }

        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return EncryptedPacket(nonce, cipher.doFinal(plaintext))
    }

    /**
     * Creates a short authentication string for both users to compare out-of-band.
     * The app must require an explicit match/confirmation before trusting the peer.
     * A six-digit code is a human verification aid, not a standalone encryption key.
     */
    fun shortAuthenticationString(sessionKey: ByteArray, transcript: ByteArray): String {
        validateKey(sessionKey)
        require(transcript.isNotEmpty() && transcript.size <= MAX_CONTEXT_BYTES) {
            "Invalid pairing transcript."
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sessionKey, "HmacSHA256"))
        val digest = mac.doFinal(transcript)
        val value = (((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or
            (digest[3].toLong() and 0xff)) % 1_000_000L
        digest.fill(0)
        return value.toString().padStart(6, '0')
    }

    /** Fails closed if the nonce, tag, key, ciphertext, or AAD is invalid. */
    fun decrypt(
        sessionKey: ByteArray,
        packet: EncryptedPacket,
        associatedData: ByteArray
    ): ByteArray {
        validateKey(sessionKey)
        require(packet.nonce.size == GCM_NONCE_BYTES) { "Invalid nonce length." }
        require(packet.ciphertextAndTag.size in GCM_TAG_BYTES..(MAX_PLAINTEXT_BYTES + GCM_TAG_BYTES)) {
            "Invalid encrypted payload length."
        }
        require(associatedData.size <= MAX_AAD_BYTES) { "Associated data exceeds the safe limit." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, packet.nonce))
        cipher.updateAAD(associatedData)
        // AEADBadTagException is intentionally allowed to propagate: callers must reject the packet.
        return cipher.doFinal(packet.ciphertextAndTag)
    }

    private fun validateKey(key: ByteArray) {
        require(key.size == AES_KEY_BYTES) { "Session key must be 256 bits." }
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(SHA256_BYTES) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        try {
            val output = ByteArray(length)
            var previous = ByteArray(0)
            var offset = 0
            var counter = 1
            while (offset < length) {
                mac.init(SecretKeySpec(prk, "HmacSHA256"))
                mac.update(previous)
                mac.update(info)
                mac.update(counter.toByte())
                val block = mac.doFinal()
                previous.fill(0)
                previous = block
                val count = minOf(block.size, length - offset)
                System.arraycopy(block, 0, output, offset, count)
                offset += count
                counter++
            }
            previous.fill(0)
            return output
        } finally {
            prk.fill(0)
        }
    }

    companion object {
        const val AES_KEY_BYTES = 32
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MAX_PLAINTEXT_BYTES = 1024 * 1024
        const val MAX_AAD_BYTES = 4096
        const val MAX_PUBLIC_KEY_BYTES = 1024
        const val MIN_SALT_BYTES = 16
        const val MAX_SALT_BYTES = 64
        const val MAX_CONTEXT_BYTES = 4096
        private const val EC_KEY_SIZE = 256
        private const val SHA256_BYTES = 32
    }
}
