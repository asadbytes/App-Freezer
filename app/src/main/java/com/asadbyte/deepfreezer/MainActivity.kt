package com.asadbyte.deepfreezer

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.asadbyte.deepfreezer.ui.freeze.AppFreezerApp
import com.asadbyte.deepfreezer.ui.freeze.DeviceAdminReceiver
import com.asadbyte.deepfreezer.ui.freeze.FreezeMonitorService
import com.asadbyte.deepfreezer.ui.theme.DeepFreezerTheme

class MainActivity : ComponentActivity() {

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "Device admin result: ${result.resultCode}")
        // Refresh the app state after returning from device admin settings
        Handler(Looper.getMainLooper()).postDelayed({
            // This will trigger a refresh of the device admin status
            recreate() // Restart the activity to refresh everything
        }, 500)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeepFreezerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppFreezerApp(
                        onRequestDeviceAdmin = { requestDeviceAdmin() }
                    )
                }
            }
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        // Start monitor service only after a delay to ensure proper initialization
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startForegroundService(Intent(this, FreezeMonitorService::class.java))
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start FreezeMonitorService", e)
            }
        }, 1000)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    private fun requestDeviceAdmin() {
        println("Requesting device admin permission") // Debug log

        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@MainActivity, DeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "App Freezer needs device admin permission to freeze apps effectively."
            )
        }

        try {
            deviceAdminLauncher.launch(intent)
        } catch (e: Exception) {
            println("Error launching device admin request: ${e.message}")
            e.printStackTrace()
        }
    }
}
