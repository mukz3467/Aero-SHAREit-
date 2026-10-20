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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
    val title: String,
    val countDescription: String,
    val sizeBytes: Long,
    val icon: ImageVector,
    val accentColor: Color,
    var isSelected: Boolean = true
)

@Composable
fun PhoneMigrationScreen(
    onBack: () -> Unit,
    onStartMigration: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val categories = remember {
        mutableStateListOf(
            MigrationCategoryItem("apps", "Apps & Packages", "4 Applications", 120_000_000L, Icons.Default.Android, AeroEmerald, true),
            MigrationCategoryItem("photos", "Photos & Gallery", "312 Images", 1_450_000_000L, Icons.Default.Image, AeroCyanAccent, true),
            MigrationCategoryItem("videos", "Videos & Movies", "18 Videos", 2_800_000_000L, Icons.Default.Videocam, AeroPurpleAccent, true),
            MigrationCategoryItem("music", "Music & Audio", "84 Tracks", 520_000_000L, Icons.Default.Headphones, AeroOrangeAccent, true),
            MigrationCategoryItem("docs", "Documents & PDFs", "46 Documents", 140_000_000L, Icons.Default.Description, AeroBlueAccent, true)
        )
    }

    val totalSelectedBytes = categories.filter { it.isSelected }.sumOf { it.sizeBytes }

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
                        text = "Clone Old Phone to New Phone • Direct 5GHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = AeroEmerald
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                items(categories) { item ->
                    AeroCardBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { item.isSelected = !item.isSelected },
                        backgroundColor = AeroCard,
                        borderColor = if (item.isSelected) AeroEmerald else AeroBorder,
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
                                    .background(if (item.isSelected) AeroEmerald else Color.Transparent)
                                    .border(1.5.dp, if (item.isSelected) AeroEmerald else AeroBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.isSelected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = AeroBackground, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Button(
                    onClick = { onStartMigration(totalSelectedBytes) },
                    colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_migration_button"),
                    enabled = totalSelectedBytes > 0
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
