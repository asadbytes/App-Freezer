package com.asadbyte.deepfreezer.ui.freeze

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asadbyte.deepfreezer.domain.AppInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Renamed from MainUiState to be more specific
data class FreezeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val allApps: List<AppInfo> = emptyList(),
    val socialMediaApps: List<AppInfo> = emptyList(),
    val frozenApps: List<AppInfo> = emptyList(),
    val error: String? = null
)

// Renamed from MainViewModel
class FreezeViewModel(application: Application) : AndroidViewModel(application) {

    private val appScanner = AppScanner(application)
    private val freezeManager = FreezeManager(application)

    private val _uiState = MutableStateFlow(FreezeUiState())
    val uiState: StateFlow<FreezeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadApps()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadApps()
        }
    }

    fun toggleAppFreeze(packageName: String) {
        val currentState = _uiState.value
        val appToToggle = currentState.allApps.find { it.packageName == packageName }
            ?: currentState.frozenApps.find { it.packageName == packageName }

        appToToggle?.let { app ->
            val isNowFrozen = !app.isFrozen
            val updatedApp = app.copy(isFrozen = isNowFrozen)
            val newAllApps = currentState.allApps.toMutableList()
            val newFrozenApps = currentState.frozenApps.toMutableList()

            if (isNowFrozen) {
                newAllApps.removeAll { it.packageName == packageName }
                if (newFrozenApps.none { it.packageName == packageName }) newFrozenApps.add(updatedApp)
            } else {
                newFrozenApps.removeAll { it.packageName == packageName }
                if (newAllApps.none { it.packageName == packageName }) newAllApps.add(updatedApp)
            }

            val socialMediaPackages = getSocialMediaPackages()
            val newSocialApps = newAllApps.filter { it.packageName in socialMediaPackages }.sortedBy { it.appName.lowercase() }

            _uiState.value = currentState.copy(
                allApps = newAllApps.sortedBy { it.appName.lowercase() },
                socialMediaApps = newSocialApps,
                frozenApps = newFrozenApps.sortedBy { it.appName.lowercase() }
            )
        }

        viewModelScope.launch {
            try {
                freezeManager.toggleAppFreeze(packageName)
            } catch (e: Exception) {
                Log.e("FreezeViewModel", "Error toggling app freeze", e)
                _uiState.update { it.copy(error = "Error: ${e.message}") }
                loadApps()
            }
        }
    }

    fun unfreezeAllApps() {
        viewModelScope.launch {
            freezeManager.unfreezeAllApps()
            loadApps()
        }
    }

    private fun getSocialMediaPackages(): Set<String> {
        return setOf(
            "com.instagram.android", "com.facebook.katana", "com.twitter.android",
            "com.snapchat.android", "com.whatsapp", "com.tiktok", "com.linkedin.android",
            "com.reddit.frontpage", "com.pinterest", "com.tumblr", "com.discord",
            "com.telegram.messenger", "com.viber.voip", "com.skype.raider",
            "com.facebook.orca", "com.zhiliaoapp.musically"
        )
    }

    private fun loadApps() {
        viewModelScope.launch {
            if (!_uiState.value.isRefreshing) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                val frozenPackages = freezeManager.getFrozenAppPackages()
                val scannedApps = appScanner.getAllInstalledApps()
                val scannedAppPackages = scannedApps.map { it.packageName }.toSet()
                val allAppsMap = scannedApps.associateBy { it.packageName }.toMutableMap()

                frozenPackages.forEach { pkg ->
                    if (!scannedAppPackages.contains(pkg)) {
                        try {
                            val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
                            val appInfo = appScanner.packageManager.getApplicationInfo(pkg, flags)
                            allAppsMap[pkg] = appScanner.mapAppInfo(appInfo)
                        } catch (e: PackageManager.NameNotFoundException) {
                            freezeManager.toggleAppFreeze(pkg)
                        }
                    }
                }

                val fullAppList = allAppsMap.values.toList()
                val completeAppListWithStatus = fullAppList.map { it.copy(isFrozen = it.packageName in frozenPackages) }

                val frozenAppList = completeAppListWithStatus.filter { it.isFrozen }.sortedBy { it.appName.lowercase() }
                val unfrozenAppList = completeAppListWithStatus.filter { !it.isFrozen }.sortedBy { it.appName.lowercase() }

                val socialMediaPackages = getSocialMediaPackages()
                val updatedSocialApps = unfrozenAppList.filter { it.packageName in socialMediaPackages }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDeviceAdminActive = freezeManager.isDeviceOwnerApp(),
                        allApps = unfrozenAppList,
                        socialMediaApps = updatedSocialApps,
                        frozenApps = frozenAppList
                    )
                }

            } catch (e: Exception) {
                Log.e("FreezeViewModel", "Failed to load apps", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load apps: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun refreshAdminStatus() {
        loadApps()
    }
}
