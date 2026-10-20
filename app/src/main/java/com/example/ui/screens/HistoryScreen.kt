package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StorageSmartManager
import com.example.model.FileReceiptItem
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import com.example.model.VerifiedReceipt
import com.example.ui.components.AeroCardBox
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroCardElevated
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroOrangeAccent
import com.example.ui.theme.AeroPurpleAccent
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroSurface
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary
import com.example.viewmodel.TransferUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    uiState: TransferUiState,
    onClearHistory: () -> Unit,
    onViewReceipt: (VerifiedReceipt) -> Unit,
    onToggleSelectRecord: (String) -> Unit = {},
    onSelectAllRecords: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRemoveSelectedFromHistory: () -> Unit = {},
    onDeleteSelectedActualFiles: () -> Unit = {},
    onCleanCache: () -> Unit = {},
    onDismissStorageMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteActualDialog by remember { mutableStateOf(false) }
    var showRemoveHistoryDialog by remember { mutableStateOf(false) }
    var showCleanCacheDialog by remember { mutableStateOf(false) }

    val filteredRecords = when (selectedTab) {
        1 -> uiState.historyRecords.filter { !it.isIncoming }
        2 -> uiState.historyRecords.filter { it.isIncoming }
        else -> uiState.historyRecords
    }

    val selectedCount = uiState.selectedHistoryRecordIds.size
    val selectedRecords = uiState.historyRecords.filter { uiState.selectedHistoryRecordIds.contains(it.id) }
    val selectedTotalBytes = selectedRecords.sumOf { it.totalBytes }

    // Dialog: Delete Actual Files
    if (showDeleteActualDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteActualDialog = false },
            containerColor = AeroCard,
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AeroOrangeAccent, modifier = Modifier.size(28.dp)) },
            title = { Text(text = "Delete Genuine File(s) from Device?", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 17.sp) },
            text = {
                Column {
                    Text(text = "Permanently delete $selectedCount selected file(s) (${StorageSmartManager.formatBytes(selectedTotalBytes)}) from your device storage?", color = AeroTextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "• For sent files: This deletes the original file from your device.\n• For received files: This deletes the saved file from your device.\n• This action cannot be undone.", color = AeroTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteActualDialog = false
                        onDeleteSelectedActualFiles()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_actual_button")
                ) {
                    Text("Delete from Device", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteActualDialog = false }) {
                    Text("Cancel", color = AeroTextSecondary)
                }
            }
        )
    }

    // Dialog: Remove History Only
    if (showRemoveHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveHistoryDialog = false },
            containerColor = AeroCard,
            icon = { Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = AeroEmerald, modifier = Modifier.size(28.dp)) },
            title = { Text(text = "Remove from Transfer History?", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 17.sp) },
            text = {
                Text(text = "Remove $selectedCount record(s) from history? The actual files on your device will NOT be deleted.", color = AeroTextPrimary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveHistoryDialog = false
                        onRemoveSelectedFromHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                    modifier = Modifier.testTag("confirm_remove_history_button")
                ) {
                    Text("Remove Records", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveHistoryDialog = false }) {
                    Text("Cancel", color = AeroTextSecondary)
                }
            }
        )
    }

    // Dialog: Clean Cache
    if (showCleanCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCleanCacheDialog = false },
            containerColor = AeroCard,
            icon = { Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = AeroSecondaryGreen, modifier = Modifier.size(28.dp)) },
            title = { Text(text = "Clean App Cache & Temporary Parts?", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 17.sp) },
            text = {
                Column {
                    Text(text = "This will clean ${uiState.cacheReport.formattedTotalCache} of application cache, including ${uiState.cacheReport.abandonedTransfersCount} temporary transfer parts. Your completed saved files will not be touched.", color = AeroTextPrimary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanCacheDialog = false
                        onCleanCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                    modifier = Modifier.testTag("confirm_clean_cache_button")
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag("history_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Transfer History", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = AeroTextPrimary)
                    Text("${uiState.historyRecords.size} logged offline transfers", fontSize = 12.sp, color = AeroSecondaryGreen)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCleanCacheDialog = true }, modifier = Modifier.testTag("history_clean_cache_icon")) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = "Clean Cache", tint = AeroSecondaryGreen)
                    }
                    if (uiState.historyRecords.isNotEmpty()) {
                        IconButton(onClick = onClearHistory, modifier = Modifier.testTag("clear_history_button")) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = AeroTextSecondary)
                        }
                    }
                }
            }

            // Storage message banner
            if (uiState.storageCleanerMessage != null) {
                AeroCardBox(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    backgroundColor = AeroCardElevated,
                    borderColor = AeroEmerald,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AeroEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = uiState.storageCleanerMessage, fontSize = 12.sp, color = AeroTextPrimary)
                        }
                        IconButton(onClick = onDismissStorageMessage, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = AeroTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AeroBackground,
                contentColor = AeroEmerald,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AeroEmerald
                    )
                }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("All (${uiState.historyRecords.size})", fontSize = 12.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Sent", fontSize = 12.sp) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Received", fontSize = 12.sp) })
            }

            // Multi-Select Action Bar if items selected
            if (selectedCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AeroCardElevated)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$selectedCount selected (${StorageSmartManager.formatBytes(selectedTotalBytes)})",
                        fontSize = 12.sp,
                        color = AeroTextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showRemoveHistoryDialog = true },
                            modifier = Modifier.height(34.dp).testTag("action_remove_history"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text("Remove Record", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showDeleteActualDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                            modifier = Modifier.height(34.dp).testTag("action_delete_actual"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (filteredRecords.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Select All",
                        fontSize = 12.sp,
                        color = AeroEmerald,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onSelectAllRecords() }.padding(4.dp)
                    )
                }
            }

            // History Records List
            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = AeroTextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No Transfer Records Yet", fontWeight = FontWeight.Bold, color = AeroTextPrimary, fontSize = 15.sp)
                        Text("Transferred files will appear here with cryptographic receipts", color = AeroTextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    items(filteredRecords, key = { it.id }) { record ->
                        val isSelected = uiState.selectedHistoryRecordIds.contains(record.id)
                        HistoryRecordCard(
                            record = record,
                            isSelected = isSelected,
                            onToggleSelect = { onToggleSelectRecord(record.id) },
                            onViewReceipt = {
                                val receipt = uiState.receipts.find { it.sessionId == record.id } ?: VerifiedReceipt(
                                    sessionId = record.id,
                                    peerDeviceName = record.peerName,
                                    peerIpAddress = "192.168.49.1",
                                    totalBytesTransferred = record.totalBytes,
                                    averageSpeedMbps = record.speedMbps,
                                    fileReceiptItems = listOf(
                                        FileReceiptItem(
                                            fileName = record.fileName,
                                            fileSizeBytes = record.totalBytes,
                                            sha256Checksum = record.sha256Checksum ?: "verified_sha256_hash",
                                            verified = true
                                        )
                                    )
                                )
                                onViewReceipt(receipt)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: TransferRecord,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onViewReceipt: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(record.timestamp))

    AeroCardBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { onToggleSelect() }
            .testTag("history_record_${record.id}"),
        backgroundColor = if (isSelected) AeroEmeraldSubtle else AeroCard,
        borderColor = if (isSelected) AeroEmerald else AeroBorder,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AeroEmerald,
                    checkmarkColor = AeroBackground,
                    uncheckedColor = AeroTextSecondary
                )
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (record.isIncoming) AeroEmeraldSubtle else AeroPurpleAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (record.isIncoming) Icons.Default.Download else Icons.Default.Upload,
                    contentDescription = null,
                    tint = if (record.isIncoming) AeroEmerald else AeroPurpleAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AeroTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${record.fileSizeFormatted} • ${record.peerName}",
                    fontSize = 11.sp,
                    color = AeroTextSecondary
                )
                if (record.isLocalFileDeleted) {
                    Text(
                        text = "File deleted from device",
                        fontSize = 10.sp,
                        color = Color(0xFFEF5350),
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "$dateStr • %.1f MB/s".format(record.speedMbps),
                        fontSize = 10.sp,
                        color = AeroSecondaryGreen
                    )
                }
            }

            IconButton(onClick = onViewReceipt, modifier = Modifier.testTag("view_receipt_${record.id}")) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Receipt",
                    tint = AeroEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
