package com.asadbyte.deepfreezer.ui.freeze

import android.app.Application
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

        // Listen to frozen apps changes
        viewModelScope.launch {
            freezeManager.frozenApps.collect {
                updateFrozenState()
            }
        }
    }

    fun refreshApps() {
        loadApps()
    }

    fun toggleAppFreeze(packageName: String) {
        viewModelScope.launch {
            try {
                val success = freezeManager.toggleAppFreeze(packageName)
                // Always update the frozen state since we're tracking it locally
                updateFrozenState()

                // Don't show error message for normal operation
                // Clear any existing error message
                _uiState.value = _uiState.value.copy(error = null)

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling app freeze", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun unfreezeApp(packageName: String) {
        viewModelScope.launch {
            freezeManager.unfreezeApp(packageName)
            updateFrozenState()
        }
    }

    fun unfreezeAllApps() {
        viewModelScope.launch {
            freezeManager.unfreezeAllApps()
            updateFrozenState()
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val allApps = appScanner.getAllInstalledApps()
                val socialMediaApps = appScanner.getSocialMediaApps()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDeviceAdminActive = freezeManager.isDeviceAdminActive(),
                    allApps = allApps.map { app ->
                        app.copy(isFrozen = freezeManager.isAppFrozen(app.packageName))
                    },
                    socialMediaApps = socialMediaApps.map { app ->
                        app.copy(isFrozen = freezeManager.isAppFrozen(app.packageName))
                    }
                )

                updateFrozenState()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load apps: ${e.message}"
                )
            }
        }
    }

    fun refreshAdminStatus() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            isDeviceAdminActive = freezeManager.isDeviceAdminActive()
        )
        val updatedAllApps = currentState.allApps.map { app ->
            app.copy(isFrozen = freezeManager.isAppFrozen(app.packageName))
        }

        val updatedSocialMediaApps = currentState.socialMediaApps.map { app ->
            app.copy(isFrozen = freezeManager.isAppFrozen(app.packageName))
        }

        val frozenApps = updatedAllApps.filter { it.isFrozen }

        _uiState.value = currentState.copy(
            isDeviceAdminActive = freezeManager.isDeviceAdminActive(),
            allApps = updatedAllApps,
            socialMediaApps = updatedSocialMediaApps,
            frozenApps = frozenApps
        )
    }

    private fun updateFrozenState() {
        val currentState = _uiState.value

        val updatedAllApps = currentState.allApps.map { app ->
            app.copy(isFrozen = freezeManager.isAppFrozen(app.packageName))
        }

        val updatedSocialMediaApps = currentState.socialMediaApps.map { app ->
            app.copy(isFrozen = freezeManager.isAppFrozen(app.packageName))
        }

        val frozenApps = updatedAllApps.filter { it.isFrozen }

        _uiState.value = currentState.copy(
            isDeviceAdminActive = freezeManager.isDeviceAdminActive(),
            allApps = updatedAllApps,
            socialMediaApps = updatedSocialMediaApps,
            frozenApps = frozenApps
        )
    }
}