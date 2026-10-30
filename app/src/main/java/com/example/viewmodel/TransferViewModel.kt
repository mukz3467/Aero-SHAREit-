package com.example.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.AeroNearbyTransport
import com.example.engine.AeroTransferClient
import com.example.engine.AeroTransferServer
import com.example.engine.AeroTransferProtocol
import com.example.engine.CacheUsageReport
import com.example.engine.FileIntegrityEngine
import com.example.engine.FileManagerAndCleanerEngine
import com.example.engine.LocalMediaStoreExplorer
import com.example.engine.NearbyDeviceDiscovery
import com.example.engine.PhoneToPcBridgeServer
import com.example.engine.SafeDropRequest
import com.example.engine.StorageSmartManager
import com.example.engine.TransferAutopilot
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FileReceiptItem
import com.example.model.GroupPeer
import com.example.model.GroupRoomSession
import com.example.model.NearbyDevice
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.model.VerifiedReceipt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

enum class ActiveScreen {
    HOME,
    FILE_BROWSER,
    DISCOVERING_PEERS,
    RECEIVING_BEACON,
    ACTIVE_TRANSFER,
    GROUP_ROOM,
    PHONE_TO_PC,
    PHONE_MIGRATION,
    RECEIPT_DETAIL,
    HISTORY,
    SETTINGS
}

data class TransferUiState(
    val currentScreen: ActiveScreen = ActiveScreen.HOME,
    val deviceName: String = "Aero SHAREit Device",
    val availableFiles: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val selectedCategory: FileCategory = FileCategory.PHOTOS,
    val nearbyDevices: List<NearbyDevice> = emptyList(),
    val localPairingCode: String = "",
    val localQrPayload: String = "",
    val selectedPeer: NearbyDevice? = null,
    val activeTransferRecord: TransferRecord? = null,
    val historyRecords: List<TransferRecord> = emptyList(),
    val receipts: List<VerifiedReceipt> = emptyList(),
    val selectedReceipt: VerifiedReceipt? = null,
    val groupSession: GroupRoomSession = GroupRoomSession(),
    val is5GhzPreferred: Boolean = true,
    val isEncryptionEnabled: Boolean = false,
    val autoDisconnect: Boolean = true,
    val isLiteMode: Boolean = false,
    val isSafeDropEnabled: Boolean = true,
    val isBridgeServerActive: Boolean = false,
    val bridgeAuthToken: String = UUID.randomUUID().toString().replace("-", ""),
    val bridgeUrl: String = "",
    val pendingSafeDropRequest: SafeDropRequest? = null,
    val connectedPcClients: Int = 0,
    val autopilotRecommendation: String = "Local-network transfer • SHA-256 integrity check",
    val totalSentBytes: Long = 0L,
    val totalReceivedBytes: Long = 0L,
    val averageSpeedMbps: Float = 0.0f,
    val hasStoragePermission: Boolean = false,
    val isScanningMedia: Boolean = false,
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val categorySizes: Map<FileCategory, Long> = emptyMap(),
    val cacheReport: CacheUsageReport = CacheUsageReport(0L, 0L, 0, 0, "0 B", "0 B"),
    val storageCleanerMessage: String? = null,
    val selectedHistoryRecordIds: Set<String> = emptySet()
)

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var transferJob: Job? = null
    private var serverEngine: AeroTransferServer? = null
    private var clientEngine: AeroTransferClient? = null
    private val groupTransferClients = mutableSetOf<AeroTransferClient>()
    private var pcBridgeServer: PhoneToPcBridgeServer? = null
    private var discoveryEngine: NearbyDeviceDiscovery? = null
    private var nearbyTransport: AeroNearbyTransport? = null
    private var nearbyToken: String = UUID.randomUUID().toString().replace("-", "")
    private var sendingNearby = false
    @Volatile private var pendingSafeDropApproval: ((Boolean) -> Unit)? = null

    init {
        initTransferEngines()
        updateAutopilot()
        refreshMediaStore()
        refreshCacheUsage()
        initNearbyDevices()
        initNearbyTransport()
    }

    private fun initTransferEngines() {
        val context = getApplication<Application>()

        serverEngine = AeroTransferServer(
            context = context,
            onIncomingRequest = { request, callback ->
                // Peer names are self-asserted and not cryptographically authenticated.
                // Never auto-approve by name alone; only the explicit SafeDrop-off setting bypasses approval.
                val autoApproved = !_uiState.value.isSafeDropEnabled
                if (autoApproved) {
                    _uiState.update { it.copy(pendingSafeDropRequest = null) }
                    callback(true)
                } else {
                    _uiState.update { it.copy(pendingSafeDropRequest = request) }
                    pendingSafeDropApproval = { accepted ->
                        _uiState.update { state ->
                            if (state.pendingSafeDropRequest?.requestId == request.requestId) {
                                state.copy(pendingSafeDropRequest = null)
                            } else {
                                state
                            }
                        }
                        callback(accepted)
                    }
                }
            },
            onProgressUpdate = { record ->
                _uiState.update { it.copy(activeTransferRecord = record) }
            },
            onTransferFinished = { receipt ->
                addVerifiedReceipt(receipt)
                val allFilesVerified = receipt.fileReceiptItems.isNotEmpty() && receipt.fileReceiptItems.all { it.verified }
                val finalRecord = TransferRecord(
                    id = receipt.sessionId,
                    fileName = receipt.fileReceiptItems.firstOrNull()?.fileName ?: "Received_Batch",
                    fileSizeFormatted = StorageSmartManager.formatBytes(receipt.totalBytesTransferred),
                    totalBytes = receipt.totalBytesTransferred,
                    transferredBytes = receipt.totalBytesTransferred,
                    progress = 1.0f,
                    speedMbps = receipt.averageSpeedMbps,
                    isIncoming = true,
                    peerName = receipt.peerDeviceName,
                    status = if (allFilesVerified) TransferStatus.COMPLETED else TransferStatus.FAILED,
                    localFilePath = receipt.fileReceiptItems.firstOrNull()?.localFilePath,
                    sha256Checksum = receipt.fileReceiptItems.firstOrNull()?.sha256Checksum
                )
                onTransferRecordCompleted(finalRecord, isOutgoing = false)
            },
            onError = { message ->
                _uiState.update { state ->
                    val cur = state.activeTransferRecord
                    state.copy(
                        activeTransferRecord = cur?.copy(status = TransferStatus.FAILED),
                        storageCleanerMessage = message
                    )
                }
            }
        )

        clientEngine = AeroTransferClient(
            context = context,
            onProgressUpdate = { record ->
                _uiState.update { it.copy(activeTransferRecord = record) }
            },
            onTransferFinished = { receipt ->
                addVerifiedReceipt(receipt)
                val cur = _uiState.value.activeTransferRecord
                val allFilesVerified = receipt.fileReceiptItems.isNotEmpty() && receipt.fileReceiptItems.all { it.verified }
                val finalStatus = if (allFilesVerified) TransferStatus.COMPLETED else TransferStatus.FAILED
                val finalRecord = cur?.copy(
                    status = finalStatus,
                    transferredBytes = receipt.totalBytesTransferred,
                    progress = 1.0f,
                    speedMbps = receipt.averageSpeedMbps
                ) ?: TransferRecord(
                    id = receipt.sessionId,
                    fileName = receipt.fileReceiptItems.firstOrNull()?.fileName ?: "Batch",
                    fileSizeFormatted = StorageSmartManager.formatBytes(receipt.totalBytesTransferred),
                    totalBytes = receipt.totalBytesTransferred,
                    transferredBytes = receipt.totalBytesTransferred,
                    progress = 1.0f,
                    speedMbps = receipt.averageSpeedMbps,
                    isIncoming = false,
                    peerName = receipt.peerDeviceName,
                    status = finalStatus
                )
                onTransferRecordCompleted(finalRecord, isOutgoing = true)
            },
            onError = { _ ->
                _uiState.update { state ->
                    val cur = state.activeTransferRecord
                    state.copy(activeTransferRecord = cur?.copy(status = TransferStatus.FAILED))
                }
            },
            liteModeProvider = { _uiState.value.isLiteMode }
        )

        pcBridgeServer = PhoneToPcBridgeServer(
            context = context,
            authToken = _uiState.value.bridgeAuthToken,
            onClientConnected = { _ ->
                _uiState.update { it.copy(connectedPcClients = it.connectedPcClients + 1) }
            }
        )

        // Start the actual TCP receiver; do not rely on demo peers or simulated transfers.
        serverEngine?.start()
    }

    private fun initNearbyDevices() {
        discoveryEngine = NearbyDeviceDiscovery(
            deviceNameProvider = { _uiState.value.deviceName },
            onDevicesChanged = { devices ->
                _uiState.update { state ->
                    state.copy(nearbyDevices = devices, selectedPeer = state.selectedPeer?.let { selected ->
                        devices.firstOrNull { it.id == selected.id }
                    })
                }
            },
            onLocalPairingCode = { code -> _uiState.update { it.copy(localPairingCode = code) } },
            onLocalQrPayload = { /* QR is generated by Nearby Connections pairing, not LAN discovery. */ }
        ).also { it.start() }
    }


    private fun initNearbyTransport() {
        nearbyTransport = AeroNearbyTransport(getApplication(), object : AeroNearbyTransport.Listener {
            override fun onReady() {
                _uiState.update { it.copy(localQrPayload = "AEROSHARE_NEARBY_V1|" + nearbyToken) }
            }
            override fun onPeerFound(endpointId: String, endpointName: String) {
                _uiState.update { it.copy(storageCleanerMessage = "Receiver found. Connecting automatically…") }
            }
            override fun onConnected(endpointId: String) {
                _uiState.update { it.copy(storageCleanerMessage = "Connected. Starting transfer…") }
                if (sendingNearby) sendSelectedFilesNearby(endpointId)
            }
            override fun onDisconnected(endpointId: String) {
                _uiState.update { it.copy(storageCleanerMessage = "Nearby device disconnected.") }
            }
            override fun onIncomingFile(endpointId: String, file: File, payloadId: Long) {
                viewModelScope.launch {
                    try {
                        val context = getApplication<Application>()
                        val folder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "Downloads")
                        folder.mkdirs()
                        val safeName = file.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
                        val destination = File(folder, safeName).let {
                            if (it.exists()) File(folder, System.currentTimeMillis().toString() + "_" + safeName) else it
                        }
                        file.copyTo(destination)
                        _uiState.update { it.copy(storageCleanerMessage = "Received " + destination.name + " in Aero SHAREit Downloads.") }
                    } catch (error: Exception) {
                        _uiState.update { it.copy(storageCleanerMessage = "Could not save received file: " + error.localizedMessage) }
                    }
                }
            }
            override fun onPayloadProgress(endpointId: String, payloadId: Long, transferred: Long, total: Long, status: Int) {
                _uiState.update { state ->
                    val current = state.activeTransferRecord ?: return@update state
                    val done = status == com.google.android.gms.nearby.connection.PayloadTransferUpdate.Status.SUCCESS
                    state.copy(activeTransferRecord = current.copy(
                        transferredBytes = transferred,
                        progress = if (total > 0L) transferred.toFloat() / total else current.progress,
                        status = if (done) TransferStatus.COMPLETED else TransferStatus.TRANSFERRING
                    ))
                }
            }
            override fun onError(message: String) {
                _uiState.update { it.copy(storageCleanerMessage = message) }
            }
        })
    }

    private fun startNearbyReceiving() {
        sendingNearby = false
        nearbyToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().take(8)
        _uiState.update { it.copy(localQrPayload = "AEROSHARE_NEARBY_V1|" + nearbyToken) }
        nearbyTransport?.startReceiving(nearbyToken)
    }

    private fun sendSelectedFilesNearby(endpointId: String) {
        val context = getApplication<Application>()
        val selected = _uiState.value.availableFiles.filter { _uiState.value.selectedFiles.contains(it.id) }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(storageCleanerMessage = "Connected. Select files to send.") }
            return
        }
        val totalBytes = selected.sumOf { it.sizeBytes }
        val record = TransferRecord(
            id = UUID.randomUUID().toString(),
            fileName = if (selected.size == 1) selected.first().name else "Batch (" + selected.size + " files)",
            fileSizeFormatted = StorageSmartManager.formatBytes(totalBytes),
            totalBytes = totalBytes,
            transferredBytes = 0L,
            progress = 0f,
            speedMbps = 0f,
            isIncoming = false,
            peerName = "Nearby Aero SHAREit device",
            status = TransferStatus.TRANSFERRING
        )
        _uiState.update { it.copy(activeTransferRecord = record, currentScreen = ActiveScreen.ACTIVE_TRANSFER) }
        viewModelScope.launch {
            try {
                selected.forEachIndexed { index, item ->
                    val uri = item.uri ?: throw IllegalStateException("File URI unavailable: " + item.name)
                    val staged = File(context.cacheDir, "nearby_" + System.currentTimeMillis() + "_" + index)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        staged.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Cannot read " + item.name)
                    nearbyTransport?.sendFile(endpointId, staged)
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(
                    activeTransferRecord = it.activeTransferRecord?.copy(status = TransferStatus.FAILED),
                    storageCleanerMessage = "Nearby transfer failed: " + error.localizedMessage
                ) }
            }
        }
    }

    fun connectUsingPairingCode(code: String) {
        val rawValue = code.trim()
        if (rawValue.startsWith("AEROSHARE_NEARBY_V1|")) {
            val token = rawValue.substringAfter("|")
            if (!token.matches(Regex("[A-Za-z0-9_-]{24,64}"))) {
                _uiState.update { it.copy(storageCleanerMessage = "Invalid Aero SHAREit QR code.") }
                return
            }
            nearbyToken = token
            sendingNearby = true
            nearbyTransport?.startSending(token)
            return
        }
        val qrPeer = parseReceiverQrPayload(rawValue)
        if (qrPeer != null) {
            selectQrReceiver(qrPeer)
            return
        }

        // Backward compatibility for old AEROSHARE:123456 QR codes and manual codes.
        val normalized = rawValue.substringAfter("AEROSHARE:", rawValue).filter(Char::isDigit)
        if (normalized.length != 6) {
            _uiState.update { it.copy(storageCleanerMessage = "Scan the QR code shown on the receiving phone.") }
            return
        }
        val peer = _uiState.value.nearbyDevices.firstOrNull { it.pairingCode == normalized }
        if (peer == null) {
            _uiState.update { it.copy(storageCleanerMessage = "Receiver not found. Make sure both phones are on the same Wi-Fi or hotspot.") }
            return
        }
        selectQrReceiver(peer)
    }

    private fun parseReceiverQrPayload(payload: String): NearbyDevice? {
        val parts = payload.split('|')
        if (parts.size != 5 || parts[0] != "AEROSHARE_CONNECT_V1") return null
        val ip = parts[1]
        if (!ip.matches(Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$"))) return null
        val octets = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        val address = try { InetAddress.getByName(ip) as? Inet4Address } catch (_: Exception) { null } ?: return null
        if (address.isLoopbackAddress || address.isMulticastAddress ||
            (!address.isSiteLocalAddress && !address.isLinkLocalAddress)) return null

        val port = parts[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val code = parts[3].takeIf { it.matches(Regex("\\d{6}")) } ?: return null
        val deviceName = try {
            String(Base64.decode(parts[4], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                .take(80).ifBlank { "Aero SHAREit receiver" }
        } catch (_: Exception) {
            "Aero SHAREit receiver"
        }
        return NearbyDevice(
            id = "qr:$ip:$port",
            name = deviceName,
            ipAddress = ip,
            port = port,
            deviceModel = "Aero SHAREit receiver",
            pairingCode = code
        )
    }

    private fun selectQrReceiver(peer: NearbyDevice) {
        if (_uiState.value.selectedFiles.isNotEmpty()) {
            startTransferToPeer(peer)
        } else {
            _uiState.update {
                it.copy(
                    selectedPeer = peer,
                    currentScreen = ActiveScreen.FILE_BROWSER,
                    storageCleanerMessage = "Receiver found: ${peer.name}. Select files and tap Send to start the transfer."
                )
            }
        }
    }

    private fun updateAutopilot() {
        val state = _uiState.value
        val selectedBytes = state.availableFiles
            .filter { state.selectedFiles.contains(it.id) }
            .sumOf { it.sizeBytes }
        val recommendation = TransferAutopilot.analyzeAndRecommend(
            context = getApplication(),
            batchBytes = selectedBytes,
            isPcPeer = state.currentScreen == ActiveScreen.PHONE_TO_PC,
            isGroupSession = state.currentScreen == ActiveScreen.GROUP_ROOM,
            isLiteMode = state.isLiteMode
        )
        _uiState.update {
            it.copy(autopilotRecommendation = "${recommendation.recommendedBand} • ${recommendation.recommendedMode}")
        }
    }

    fun refreshMediaStore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningMedia = true) }
            val scanResult = LocalMediaStoreExplorer.queryAllCategorized(getApplication())
            _uiState.update { state ->
                state.copy(
                    availableFiles = scanResult.allFiles,
                    hasStoragePermission = scanResult.hasPermission,
                    isScanningMedia = false,
                    categoryCounts = scanResult.categoryCounts,
                    categorySizes = scanResult.categorySizes
                )
            }
            updateAutopilot()
        }
    }

    fun navigateTo(screen: ActiveScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
        if (screen == ActiveScreen.RECEIVING_BEACON) startNearbyReceiving()
        if (screen == ActiveScreen.HOME) nearbyTransport?.stopAll()
        updateAutopilot()
    }

    fun selectCategory(category: FileCategory) {
        _uiState.update { it.copy(selectedCategory = category, currentScreen = ActiveScreen.FILE_BROWSER) }
    }

    fun toggleFileSelection(fileId: String) {
        _uiState.update { state ->
            val set = state.selectedFiles.toMutableSet()
            if (set.contains(fileId)) set.remove(fileId) else set.add(fileId)
            state.copy(selectedFiles = set)
        }
        updateAutopilot()
    }

    fun selectAllFilesInCategory(category: FileCategory) {
        _uiState.update { state ->
            val idsInCat = state.availableFiles.filter { it.category == category }.map { it.id }
            val set = state.selectedFiles.toMutableSet()
            if (set.containsAll(idsInCat)) {
                set.removeAll(idsInCat.toSet())
            } else {
                set.addAll(idsInCat)
            }
            state.copy(selectedFiles = set)
        }
        updateAutopilot()
    }

    fun clearSelectedFiles() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
        updateAutopilot()
    }

    fun startTransferToPeer(peer: NearbyDevice) {
        val selected = _uiState.value.availableFiles.filter { _uiState.value.selectedFiles.contains(it.id) }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(storageCleanerMessage = "Select at least one file before connecting to a receiver.") }
            return
        }
        val totalBytes = selected.sumOf { it.sizeBytes }

        val record = TransferRecord(
            id = UUID.randomUUID().toString(),
            fileName = selected.firstOrNull()?.name ?: "Batch (${selected.size} files)",
            fileSizeFormatted = StorageSmartManager.formatBytes(totalBytes),
            totalBytes = totalBytes,
            transferredBytes = 0L,
            progress = 0f,
            speedMbps = 0f,
            isIncoming = false,
            peerName = peer.name,
            status = TransferStatus.HANDSHAKING
        )

        _uiState.update {
            it.copy(
                selectedPeer = peer,
                activeTransferRecord = record,
                currentScreen = ActiveScreen.ACTIVE_TRANSFER
            )
        }

        transferJob?.cancel()
        clientEngine?.startTransfer(
            targetIp = peer.ipAddress,
            targetPort = peer.port,
            localDeviceName = _uiState.value.deviceName,
            files = selected
        )
    }

    fun togglePauseTransfer() {
        _uiState.update { state ->
            val cur = state.activeTransferRecord ?: return@update state
            val next = if (cur.status == TransferStatus.PAUSED) TransferStatus.TRANSFERRING else TransferStatus.PAUSED
            clientEngine?.setPaused(next == TransferStatus.PAUSED)
            state.copy(activeTransferRecord = cur.copy(status = next))
        }
    }

    fun cancelTransfer() {
        clientEngine?.cancel()
        synchronized(groupTransferClients) { groupTransferClients.forEach { it.cancel() }; groupTransferClients.clear() }
        _uiState.update { state ->
            val cur = state.activeTransferRecord
            state.copy(
                activeTransferRecord = cur?.copy(status = TransferStatus.CANCELLED),
                currentScreen = ActiveScreen.HOME
            )
        }
    }

    fun togglePcBridge(active: Boolean) {
        if (active) {
            pcBridgeServer?.start()
            _uiState.update {
                it.copy(
                    isBridgeServerActive = true,
                    bridgeUrl = "",
                    storageCleanerMessage = "Starting local PC bridge…"
                )
            }
            // Hotspot/Wi-Fi interfaces may appear a moment after the user enables them.
            // Retry address discovery so users don't need to toggle the bridge off and on.
            viewModelScope.launch {
                repeat(20) {
                    if (!_uiState.value.isBridgeServerActive) return@launch
                    val address = findLocalIpv4Address()
                    if (address != null) {
                        _uiState.update { state ->
                            if (!state.isBridgeServerActive) state
                            else state.copy(
                                bridgeUrl = "http://" + address + ":" + AeroTransferProtocol.PC_BRIDGE_PORT + "/?token=" + state.bridgeAuthToken
                            )
                        }
                        return@launch
                    }
                    delay(500)
                }
                if (_uiState.value.isBridgeServerActive && _uiState.value.bridgeUrl.isBlank()) {
                    _uiState.update {
                        it.copy(
                            storageCleanerMessage = "No local address found yet. Turn on the phone hotspot, connect the PC, then wait a few seconds. Internet is not required."
                        )
                    }
                }
            }
        } else {
            pcBridgeServer?.stop()
            _uiState.update { it.copy(isBridgeServerActive = false, bridgeUrl = "") }
        }
    }

    /**
     * Finds an active private IPv4 address, including phone-hotspot interfaces.
     * This lets the PC bridge work on a direct local hotspot without internet or a router.
     */
    private fun findLocalIpv4Address(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { network -> network.isUp && !network.isLoopback }
            val addresses = interfaces.flatMap { network ->
                Collections.list(network.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .filter { address -> !address.isLoopbackAddress && address.isSiteLocalAddress }
                    .map { address -> network.name.lowercase() to address.hostAddress }
            }
            addresses.firstOrNull { (name, _) ->
                name.contains("wlan") || name.contains("ap") || name.contains("wifi") || name.contains("swlan")
            }?.second ?: addresses.firstOrNull()?.second
        } catch (_: Exception) {
            null
        }
    }

    fun startGroupDrop() {
        val selected = _uiState.value.availableFiles.filter { _uiState.value.selectedFiles.contains(it.id) }
        val members = _uiState.value.groupSession.members
        if (selected.isEmpty()) {
            _uiState.update { it.copy(storageCleanerMessage = "Select at least one file before starting GroupDrop.") }
            return
        }
        if (members.isEmpty()) {
            _uiState.update { it.copy(storageCleanerMessage = "Discover and add at least one peer before starting GroupDrop.") }
            return
        }
        val files = selected

        _uiState.update { state ->
            state.copy(groupSession = state.groupSession.copy(isGroupDropActive = true) )
        }

        // One client/socket per peer; each transfer must finish verification before the next starts.
        viewModelScope.launch {
            try {
                for (peer in members) {
                    val completed = CompletableDeferred<Boolean>()
                    val groupClient = AeroTransferClient(
                        context = getApplication(),
                        onProgressUpdate = { record ->
                            _uiState.update { it.copy(activeTransferRecord = record) }
                        },
                        onTransferFinished = { receipt ->
                            addVerifiedReceipt(receipt)
                            val verified = receipt.fileReceiptItems.isNotEmpty() && receipt.fileReceiptItems.all { it.verified }
                            val record = TransferRecord(
                                id = receipt.sessionId,
                                fileName = receipt.fileReceiptItems.firstOrNull()?.fileName ?: "Batch",
                                fileSizeFormatted = StorageSmartManager.formatBytes(receipt.totalBytesTransferred),
                                totalBytes = receipt.totalBytesTransferred,
                                transferredBytes = receipt.totalBytesTransferred,
                                progress = if (verified) 1f else 0f,
                                speedMbps = receipt.averageSpeedMbps,
                                isIncoming = false,
                                peerName = peer.name,
                                status = if (verified) TransferStatus.COMPLETED else TransferStatus.FAILED
                            )
                            onTransferRecordCompleted(record, isOutgoing = true)
                            completed.complete(verified)
                        },
                        onError = { message ->
                            _uiState.update { state ->
                                state.copy(
                                    activeTransferRecord = state.activeTransferRecord?.copy(status = TransferStatus.FAILED),
                                    storageCleanerMessage = message
                                )
                            }
                            completed.complete(false)
                        },
                        liteModeProvider = { _uiState.value.isLiteMode }
                    )
                    synchronized(groupTransferClients) { groupTransferClients.add(groupClient) }
                    groupClient.startTransfer(
                        targetIp = peer.ipAddress,
                        targetPort = AeroTransferProtocol.DEFAULT_PORT,
                        localDeviceName = _uiState.value.deviceName,
                        files = files
                    )
                    val succeeded = try {
                        completed.await()
                    } finally {
                        groupClient.cancel()
                        synchronized(groupTransferClients) { groupTransferClients.remove(groupClient) }
                    }
                    if (!succeeded) break
                }
            } finally {
                _uiState.update { state ->
                    state.copy(groupSession = state.groupSession.copy(isGroupDropActive = false))
                }
            }
        }
    }

    fun addGroupPeer() {
        val state = _uiState.value
        val existingIds = state.groupSession.members.map { it.id }.toSet()
        val peer = state.nearbyDevices.firstOrNull { it.id !in existingIds } ?: return
        val newPeer = GroupPeer(
            id = peer.id,
            name = peer.name,
            model = peer.deviceModel,
            ipAddress = peer.ipAddress
        )
        _uiState.update { current ->
            current.copy(
                groupSession = current.groupSession.copy(
                    members = current.groupSession.members + newPeer
                )
            )
        }
    }

    fun simulateIncomingTransfer() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val samplePayload = "AeroShare_Offline_P2P_Beam_Sample_Content_${System.currentTimeMillis()}".toByteArray()
            val hash = FileIntegrityEngine.calculateSha256(samplePayload)
            val transferDir = File(context.cacheDir, "transfers").apply { mkdirs() }
            val receivedFile = File(transferDir, "AeroBeam_${System.currentTimeMillis() % 10000}.mp4")
            receivedFile.writeBytes(samplePayload)

            val record = TransferRecord(
                id = UUID.randomUUID().toString(),
                fileName = receivedFile.name,
                fileSizeFormatted = StorageSmartManager.formatBytes(480_000_000L),
                totalBytes = 480_000_000L,
                transferredBytes = 480_000_000L,
                progress = 1.0f,
                speedMbps = 41.5f,
                isIncoming = true,
                peerName = "Nearby Peer (5GHz Turbo)",
                status = TransferStatus.COMPLETED,
                localFilePath = receivedFile.absolutePath,
                sha256Checksum = hash
            )

            val receipt = VerifiedReceipt(
                id = record.id,
                sessionId = record.id,
                peerDeviceName = record.peerName,
                peerIpAddress = "192.168.49.12",
                totalBytesTransferred = record.totalBytes,
                totalFilesCount = 1,
                durationSeconds = 12,
                averageSpeedMbps = record.speedMbps,
                fileReceiptItems = listOf(
                    FileReceiptItem(
                        fileName = record.fileName,
                        fileSizeBytes = record.totalBytes,
                        sha256Checksum = hash,
                        verified = true
                    )
                ),
                timestamp = System.currentTimeMillis()
            )

            addVerifiedReceipt(receipt)
            onTransferRecordCompleted(record, isOutgoing = false)
            _uiState.update {
                it.copy(
                    activeTransferRecord = record,
                    currentScreen = ActiveScreen.ACTIVE_TRANSFER
                )
            }
            refreshCacheUsage()
        }
    }

    fun startPhoneMigration(totalBytes: Long = 0L) {
        val allFiles = _uiState.value.availableFiles
        if (allFiles.isEmpty()) {
            _uiState.update { it.copy(storageCleanerMessage = "No files found on device to migrate.") }
            return
        }

        _uiState.update { state ->
            state.copy(
                selectedFiles = allFiles.map { it.id }.toSet(),
                currentScreen = ActiveScreen.DISCOVERING_PEERS
            )
        }
    }

    fun startPhoneMigration(selectedCategories: Set<FileCategory>) {
        val migrationFiles = _uiState.value.availableFiles.filter { selectedCategories.contains(it.category) }
        if (migrationFiles.isEmpty()) {
            _uiState.update { it.copy(storageCleanerMessage = "No files found in the selected categories.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                selectedFiles = migrationFiles.map { it.id }.toSet(),
                currentScreen = ActiveScreen.DISCOVERING_PEERS
            )
        }
    }

    fun approveSafeDrop(approved: Boolean) {
        val approval = pendingSafeDropApproval
        pendingSafeDropApproval = null
        _uiState.update { it.copy(pendingSafeDropRequest = null) }
        approval?.invoke(approved)
    }

    private fun addVerifiedReceipt(receipt: VerifiedReceipt) {
        _uiState.update { it.copy(receipts = listOf(receipt) + it.receipts) }
    }

    private fun onTransferRecordCompleted(record: TransferRecord, isOutgoing: Boolean) {
        _uiState.update { state ->
            val updated = listOf(record) + state.historyRecords.filterNot { it.id == record.id }
            val sent = if (isOutgoing) state.totalSentBytes + record.totalBytes else state.totalSentBytes
            val recv = if (!isOutgoing) state.totalReceivedBytes + record.totalBytes else state.totalReceivedBytes
            state.copy(
                historyRecords = updated,
                totalSentBytes = sent,
                totalReceivedBytes = recv
            )
        }
        refreshCacheUsage()
    }

    fun viewReceipt(receipt: VerifiedReceipt) {
        _uiState.update { it.copy(selectedReceipt = receipt, currentScreen = ActiveScreen.RECEIPT_DETAIL) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(historyRecords = emptyList(), selectedHistoryRecordIds = emptySet()) }
    }

    fun toggleSelectHistoryRecord(id: String) {
        _uiState.update { state ->
            val set = state.selectedHistoryRecordIds.toMutableSet()
            if (set.contains(id)) set.remove(id) else set.add(id)
            state.copy(selectedHistoryRecordIds = set)
        }
    }

    fun selectAllHistoryRecords() {
        _uiState.update { state ->
            val allIds = state.historyRecords.map { it.id }.toSet()
            state.copy(selectedHistoryRecordIds = allIds)
        }
    }

    fun clearSelectedHistoryRecords() {
        _uiState.update { it.copy(selectedHistoryRecordIds = emptySet()) }
    }

    fun removeSelectedFromHistory() {
        _uiState.update { state ->
            val toRemove = state.selectedHistoryRecordIds
            val updatedHistory = state.historyRecords.filterNot { toRemove.contains(it.id) }
            state.copy(
                historyRecords = updatedHistory,
                selectedHistoryRecordIds = emptySet(),
                storageCleanerMessage = "Removed ${toRemove.size} record(s) from history. Actual files remain safe on device."
            )
        }
    }

    fun deleteSelectedActualFiles() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedIds = state.selectedHistoryRecordIds
            val recordsToDelete = state.historyRecords.filter { selectedIds.contains(it.id) }

            if (recordsToDelete.isEmpty()) return@launch

            val batchResult = FileManagerAndCleanerEngine.deleteMultipleActualFiles(
                context = getApplication(),
                records = recordsToDelete
            )

            val successfulIds = batchResult.details.filter { it.wasDeletedFromDisk }.map { it.recordId }.toSet()
            val deletedNames = batchResult.details.filter { it.wasDeletedFromDisk }.map { it.fileName }.toSet()

            _uiState.update { current ->
                val updatedHistory = current.historyRecords.map { record ->
                    if (successfulIds.contains(record.id)) record.copy(isLocalFileDeleted = true) else record
                }
                val updatedCatalog = current.availableFiles.filterNot { deletedNames.contains(it.name) }
                current.copy(
                    historyRecords = updatedHistory,
                    availableFiles = updatedCatalog,
                    selectedHistoryRecordIds = emptySet(),
                    storageCleanerMessage = if (batchResult.successfullyDeletedCount > 0) {
                        "Permanently deleted ${batchResult.successfullyDeletedCount} file(s) from device storage. Freed ${batchResult.formattedBytesFreed}."
                    } else {
                        "Could not delete actual files (files may not exist or require system authorization)."
                    }
                )
            }
            refreshMediaStore()
            refreshCacheUsage()
        }
    }

    fun refreshCacheUsage() {
        viewModelScope.launch {
            val report = FileManagerAndCleanerEngine.measureCacheUsage(getApplication())
            _uiState.update { it.copy(cacheReport = report) }
        }
    }

    fun cleanAppTemporaryCache(cleanAll: Boolean = true) {
        viewModelScope.launch {
            val activeRecord = _uiState.value.activeTransferRecord
            val transferIsActive = activeRecord != null && activeRecord.status in setOf(
                TransferStatus.WAITING_ACCEPT,
                TransferStatus.HANDSHAKING,
                TransferStatus.TRANSFERRING,
                TransferStatus.PAUSED,
                TransferStatus.VERIFYING
            )
            val activeSet = if (transferIsActive && activeRecord != null) {
                setOf(activeRecord.fileName)
            } else {
                emptySet()
            }

            val result = FileManagerAndCleanerEngine.cleanAppTemporaryCache(
                context = getApplication(),
                activeTransferNames = activeSet,
                cleanAllCache = cleanAll
            )

            val report = FileManagerAndCleanerEngine.measureCacheUsage(getApplication())
            _uiState.update {
                it.copy(
                    cacheReport = report,
                    storageCleanerMessage = result.message
                )
            }
        }
    }

    fun clearStorageCleanerMessage() {
        _uiState.update { it.copy(storageCleanerMessage = null) }
    }

    fun updateDeviceName(newName: String) {
        _uiState.update { it.copy(deviceName = newName) }
    }

    fun toggle5Ghz(enabled: Boolean) {
        _uiState.update { it.copy(is5GhzPreferred = enabled) }
        updateAutopilot()
    }

    fun toggleEncryption(enabled: Boolean) {
        _uiState.update { it.copy(isEncryptionEnabled = enabled) }
    }

    fun toggleAutoDisconnect(enabled: Boolean) {
        _uiState.update { it.copy(autoDisconnect = enabled) }
    }

    fun toggleLiteMode(enabled: Boolean) {
        _uiState.update { it.copy(isLiteMode = enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        serverEngine?.stop()
        discoveryEngine?.stop()
        pcBridgeServer?.stop()
        nearbyTransport?.stopAll()
        clientEngine?.cancel()
    }
}
