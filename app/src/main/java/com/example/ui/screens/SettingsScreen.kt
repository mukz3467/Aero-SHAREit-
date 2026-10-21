package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AeroCardBox
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroSurface
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary
import com.example.viewmodel.TransferUiState

@Composable
fun SettingsScreen(
    uiState: TransferUiState,
    onUpdateDeviceName: (String) -> Unit,
    onToggle5Ghz: (Boolean) -> Unit,
    onToggleEncryption: (Boolean) -> Unit,
    onToggleAutoDisconnect: (Boolean) -> Unit,
    onToggleLiteMode: (Boolean) -> Unit,
    onCleanCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCleanCacheDialog by remember { mutableStateOf(false) }
    var deviceNameInput by remember(uiState.deviceName) { mutableStateOf(uiState.deviceName) }

    if (showCleanCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCleanCacheDialog = false },
            containerColor = AeroCard,
            icon = { Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = AeroEmerald, modifier = Modifier.size(28.dp)) },
            title = { Text(text = "Clean App Cache & Temporary Parts?", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 17.sp) },
            text = {
                Text(
                    text = "This will clean ${uiState.cacheReport.formattedTotalCache} of cache, including temporary transfer parts. Verified received files are stored in Aero SHAREit's app-specific Downloads folder and are not removed by cache cleanup.",
                    color = AeroTextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanCacheDialog = false
                        onCleanCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                    modifier = Modifier.testTag("confirm_clean_cache_settings")
                ) {
                    Text("Clean Cache", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanCacheDialog = false }) {
                    Text("Cancel", color = AeroTextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Settings & Hardware Links",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AeroTextPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
            )
        }

        // Device Identity
        item {
            SectionTitle(title = "Device Identity")
        }

        item {
            AeroCardBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                backgroundColor = AeroCard,
                borderColor = AeroBorder,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Broadcast Device Name", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AeroTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = deviceNameInput,
                        onValueChange = {
                            deviceNameInput = it
                            onUpdateDeviceName(it)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_device_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AeroEmerald,
                            unfocusedBorderColor = AeroBorder,
                            focusedTextColor = AeroTextPrimary,
                            unfocusedTextColor = AeroTextPrimary
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // Security & Protocols
        item {
            SectionTitle(title = "Transfer Security")
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "AES-GCM session encryption is active",
                    color = AeroTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "File chunks are encrypted with AES-GCM using an ECDH-derived session key, and received files are verified with SHA-256. Peer identity is not yet authenticated, so this does not prevent a man-in-the-middle attack; use trusted local networks.",
                    color = AeroTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        item {
            SettingsInfoCard(
                icon = Icons.Default.Wifi,
                title = "Nearby Wi-Fi Discovery",
                value = "Automatic discovery on the same local Wi-Fi network"
            )
        }

        // Hardware & Bands
        item {
            SectionTitle(title = "Hardware & Wi-Fi Bands")
        }

        item {
            SettingsToggleCard(
                icon = Icons.Default.Wifi,
                title = "Prefer 5GHz Band (advisory)",
                subtitle = "This preference does not force the Wi-Fi band; Android and the connected network control it.",
                isChecked = uiState.is5GhzPreferred,
                onCheckedChange = onToggle5Ghz
            )
        }

        item {
            SettingsToggleCard(
                icon = Icons.Default.WifiTethering,
                title = "Auto-Disconnect (not integrated)",
                subtitle = "Automatic hotspot or Wi-Fi Direct lifecycle management is not implemented yet.",
                isChecked = uiState.autoDisconnect,
                onCheckedChange = onToggleAutoDisconnect
            )
        }

        // Storage & Cache
        item {
            SectionTitle(title = "Storage & Integrity")
        }

        item {
            SettingsInfoCard(
                icon = Icons.Default.Folder,
                title = "Download Location",
                value = "/storage/emulated/0/Download/AeroShare"
            )
        }

        item {
            SettingsActionCard(
                icon = Icons.Default.CleaningServices,
                title = "App Cache & Temporary Parts",
                subtitle = "${uiState.cacheReport.formattedTotalCache} cache • ${uiState.cacheReport.abandonedTransfersCount} temporary parts",
                actionLabel = "Clean",
                onClick = { showCleanCacheDialog = true }
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "AeroShare v1.0.0", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AeroTextSecondary)
                Text(text = "Secure Offline P2P Transfer • Zero Cloud Routing", fontSize = 11.sp, color = AeroSecondaryGreen)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = AeroTextPrimary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AeroCardBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        backgroundColor = AeroCard,
        borderColor = AeroBorder,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(AeroEmeraldSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = AeroEmerald, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AeroTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 11.sp, color = AeroTextSecondary, lineHeight = 15.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AeroBackground,
                    checkedTrackColor = AeroEmerald,
                    uncheckedThumbColor = AeroTextSecondary,
                    uncheckedTrackColor = AeroSurface
                )
            )
        }
    }
}

@Composable
private fun SettingsInfoCard(
    icon: ImageVector,
    title: String,
    value: String
) {
    AeroCardBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        backgroundColor = AeroCard,
        borderColor = AeroBorder,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(AeroEmeraldSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = AeroEmerald, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AeroTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = value, fontSize = 11.sp, color = AeroTextSecondary)
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    AeroCardBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        backgroundColor = AeroCard,
        borderColor = AeroBorder,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(AeroEmeraldSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = AeroEmerald, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AeroTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 11.sp, color = AeroTextSecondary)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = AeroEmeraldSubtle, contentColor = AeroEmerald),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp).testTag("settings_clean_cache_button")
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
