package com.example.engine

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Radio-managed, offline peer transport for the QR send/receive flow.
 *
 * The receiver advertises a one-time QR token as its endpoint name. The sender
 * scans that QR token and discovers only the matching advertiser. Possession
 * of the short-lived QR token is the pairing authorization; Nearby handles
 * the underlying Bluetooth/BLE/Wi-Fi medium selection and encrypted channel.
 *
 * This class deliberately exposes FILE payloads instead of opening TCP sockets.
 * Callers must still handle file metadata, destination selection and integrity
 * verification in their transfer workflow.
 */
class AeroNearbyTransport(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onReady()
        fun onPeerFound(endpointId: String, endpointName: String)
        fun onConnected(endpointId: String)
        fun onDisconnected(endpointId: String)
        fun onIncomingFile(endpointId: String, file: File, payloadId: Long)
        fun onPayloadProgress(endpointId: String, payloadId: Long, transferred: Long, total: Long, status: Int)
        fun onError(message: String)
    }

    companion object {
        const val SERVICE_ID = "com.aistudio.aeroshare.kxveqm.NEARBY_V1"
        private const val ENDPOINT_PREFIX = "AERO_QR_V1:"
    }

    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val connected = ConcurrentHashMap.newKeySet<String>()
    private val discovered = ConcurrentHashMap<String, String>()
    @Volatile private var activeToken: String? = null
    @Volatile private var advertising = false
    @Volatile private var discovering = false

    fun startReceiving(qrToken: String) {
        if (!validToken(qrToken)) {
            listener.onError("Invalid or expired QR pairing token.")
            return
        }
        stopDiscovery()
        activeToken = qrToken
        val endpointName = ENDPOINT_PREFIX + qrToken
        client.startAdvertising(
            endpointName,
            SERVICE_ID,
            lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            advertising = true
            listener.onReady()
        }.addOnFailureListener { error ->
            advertising = false
            listener.onError("Could not start nearby receiving: ${error.localizedMessage ?: "unknown error"}")
        }
    }

    fun startSending(scannedQrToken: String) {
        if (!validToken(scannedQrToken)) {
            listener.onError("This QR code is not a valid Aero SHAREit pairing code.")
            return
        }
        stopAdvertising()
        activeToken = scannedQrToken
        discovered.clear()
        client.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    if (info.endpointName == ENDPOINT_PREFIX + scannedQrToken) {
                        discovered[endpointId] = info.endpointName
                        listener.onPeerFound(endpointId, Build.MODEL)
                        client.requestConnection(
                            Build.MODEL.take(30),
                            endpointId,
                            lifecycleCallback
                        ).addOnFailureListener { error ->
                            listener.onError("Connection request failed: ${error.localizedMessage ?: "unknown error"}")
                        }
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    discovered.remove(endpointId)
                }
            },
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            discovering = true
            listener.onReady()
        }.addOnFailureListener { error ->
            discovering = false
            listener.onError("Could not scan for the receiver: ${error.localizedMessage ?: "unknown error"}")
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val expected = activeToken?.let { ENDPOINT_PREFIX + it }
            val isExpectedPeer = info.endpointName == expected ||
                (discovered.containsKey(endpointId) && discovered[endpointId] == expected)
            if (!isExpectedPeer) {
                client.rejectConnection(endpointId)
                return
            }
            // The QR contains a cryptographically random, one-use token. Only the
            // peer presenting the exact token is auto-accepted, avoiding a second
            // manual confirmation step for the normal QR workflow.
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error ->
                    listener.onError("Could not accept nearby connection: ${error.localizedMessage ?: "unknown error"}")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected.add(endpointId)
                listener.onConnected(endpointId)
                stopDiscovery()
            } else {
                listener.onError("Nearby connection failed (status ${result.status.statusCode}).")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            listener.onDisconnected(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val received = payload.asFile()?.asJavaFile()
            if (received != null) {
                listener.onIncomingFile(endpointId, received, payload.id)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            listener.onPayloadProgress(
                endpointId,
                update.payloadId,
                update.bytesTransferred,
                update.totalBytes,
                update.status
            )
        }
    }

    fun sendFile(endpointId: String, file: File) {
        if (endpointId !in connected) {
            listener.onError("The receiver is not connected.")
            return
        }
        if (!file.isFile || !file.canRead()) {
            listener.onError("Selected file cannot be read: ${file.name}")
            return
        }
        client.sendPayload(endpointId, Payload.fromFile(file))
            .addOnFailureListener { error ->
                listener.onError("File transfer could not start: ${error.localizedMessage ?: "unknown error"}")
            }
    }

    fun sendBytes(endpointId: String, bytes: ByteArray) {
        if (endpointId !in connected) {
            listener.onError("The receiver is not connected.")
            return
        }
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener { error ->
                listener.onError("Message transfer failed: ${error.localizedMessage ?: "unknown error"}")
            }
    }

    fun stopAdvertising() {
        if (advertising) client.stopAdvertising()
        advertising = false
    }

    fun stopDiscovery() {
        if (discovering) client.stopDiscovery()
        discovering = false
    }

    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        connected.remove(endpointId)
    }

    fun stopAll() {
        stopAdvertising()
        stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
        discovered.clear()
        activeToken = null
    }

    private fun validToken(value: String): Boolean =
        value.length in 24..64 && value.matches(Regex("[A-Za-z0-9_-]+"))
}
