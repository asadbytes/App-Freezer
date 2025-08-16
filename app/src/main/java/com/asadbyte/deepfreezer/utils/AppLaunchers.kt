package com.asadbyte.deepfreezer.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppLauncher(private val context: Context) {

    private val packageManager = context.packageManager

    /**
     * Launches an app using multiple fallback methods
     */
    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val methods = listOf(
            ::launchWithGetLaunchIntentForPackage,
            ::launchWithQueryIntentActivities,
            ::launchWithMainActivity,
            ::launchWithComponentName,
            ::launchWithMonkeyRunner
        )

        for (method in methods) {
            try {
                if (method(packageName)) {
                    Log.d("AppLauncher", "Successfully launched $packageName using ${method.name}")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w("AppLauncher", "Method ${method.name} failed for $packageName", e)
            }
        }

        Log.e("AppLauncher", "All launch methods failed for $packageName")
        return@withContext false
    }

    /**
     * Method 1: Standard getLaunchIntentForPackage (your current method)
     */
    private suspend fun launchWithGetLaunchIntentForPackage(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Method 2: Query all activities and find launchable ones
     */
    private suspend fun launchWithQueryIntentActivities(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        if (resolveInfos.isNotEmpty()) {
            val resolveInfo = resolveInfos[0]
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Method 3: Find main activity directly from package info
     */
    private suspend fun launchWithMainActivity(packageName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }

            val activities = packageInfo.activities
            if (!activities.isNullOrEmpty()) {
                // Look for the main/launcher activity
                for (activity in activities) {
                    if (activity.name.contains("MainActivity", ignoreCase = true) ||
                        activity.name.contains("LauncherActivity", ignoreCase = true) ||
                        activity.name.contains("SplashActivity", ignoreCase = true)) {

                        val intent = Intent().apply {
                            component = ComponentName(packageName, activity.name)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        return@withContext true
                    }
                }

                // If no obvious main activity, try the first one
                val intent = Intent().apply {
                    component = ComponentName(packageName, activities[0].name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return@withContext true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppLauncher", "Package not found: $packageName")
        }
        return@withContext false
    }

    /**
     * Method 4: Try with different component variations
     */
    private suspend fun launchWithComponentName(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val commonActivityNames = listOf(
            ".MainActivity",
            ".ui.MainActivity",
            ".activities.MainActivity",
            ".LauncherActivity",
            ".SplashActivity",
            ".HomeActivity",
            ".activity.MainActivity",
            ".main.MainActivity"
        )

        for (activityName in commonActivityNames) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(packageName, packageName + activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return@withContext true
            } catch (e: Exception) {
                // Continue to next activity name
            }
        }
        return@withContext false
    }

    /**
     * Method 5: Monkey runner approach - simulate launcher tap
     */
    private suspend fun launchWithMonkeyRunner(packageName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            // This creates an intent that mimics what the launcher does
            val intent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                setPackage(packageName)
            }

            // Get all activities that can handle this intent
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                packageManager.queryIntentActivities(intent, 0)
            }

            if (resolveInfos.isNotEmpty()) {
                // Sort by priority and take the highest
                val bestMatch = resolveInfos.maxByOrNull { it.priority }
                bestMatch?.let {
                    val launchIntent = Intent().apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    context.startActivity(launchIntent)
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e("AppLauncher", "Monkey runner method failed", e)
        }
        return@withContext false
    }

    /**
     * Additional utility: Check if app can be launched
     */
    fun canLaunchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get all launchable activities for a package
     */
    fun getLaunchableActivities(packageName: String): List<String> {
        val activities = mutableListOf<String>()
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }

            packageInfo.activities?.forEach { activityInfo ->
                activities.add(activityInfo.name)
            }
        } catch (e: Exception) {
            Log.e("AppLauncher", "Error getting activities for $packageName", e)
        }
        return activities
    }
}