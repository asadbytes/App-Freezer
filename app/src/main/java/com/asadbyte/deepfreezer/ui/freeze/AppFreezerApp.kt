package com.asadbyte.deepfreezer.ui.freeze

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asadbyte.deepfreezer.domain.AppInfo
import com.asadbyte.deepfreezer.utils.rememberDrawablePainter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asadbyte.deepfreezer.ui.settings.SettingsScreen
import com.asadbyte.deepfreezer.ui.settings.SettingsViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFreezerApp(
    onRequestDeviceAdmin: () -> Unit,
    freezeViewModel: FreezeViewModel = viewModel(), // Renamed for clarity
    settingsViewModel: SettingsViewModel = viewModel() // New ViewModel for settings
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        // Pass the settings ViewModel to the SettingsScreen
        SettingsScreen(
            viewModel = settingsViewModel,
            onNavigateBack = { showSettings = false }
        )
    } else {
        val uiState by freezeViewModel.uiState.collectAsState()
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("All Apps", "Social Media", "Frozen Apps")
        val searchQuery by freezeViewModel.searchQuery.collectAsState()

        val filteredAllApps = remember(uiState.allApps, searchQuery) {
            uiState.allApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        val filteredSocialMediaApps = remember(uiState.socialMediaApps, searchQuery) {
            uiState.socialMediaApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        // Simple pull-to-refresh using Material 3 built-in functionality
        var isRefreshing by remember { mutableStateOf(false) }

        // Sync with ViewModel state
        LaunchedEffect(uiState.isRefreshing) {
            isRefreshing = uiState.isRefreshing
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("App Freezer", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { freezeViewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Search Apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { freezeViewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true
                )

                if (!uiState.isDeviceAdminActive && !uiState.isLoading) {
                    DeviceAdminBanner(onRequestDeviceAdmin = onRequestDeviceAdmin)
                }

                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = error, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = when (index) {
                                        0 -> "$title (${filteredAllApps.size})"
                                        1 -> "$title (${filteredSocialMediaApps.size})"
                                        2 -> "$title (${uiState.frozenApps.size})"
                                        else -> title
                                    }
                                )
                            }
                        )
                    }
                }

                // Use LazyColumn with pullRefresh modifier (if available in your Compose version)
                // Or add a simple refresh button as fallback
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> AppList(
                            apps = filteredAllApps,
                            onToggleFreeze = freezeViewModel::toggleAppFreeze,
                            isLoading = uiState.isLoading
                        )
                        1 -> AppList(
                            apps = filteredSocialMediaApps,
                            onToggleFreeze = freezeViewModel::toggleAppFreeze,
                            isLoading = uiState.isLoading
                        )
                        2 -> FrozenAppsList(
                            apps = uiState.frozenApps,
                            onUnfreeze = { freezeViewModel.toggleAppFreeze(it) },
                            onUnfreezeAll = freezeViewModel::unfreezeAllApps,
                            isLoading = uiState.isLoading
                        )
                    }

                    // Refresh FAB as alternative to pull-to-refresh
                    if (!uiState.isLoading) {
                        FloatingActionButton(
                            onClick = { freezeViewModel.refreshApps() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Apps"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceAdminBanner(onRequestDeviceAdmin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Device Admin Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "App Freezer needs Device Administrator permissions to effectively freeze apps. This is a one-time setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestDeviceAdmin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun AppList(
    apps: List<AppInfo>,
    onToggleFreeze: (String) -> Unit,
    isLoading: Boolean
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (apps.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No apps found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppItem(
                app = app,
                onToggleFreeze = { onToggleFreeze(app.packageName) }
            )
        }
    }
}

@Composable
fun FrozenAppsList(
    apps: List<AppInfo>,
    onUnfreeze: (String) -> Unit,
    onUnfreezeAll: () -> Unit,
    isLoading: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (apps.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Button(
                    onClick = onUnfreezeAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unfreeze All Apps")
                }
            }
        }

        // We can reuse the same AppList composable for the frozen apps.
        AppList(
            apps = apps,
            onToggleFreeze = onUnfreeze,
            isLoading = isLoading
        )
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    onToggleFreeze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.icon?.let {
                Icon(
                    painter = rememberDrawablePainter(it),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified
                )
            } ?: Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Switch(
                checked = app.isFrozen,
                onCheckedChange = { onToggleFreeze() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}