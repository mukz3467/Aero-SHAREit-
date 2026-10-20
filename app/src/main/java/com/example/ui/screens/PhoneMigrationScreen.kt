package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StorageSmartManager
import com.example.model.FileCategory
import com.example.viewmodel.TransferUiState
import com.example.ui.components.AeroCardBox
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBlueAccent
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroCyanAccent
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroOrangeAccent
import com.example.ui.theme.AeroPurpleAccent
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary

data class MigrationCategoryItem(
    val id: String,
    val category: FileCategory,
    val title: String,
    val countDescription: String,
    val sizeBytes: Long,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun PhoneMigrationScreen(
    uiState: TransferUiState,
    onBack: () -> Unit,
    onStartMigration: (Set<FileCategory>) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val categories = remember(uiState.availableFiles, uiState.categoryCounts, uiState.categorySizes) {
        FileCategory.values().mapNotNull { category ->
            val matchingFiles = uiState.availableFiles.filter { it.category == category }
            if (matchingFiles.isEmpty()) return@mapNotNull null
            val icon = when (category) {
                FileCategory.PHOTOS -> Icons.Default.Image
                FileCategory.VIDEOS -> Icons.Default.Videocam
                FileCategory.AUDIO -> Icons.Default.Headphones
                FileCategory.APPS -> Icons.Default.Android
                FileCategory.DOCUMENTS, FileCategory.ARCHIVES -> Icons.Default.Description
            }
            val accent = when (category) {
                FileCategory.PHOTOS -> AeroCyanAccent
                FileCategory.VIDEOS -> AeroPurpleAccent
                FileCategory.AUDIO -> AeroOrangeAccent
                FileCategory.APPS -> AeroEmerald
                FileCategory.DOCUMENTS -> AeroBlueAccent
                FileCategory.ARCHIVES -> AeroPurpleAccent
            }
            MigrationCategoryItem(
                id = category.name,
                category = category,
                title = category.displayName,
                countDescription = "${matchingFiles.size} files",
                sizeBytes = matchingFiles.sumOf { it.sizeBytes },
                icon = icon,
                accentColor = accent
            )
        }
    }
    var selectedIds by remember(categories) { mutableStateOf(categories.map { it.id }.toSet()) }
    val selectedCategories = categories.filter { it.id in selectedIds }.map { it.category }.toSet()
    val totalSelectedBytes = categories.filter { it.id in selectedIds }.sumOf { it.sizeBytes }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeroBackground)
            .testTag("phone_migration_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("migration_back")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AeroTextPrimary)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phone Migration Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AeroTextPrimary
                    )
                    Text(
                        text = "Select files available on this device to send",
                        style = MaterialTheme.typography.labelSmall,
                        color = AeroEmerald
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                if (categories.isEmpty()) {
                    item {
                        Text(
                            text = "No accessible files found. Grant media permissions and rescan your files first.",
                            color = AeroTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                items(categories) { item ->
                    AeroCardBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id },
                        backgroundColor = AeroCard,
                        borderColor = if ((item.id in selectedIds)) AeroEmerald else AeroBorder,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(item.accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = item.icon, contentDescription = null, tint = item.accentColor, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AeroTextPrimary)
                                Text(text = "${item.countDescription} • ${StorageSmartManager.formatBytes(item.sizeBytes)}", fontSize = 11.sp, color = AeroTextSecondary)
                            }

                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if ((item.id in selectedIds)) AeroEmerald else Color.Transparent)
                                    .border(1.5.dp, if ((item.id in selectedIds)) AeroEmerald else AeroBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if ((item.id in selectedIds)) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = AeroBackground, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Button(
                    onClick = { onStartMigration(selectedCategories) },
                    colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_migration_button"),
                    enabled = totalSelectedBytes > 0 && selectedCategories.isNotEmpty()
                ) {
                    Text(
                        text = "Migrate Selected Data (${StorageSmartManager.formatBytes(totalSelectedBytes)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
