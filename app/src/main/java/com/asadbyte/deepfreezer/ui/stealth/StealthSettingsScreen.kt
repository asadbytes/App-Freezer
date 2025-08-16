package com.asadbyte.deepfreezer.ui.stealth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import com.asadbyte.deepfreezer.ui.settings.SettingsViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asadbyte.deepfreezer.domain.AppInfo
import com.asadbyte.deepfreezer.utils.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StealthModeSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    // A bug fix: Use LaunchedEffect to ensure selectedApps updates when the screen is shown
    var selectedApps by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(uiState.whitelistedStealthApps) {
        selectedApps = uiState.whitelistedStealthApps
    }

    var searchQuery by remember { mutableStateOf("") }
    val localFocusManager = LocalFocusManager.current

    val filteredApps = remember(uiState.allApps, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.allApps
        } else {
            uiState.allApps.filter { app ->
                app.appName.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // --- THIS IS THE FIX ---
    // This new derived state sorts the filtered list.
    // It prioritizes selected apps, then sorts alphabetically.
    val sortedAndFilteredApps = remember(filteredApps, selectedApps) {
        filteredApps.sortedWith(
            compareBy<AppInfo> { it.packageName !in selectedApps } // Selected apps first
                .thenBy { it.appName.lowercase() } // Then sort by name
        )
    }
    // --- END OF FIX ---

    LaunchedEffect(Unit) {
        viewModel.loadAllAppsForSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Stealth Apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveStealthModeApps(selectedApps)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Search Apps") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                keyboardActions = KeyboardActions(
                    onDone = { localFocusManager.clearFocus() }
                )
            )

            if (uiState.allApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Use the new sortedAndFilteredApps list here
                    items(sortedAndFilteredApps, key = { it.packageName }) { app ->
                        AppSelectItem(
                            app = app,
                            isSelected = app.packageName in selectedApps,
                            onToggle = {
                                selectedApps = if (app.packageName in selectedApps) {
                                    selectedApps - app.packageName
                                } else {
                                    selectedApps + app.packageName
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    BackHandler {
        onNavigateBack()
    }
}

@Composable
fun AppSelectItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.icon?.let {
            Icon(
                painter = rememberDrawablePainter(it),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.appName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}