package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.DeviceConnectScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.GroupRoomScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PhoneMigrationScreen
import com.example.ui.screens.PhoneToPcScreen
import com.example.ui.screens.ReceiptDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransferProgressScreen
import com.example.ui.theme.AeroShareTheme
import com.example.viewmodel.ActiveScreen
import com.example.viewmodel.TransferViewModel

class MainActivity : ComponentActivity() {
    private val transferViewModel: TransferViewModel by viewModels()

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        transferViewModel.refreshMediaStore()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissionsIfNeeded()
        requestNearbyPermissionsIfNeeded()
        setContent {
            AeroShareTheme {
                val state by transferViewModel.uiState.collectAsState()
                AeroAppContent(
                    state = state,
                    viewModel = transferViewModel,
                    onRequestStoragePermissions = ::requestStoragePermissionsIfNeeded
                )
            }
        }
    }


    private val nearbyPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private fun requestNearbyPermissionsIfNeeded() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT <= 32) required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= 29) {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= 23) {
            required.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) required.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        val missing = required.distinct().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) nearbyPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun requestStoragePermissionsIfNeeded() {
        val required = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) storagePermissionLauncher.launch(missing.toTypedArray())
        else transferViewModel.refreshMediaStore()
    }
}

@androidx.compose.runtime.Composable
private fun AeroAppContent(
    state: com.example.viewmodel.TransferUiState,
    viewModel: TransferViewModel,
    onRequestStoragePermissions: () -> Unit
) {
    when (state.currentScreen) {
        ActiveScreen.HOME -> HomeScreen(
            uiState = state,
            onNavigate = viewModel::navigateTo,
            onSelectCategory = viewModel::selectCategory,
            onStartSendFlow = { viewModel.navigateTo(ActiveScreen.FILE_BROWSER) },
            onStartReceiveFlow = { viewModel.navigateTo(ActiveScreen.RECEIVING_BEACON) }
        )

        ActiveScreen.FILE_BROWSER -> FileBrowserScreen(
            uiState = state,
            onBack = { viewModel.navigateTo(ActiveScreen.HOME) },
            onSelectCategory = viewModel::selectCategory,
            onToggleSelectFile = viewModel::toggleFileSelection,
            onSelectAllCategory = viewModel::selectAllFilesInCategory,
            onClearSelection = viewModel::clearSelectedFiles,
            onSendSelected = { state.selectedPeer?.let(viewModel::startTransferToPeer) ?: viewModel.navigateTo(ActiveScreen.DISCOVERING_PEERS) },
            onRefreshScan = onRequestStoragePermissions
        )

        ActiveScreen.DISCOVERING_PEERS -> DeviceConnectScreen(
            isReceiveMode = false,
            uiState = state,
            onBack = { viewModel.navigateTo(ActiveScreen.FILE_BROWSER) },
            onSelectPeer = viewModel::startTransferToPeer,
            onConnectWithPin = viewModel::connectUsingPairingCode,
            localQrPayload = state.localQrPayload
        )

        ActiveScreen.RECEIVING_BEACON -> DeviceConnectScreen(
            isReceiveMode = true,
            uiState = state,
            onBack = { viewModel.navigateTo(ActiveScreen.HOME) },
            onSelectPeer = viewModel::startTransferToPeer,
            onConnectWithPin = viewModel::connectUsingPairingCode,
            localQrPayload = state.localQrPayload
        )

        ActiveScreen.ACTIVE_TRANSFER -> TransferProgressScreen(
            record = state.activeTransferRecord,
            onTogglePause = viewModel::togglePauseTransfer,
            onCancel = viewModel::cancelTransfer,
            onDone = { viewModel.navigateTo(ActiveScreen.HOME) }
        )

        ActiveScreen.GROUP_ROOM -> GroupRoomScreen(
            groupSession = state.groupSession,
            onBack = { viewModel.navigateTo(ActiveScreen.HOME) },
            onStartGroupDrop = viewModel::startGroupDrop,
            onSimulateAddMember = viewModel::addGroupPeer
        )

        ActiveScreen.PHONE_TO_PC -> PhoneToPcScreen(
            isBridgeActive = state.isBridgeServerActive,
            bridgeUrl = state.bridgeUrl,
            connectedClientsCount = state.connectedPcClients,
            onToggleBridge = viewModel::togglePcBridge,
            onBack = { viewModel.navigateTo(ActiveScreen.HOME) }
        )

        ActiveScreen.PHONE_MIGRATION -> PhoneMigrationScreen(
            uiState = state,
            onBack = { viewModel.navigateTo(ActiveScreen.HOME) },
            onStartMigration = { categories -> viewModel.startPhoneMigration(categories) }
        )

        ActiveScreen.RECEIPT_DETAIL -> {
            val receipt = state.selectedReceipt
            if (receipt == null) {
                viewModel.navigateTo(ActiveScreen.HISTORY)
            } else {
                ReceiptDetailScreen(
                    receipt = receipt,
                    onBack = { viewModel.navigateTo(ActiveScreen.HISTORY) }
                )
            }
        }

        ActiveScreen.HISTORY -> HistoryScreen(
            uiState = state,
            onClearHistory = viewModel::clearHistory,
            onViewReceipt = viewModel::viewReceipt,
            onToggleSelectRecord = viewModel::toggleSelectHistoryRecord,
            onSelectAllRecords = viewModel::selectAllHistoryRecords,
            onClearSelection = viewModel::clearSelectedHistoryRecords,
            onRemoveSelectedFromHistory = viewModel::removeSelectedFromHistory,
            onDeleteSelectedActualFiles = viewModel::deleteSelectedActualFiles,
            onCleanCache = { viewModel.cleanAppTemporaryCache() },
            onDismissStorageMessage = viewModel::clearStorageCleanerMessage
        )

        ActiveScreen.SETTINGS -> SettingsScreen(
            uiState = state,
            onUpdateDeviceName = viewModel::updateDeviceName,
            onToggle5Ghz = viewModel::toggle5Ghz,
            onToggleEncryption = viewModel::toggleEncryption,
            onToggleAutoDisconnect = viewModel::toggleAutoDisconnect,
            onToggleLiteMode = viewModel::toggleLiteMode,
            onCleanCache = { viewModel.cleanAppTemporaryCache() }
        )
    }

    val pending = state.pendingSafeDropRequest
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { viewModel.approveSafeDrop(false) },
            title = { Text("Incoming transfer request") },
            text = {
                Text(
                    "${pending.peerName} (${pending.peerIp}) wants to send ${pending.filesCount} file(s), ${pending.totalSizeFormatted}. Accept only if you trust this device. Current transfer traffic is not encrypted."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.approveSafeDrop(true) }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.approveSafeDrop(false) }) { Text("Reject") }
            }
        )
    }
}
