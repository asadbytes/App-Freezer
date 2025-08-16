package com.asadbyte.deepfreezer.ui.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.asadbyte.deepfreezer.domain.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppScanner(private val context: Context) {

    val packageManager: PackageManager = context.packageManager

    suspend fun getAllInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            // Add MATCH_UNINSTALLED_PACKAGES for a more robust scan to find hidden apps.
            val flags = PackageManager.GET_META_DATA or
                    PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES

            packageManager.getInstalledApplications(flags)
                .mapNotNull { appInfo ->
                    // Filter out system apps and our own app.
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (appInfo.packageName != context.packageName) {
                        mapAppInfo(appInfo)
                    } else {
                        null
                    }
                }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e("AppScanner", "Error getting installed apps", e)
            emptyList()
        }
    }

    // Helper to build an AppInfo object from ApplicationInfo
    fun mapAppInfo(appInfo: ApplicationInfo): AppInfo {
        return AppInfo(
            packageName = appInfo.packageName,
            appName = appInfo.loadLabel(packageManager).toString(),
            icon = appInfo.loadIcon(packageManager),
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )
    }
}
