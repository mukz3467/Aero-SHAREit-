package com.example.engine

import java.security.SecureRandom
import java.util.Base64

/**
 * Local, in-memory pairing-code lifecycle for Aero TrustLink.
 *
 * This helper manages short-lived pairing invitations only. It does not discover
 * radios, authenticate a remote peer, perform a handshake, or encrypt files.
 * A successful code check must be followed by authenticated key exchange and
 * explicit receiver approval. Never derive an encryption key from the PIN.
 *
 * Keep one instance per active pairing flow. For distributed PIN discovery,
 * the transport must securely advertise the invitation and rate-limit attempts
 * across the whole invitation, not just within one process.
 */
class PairingCodeManager(
    private val random: SecureRandom = SecureRandom(),
    private val lifetimeMillis: Long = DEFAULT_LIFETIME_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
) {
    data class Invitation internal constructor(
        val method: Method,
        val code: String,
        val expiresAtMillis: Long
    )

    enum class Method { PIN, QR }

    private var active: Invitation? = null
    private var attempts = 0

    init {
        require(lifetimeMillis in 1_000L..MAX_LIFETIME_MS) { "Invalid invitation lifetime." }
        require(maxAttempts in 1..20) { "Invalid attempt limit." }
    }

    @Synchronized
    fun createPin(nowMillis: Long = System.currentTimeMillis()): Invitation {
        val number = random.nextInt(PIN_SPACE)
        val invitation = Invitation(Method.PIN, number.toString().padStart(PIN_DIGITS, '0'), nowMillis + lifetimeMillis)
        active = invitation
        attempts = 0
        return invitation
    }

    /**
     * QR invitation carries a high-entropy, URL-safe random token, not a 6-digit PIN.
     * The caller should encode a versioned payload and must not put personal data in it.
     */
    @Synchronized
    fun createQrToken(nowMillis: Long = System.currentTimeMillis()): Invitation {
        val bytes = ByteArray(QR_TOKEN_BYTES).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        bytes.fill(0)
        val invitation = Invitation(Method.QR, token, nowMillis + lifetimeMillis)
        active = invitation
        attempts = 0
        return invitation
    }

    /**
     * Returns true once only, while the invitation is unexpired and attempts remain.
     * Caller MUST still perform authenticated ECDH and require receiver approval.
     */
    @Synchronized
    fun consume(candidate: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val invitation = active ?: return false
        if (nowMillis >= invitation.expiresAtMillis || attempts >= maxAttempts) {
            clear()
            return false
        }
        if (candidate != invitation.code) {
            attempts++
            if (attempts >= maxAttempts) clear()
            return false
        }
        clear()
        return true
    }

    @Synchronized
    fun invalidate() {
        clear()
    }

    @Synchronized
    fun attemptsRemaining(nowMillis: Long = System.currentTimeMillis()): Int {
        val invitation = active ?: return 0
        if (nowMillis >= invitation.expiresAtMillis) {
            clear()
            return 0
        }
        return (maxAttempts - attempts).coerceAtLeast(0)
    }

    private fun clear() {
        active = null
        attempts = 0
    }

    companion object {
        const val PIN_DIGITS = 6
        const val DEFAULT_LIFETIME_MS = 120_000L
        const val MAX_LIFETIME_MS = 300_000L
        const val DEFAULT_MAX_ATTEMPTS = 5
        private const val PIN_SPACE = 1_000_000
        private const val QR_TOKEN_BYTES = 32
    }
}
