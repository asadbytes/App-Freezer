package com.asadbyte.deepfreezer.ui.freeze

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.asadbyte.deepfreezer.MainActivity
import com.asadbyte.deepfreezer.R
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

class FreezeMonitorService : Service() {

    private lateinit var freezeManager: FreezeManager
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "freeze_monitor_channel"
        private const val CHANNEL_NAME = "App Freezer Monitor"
    }

    override fun onCreate() {
        super.onCreate()
        freezeManager = FreezeManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            monitorFrozenApps()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        serviceScope.cancel()
    }


    private suspend fun monitorFrozenApps() {
        while (coroutineContext.isActive) {
            try {
                if (freezeManager.isDeviceAdminActive()) {
                    // Monitor and enforce frozen state by killing frozen apps
                    freezeManager.frozenApps.value.forEach { packageName ->
                        try {
                            // Check if the frozen app is currently running
                            val isRunning = isAppRunning(packageName)
                            if (isRunning) {
                                // Try to stop the app
                                forceStopApp(packageName)
                                Log.d("FreezeMonitorService", "Stopped frozen app: $packageName")
                            }
                        } catch (e: Exception) {
                            Log.w("FreezeMonitorService", "Cannot stop app $packageName: ${e.message}")
                        }
                    }
                }

                // Update notification
                val frozenCount = freezeManager.getFrozenAppsCount()
                updateNotification(frozenCount)

                delay(5_000) // Check every 5 seconds for more responsive monitoring
            } catch (e: Exception) {
                Log.e("FreezeMonitorService", "Error in monitoring loop", e)
                delay(10_000) // Wait longer if there's an error
            }
        }
    }

    private fun isAppRunning(packageName: String): Boolean {
        return try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val processes = activityManager.runningAppProcesses
            processes?.any { it.processName == packageName } == true
        } catch (e: Exception) {
            false
        }
    }

    @RequiresPermission(Manifest.permission.KILL_BACKGROUND_PROCESSES)
    private fun forceStopApp(packageName: String) {
        try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            // This will only work if we have permission, otherwise it fails silently
            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.w("FreezeMonitorService", "Cannot force stop $packageName: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors frozen apps"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("App Freezer Active")
        .setContentText("Monitoring frozen apps")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(createPendingIntent())
        .setOngoing(true)
        .setShowWhen(false)
        .build()

    private fun updateNotification(frozenCount: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Freezer Active")
            .setContentText("$frozenCount apps frozen")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(createPendingIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}