package com.example.model

data class PairingSession(
    val sessionId: String,
    val code: String,
    val hostIp: String,
    val port: Int,
    val expiresAt: Long
)
