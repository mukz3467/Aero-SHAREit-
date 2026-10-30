package com.example.engine

import com.example.model.NearbyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import android.util.Base64
import java.util.Collections
import java.util.UUID
import java.security.SecureRandom

/**
 * Best-effort discovery for peers on the same local IP network.
 * This is LAN discovery; it does not negotiate Android Wi-Fi Direct groups.
 */
class NearbyDeviceDiscovery(
    private val deviceNameProvider: () -> String,
    private val onDevicesChanged: (List<NearbyDevice>) -> Unit,
    private val onLocalPairingCode: (String) -> Unit = {},
    private val tcpPort: Int = AeroTransferProtocol.DEFAULT_PORT
) {
    companion object {
        const val DISCOVERY_PORT = 8830
        private const val MAGIC = "AERO_DISCOVERY_V1"
        private const val PEER_TTL_MS = 8_000L
    }

    private val instanceId = UUID.randomUUID().toString()
    // This six-digit code is unpredictable, but it is broadcast on the LAN.
    // Treat it only as a discovery hint, never as peer authentication.
    private val pairingCode = (SecureRandom().nextInt(900_000) + 100_000).toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var stopped = false

    fun start() {
        if (job?.isActive == true) return
        stopped = false
        onLocalPairingCode(pairingCode)
        job = scope.launch {
            val peers = linkedMapOf<String, Pair<NearbyDevice, Long>>()
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
            } catch (_: Exception) {
                onDevicesChanged(emptyList())
                return@launch
            }

            socket.use { udp ->
                var lastBroadcast = 0L
                var lastPublishedSignature: String? = null
                val receiveBuffer = ByteArray(2048)
                while (isActive && !stopped && !udp.isClosed) {
                    val now = System.currentTimeMillis()
                    if (now - lastBroadcast >= 2000L) {
                        val safeName = Base64.encodeToString(
                            deviceNameProvider().take(80).toByteArray(Charsets.UTF_8),
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                        )
                        val message = "$MAGIC|$instanceId|$tcpPort|$safeName|$pairingCode".toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(message, message.size)
                        try {
                            for (address in broadcastAddresses()) {
                                packet.address = address
                                packet.port = DISCOVERY_PORT
                                udp.send(packet)
                            }
                        } catch (_: Exception) {
                            // A network may temporarily block broadcast; continue listening.
                        }
                        lastBroadcast = now
                    }

                    try {
                        val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        udp.receive(packet)
                        // Accept current five-field and older four-field packets for cross-version discovery.
                        val parts = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).split('|', limit = 5)
                        if ((parts.size == 4 || parts.size == 5) && parts[0] == MAGIC && parts[1] != instanceId) {
                            val peerPort = parts[2].toIntOrNull()
                            val peerName = try {
                                String(Base64.decode(parts[3], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                            } catch (_: Exception) {
                                ""
                            }
                            val ip = packet.address.hostAddress
                            if (peerPort != null && peerPort in 1..65535 && peerName.isNotBlank() && ip != null) {
                                val peerPairingCode = parts.getOrNull(4)
                                    ?.takeIf { it.matches(Regex("\\d{6}")) }
                                    .orEmpty()
                                peers[parts[1]] = NearbyDevice(
                                    id = parts[1],
                                    name = peerName.take(80),
                                    ipAddress = ip,
                                    port = peerPort,
                                    is5Ghz = false,
                                    signalStrength = 0,
                                    deviceModel = "Aero SHAREit peer",
                                    pairingCode = peerPairingCode
                                ) to System.currentTimeMillis()
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Periodic timeout allows stale peers to expire.
                    } catch (_: Exception) {
                        if (!udp.isClosed) delay(150)
                    }

                    val cutoff = System.currentTimeMillis() - PEER_TTL_MS
                    peers.entries.removeAll { it.value.second < cutoff }
                    val snapshot = peers.values.map { it.first }.sortedBy { it.name.lowercase() }
                    val signature = snapshot.joinToString(";") { "${it.id}|${it.ipAddress}|${it.port}|${it.name}" }
                    if (signature != lastPublishedSignature) {
                        lastPublishedSignature = signature
                        onDevicesChanged(snapshot)
                    }
                }
            }
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    interfaceAddress.broadcast?.let { addresses.add(it) }
                }
            }
        } catch (_: Exception) {
        }
        if (addresses.isEmpty()) {
            addresses.add(InetAddress.getByName("255.255.255.255"))
        }
        return addresses.toList()
    }

    fun stop() {
        stopped = true
        job?.cancel()
        job = null
    }
}
