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

    fun deriveAesKey(sharedSecret: ByteArray, salt: ByteArray, info: String): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(sharedSecret)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        mac.update(0x01.toByte())
        val okm = mac.doFinal()
        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    fun generateSasCode(sharedSecret: ByteArray, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(sharedSecret)
        md.update(salt)
        val digest = md.digest()
        val num = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
        val code = (num % 900000) + 100000
        return code.toString()
    }

    fun encryptChunk(data: ByteArray, offset: Int, length: Int, key: SecretKeySpec, aad: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.updateAAD(aad)
        val cipherBytes = cipher.doFinal(data, offset, length)
        return iv + cipherBytes
    }

    fun decryptChunk(encryptedData: ByteArray, key: SecretKeySpec, aad: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val cipherBytes = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherBytes)
    }
}
