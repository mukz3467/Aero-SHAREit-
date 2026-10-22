package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import com.journeyapps.barcodescanner.DecoratedBarcodeView
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
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
    onConnectWithPin: (String) -> Unit,
    localPairingCode: String,
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
                        text = if (isReceiveMode) "Listening for incoming transfers" else "Scanning local Wi-Fi peers",
                        style = MaterialTheme.typography.labelSmall,
                        color = AeroSecondaryGreen
                    )
                }
            }

            if (isReceiveMode) {
                ReceiveBeaconView(
                    deviceName = uiState.deviceName,
                    pairingCode = localPairingCode
                )
            } else {
                SendDiscoveryView(
                    uiState = uiState,
                    onSelectPeer = onSelectPeer,
                    onConnectWithPin = onConnectWithPin
                )
            }
        }
    }
}

@Composable
private fun ReceiveBeaconView(
    deviceName: String,
    pairingCode: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Ready to Receive",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = AeroTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Keep both devices on the same local Wi-Fi network. After approval and SHA-256 verification, received files are saved in Aero SHAREit's app-specific Downloads folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = AeroTextSecondary,
                fontSize = 13.sp
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
                            text = "Local Receiver Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AeroTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Device: $deviceName", fontSize = 12.sp, color = AeroTextSecondary)
                    Text(text = "Network: Same local Wi-Fi • TCP port 8829", fontSize = 12.sp, color = AeroTextSecondary)
                    Text(text = "Safety: Incoming approval + SHA-256 file verification", fontSize = 12.sp, color = AeroSecondaryGreen, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Your 6-digit PIN", fontSize = 12.sp, color = AeroTextSecondary)
                    Text(text = pairingCode.ifBlank { "Starting…" }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = AeroEmerald)
                    if (pairingCode.length == 6) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AndroidView(
                            modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally),
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    val matrix = MultiFormatWriter().encode("AEROSHARE:$pairingCode", BarcodeFormat.QR_CODE, 420, 420, mapOf(EncodeHintType.MARGIN to 1))
                                    val bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
                                    for (x in 0 until 420) for (y in 0 until 420) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                    setImageBitmap(bitmap)
                                }
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun SendDiscoveryView(
    uiState: TransferUiState,
    onSelectPeer: (NearbyDevice) -> Unit,
    onConnectWithPin: (String) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    val scannerView = remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission = granted; if (granted) scanning = true }

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
            AeroCardBox(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                backgroundColor = AeroCard,
                borderColor = AeroBorder,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Connect with PIN", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AeroTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("6-digit receiver PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onConnectWithPin(pinInput) }, enabled = pinInput.length == 6, modifier = Modifier.weight(1f)) { Text("Connect with PIN") }
                        Button(onClick = {
                            if (hasCameraPermission) scanning = true
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }, modifier = Modifier.weight(1f)) { Text("Scan QR") }
                    }
                    uiState.storageCleanerMessage?.let { message ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(message, fontSize = 12.sp, color = AeroSecondaryGreen)
                    }
                    if (scanning && hasCameraPermission) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            factory = { ctx ->
                                DecoratedBarcodeView(ctx).apply {
                                    scannerView.value = this
                                    decodeContinuous { result ->
                                        val scannedCode = result.text.substringAfter("AEROSHARE:", result.text).filter(Char::isDigit)
                                        if (scannedCode.length == 6) {
                                            scanning = false
                                            pause()
                                            post { onConnectWithPin(scannedCode) }
                                        }
                                    }
                                    resume()
                                }
                            },
                            update = { it.resume() }
                        )
                        DisposableEffect(scanning) {
                            onDispose { scannerView.value?.pause() }
                        }
                        TextButton(onClick = { scanning = false; scannerView.value?.pause() }, modifier = Modifier.align(Alignment.End)) { Text("Close scanner") }
                    }
                }
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

        if (uiState.nearbyDevices.isEmpty()) {
            item {
                AeroCardBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    backgroundColor = AeroCard,
                    borderColor = AeroBorder,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Searching for devices…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AeroTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Keep both phones on the same Wi-Fi network and open Receive on the other phone. Nearby Aero SHAREit devices appear here automatically.",
                            fontSize = 12.sp,
                            color = AeroTextSecondary
                        )
                    }
                }
            }
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
                        Text(text = "${peer.deviceModel} • Local TCP ${peer.port}", fontSize = 11.sp, color = AeroSecondaryGreen)
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
