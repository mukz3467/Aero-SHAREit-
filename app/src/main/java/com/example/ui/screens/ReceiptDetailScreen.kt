package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
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
import com.example.engine.StorageSmartManager
import com.example.model.VerifiedReceipt
import com.example.ui.components.AeroCardBox
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptDetailScreen(
    receipt: VerifiedReceipt,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val formattedDate = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()).format(Date(receipt.timestamp))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag("receipt_detail_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("receipt_back")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AeroTextPrimary)
                }

                Text(
                    text = "Cryptographic Transfer Receipt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AeroTextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                item {
                    AeroCardBox(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        backgroundColor = AeroCard,
                        borderColor = AeroEmerald,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = AeroEmerald, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Cryptographically Verified", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AeroTextPrimary)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "Session ID: ${receipt.sessionId}", fontSize = 11.sp, color = AeroTextSecondary)
                            Text(text = "Peer: ${receipt.peerDeviceName} (${receipt.peerIpAddress})", fontSize = 12.sp, color = AeroTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(text = "Total Payload: ${StorageSmartManager.formatBytes(receipt.totalBytesTransferred)}", fontSize = 12.sp, color = AeroTextSecondary)
                            Text(text = "Average Speed: %.1f MB/s".format(receipt.averageSpeedMbps), fontSize = 12.sp, color = AeroSecondaryGreen, fontWeight = FontWeight.Bold)
                            Text(text = "Timestamp: $formattedDate", fontSize = 11.sp, color = AeroTextSecondary)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Verified Files (${receipt.fileReceiptItems.size})", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(receipt.fileReceiptItems) { item ->
                    AeroCardBox(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        backgroundColor = AeroCard,
                        borderColor = AeroBorder,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = item.fileName, fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 13.sp)
                                Text(text = StorageSmartManager.formatBytes(item.fileSizeBytes), color = AeroTextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-256: ${item.sha256Checksum}",
                                fontSize = 10.sp,
                                color = AeroSecondaryGreen,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
