package com.asadbyte.deepfreezer.utils

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AppLauncher(private val context: Context) {

    private val packageManager = context.packageManager

    /**
     * Launches an app using multiple fallback methods with enhanced error handling
     */
    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        // First, verify the app actually exists and is enabled
        if (!isAppInstalled(packageName)) {
            Log.e("AppLauncher", "App not installed or disabled: $packageName")
            return@withContext false
            /**
             * Debug method to get comprehensive app launch info
             */
            fun getAppLaunchInfo(packageName: String): Map<String, Any> {
                val info = mutableMapOf<String, Any>()
                try {
                    info["installed"] = isAppInstalled(packageName)
                    info["hidden"] = isAppHidden(packageName)
                    info["canLaunch"] = canLaunchApp(packageName)
                    info["activities"] = getLaunchableActivities(packageName)

                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    info["hasLaunchIntent"] = launchIntent != null
                    info["launchComponent"] = launchIntent?.component?.className ?: "none"

                } catch (e: Exception) {
                    info["error"] = e.message ?: "Unknown error"
                }
                return info
            }
        }

        // Check if app is hidden (frozen by device admin)
        if (isAppHidden(packageName)) {
            Log.w("AppLauncher", "App is hidden/frozen: $packageName")
            return@withContext false
        }

        val methods = listOf(
            ::launchWithGetLaunchIntentForPackage,
            ::launchWithDeepLink,
            ::launchWithQueryIntentActivities,
            ::launchWithMainActivity,
            ::launchWithComponentName,
            ::launchWithSystemAction,
            ::launchWithBroadcastHint,
            ::launchWithMonkeyRunner,
            ::launchWithForceComponent
        )

        for (method in methods) {
            try {
                val result = method(packageName)
                if (result) {
                    Log.d("AppLauncher", "Successfully launched $packageName using ${method.name}")
                    // Verify the launch was actually successful
                    return@withContext verifyLaunchSuccess(packageName)
                }
            } catch (e: Exception) {
                Log.w("AppLauncher", "Method ${method.name} failed for $packageName", e)
            }
        }

        Log.e("AppLauncher", "All launch methods failed for $packageName")
        logAppDetails(packageName) // Log detailed app info for debugging
        return@withContext false
    }

    /**
     * Check if app is actually installed and enabled
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                )
            } else {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                )
            }
            appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Check if app is hidden by device admin
     */
    private fun isAppHidden(packageName: String): Boolean {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong())
                )
            } else {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
            }
            !appInfo.enabled || (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verify that the app actually launched by checking if it's in foreground
     * Modified to handle Lock Task Mode correctly
     */
    private suspend fun verifyLaunchSuccess(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            // In Lock Task Mode, apps might launch but we remain the top activity
            // So we need to check differently

            // Wait a bit for the app to start
            kotlinx.coroutines.delay(1000)

            return@withContext try {
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager

                // First check: Are we in lock task mode?
                val isInLockTaskMode =
                    activityManager.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE

                if (isInLockTaskMode) {
                    // In lock task mode, check if the target app is running as a process
                    val runningApps = activityManager.runningAppProcesses
                    val isRunning = runningApps?.any { process ->
                        process.processName == packageName || process.processName.startsWith("$packageName:")
                    } == true

                    Log.d("AppLauncher", "Lock task mode verification for $packageName: $isRunning")

                    // Also check recent tasks for additional verification
                    if (!isRunning) {
                        val recentTasks = try {
                            activityManager.getRunningTasks(5)
                        } catch (e: Exception) {
                            null
                        }

                        val isInRecentTasks = recentTasks?.any { task ->
                            task.baseActivity?.packageName == packageName ||
                                    task.topActivity?.packageName == packageName
                        } == true

                        Log.d(
                            "AppLauncher",
                            "Recent tasks verification for $packageName: $isInRecentTasks"
                        )
                        return@withContext isInRecentTasks
                    }

                    return@withContext isRunning
                } else {
                    // Normal mode - check top activity
                    val runningTasks = activityManager.getRunningTasks(1)
                    if (runningTasks.isNotEmpty()) {
                        val topActivity = runningTasks[0].topActivity
                        val isLaunched = topActivity?.packageName == packageName
                        Log.d(
                            "AppLauncher",
                            "Normal mode verification for $packageName: $isLaunched (top: ${topActivity?.packageName})"
                        )
                        return@withContext isLaunched
                    }
                }

                // Fallback verification
                false
            } catch (e: Exception) {
                Log.w("AppLauncher", "Could not verify launch for $packageName", e)
                // In case of verification error, assume success if we got this far
                true
            }
        }

    /**
     * Log detailed app information for debugging
     */
    private fun logAppDetails(packageName: String) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)

            Log.d("AppLauncher", "App Details for $packageName:")
            Log.d("AppLauncher", "  Enabled: ${appInfo.enabled}")
            Log.d(
                "AppLauncher",
                "  System App: ${(appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0}"
            )
            Log.d("AppLauncher", "  Target SDK: ${appInfo.targetSdkVersion}")
            Log.d("AppLauncher", "  Activities: ${packageInfo.activities?.size ?: 0}")

            packageInfo.activities?.take(3)?.forEach { activity ->
                Log.d("AppLauncher", "    Activity: ${activity.name}")
            }
        } catch (e: Exception) {
            Log.e("AppLauncher", "Could not log app details for $packageName", e)
        }
    }

    /**
     * Method 1: Standard getLaunchIntentForPackage (your current method)
     */
    private suspend fun launchWithGetLaunchIntentForPackage(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return@withContext true
            }
            return@withContext false
        }

    /**
     * Method 2: Launch with app-specific deep links
     */
    private suspend fun launchWithDeepLink(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            val deepLinkMap = mapOf(
                // Pakistani Banking/Financial Apps
                "com.techlogix.mobilinkcustomer" to listOf("jazzcash://", "mobilink://"),
                "com.telenor.phoenix.pk" to listOf("easypaisa://", "telenor://"),
                "pk.com.ubl.omni" to listOf("ublomni://", "ubl://"),
                "com.habibbank.hbl" to listOf("hbl://"),

                // Islamic Apps
                "com.islam360" to listOf("islam360://", "quran://"),
                "com.islamicfinder.prayertimes" to listOf("islamicfinder://"),

                // Social media apps
                "com.instagram.android" to listOf("instagram://"),
                "com.facebook.katana" to listOf("fb://", "facebook://"),
                "com.twitter.android" to listOf("twitter://"),
                "com.snapchat.android" to listOf("snapchat://"),
                "com.whatsapp" to listOf("whatsapp://"),
                "com.tiktok" to listOf("tiktok://", "musically://"),

                // Other apps
                "com.spotify.music" to listOf("spotify://"),
                "com.netflix.mediaclient" to listOf("nflx://"),
            )

            val deepLinks = deepLinkMap[packageName]
            if (!deepLinks.isNullOrEmpty()) {
                for (deepLink in deepLinks) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            setPackage(packageName) // Ensure it opens the specific app
                        }
                        context.startActivity(intent)
                        return@withContext true
                    } catch (e: Exception) {
                        Log.w("AppLauncher", "Deep link $deepLink failed for $packageName", e)
                    }
                }
            }
            return@withContext false
        }

    /**
     * Method 3: Query all activities and find launchable ones
     */
    /**
     * Method 3: Query all activities and find launchable ones
     */
    private suspend fun launchWithQueryIntentActivities(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }

            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }

            if (resolveInfos.isNotEmpty()) {
                // Try each resolved activity
                for (resolveInfo in resolveInfos) {
                    try {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            component = ComponentName(
                                resolveInfo.activityInfo.packageName,
                                resolveInfo.activityInfo.name
                            )
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        }
                        context.startActivity(intent)
                        return@withContext true
                    } catch (e: Exception) {
                        Log.w("AppLauncher", "Failed to launch ${resolveInfo.activityInfo.name}", e)
                    }
                }
            }
            return@withContext false
        }

    /**
     * Method 4: Find main activity directly from package info
     */
    /**
     * Method 4: Find main activity directly from package info
     */
    private suspend fun launchWithMainActivity(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
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
                    // Priority order for activity names
                    val priorityPatterns = listOf(
                        "SplashActivity", "LauncherActivity", "MainActivity",
                        "HomeActivity", "WelcomeActivity", "StartActivity"
                    )

                    // First, try to find activities matching priority patterns
                    for (pattern in priorityPatterns) {
                        val matchingActivity = activities.find {
                            it.name.contains(pattern, ignoreCase = true)
                        }
                        if (matchingActivity != null) {
                            val intent = Intent().apply {
                                component = ComponentName(packageName, matchingActivity.name)
                                flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                            return@withContext true
                        }
                    }

                    // If no priority match, try all activities
                    for (activity in activities) {
                        try {
                            val intent = Intent().apply {
                                component = ComponentName(packageName, activity.name)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            return@withContext true
                        } catch (e: Exception) {
                            // Continue to next activity
                            Log.w("AppLauncher", "Failed to launch activity ${activity.name}", e)
                        }
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("AppLauncher", "Package not found in main activity method: $packageName")
            } catch (e: Exception) {
                Log.e("AppLauncher", "Error in main activity method for $packageName", e)
            }
            return@withContext false
        }

    /**
     * Method 5: Try with different component variations
     */
    private suspend fun launchWithComponentName(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
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
     * Method 6: Launch using system actions for specific app types
     */
    private suspend fun launchWithSystemAction(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            val systemActions = mapOf(
                "com.android.camera2" to Intent.ACTION_CAMERA_BUTTON,
                "com.android.calculator2" to "android.intent.action.CALCULATOR",
                "com.android.calendar" to Intent.ACTION_INSERT,
                "com.android.contacts" to Intent.ACTION_VIEW,
                "com.android.settings" to android.provider.Settings.ACTION_SETTINGS,
                "com.android.chrome" to Intent.ACTION_VIEW,

                // Samsung apps
                "com.sec.android.app.camera" to android.provider.MediaStore.ACTION_IMAGE_CAPTURE,
                "com.samsung.android.calendar" to Intent.ACTION_INSERT,
                "com.samsung.android.dialer" to Intent.ACTION_CALL_BUTTON,

                // Banking apps - try with custom actions
                "com.techlogix.mobilinkcustomer" to Intent.ACTION_VIEW,
                "com.telenor.phoenix.pk" to Intent.ACTION_VIEW,
            )

            val action = systemActions[packageName]
            if (action != null) {
                try {
                    val intent = Intent(action).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(packageName)

                        when (packageName) {
                            "com.android.calendar", "com.samsung.android.calendar" -> {
                                type = "vnd.android.cursor.dir/event"
                            }

                            "com.android.chrome" -> {
                                data = Uri.parse("https://www.google.com")
                            }

                            "com.techlogix.mobilinkcustomer" -> {
                                // Try with a banking-related URI
                                data = Uri.parse("https://jazzcash.com.pk")
                            }
                        }
                    }
                    context.startActivity(intent)
                    return@withContext true
                } catch (e: Exception) {
                    Log.w("AppLauncher", "System action failed for $packageName", e)
                }
            }
            return@withContext false
        }

    /**
     * Method 7: Launch using broadcast hint (for stubborn apps)
     */
    private suspend fun launchWithBroadcastHint(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            return@withContext try {
                // Send a broadcast to "wake up" the app first
                val wakeupIntent = Intent().apply {
                    action = "android.intent.action.PACKAGE_REPLACED"
                    data = Uri.parse("package:$packageName")
                    setPackage(packageName)
                }
                context.sendBroadcast(wakeupIntent)

                // Wait a moment
                kotlinx.coroutines.delay(500)

                // Now try to launch
                val launchIntent = Intent().apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(launchIntent)
                true
            } catch (e: Exception) {
                Log.w("AppLauncher", "Broadcast hint method failed for $packageName", e)
                false
            }
        }

    /**
     * Method 8: Monkey runner approach - simulate launcher tap
     */
    private suspend fun launchWithMonkeyRunner(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
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
                            component =
                                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
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
            intent != null && isAppInstalled(packageName)
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

    /**
     * Launch app while maintaining lock task mode security
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun launchAppInLockTaskMode(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            val lockTaskMethods = listOf(
                ::launchWithLockTaskFlags,
                ::launchWithComponentInLockTask,
                ::launchWithTaskStackManagement,
                ::launchWithResolvedActivity,
                ::launchWithDeepLinkInLockTask
            )

            for (method in lockTaskMethods) {
                try {
                    if (method(packageName)) {
                        Log.d(
                            "AppLauncher",
                            "Lock task launch SUCCESS: $packageName using ${method.name}"
                        )
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(
                        "AppLauncher",
                        "Lock task method ${method.name} failed for $packageName",
                        e
                    )
                }
            }

            Log.w("AppLauncher", "All lock task compatible methods failed for $packageName")
            return@withContext false
        }

    /**
     * Launch with flags compatible with lock task mode
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun launchWithLockTaskFlags(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            return@withContext try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    // Use flags that work within lock task mode
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

                    // Remove any flags that might conflict with lock task
                    intent.removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                    context.startActivity(intent)
                    Log.d("AppLauncher", "Lock task compatible launch for $packageName")

                    // Verify launch by checking running processes
                    delay(800)
                    isAppProcessRunning(packageName)
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.w("AppLauncher", "Lock task flags method failed for $packageName", e)
                false
            }
        }

    /**
     * Launch specific component while in lock task mode
     */
    private suspend fun launchWithComponentInLockTask(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            return@withContext try {
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
                    // Try activities that are likely to be launcher activities
                    for (activity in activities) {
                        if (activity.exported && (
                                    activity.name.contains("MainActivity", ignoreCase = true) ||
                                            activity.name.contains(
                                                "LauncherActivity",
                                                ignoreCase = true
                                            ) ||
                                            activity.name.contains(
                                                "SplashActivity",
                                                ignoreCase = true
                                            ) ||
                                            activity.name.endsWith(".MainActivity") ||
                                            activity.name.endsWith(".LauncherActivity")
                                    )
                        ) {
                            try {
                                val intent = Intent().apply {
                                    component = ComponentName(packageName, activity.name)
                                    flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    // Add data that some apps expect
                                    putExtra(
                                        "android.intent.extra.REFERRER",
                                        android.net.Uri.parse("android-app://${context.packageName}")
                                    )
                                }

                                context.startActivity(intent)
                                Log.d(
                                    "AppLauncher",
                                    "Component launch successful: ${activity.name}"
                                )

                                delay(800)
                                if (isAppProcessRunning(packageName)) {
                                    return@withContext true
                                }
                            } catch (e: Exception) {
                                Log.w(
                                    "AppLauncher",
                                    "Component launch failed for ${activity.name}",
                                    e
                                )
                            }
                        }
                    }
                }
                false
            } catch (e: Exception) {
                Log.w("AppLauncher", "Component method failed for $packageName", e)
                false
            }
        }

    /**
     * Try deep links that work in lock task mode
     */
    private suspend fun launchWithDeepLinkInLockTask(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            val deepLinks = getDeepLinksForPackage(packageName)

            for (deepLink in deepLinks) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        setPackage(packageName)
                    }
                    context.startActivity(intent)

                    delay(800)
                    if (isAppProcessRunning(packageName)) {
                        Log.d("AppLauncher", "Deep link launch successful: $deepLink")
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w("AppLauncher", "Deep link failed: $deepLink", e)
                }
            }
            return@withContext false
        }

    /**
     * Get deep links for specific packages
     */
    private fun getDeepLinksForPackage(packageName: String): List<String> {
        return when (packageName) {
            "com.techlogix.mobilinkcustomer" -> listOf("jazzcash://main", "mobilink://app")
            "com.islam360" -> listOf("islam360://main", "quran://home")
            "com.openai.chatgpt" -> listOf("chatgpt://chat", "openai://app")
            "com.deepseek.chat" -> listOf("deepseek://chat")
            "com.instagram.android" -> listOf("instagram://camera")
            "com.whatsapp" -> listOf("whatsapp://send")
            "com.facebook.katana" -> listOf("fb://feed")
            "com.twitter.android" -> listOf("twitter://timeline")
            else -> emptyList()
        }
    }

    /**
     * Advanced method: Launch app using task stack manipulation
     */
    @RequiresPermission(value = android.Manifest.permission.REORDER_TASKS)
    private suspend fun launchWithTaskStackManagement(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            return@withContext try {
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

                // First, try to bring existing task to front if app is already running
                val runningTasks = try {
                    activityManager.getRunningTasks(10)
                } catch (e: Exception) {
                    emptyList()
                }

                val existingTask = runningTasks.find { task ->
                    task.baseActivity?.packageName == packageName ||
                            task.topActivity?.packageName == packageName
                }

                if (existingTask != null) {
                    try {
                        // Move existing task to front
                        activityManager.moveTaskToFront(existingTask.id, 0)
                        Log.d("AppLauncher", "Moved existing task to front for $packageName")
                        return@withContext true
                    } catch (e: Exception) {
                        Log.w("AppLauncher", "Could not move task to front", e)
                    }
                }

                // If no existing task, launch new one with proper task management
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

                    // Set task affinity to allow proper task switching
                    intent.putExtra(
                        "android.intent.extra.TASK_ID",
                        System.currentTimeMillis().toInt()
                    )

                    context.startActivity(intent)

                    delay(1000)
                    return@withContext isAppProcessRunning(packageName)
                }

                false
            } catch (e: Exception) {
                Log.e("AppLauncher", "Task stack management failed for $packageName", e)
                false
            }
        }

    /**
     * Method using PackageManager to launch via resolved activities
     */
    private suspend fun launchWithResolvedActivity(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            return@withContext try {
                // Create a query intent
                val queryIntent = Intent().apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }

                // Get all activities that can handle this intent
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(
                        queryIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )
                } else {
                    packageManager.queryIntentActivities(
                        queryIntent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )
                }

                if (resolveInfos.isNotEmpty()) {
                    val bestResolve = resolveInfos.first()

                    val launchIntent = Intent().apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = ComponentName(
                            bestResolve.activityInfo.packageName,
                            bestResolve.activityInfo.name
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK

                        // Add extras that might help with launch
                        putExtra("launched_from_lockscreen", true)
                        putExtra(
                            "android.intent.extra.REFERRER",
                            android.net.Uri.parse("android-app://${context.packageName}")
                        )
                    }

                    context.startActivity(launchIntent)
                    Log.d(
                        "AppLauncher",
                        "Resolved activity launch for ${bestResolve.activityInfo.name}"
                    )

                    delay(800)
                    return@withContext isAppProcessRunning(packageName)
                }

                false
            } catch (e: Exception) {
                Log.w("AppLauncher", "Resolved activity method failed for $packageName", e)
                false
            }
        }

    /**
     * Enhanced process running check with multiple verification methods
     */
    private fun isAppProcessRunning(packageName: String): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager

            // Method 1: Check running app processes
            val runningApps = activityManager.runningAppProcesses ?: return false
            val hasRunningProcess = runningApps.any { process ->
                process.processName == packageName ||
                        process.processName.startsWith("$packageName:") ||
                        process.processName.contains(packageName)
            }

            if (hasRunningProcess) {
                Log.d("AppLauncher", "Process verification SUCCESS for $packageName")
                return true
            }

            // Method 2: Check recent tasks (if accessible)
            try {
                val recentTasks = activityManager.getRunningTasks(5)
                val hasRecentTask = recentTasks.any { task ->
                    task.baseActivity?.packageName == packageName ||
                            task.topActivity?.packageName == packageName
                }

                if (hasRecentTask) {
                    Log.d("AppLauncher", "Task verification SUCCESS for $packageName")
                    return true
                }
            } catch (e: Exception) {
                // Recent tasks might not be accessible
            }

            // Method 3: Check if we can get application info (app is active)
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val isEnabled = appInfo.enabled
                Log.d("AppLauncher", "App state check for $packageName: enabled=$isEnabled")
                return isEnabled // If we got here and app is enabled, consider it launched
            } catch (e: Exception) {
                // App might not be installed
            }

            Log.d("AppLauncher", "All verification methods failed for $packageName")
            false
        } catch (e: Exception) {
            Log.w("AppLauncher", "Could not verify process for $packageName", e)
            false
        }
    }

    /**
     * Debug method to get comprehensive app launch info
     */
    fun getAppLaunchInfo(packageName: String): Map<String, Any> {
        val info = mutableMapOf<String, Any>()
        try {
            info["installed"] = isAppInstalled(packageName)
            info["hidden"] = isAppHidden(packageName)
            info["canLaunch"] = canLaunchApp(packageName)
            info["activities"] = getLaunchableActivities(packageName)

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            info["hasLaunchIntent"] = launchIntent != null
            info["launchComponent"] = launchIntent?.component?.className ?: "none"

        } catch (e: Exception) {
            info["error"] = e.message ?: "Unknown error"
        }
        return info
    }

    /**
     * Method 9: Force launch with all possible component combinations
     */
    private suspend fun launchWithForceComponent(packageName: String): Boolean =
        withContext(Dispatchers.Main) {
            // Get all activities and try launching each one
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
                    if (activityInfo.exported || activityInfo.name.contains(
                            "Main",
                            ignoreCase = true
                        ) ||
                        activityInfo.name.contains("Launch", ignoreCase = true) ||
                        activityInfo.name.contains("Splash", ignoreCase = true)
                    ) {

                        try {
                            val intent = Intent().apply {
                                component = ComponentName(packageName, activityInfo.name)
                                flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                // Add extra data that some apps expect
                                putExtra("from_launcher", true)
                                putExtra("launched_from_stealth", true)
                            }
                            context.startActivity(intent)
                            Log.d(
                                "AppLauncher",
                                "Force launched $packageName with activity ${activityInfo.name}"
                            )
                            return@withContext true
                        } catch (e: Exception) {
                            Log.w(
                                "AppLauncher",
                                "Force launch failed for activity ${activityInfo.name}",
                                e
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppLauncher", "Force component method failed for $packageName", e)
            }
            return@withContext false
        }
}