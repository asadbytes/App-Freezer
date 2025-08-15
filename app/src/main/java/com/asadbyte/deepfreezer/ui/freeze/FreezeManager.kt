package com.asadbyte.deepfreezer.ui.freeze

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FreezeManager(private val context: Context) {

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val packageManager = context.packageManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
    private val prefs: SharedPreferences = context.getSharedPreferences("frozen_apps", Context.MODE_PRIVATE)

    private val _frozenApps = MutableStateFlow<Set<String>>(emptySet())
    val frozenApps: StateFlow<Set<String>> = _frozenApps.asStateFlow()

    init {
        loadFrozenApps()
    }

    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    fun isAppFrozen(packageName: String): Boolean {
        return _frozenApps.value.contains(packageName)
    }

    suspend fun freezeApp(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w("FreezeManager", "Device admin not active, cannot freeze app")
                return false
            }

            // For now, we'll focus on state tracking since component disabling requires system permissions
            // The background service will monitor and attempt to close frozen apps
            saveFrozenApp(packageName, true)
            updateFrozenAppsState()
            Log.d("FreezeManager", "App marked as frozen: $packageName")

            // Optional: Try to disable components if we have permission (will fail gracefully)
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.component?.let { componentName ->
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.d("FreezeManager", "Successfully disabled component for: $packageName")
                }
            } catch (e: SecurityException) {
                Log.d("FreezeManager", "Component disabling not available, using monitoring approach")
            }

            true

        } catch (e: Exception) {
            Log.e("FreezeManager", "Failed to freeze app: $packageName", e)
            false
        }
    }

    suspend fun unfreezeApp(packageName: String): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w("FreezeManager", "Device admin not active, cannot unfreeze app")
                return false
            }

            // Remove from frozen state
            saveFrozenApp(packageName, false)
            updateFrozenAppsState()
            Log.d("FreezeManager", "App marked as unfrozen: $packageName")

            // Optional: Try to re-enable components if we had disabled them
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.component?.let { componentName ->
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.d("FreezeManager", "Successfully enabled component for: $packageName")
                }
            } catch (e: SecurityException) {
                Log.d("FreezeManager", "Component enabling not available")
            }

            true

        } catch (e: Exception) {
            Log.e("FreezeManager", "Failed to unfreeze app: $packageName", e)
            false
        }
    }

    suspend fun toggleAppFreeze(packageName: String): Boolean {
        return if (isAppFrozen(packageName)) {
            unfreezeApp(packageName)
        } else {
            freezeApp(packageName)
        }
    }

    fun getFrozenAppsCount(): Int = _frozenApps.value.size

    suspend fun unfreezeAllApps() {
        if (!isDeviceAdminActive()) return

        _frozenApps.value.forEach { packageName ->
            unfreezeApp(packageName)
        }

        prefs.edit().clear().apply()
        updateFrozenAppsState()
    }

    fun isAppEnabled(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.enabled && packageManager.getApplicationEnabledSetting(packageName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (e: Exception) {
            true // If we can't check, assume it's enabled
        }
    }

    private fun saveFrozenApp(packageName: String, isFrozen: Boolean) {
        prefs.edit().apply {
            if (isFrozen) {
                putBoolean(packageName, true)
            } else {
                remove(packageName)
            }
            apply()
        }
    }

    private fun loadFrozenApps() {
        val frozenSet = prefs.all.keys.toSet()
        _frozenApps.value = frozenSet
    }

    private fun updateFrozenAppsState() {
        loadFrozenApps()
    }
}