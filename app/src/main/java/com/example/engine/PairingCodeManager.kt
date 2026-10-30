package com.example.engine

import com.example.model.PairingSession
import java.net.NetworkInterface
import java.util.UUID

object PairingCodeManager {
    fun generatePairingSession(
        hostIp: String = getLocalIpAddress(),
        port: Int = AeroTransferProtocol.DEFAULT_PORT
    ): PairingSession {
        val code = generateCode()
        return PairingSession(
            sessionId = UUID.randomUUID().toString(),
            code = code,
            hostIp = hostIp,
            port = port,
            expiresAt = System.currentTimeMillis() + AeroTransferProtocol.PAIRING_SESSION_TTL_MS
        )
    }

    fun parseManualCode(
        code: String,
        hostIp: String,
        port: Int = AeroTransferProtocol.DEFAULT_PORT
    ): PairingSession? {
        if (!code.matches(Regex("\\d{" + AeroTransferProtocol.PAIRING_CODE_LENGTH + "}"))) {
            return null
        }

        return PairingSession(
            sessionId = UUID.randomUUID().toString(),
            code = code,
            hostIp = hostIp,
            port = port,
            expiresAt = System.currentTimeMillis() + AeroTransferProtocol.PAIRING_SESSION_TTL_MS
        )
    }

    fun parseQrPayload(payload: String): PairingSession? {
        if (!payload.startsWith("aeroshare://pair?")) return null

        val params = payload.removePrefix("aeroshare://pair?")
            .split("&")
            .associate { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size != 2) return null
                parts[0] to parts[1]
            }

        val code = params["code"] ?: return null
        val hostIp = params["host"] ?: return null
        val port = params["port"]?.toIntOrNull() ?: AeroTransferProtocol.DEFAULT_PORT
        val sessionId = params["session"] ?: UUID.randomUUID().toString()

        return PairingSession(
            sessionId = sessionId,
            code = code,
            hostIp = hostIp,
            port = port,
            expiresAt = System.currentTimeMillis() + AeroTransferProtocol.PAIRING_SESSION_TTL_MS
        )
    }

    fun generateCode(): String {
        return (100000..999999).random().toString()
    }

    fun getLocalIpAddress(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (network.isLoopback || !network.isUp) continue

            val addresses = network.inetAddresses ?: continue
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress != null && address.hostAddress.contains(".")) {
                    return address.hostAddress
                }
            }
        }
        return "127.0.0.1"
    }
}
