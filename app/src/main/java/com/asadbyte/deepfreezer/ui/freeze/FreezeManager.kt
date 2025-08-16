package com.asadbyte.deepfreezer.ui.freeze

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class FreezeManager(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("frozen_apps_prefs", Context.MODE_PRIVATE)

    fun isDeviceOwnerApp(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun isAppFrozen(packageName: String): Boolean {
        // Check our SharedPreferences ledger to see if the app is frozen.
        return prefs.contains(packageName)
    }

    fun getFrozenAppPackages(): Set<String> {
        // Return all keys from SharedPreferences, which are the package names.
        return prefs.all.keys
    }

    fun toggleAppFreeze(packageName: String) {
        if (isAppFrozen(packageName)) {
            unfreezeApp(packageName)
        } else {
            freezeApp(packageName)
        }
    }

    private fun freezeApp(packageName: String) {
        if (!isDeviceOwnerApp()) {
            Log.e("FreezeManager", "Cannot freeze app: Not a device owner.")
            return
        }
        try {
            // Use device owner power to hide/disable the app.
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            // Add the app to our SharedPreferences ledger.
            prefs.edit().putBoolean(packageName, true).apply()
            Log.d("FreezeManager", "Froze and saved to prefs: $packageName")
        } catch (e: Exception) {
            Log.e("FreezeManager", "Error freezing app: $packageName", e)
        }
    }

    fun unfreezeApp(packageName: String) {
        if (!isDeviceOwnerApp()) {
            Log.e("FreezeManager", "Cannot unfreeze app: Not a device owner.")
            return
        }
        try {
            // Use device owner power to unhide/enable the app.
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
            // Remove the app from our SharedPreferences ledger.
            prefs.edit().remove(packageName).apply()
            Log.d("FreezeManager", "Unfroze and removed from prefs: $packageName")
        } catch (e: Exception) {
            Log.e("FreezeManager", "Error unfreezing app: $packageName", e)
        }
    }

    fun unfreezeAllApps() {
        if (!isDeviceOwnerApp()) return
        // Get a copy of all frozen app packages from our ledger.
        val frozenPackages = getFrozenAppPackages().toList()
        frozenPackages.forEach { packageName ->
            unfreezeApp(packageName)
        }
    }
}