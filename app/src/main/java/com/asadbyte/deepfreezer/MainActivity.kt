package com.asadbyte.deepfreezer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.asadbyte.deepfreezer.ui.freeze.AppFreezerApp
import com.asadbyte.deepfreezer.ui.freeze.DeviceAdminReceiver
import com.asadbyte.deepfreezer.ui.freeze.MainViewModel
import com.asadbyte.deepfreezer.ui.theme.DeepFreezerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "Device admin result: ${result.resultCode}")
        // This is more efficient than recreating the whole activity.
        // It just tells the ViewModel to re-check the admin status.
        viewModel.refreshAdminStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeepFreezerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the viewModel instance to the AppFreezerApp composable
                    AppFreezerApp(
                        onRequestDeviceAdmin = { requestDeviceAdmin() },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@MainActivity, DeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "App Freezer needs this permission to become a device owner and freeze apps."
            )
        }

        try {
            deviceAdminLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching device admin request", e)
        }
    }
}
