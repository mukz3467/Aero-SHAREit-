package com.example.engine

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AeroCryptoEngine {
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BYTES = GCM_TAG_LENGTH / 8
    private const val AES_KEY_BYTES = 32
    private const val MAX_AAD_BYTES = 4096

    fun generateEcdhKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        return kpg.generateKeyPair()
    }

    fun computeSharedSecret(myPrivateKey: java.security.PrivateKey, peerPublicKey: java.security.PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myPrivateKey)
        ka.doPhase(peerPublicKey, true)
        return ka.generateSecret()
    }

    /** HKDF-SHA-256 extract-and-expand for one 32-byte AES-256 key. */
    fun deriveAesKey(sharedSecret: ByteArray, salt: ByteArray, info: String): SecretKeySpec {
        require(sharedSecret.size >= 16) { "ECDH shared secret is too short." }
        require(salt.size in 16..64) { "Session salt length is invalid." }
        require(info.isNotBlank() && info.toByteArray(Charsets.UTF_8).size <= 1024) {
            "Key derivation context is invalid."
        }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(sharedSecret)
        var okm: ByteArray? = null
        try {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(info.toByteArray(Charsets.UTF_8))
            mac.update(0x01.toByte())
            okm = mac.doFinal()
            return SecretKeySpec(okm.copyOf(AES_KEY_BYTES), "AES")
        } finally {
            prk.fill(0)
            okm?.fill(0)
        }
    }

    /**
     * Six-digit short authentication string. It is for out-of-band comparison,
     * not an encryption key; the UI must require users to verify it.
     */
    fun generateSasCode(sharedSecret: ByteArray, salt: ByteArray): String {
        require(sharedSecret.size >= 16) { "ECDH shared secret is too short." }
        require(salt.size in 16..64) { "Session salt length is invalid." }
        val md = MessageDigest.getInstance("SHA-256")
        md.update("Aero-SAS-v1".toByteArray(Charsets.UTF_8))
        md.update(sharedSecret)
        val digest = md.digest(salt)
        val value = (((digest[0].toLong() and 0xff) shl 24) or
            ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or
            (digest[3].toLong() and 0xff)) % 1_000_000L
        digest.fill(0)
        return value.toString().padStart(6, '0')
    }

    fun encryptChunk(data: ByteArray, offset: Int, length: Int, key: SecretKeySpec, aad: ByteArray): ByteArray {
        require(key.algorithm == "AES" && key.encoded?.size == AES_KEY_BYTES) {
            "AES-256 session key is required."
        }
        require(offset >= 0 && length > 0 && offset <= data.size - length) {
            "Invalid plaintext chunk bounds."
        }
        require(length <= AeroTransferProtocol.TURBO_CHUNK_SIZE) { "Chunk exceeds the protocol limit." }
        require(aad.size <= MAX_AAD_BYTES) { "Associated data exceeds the safe limit." }

        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.updateAAD(aad)
        val cipherBytes = cipher.doFinal(data, offset, length)
        return iv + cipherBytes
    }

    fun decryptChunk(encryptedData: ByteArray, key: SecretKeySpec, aad: ByteArray): ByteArray {
        require(key.algorithm == "AES" && key.encoded?.size == AES_KEY_BYTES) {
            "AES-256 session key is required."
        }
        require(encryptedData.size in (GCM_IV_LENGTH + GCM_TAG_BYTES)..(AeroTransferProtocol.TURBO_CHUNK_SIZE + GCM_IV_LENGTH + GCM_TAG_BYTES)) {
            "Encrypted chunk length is invalid."
        }
        require(aad.size <= MAX_AAD_BYTES) { "Associated data exceeds the safe limit." }

        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val cipherBytes = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherBytes)
    }
}
