package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StorageSmartManager
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.ui.components.AeroCardBox
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroCardElevated
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroOrangeAccent
import com.example.ui.theme.AeroRedAccent
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary

@Composable
fun TransferProgressScreen(
    record: TransferRecord?,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDone() }

    val rec = record ?: TransferRecord(
        id = "mock",
        fileName = "Waiting for Transfer",
        fileSizeFormatted = "0 B",
        totalBytes = 0L,
        transferredBytes = 0L,
        progress = 0f,
        speedMbps = 0f,
        isIncoming = false,
        peerName = "Peer",
        status = TransferStatus.WAITING_ACCEPT
    )

    val animatedProgress by animateFloatAsState(targetValue = rec.progress, label = "ProgressAnim")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag("transfer_progress_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (rec.isIncoming) "Incoming Offline Stream" else "Outgoing Offline Stream",
                    fontSize = 13.sp,
                    color = AeroSecondaryGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rec.fileName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AeroTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "Connected with: ${rec.peerName}",
                    fontSize = 12.sp,
                    color = AeroTextSecondary
                )
            }

            // Big Speed / Progress Circle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(AeroCardElevated)
                        .border(3.dp, if (rec.status == TransferStatus.COMPLETED) AeroEmerald else AeroEmeraldSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (rec.status == TransferStatus.COMPLETED) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Completed", tint = AeroEmerald, modifier = Modifier.size(52.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("100%", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = AeroTextPrimary)
                        } else {
                            Text(
                                text = "%.1f".format(rec.speedMbps),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 38.sp,
                                color = AeroEmerald
                            )
                            Text(text = "MB/s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AeroSecondaryGreen)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "${(rec.progress * 100).toInt()}%", fontSize = 14.sp, color = AeroTextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AeroEmerald,
                    trackColor = AeroCard
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${StorageSmartManager.formatBytes(rec.transferredBytes)} / ${rec.fileSizeFormatted}",
                        fontSize = 12.sp,
                        color = AeroTextSecondary
                    )
                    Text(
                        text = if (rec.status == TransferStatus.COMPLETED) "Verified SHA-256" else "AES-256-GCM Secure",
                        fontSize = 12.sp,
                        color = AeroSecondaryGreen
                    )
                }
            }

            // Action Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                if (rec.status == TransferStatus.COMPLETED) {
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("transfer_done_button")
                    ) {
                        Text("Finish & View History", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTogglePause,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(imageVector = if (rec.status == TransferStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (rec.status == TransferStatus.PAUSED) "Resume" else "Pause")
                        }

                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = AeroRedAccent, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}
