package com.example.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.components.AeroCardBox
import com.example.ui.components.getCategoryVisual
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroCardElevated
import com.example.ui.theme.AeroCyanAccent
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldMedium
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroSurface
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary
import com.example.viewmodel.ActiveScreen
import com.example.viewmodel.TransferUiState

data class CategoryItemData(
    val category: FileCategory,
    val count: Int,
    val sizeBytes: Long
)

@Composable
fun HomeScreen(
    uiState: TransferUiState,
    onNavigate: (ActiveScreen) -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onStartSendFlow: () -> Unit,
    onStartReceiveFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AeroBackground,
        bottomBar = {
            AeroBottomNavBar(
                currentScreen = uiState.currentScreen,
                onNavigate = onNavigate,
                historyBadgeCount = uiState.historyRecords.size
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header
            item {
                HomeHeaderSection(
                    deviceName = uiState.deviceName,
                    autopilotRecommendation = uiState.autopilotRecommendation
                )
            }

            // Big Action Cards: Send & Receive
            item {
                SendReceiveCardsSection(
                    onSend = onStartSendFlow,
                    onReceive = onStartReceiveFlow
                )
            }

            // Category Explorer Grid
            item {
                CategoryGridSection(
                    categoryCounts = uiState.categoryCounts,
                    categorySizes = uiState.categorySizes,
                    onSelectCategory = onSelectCategory
                )
            }

            // Quick Tools Section
            item {
                QuickToolsSection(
                    onPinCodeClick = { onNavigate(ActiveScreen.RECEIVING_BEACON) },
                    onQrScannerClick = { onNavigate(ActiveScreen.DISCOVERING_PEERS) },
                    onWebShareClick = { onNavigate(ActiveScreen.PHONE_TO_PC) },
                    onGroupRoomClick = { onNavigate(ActiveScreen.GROUP_ROOM) },
                    onClonePhoneClick = { onNavigate(ActiveScreen.PHONE_MIGRATION) }
                )
            }
        }
    }
}

@Composable
private fun HomeHeaderSection(
    deviceName: String,
    autopilotRecommendation: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AeroShare",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = AeroTextPrimary
                )
                Text(
                    text = "Device: $deviceName",
                    fontSize = 12.sp,
                    color = AeroSecondaryGreen,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AeroEmeraldSubtle)
                    .border(1.dp, AeroEmerald, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Turbo",
                        tint = AeroEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Local Network",
                        color = AeroEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Autopilot Recommendation Chip
        AeroCardBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = AeroCard,
            borderColor = AeroBorder,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wi-Fi",
                    tint = AeroSecondaryGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = autopilotRecommendation,
                    fontSize = 11.sp,
                    color = AeroTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SendReceiveCardsSection(
    onSend: () -> Unit,
    onReceive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SEND CARD
        AeroCardBox(
            modifier = Modifier
                .weight(1f)
                .height(120.dp)
                .clickable { onSend() }
                .testTag("home_send_button"),
            backgroundColor = AeroEmerald,
            borderColor = AeroSecondaryGreen,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AeroBackground.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = AeroBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "SEND",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = AeroBackground
                    )
                    Text(
                        text = "Pick files to beam",
                        fontSize = 11.sp,
                        color = AeroBackground.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // RECEIVE CARD
        AeroCardBox(
            modifier = Modifier
                .weight(1f)
                .height(120.dp)
                .clickable { onReceive() }
                .testTag("home_receive_button"),
            backgroundColor = AeroCardElevated,
            borderColor = AeroEmerald,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AeroEmeraldSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignBottom,
                        contentDescription = "Receive",
                        tint = AeroEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "RECEIVE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = AeroTextPrimary
                    )
                    Text(
                        text = "Ready to accept",
                        fontSize = 11.sp,
                        color = AeroSecondaryGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGridSection(
    categoryCounts: Map<FileCategory, Int>,
    categorySizes: Map<FileCategory, Long>,
    onSelectCategory: (FileCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Categorized Storage",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = AeroTextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        val categories = listOf(
            FileCategory.PHOTOS,
            FileCategory.VIDEOS,
            FileCategory.AUDIO,
            FileCategory.DOCUMENTS,
            FileCategory.APPS,
            FileCategory.ARCHIVES
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                category = categories[0],
                count = categoryCounts[categories[0]] ?: 0,
                size = categorySizes[categories[0]] ?: 0L,
                onClick = { onSelectCategory(categories[0]) },
                modifier = Modifier.weight(1f)
            )
            CategoryTile(
                category = categories[1],
                count = categoryCounts[categories[1]] ?: 0,
                size = categorySizes[categories[1]] ?: 0L,
                onClick = { onSelectCategory(categories[1]) },
                modifier = Modifier.weight(1f)
            )
            CategoryTile(
                category = categories[2],
                count = categoryCounts[categories[2]] ?: 0,
                size = categorySizes[categories[2]] ?: 0L,
                onClick = { onSelectCategory(categories[2]) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                category = categories[3],
                count = categoryCounts[categories[3]] ?: 0,
                size = categorySizes[categories[3]] ?: 0L,
                onClick = { onSelectCategory(categories[3]) },
                modifier = Modifier.weight(1f)
            )
            CategoryTile(
                category = categories[4],
                count = categoryCounts[categories[4]] ?: 0,
                size = categorySizes[categories[4]] ?: 0L,
                onClick = { onSelectCategory(categories[4]) },
                modifier = Modifier.weight(1f)
            )
            CategoryTile(
                category = categories[5],
                count = categoryCounts[categories[5]] ?: 0,
                size = categorySizes[categories[5]] ?: 0L,
                onClick = { onSelectCategory(categories[5]) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryTile(
    category: FileCategory,
    count: Int,
    size: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, icon) = getCategoryVisual(category)

    AeroCardBox(
        modifier = modifier
            .height(86.dp)
            .clickable { onClick() }
            .testTag("category_tile_${category.name.lowercase()}"),
        backgroundColor = AeroCard,
        borderColor = AeroBorder,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.displayName,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = category.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = AeroTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "$count items",
                    fontSize = 10.sp,
                    color = AeroTextSecondary
                )
            }
        }
    }
}

@Composable
private fun QuickToolsSection(
    onPinCodeClick: () -> Unit,
    onQrScannerClick: () -> Unit,
    onWebShareClick: () -> Unit,
    onGroupRoomClick: () -> Unit,
    onClonePhoneClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Quick Tools & Transfer Hub",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = AeroTextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolCard(
                icon = Icons.Default.Password,
                title = "PIN Code",
                subtitle = "Connect with PIN",
                onClick = onPinCodeClick,
                modifier = Modifier.weight(1f)
            )
            ToolCard(
                icon = Icons.Default.QrCodeScanner,
                title = "QR Scanner",
                subtitle = "Scan to connect",
                onClick = onQrScannerClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolCard(
                icon = Icons.Default.Laptop,
                title = "PC Bridge",
                subtitle = "Port 8844",
                onClick = onWebShareClick,
                modifier = Modifier.weight(1f)
            )
            ToolCard(
                icon = Icons.Default.Group,
                title = "Group Room",
                subtitle = "Multi-Drop",
                onClick = onGroupRoomClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolCard(
                icon = Icons.Default.Sync,
                title = "Clone Phone",
                subtitle = "Migration",
                onClick = onClonePhoneClick,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AeroCardBox(
        modifier = modifier
            .height(64.dp)
            .clickable { onClick() },
        backgroundColor = AeroCard,
        borderColor = AeroBorder,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AeroEmeraldSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = AeroEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = AeroTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = AeroTextSecondary
                )
            }
        }
    }
}

@Composable
private fun AeroBottomNavBar(
    currentScreen: ActiveScreen,
    onNavigate: (ActiveScreen) -> Unit,
    historyBadgeCount: Int
) {
    NavigationBar(
        containerColor = AeroSurface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == ActiveScreen.HOME,
            onClick = { onNavigate(ActiveScreen.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AeroEmerald,
                selectedTextColor = AeroEmerald,
                unselectedIconColor = AeroTextSecondary,
                unselectedTextColor = AeroTextSecondary,
                indicatorColor = AeroEmeraldSubtle
            )
        )
        NavigationBarItem(
            selected = currentScreen == ActiveScreen.FILE_BROWSER,
            onClick = { onNavigate(ActiveScreen.FILE_BROWSER) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
            label = { Text("Files", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AeroEmerald,
                selectedTextColor = AeroEmerald,
                unselectedIconColor = AeroTextSecondary,
                unselectedTextColor = AeroTextSecondary,
                indicatorColor = AeroEmeraldSubtle
            )
        )
        NavigationBarItem(
            selected = currentScreen == ActiveScreen.HISTORY,
            onClick = { onNavigate(ActiveScreen.HISTORY) },
            icon = {
                if (historyBadgeCount > 0) {
                    BadgedBox(badge = { Badge { Text("$historyBadgeCount") } }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                } else {
                    Icon(Icons.Default.History, contentDescription = "History")
                }
            },
            label = { Text("History", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AeroEmerald,
                selectedTextColor = AeroEmerald,
                unselectedIconColor = AeroTextSecondary,
                unselectedTextColor = AeroTextSecondary,
                indicatorColor = AeroEmeraldSubtle
            )
        )
        NavigationBarItem(
            selected = currentScreen == ActiveScreen.SETTINGS,
            onClick = { onNavigate(ActiveScreen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AeroEmerald,
                selectedTextColor = AeroEmerald,
                unselectedIconColor = AeroTextSecondary,
                unselectedTextColor = AeroTextSecondary,
                indicatorColor = AeroEmeraldSubtle
            )
        )
    }
}
