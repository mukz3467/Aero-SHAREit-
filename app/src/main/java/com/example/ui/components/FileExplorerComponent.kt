package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StorageSmartManager
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBlueAccent
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroCardElevated
import com.example.ui.theme.AeroCyanAccent
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroOrangeAccent
import com.example.ui.theme.AeroPurpleAccent
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary

enum class ExplorerSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    DATE_DESC("Newest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

data class CategoryTabInfo(
    val category: FileCategory,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun FileExplorerComponent(
    files: List<FileItem>,
    selectedFileIds: Set<String>,
    currentCategory: FileCategory,
    categoryCounts: Map<FileCategory, Int>,
    categorySizes: Map<FileCategory, Long>,
    isScanning: Boolean,
    hasStoragePermission: Boolean,
    onSelectCategory: (FileCategory) -> Unit,
    onToggleSelectFile: (String) -> Unit,
    onSelectAllCurrentCategory: (FileCategory) -> Unit,
    onClearSelection: () -> Unit,
    onSendSelected: () -> Unit,
    onSortChanged: (ExplorerSortOption) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(ExplorerSortOption.DATE_DESC) }

    val filteredFiles = remember(files, currentCategory, searchQuery, currentSort) {
        var result = files.filter { it.category == currentCategory }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(q) }
        }
        when (currentSort) {
            ExplorerSortOption.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            ExplorerSortOption.DATE_DESC -> result.sortedByDescending { it.dateModified }
            ExplorerSortOption.SIZE_DESC -> result.sortedByDescending { it.sizeBytes }
            ExplorerSortOption.SIZE_ASC -> result.sortedBy { it.sizeBytes }
        }
    }

    val duplicateFileIds = remember(files) {
        files.groupBy { "${it.name.trim().lowercase()}|${it.sizeBytes}" }
            .values
            .filter { it.size > 1 }
            .flatten()
            .map { it.id }
            .toSet()
    }
    val selectedDuplicateCount = files.count {
        selectedFileIds.contains(it.id) && duplicateFileIds.contains(it.id)
    }
    val selectedCount = selectedFileIds.size
    val selectedTotalBytes = remember(selectedFileIds, files) {
        files.filter { selectedFileIds.contains(it.id) }.sumOf { it.sizeBytes }
    }

    Box(modifier = modifier.fillMaxSize().background(AeroBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search & View Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ${currentCategory.displayName}...", fontSize = 13.sp, color = AeroTextSecondary) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = AeroTextSecondary, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("file_explorer_search"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AeroEmerald,
                        unfocusedBorderColor = AeroBorder,
                        focusedContainerColor = AeroCard,
                        unfocusedContainerColor = AeroCard,
                        focusedTextColor = AeroTextPrimary,
                        unfocusedTextColor = AeroTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { isGridView = !isGridView },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AeroCard)
                        .border(1.dp, AeroBorder, RoundedCornerShape(10.dp))
                        .testTag("toggle_grid_view")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = AeroEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Category Horizontal Tabs
            ExplorerCategoryTabs(
                selectedCategory = currentCategory,
                categoryCounts = categoryCounts,
                onSelectCategory = onSelectCategory
            )

            // Select All / Action Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${filteredFiles.size} items • ${StorageSmartManager.formatBytes(categorySizes[currentCategory] ?: 0L)}",
                    fontSize = 12.sp,
                    color = AeroTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Row {
                    Text(
                        text = "Select All",
                        fontSize = 12.sp,
                        color = AeroEmerald,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onSelectAllCurrentCategory(currentCategory) }
                            .padding(4.dp)
                            .testTag("explorer_select_all")
                    )
                }
            }

            // Content List / Grid
            if (isScanning) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AeroEmerald, strokeWidth = 2.dp)
                }
            } else if (filteredFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = getCategoryVisual(currentCategory).second, contentDescription = null, tint = AeroTextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No ${currentCategory.displayName} Found", color = AeroTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Tap + SAF Picker to import external files", color = AeroTextSecondary, fontSize = 12.sp)
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { item ->
                        FileGridItem(
                            item = item,
                            isSelected = selectedFileIds.contains(item.id),
                            onToggle = { onToggleSelectFile(item.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { item ->
                        FileListRowItem(
                            item = item,
                            isSelected = selectedFileIds.contains(item.id),
                            onToggle = { onToggleSelectFile(item.id) }
                        )
                    }
                }
            }
        }

        // Floating Batch Dock
        AnimatedVisibility(
            visible = selectedCount > 0,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            AeroCardBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (selectedDuplicateCount > 0) 78.dp else 60.dp),
                backgroundColor = AeroCardElevated,
                borderColor = AeroEmerald,
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$selectedCount Selected (${StorageSmartManager.formatBytes(selectedTotalBytes)})",
                            color = AeroTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (selectedDuplicateCount > 0) {
                            Text(
                                text = "Potential duplicates: $selectedDuplicateCount selected",
                                color = AeroOrangeAccent,
                                fontSize = 10.sp
                            )
                        } else {
                            Text(
                                text = "Direct local-network transfer",
                                color = AeroSecondaryGreen,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = onSendSelected,
                        colors = ButtonDefaults.buttonColors(containerColor = AeroEmerald, contentColor = AeroBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp).testTag("explorer_beam_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Beam Files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerCategoryTabs(
    selectedCategory: FileCategory,
    categoryCounts: Map<FileCategory, Int>,
    onSelectCategory: (FileCategory) -> Unit
) {
    val tabs = listOf(
        CategoryTabInfo(FileCategory.PHOTOS, Icons.Default.Image, AeroCyanAccent),
        CategoryTabInfo(FileCategory.VIDEOS, Icons.Default.Videocam, AeroPurpleAccent),
        CategoryTabInfo(FileCategory.AUDIO, Icons.Default.Headphones, AeroOrangeAccent),
        CategoryTabInfo(FileCategory.DOCUMENTS, Icons.Default.Description, AeroBlueAccent),
        CategoryTabInfo(FileCategory.APPS, Icons.Default.PhoneAndroid, AeroEmerald),
        CategoryTabInfo(FileCategory.ARCHIVES, Icons.Default.FolderZip, AeroSecondaryGreen)
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs) { tab ->
            val isSelected = tab.category == selectedCategory
            val count = categoryCounts[tab.category] ?: 0

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AeroEmeraldSubtle else AeroCard)
                    .border(1.dp, if (isSelected) AeroEmerald else AeroBorder, RoundedCornerShape(12.dp))
                    .clickable { onSelectCategory(tab.category) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("category_tab_${tab.category.name.lowercase()}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.category.displayName,
                        tint = if (isSelected) AeroEmerald else tab.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.category.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AeroEmerald else AeroTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($count)",
                        fontSize = 10.sp,
                        color = AeroTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun FileListRowItem(
    item: FileItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val (color, icon) = getCategoryVisual(item.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .background(if (isSelected) AeroEmeraldSubtle.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = AeroTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.sizeFormatted} • ${item.extension}",
                fontSize = 11.sp,
                color = AeroTextSecondary
            )
        }

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) AeroEmerald else Color.Transparent)
                .border(1.5.dp, if (isSelected) AeroEmerald else AeroBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = AeroBackground, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun FileGridItem(
    item: FileItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val (color, icon) = getCategoryVisual(item.category)

    AeroCardBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable { onToggle() },
        backgroundColor = if (isSelected) AeroEmeraldSubtle else AeroCard,
        borderColor = if (isSelected) AeroEmerald else AeroBorder,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AeroEmerald else Color.Transparent)
                        .border(1.dp, if (isSelected) AeroEmerald else AeroBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = AeroBackground, modifier = Modifier.size(12.dp))
                    }
                }
            }

            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.name,
                    fontSize = 11.sp,
                    color = AeroTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = item.sizeFormatted, fontSize = 9.sp, color = AeroTextSecondary)
            }
        }
    }
}

fun getCategoryVisual(category: FileCategory): Pair<Color, ImageVector> {
    return when (category) {
        FileCategory.PHOTOS -> Pair(AeroCyanAccent, Icons.Default.Image)
        FileCategory.VIDEOS -> Pair(AeroPurpleAccent, Icons.Default.Videocam)
        FileCategory.AUDIO -> Pair(AeroOrangeAccent, Icons.Default.Headphones)
        FileCategory.DOCUMENTS -> Pair(AeroBlueAccent, Icons.Default.Description)
        FileCategory.APPS -> Pair(AeroEmerald, Icons.Default.PhoneAndroid)
        FileCategory.ARCHIVES -> Pair(AeroSecondaryGreen, Icons.Default.FolderZip)
    }
}
