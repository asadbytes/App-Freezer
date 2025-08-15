package com.asadbyte.deepfreezer.ui.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.asadbyte.deepfreezer.domain.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppScanner(private val context: Context) {

    private val packageManager = context.packageManager

    suspend fun getAllInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    // Filter out system apps that shouldn't be frozen
                    !isSystemApp(appInfo) && packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
                }
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = getAppName(appInfo),
                        icon = getAppIcon(appInfo),
                        isSystemApp = isSystemApp(appInfo)
                    )
                }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSocialMediaApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val socialMediaPackages = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.twitter.android",
            "com.snapchat.android",
            "com.whatsapp",
            "com.tiktok",
            "com.linkedin.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.tumblr",
            "com.discord",
            "com.telegram.messenger",
            "com.viber.voip",
            "com.skype.raider",
            "com.facebook.orca", // Messenger
            "com.zhiliaoapp.musically", // TikTok alternative package
        )

        getAllInstalledApps().filter { it.packageName in socialMediaPackages }
    }

    private fun getAppName(appInfo: ApplicationInfo): String {
        return try {
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appInfo.packageName
        }
    }

    private fun getAppIcon(appInfo: ApplicationInfo) = try {
        packageManager.getApplicationIcon(appInfo)
    } catch (e: Exception) {
        null
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }
}