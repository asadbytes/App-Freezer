package com.asadbyte.deepfreezer.ui.freeze

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import android.content.Intent
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FreezeManager(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("frozen_apps_prefs", Context.MODE_PRIVATE)
    private val stealthPrefs: SharedPreferences =
        context.getSharedPreferences("stealth_frozen_apps_prefs", Context.MODE_PRIVATE)

    // --- SAFEGUARD: A blocklist of critical packages that should never be frozen ---
    private val criticalPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "android",
        context.packageName // Never freeze the App Freezer itself
    )

    fun isDeviceOwnerApp(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun isAppFrozen(packageName: String): Boolean {
        return prefs.contains(packageName)
    }

    fun getFrozenAppPackages(): Set<String> {
        return prefs.all.keys
    }

    suspend fun toggleAppFreeze(packageName: String) {
        // --- SAFEGUARD CHECK ---
        if (isPackageCritical(packageName)) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Cannot freeze a critical system app.", Toast.LENGTH_LONG).show()
            }
            return // Stop the function here
        }

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
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            prefs.edit().putBoolean(packageName, true).apply()
            Log.d("FreezeManager", "Froze and saved to prefs: $packageName")
        } catch (e: Exception) {
            Log.e("FreezeManager", "Error freezing app: $packageName", e)
        }
    }

    private fun unfreezeApp(packageName: String) {
        if (!isDeviceOwnerApp()) {
            Log.e("FreezeManager", "Cannot unfreeze app: Not a device owner.")
            return
        }
        try {
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
            prefs.edit().remove(packageName).apply()
            Log.d("FreezeManager", "Unfroze and removed from prefs: $packageName")
        } catch (e: Exception) {
            Log.e("FreezeManager", "Error unfreezing app: $packageName", e)
        }
    }

    // Helper function to check if an app is critical
    private suspend fun isPackageCritical(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (criticalPackages.contains(packageName)) {
            return@withContext true
        }

        // Also, prevent the default launcher from being frozen
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return@withContext resolveInfo?.activityInfo?.packageName == packageName
    }

    fun unfreezeAllApps() {
        if (!isDeviceOwnerApp()) return
        val frozenPackages = getFrozenAppPackages().toList()
        frozenPackages.forEach { packageName ->
            unfreezeApp(packageName)
        }
    }
}