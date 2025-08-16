package com.asadbyte.deepfreezer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.asadbyte.deepfreezer.ui.freeze.AppFreezerApp
import com.asadbyte.deepfreezer.ui.freeze.DeviceAdminReceiver
import com.asadbyte.deepfreezer.ui.freeze.FreezeViewModel
import com.asadbyte.deepfreezer.ui.settings.SettingsViewModel
import com.asadbyte.deepfreezer.ui.theme.DeepFreezerTheme

class MainActivity : FragmentActivity() {

    private val freezeViewModel: FreezeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels() // New ViewModel

    private var isAuthenticated by mutableStateOf(false)
    private var isAuthRequired by mutableStateOf(false)

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        freezeViewModel.refreshAdminStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeepFreezerTheme {
                // Get the app lock state from the SettingsViewModel
                val settingsState by settingsViewModel.uiState.collectAsState()
                isAuthRequired = settingsState.isAppLockEnabled

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthenticated || !isAuthRequired) {
                        // Pass both ViewModels to the main app composable
                        AppFreezerApp(
                            onRequestDeviceAdmin = { requestDeviceAdmin() },
                            freezeViewModel = freezeViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Authentication Required")
                        }
                        LaunchedEffect(Unit) {
                            authenticate()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthRequired && !isAuthenticated) {
            authenticate()
        }
    }

    private fun authenticate() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticated = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("Auth", "Authentication error: $errString")
                    finish()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Freezer Locked")
            .setSubtitle("Authenticate to access the app")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@MainActivity, DeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Permission needed to become a device owner and freeze apps."
            )
        }
        deviceAdminLauncher.launch(intent)
    }
}
