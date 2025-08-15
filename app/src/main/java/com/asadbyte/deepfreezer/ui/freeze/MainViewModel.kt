package com.asadbyte.deepfreezer.ui.freeze

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asadbyte.deepfreezer.domain.AppInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = true,
    val isDeviceAdminActive: Boolean = false,
    val allApps: List<AppInfo> = emptyList(),
    val socialMediaApps: List<AppInfo> = emptyList(),
    val frozenApps: List<AppInfo> = emptyList(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appScanner = AppScanner(application)
    private val freezeManager = FreezeManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun refreshApps() {
        loadApps()
    }

    fun toggleAppFreeze(packageName: String) {
        // 1. Perform an "optimistic" UI update for an instant response.
        val currentState = _uiState.value

        // Find the app in either the 'all' or 'frozen' list.
        val appToToggle = currentState.allApps.find { it.packageName == packageName }
            ?: currentState.frozenApps.find { it.packageName == packageName }

        appToToggle?.let { app ->
            val isNowFrozen = !app.isFrozen
            val updatedApp = app.copy(isFrozen = isNowFrozen)

            val newAllApps = currentState.allApps.toMutableList()
            val newFrozenApps = currentState.frozenApps.toMutableList()

            if (isNowFrozen) {
                // Move app from 'all' lists to 'frozen' list
                newAllApps.removeAll { it.packageName == packageName }
                if (newFrozenApps.none { it.packageName == packageName }) {
                    newFrozenApps.add(updatedApp)
                }
            } else {
                // Move app from 'frozen' list to 'all' list
                newFrozenApps.removeAll { it.packageName == packageName }
                if (newAllApps.none { it.packageName == packageName }) {
                    newAllApps.add(updatedApp)
                }
            }

            val socialMediaPackages = getSocialMediaPackages()
            val newSocialApps = newAllApps.filter { it.packageName in socialMediaPackages }.sortedBy { it.appName.lowercase() }

            _uiState.value = currentState.copy(
                allApps = newAllApps.sortedBy { it.appName.lowercase() },
                socialMediaApps = newSocialApps,
                frozenApps = newFrozenApps.sortedBy { it.appName.lowercase() }
            )
        }

        // 2. Launch the actual freeze/unfreeze operation in the background.
        viewModelScope.launch {
            try {
                freezeManager.toggleAppFreeze(packageName)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling app freeze", e)
                _uiState.update { it.copy(error = "Error: ${e.message}") }
                loadApps() // Revert UI on error
            }
        }
    }

    fun unfreezeAllApps() {
        viewModelScope.launch {
            freezeManager.unfreezeAllApps()
            loadApps() // Reload after a bulk operation.
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
            _uiState.update { it.copy(isLoading = true, error = null) }

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
                            Log.w("MainViewModel", "Recovered missing frozen app: $pkg")
                        } catch (e: PackageManager.NameNotFoundException) {
                            Log.e("MainViewModel", "App in prefs not found on device, removing: $pkg", e)
                            freezeManager.unfreezeApp(pkg)
                        }
                    }
                }

                val fullAppList = allAppsMap.values.toList()
                val completeAppListWithStatus = fullAppList.map { it.copy(isFrozen = it.packageName in frozenPackages) }

                // **UI IMPROVEMENT LOGIC**
                val frozenAppList = completeAppListWithStatus.filter { it.isFrozen }.sortedBy { it.appName.lowercase() }
                val unfrozenAppList = completeAppListWithStatus.filter { !it.isFrozen }.sortedBy { it.appName.lowercase() }

                val socialMediaPackages = getSocialMediaPackages()
                val updatedSocialApps = unfrozenAppList.filter { it.packageName in socialMediaPackages }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDeviceAdminActive = freezeManager.isDeviceOwnerApp(),
                        allApps = unfrozenAppList, // Only show unfrozen apps here
                        socialMediaApps = updatedSocialApps, // Only show unfrozen social apps
                        frozenApps = frozenAppList // Only show frozen apps here
                    )
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load apps", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load apps: ${e.message}")
                }
            }
        }
    }

    fun refreshAdminStatus() {
        loadApps()
    }
}
