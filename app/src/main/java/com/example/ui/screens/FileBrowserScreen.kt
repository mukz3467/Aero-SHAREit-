package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.FileCategory
import com.example.ui.components.FileExplorerComponent
import com.example.ui.theme.AeroBackground
import com.example.viewmodel.ActiveScreen
import com.example.viewmodel.TransferUiState

@Composable
fun FileBrowserScreen(
    uiState: TransferUiState,
    onBack: () -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onToggleSelectFile: (String) -> Unit,
    onSelectAllCategory: (FileCategory) -> Unit,
    onClearSelection: () -> Unit,
    onSendSelected: () -> Unit,
    onRefreshScan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AeroBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            FileExplorerComponent(
                files = uiState.availableFiles,
                selectedFileIds = uiState.selectedFiles,
                currentCategory = uiState.selectedCategory,
                categoryCounts = uiState.categoryCounts,
                categorySizes = uiState.categorySizes,
                isScanning = uiState.isScanningMedia,
                hasStoragePermission = uiState.hasStoragePermission,
                onSelectCategory = onSelectCategory,
                onToggleSelectFile = onToggleSelectFile,
                onSelectAllCurrentCategory = onSelectAllCategory,
                onClearSelection = onClearSelection,
                onSendSelected = onSendSelected,
                onRequestPermission = onRefreshScan
            )
        }
    }
}
