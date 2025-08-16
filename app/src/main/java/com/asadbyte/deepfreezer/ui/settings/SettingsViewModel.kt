package com.asadbyte.deepfreezer.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asadbyte.deepfreezer.domain.AppInfo
import com.asadbyte.deepfreezer.ui.freeze.AppScanner
import com.asadbyte.deepfreezer.ui.stealth.stealthModeAllowedApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class to hold the state for the Settings screen
data class SettingsUiState(
    val isLoading: Boolean = true, // Start in a loading state by default
    val isAppLockEnabled: Boolean = false,
    val isStealthModeEnabled: Boolean = false,
    val allApps: List<AppInfo> = emptyList(),
    val whitelistedStealthApps: Set<String> = emptySet()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsPrefs = application.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
    private val appScanner = AppScanner(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load all necessary data as soon as the ViewModel is created
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // First, load user's saved settings (this is fast)
            val isAppLockEnabled = settingsPrefs.getBoolean("app_lock_enabled", true)
            val isStealthModeEnabled = settingsPrefs.getBoolean("stealth_mode_enabled", false)
            val defaultPackages = stealthModeAllowedApps.map { it.packageName }.toSet()
            val whitelistedStealthApps = settingsPrefs.getStringSet("stealth_whitelisted_apps", defaultPackages) ?: defaultPackages

            // Update the state with the settings we just loaded
            _uiState.update {
                it.copy(
                    isAppLockEnabled = isAppLockEnabled,
                    isStealthModeEnabled = isStealthModeEnabled,
                    whitelistedStealthApps = whitelistedStealthApps
                )
            }

            // Now, perform the slow task of scanning all apps on the device
            val allApps = appScanner.getAllInstalledApps()

            // Finally, update the state with the app list and set isLoading to false
            _uiState.update {
                it.copy(
                    allApps = allApps,
                    isLoading = false // Signal that all loading is complete
                )
            }
        }
    }

    // This function is no longer needed in the settings screen, but we keep it
    // in case you want to add a "refresh" button later.
    fun loadAllAppsForSelection() {
        viewModelScope.launch {
            val allApps = appScanner.getAllInstalledApps()
            _uiState.update { it.copy(allApps = allApps) }
        }
    }

    fun saveStealthModeApps(selectedPackages: Set<String>) {
        settingsPrefs.edit().putStringSet("stealth_whitelisted_apps", selectedPackages).apply()
        _uiState.update { it.copy(whitelistedStealthApps = selectedPackages) }
    }

    fun setAppLock(enabled: Boolean) {
        settingsPrefs.edit().putBoolean("app_lock_enabled", enabled).apply()
        _uiState.update { it.copy(isAppLockEnabled = enabled) }
    }

    fun setStealthMode(enabled: Boolean) {
        settingsPrefs.edit().putBoolean("stealth_mode_enabled", enabled).apply()
        _uiState.update { it.copy(isStealthModeEnabled = enabled) }
    }
}