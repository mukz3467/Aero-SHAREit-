package com.example.engine

import java.security.SecureRandom

/**
 * In-memory lifecycle manager for short-lived pairing invitations.
 *
 * Invitations do not discover a peer, establish a transport, authenticate an
 * ECDH transcript, or encrypt files by themselves. Callers must bind a consumed
 * invitation to the authenticated handshake and enforce receiver approval.
 */
class PairingCodeManager(
    private val random: SecureRandom = SecureRandom(),
    lifetimeMillis: Long = DEFAULT_LIFETIME_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
) {
    enum class Method { PIN, QR }

    data class Invitation(
        val method: Method,
        val code: String,
        val expiresAtMillis: Long
    )

    private val lifetimeMillis = lifetimeMillis.coerceIn(1L, MAX_LIFETIME_MS)
    private var activeInvitation: Invitation? = null
    private var attemptsLeft: Int = 0
    private var previousPin: String? = null

    init {
        require(maxAttempts > 0) { "Attempt limit must be positive." }
    }

    @Synchronized
    fun createPin(nowMillis: Long = System.currentTimeMillis()): Invitation {
        var pin: String
        do {
            pin = random.nextInt(1_000_000).toString().padStart(6, '0')
        } while (pin == previousPin)
        previousPin = pin
        return replaceInvitation(Method.PIN, pin, nowMillis)
    }

    @Synchronized
    fun createQrToken(nowMillis: Long = System.currentTimeMillis()): Invitation {
        // 32 random bytes encoded as 43 URL-safe characters (256 bits of entropy).
        val bytes = ByteArray(QR_TOKEN_BYTES).also(random::nextBytes)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val token = buildString(bytes.size * 8 / 6 + 1) {
            var buffer = 0
            var bits = 0
            for (byte in bytes) {
                buffer = (buffer shl 8) or (byte.toInt() and 0xff)
                bits += 8
                while (bits >= 6) {
                    bits -= 6
                    append(alphabet[(buffer shr bits) and 0x3f])
                }
            }
            if (bits > 0) append(alphabet[(buffer shl (6 - bits)) and 0x3f])
        }
        return replaceInvitation(Method.QR, token, nowMillis)
    }

    @Synchronized
    fun consume(code: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val invitation = activeInvitation ?: return false
        if (nowMillis >= invitation.expiresAtMillis || attemptsLeft <= 0) {
            clearInvitation()
            return false
        }
        if (code != invitation.code) {
            attemptsLeft -= 1
            if (attemptsLeft <= 0) clearInvitation()
            return false
        }
        clearInvitation()
        return true
    }

    @Synchronized
    fun attemptsRemaining(nowMillis: Long = System.currentTimeMillis()): Int {
        val invitation = activeInvitation ?: return 0
        if (nowMillis >= invitation.expiresAtMillis) {
            clearInvitation()
            return 0
        }
        return attemptsLeft
    }

    private fun replaceInvitation(method: Method, code: String, nowMillis: Long): Invitation {
        val expiresAt = if (nowMillis > Long.MAX_VALUE - lifetimeMillis) {
            Long.MAX_VALUE
        } else {
            nowMillis + lifetimeMillis
        }
        val invitation = Invitation(method, code, expiresAt)
        activeInvitation = invitation
        attemptsLeft = maxAttempts
        return invitation
    }

    private fun clearInvitation() {
        activeInvitation = null
        attemptsLeft = 0
    }

    companion object {
        const val DEFAULT_LIFETIME_MS = 120_000L
        const val MAX_LIFETIME_MS = 300_000L
        const val DEFAULT_MAX_ATTEMPTS = 5
        private const val QR_TOKEN_BYTES = 32
    }
}
