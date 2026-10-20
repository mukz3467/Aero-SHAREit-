package com.example.engine

import java.util.UUID

data class SafeDropRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val peerName: String,
    val peerModel: String,
    val peerIp: String,
    val filesCount: Int,
    val totalSizeBytes: Long,
    val totalSizeFormatted: String,
    val pinCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object SafeDropManager {
    private val trustedDevices = mutableSetOf<String>()

    fun isDeviceTrusted(peerName: String): Boolean {
        return trustedDevices.contains(peerName.trim())
    }

    fun addTrustedDevice(peerName: String) {
        trustedDevices.add(peerName.trim())
    }

    fun removeTrustedDevice(peerName: String) {
        trustedDevices.remove(peerName.trim())
    }

    fun clearTrustedDevices() {
        trustedDevices.clear()
    }
}
