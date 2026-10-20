package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NearbyDevice
import com.example.ui.components.AeroCardBox
import com.example.ui.components.RadarPulseEffect
import com.example.ui.components.StylizedQrMatrix
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroCardElevated
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary
import com.example.viewmodel.TransferUiState

@Composable
fun DeviceConnectScreen(
    isReceiveMode: Boolean,
    uiState: TransferUiState,
    onBack: () -> Unit,
    onSelectPeer: (NearbyDevice) -> Unit,
    onSimulateReceive: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag(if (isReceiveMode) "receive_beacon_screen" else "device_discovery_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("connect_screen_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AeroTextPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isReceiveMode) "Receive Mode" else "Find Receiver",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AeroTextPrimary
                    )
                    Text(
                        text = if (isReceiveMode) "Beacon broadcasting on 5GHz" else "Scanning nearby Wi-Fi Direct peers",
                        style = MaterialTheme.typography.labelSmall,
                        color = AeroSecondaryGreen
                    )
                }
            }

            if (isReceiveMode) {
                ReceiveBeaconView(
                    deviceName = uiState.deviceName,
                    onSimulateIncoming = onSimulateReceive
                )
            } else {
                SendDiscoveryView(
                    uiState = uiState,
                    onSelectPeer = onSelectPeer
                )
            }
        }
    }
}

@Composable
private fun ReceiveBeaconView(
    deviceName: String,
    onSimulateIncoming: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Show QR to Sender",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = AeroTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The sender can scan this code to connect instantly without passwords",
                style = MaterialTheme.typography.bodyMedium,
                color = AeroTextSecondary,
                fontSize = 13.sp
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            StylizedQrMatrix(
                modifier = Modifier.fillMaxWidth(),
                ipText = "192.168.49.1:8829"
            )
        }

        item {
            AeroCardBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                backgroundColor = AeroCard,
                borderColor = AeroBorder,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Hotspot",
                            tint = AeroEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hotspot Beacon Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AeroTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Device: $deviceName", fontSize = 12.sp, color = AeroTextSecondary)
                    Text(text = "Network: DIRECT-AeroShare-8829", fontSize = 12.sp, color = AeroTextSecondary)
                    Text(text = "Security: Ephemeral ECDH + AES-256-GCM (SAS Verified)", fontSize = 12.sp, color = AeroSecondaryGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSimulateIncoming,
                colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("simulate_receive_button")
            ) {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = "Test Beam", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Accept Test Beam (480 MB)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SendDiscoveryView(
    uiState: TransferUiState,
    onSelectPeer: (NearbyDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                RadarPulseEffect(modifier = Modifier.size(160.dp))
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Scanning",
                    tint = AeroEmerald,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        item {
            Text(
                text = "Nearby Receivers (${uiState.nearbyDevices.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AeroTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(uiState.nearbyDevices) { peer ->
            AeroCardBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onSelectPeer(peer) }
                    .testTag("peer_item_${peer.id}"),
                backgroundColor = AeroCard,
                borderColor = AeroBorder,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AeroEmeraldSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Device", tint = AeroEmerald, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = peer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AeroTextPrimary)
                        Text(text = "${peer.deviceModel} • 5GHz Turbo Link", fontSize = 11.sp, color = AeroSecondaryGreen)
                    }
                    Button(
                        onClick = { onSelectPeer(peer) },
                        colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
