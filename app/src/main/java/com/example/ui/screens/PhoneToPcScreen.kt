package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AeroCardBox
import com.example.ui.components.StylizedQrMatrix
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroSurface
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary

@Composable
fun PhoneToPcScreen(
    isBridgeActive: Boolean,
    bridgeUrl: String,
    connectedClientsCount: Int,
    onToggleBridge: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag("phone_to_pc_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("pc_bridge_back")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AeroTextPrimary)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phone-to-PC WebShare",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AeroTextPrimary
                    )
                    Text(
                        text = if (isBridgeActive) "HTTP Server Running on Port 8844" else "Bridge Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBridgeActive) AeroSecondaryGreen else AeroTextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Toggle Card
                AeroCardBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = AeroCard,
                    borderColor = AeroBorder,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AeroEmeraldSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Laptop, contentDescription = null, tint = AeroEmerald, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("WebShare Server", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 14.sp)
                                Text("No software needed on PC", color = AeroTextSecondary, fontSize = 11.sp)
                            }
                        }

                        Switch(
                            checked = isBridgeActive,
                            onCheckedChange = onToggleBridge,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AeroBackground,
                                checkedTrackColor = AeroEmerald,
                                uncheckedThumbColor = AeroTextSecondary,
                                uncheckedTrackColor = AeroSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isBridgeActive) {
                    Text(
                        text = "Open this link in any browser on your PC:",
                        color = AeroTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AeroCardBox(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = AeroCard,
                        borderColor = AeroEmerald,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = bridgeUrl,
                            fontWeight = FontWeight.Bold,
                            color = AeroEmerald,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StylizedQrMatrix(
                        modifier = Modifier.fillMaxWidth(),
                        ipText = bridgeUrl
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Computer, contentDescription = null, tint = AeroTextSecondary, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("This bridge currently confirms a local connection only. File upload and download are not implemented yet.", color = AeroTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
