package com.asadbyte.deepfreezer.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class to troubleshoot and provide alternative solutions for problematic apps
 */
class AppTroubleshooter(private val context: Context, private val appLauncher: AppLauncher) {

    /**
     * Analyze why an app failed to launch and provide alternatives
     */
    suspend fun troubleshootApp(packageName: String): TroubleshootResult = withContext(Dispatchers.IO) {
        val launchInfo = appLauncher.getAppLaunchInfo(packageName)

        when {
            launchInfo["installed"] == false -> {
                TroubleshootResult.NotInstalled(packageName)
            }
            launchInfo["hidden"] == true -> {
                TroubleshootResult.AppHidden(packageName)
            }
            launchInfo["hasLaunchIntent"] == false -> {
                TroubleshootResult.NoLaunchIntent(packageName, getSuggestedAlternatives(packageName))
            }
            else -> {
                // App exists but failed to launch - provide specific solutions
                TroubleshootResult.LaunchFailed(packageName, getSpecificSolutions(packageName))
            }
        }
    }

    /**
     * Get suggested alternatives for apps that can't be launched
     */
    private fun getSuggestedAlternatives(packageName: String): List<Alternative> {
        return when (packageName) {
            "com.techlogix.mobilinkcustomer" -> listOf(
                Alternative("Try opening via web", "https://jazzcash.com.pk"),
                Alternative("Check if JazzCash is updated", "Update from Play Store"),
                Alternative("Use USSD code", "*786#")
            )
            "com.islam360" -> listOf(
                Alternative("Try web version", "https://islam360.com"),
                Alternative("Alternative Quran app", "com.quran.labs.androidquran"),
                Alternative("Check app permissions", "Enable all required permissions")
            )
            "com.telenor.phoenix.pk" -> listOf(
                Alternative("Try Easypaisa web", "https://easypaisa.com.pk"),
                Alternative("Use USSD code", "*786*1#")
            )
            else -> listOf(
                Alternative("Open via Play Store", "market://details?id=$packageName"),
                Alternative("Check app info", "Open app info in settings"),
                Alternative("Restart device", "Some apps require restart after installation")
            )
        }
    }

    /**
     * Get specific solutions for apps that failed to launch
     */
    private fun getSpecificSolutions(packageName: String): List<Solution> {
        return when (packageName) {
            "com.techlogix.mobilinkcustomer" -> listOf(
                Solution("Clear app cache", "Go to Settings > Apps > JazzCash > Storage > Clear Cache"),
                Solution("Reset app preferences", "Settings > Apps > Reset app preferences"),
                Solution("Check app permissions", "Ensure all permissions are granted"),
                Solution("Update the app", "Check Play Store for updates"),
                Solution("Reinstall app", "Uninstall and reinstall from Play Store")
            )
            "com.islam360" -> listOf(
                Solution("Force stop and restart", "Settings > Apps > Islam360 > Force Stop, then try again"),
                Solution("Check storage space", "Ensure device has enough storage"),
                Solution("Clear app data", "Settings > Apps > Islam360 > Storage > Clear Data"),
                Solution("Disable battery optimization", "Settings > Battery > App optimization")
            )
            else -> listOf(
                Solution("Clear app cache", "Settings > Apps > [AppName] > Storage > Clear Cache"),
                Solution("Force stop app", "Settings > Apps > [AppName] > Force Stop"),
                Solution("Check permissions", "Ensure all required permissions are granted"),
                Solution("Update app", "Check for updates in Play Store")
            )
        }
    }

    /**
     * Attempt to launch using alternative methods based on troubleshooting
     */
    suspend fun attemptAlternativeLaunch(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val alternatives = getAlternativeLaunchMethods(packageName)

        for (method in alternatives) {
            try {
                if (method()) {
                    Log.d("AppTroubleshooter", "Alternative launch successful for $packageName")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w("AppTroubleshooter", "Alternative method failed for $packageName", e)
            }
        }
        return@withContext false
    }

    /**
     * Get alternative launch methods for specific apps
     */
    private fun getAlternativeLaunchMethods(packageName: String): List<suspend () -> Boolean> {
        return when (packageName) {
            "com.techlogix.mobilinkcustomer" -> listOf(
                { launchViaPlayStore(packageName) },
                { launchViaWebBrowser("https://jazzcash.com.pk") },
                { launchUSSDCode("*786#") }
            )
            "com.islam360" -> listOf(
                { launchViaPlayStore(packageName) },
                { launchViaWebBrowser("https://islam360.com") },
                { launchAlternativeApp("com.quran.labs.androidquran") }
            )
            else -> listOf(
                { launchViaPlayStore(packageName) },
                { openAppSettings(packageName) }
            )
        }
    }

    /**
     * Launch app via Play Store
     */
    private suspend fun launchViaPlayStore(packageName: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$packageName")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to web Play Store
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Launch via web browser
     */
    private suspend fun launchViaWebBrowser(url: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch USSD code (for banking apps)
     */
    private suspend fun launchUSSDCode(ussd: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_CALL,
                android.net.Uri.parse("tel:${android.net.Uri.encode(ussd)}")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch alternative app
     */
    private suspend fun launchAlternativeApp(alternativePackage: String): Boolean {
        return appLauncher.launchApp(alternativePackage)
    }

    /**
     * Open app settings
     */
    private suspend fun openAppSettings(packageName: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Data classes for troubleshooting results
 */
sealed class TroubleshootResult {
    data class NotInstalled(val packageName: String) : TroubleshootResult()
    data class AppHidden(val packageName: String) : TroubleshootResult()
    data class NoLaunchIntent(val packageName: String, val alternatives: List<Alternative>) : TroubleshootResult()
    data class LaunchFailed(val packageName: String, val solutions: List<Solution>) : TroubleshootResult()
}

data class Alternative(
    val title: String,
    val description: String
)

data class Solution(
    val title: String,
    val description: String
)