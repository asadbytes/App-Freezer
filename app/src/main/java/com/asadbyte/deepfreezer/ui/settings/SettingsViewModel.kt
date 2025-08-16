package com.asadbyte.deepfreezer.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class to hold the state for the Settings screen
data class SettingsUiState(
    val isAppLockEnabled: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    // Use a separate SharedPreferences file for settings
    private val settingsPrefs = application.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load the initial state from SharedPreferences when the ViewModel is created
        viewModelScope.launch {
            val isAppLockEnabled = settingsPrefs.getBoolean("app_lock_enabled", true)
            _uiState.update { it.copy(isAppLockEnabled = isAppLockEnabled) }
        }
    }

    /**
     * Toggles the app lock setting and saves it to SharedPreferences.
     */
    fun setAppLock(enabled: Boolean) {
        // Save the new setting
        settingsPrefs.edit().putBoolean("app_lock_enabled", enabled).apply()
        // Update the UI state
        _uiState.update { it.copy(isAppLockEnabled = enabled) }
    }
}
