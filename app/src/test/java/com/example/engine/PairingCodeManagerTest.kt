package com.example.engine

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeManagerTest {
    @Test
    fun pinIsSingleUseAndExpires() {
        val manager = PairingCodeManager(SecureRandom(), lifetimeMillis = 2_000L)
        val invite = manager.createPin(nowMillis = 10_000)
        assertEquals(6, invite.code.length)
        assertTrue(manager.consume(invite.code, nowMillis = 10_100))
        assertFalse(manager.consume(invite.code, nowMillis = 10_101))

        val expired = manager.createPin(nowMillis = 20_000)
        assertFalse(manager.consume(expired.code, nowMillis = 22_000))
    }

    @Test
    fun repeatedWrongPinsExhaustInvitation() {
        val manager = PairingCodeManager(SecureRandom(), maxAttempts = 3)
        val invite = manager.createPin(nowMillis = 1_000)
        repeat(3) { assertFalse(manager.consume("wrong!", nowMillis = 1_100L + it)) }
        assertEquals(0, manager.attemptsRemaining(nowMillis = 1_200))
        assertFalse(manager.consume(invite.code, nowMillis = 1_300))
    }

    @Test
    fun qrTokenIsHighEntropyUrlSafeAndSingleUse() {
        val manager = PairingCodeManager()
        val invite = manager.createQrToken(nowMillis = 5_000)
        assertEquals(PairingCodeManager.Method.QR, invite.method)
        assertTrue(invite.code.length >= 40)
        assertTrue(invite.code.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(manager.consume(invite.code, nowMillis = 5_001))
        assertFalse(manager.consume(invite.code, nowMillis = 5_002))
    }

    @Test
    fun aNewInvitationReplacesThePreviousOne() {
        val manager = PairingCodeManager()
        val first = manager.createPin(nowMillis = 1_000)
        val second = manager.createPin(nowMillis = 1_001)
        assertNotEquals(first.code, second.code)
        assertFalse(manager.consume(first.code, nowMillis = 1_002))
        assertTrue(manager.consume(second.code, nowMillis = 1_003))
    }
}
