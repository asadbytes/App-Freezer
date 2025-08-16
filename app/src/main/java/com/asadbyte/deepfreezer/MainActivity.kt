package com.asadbyte.deepfreezer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
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
import com.asadbyte.deepfreezer.ui.stealth.StealthModeScreen
import com.asadbyte.deepfreezer.ui.stealth.stealthModeAllowedApps
import com.asadbyte.deepfreezer.ui.theme.DeepFreezerTheme

class MainActivity : FragmentActivity() {

    private val freezeViewModel: FreezeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var isAuthenticated by mutableStateOf(false)
    private var isAuthRequired by mutableStateOf(false)

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        freezeViewModel.refreshAdminStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, DeviceAdminReceiver::class.java)

        // Whitelist apps for Lock Task Mode
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val defaultDialerPackage = telecomManager.defaultDialerPackage

            // List of user-approved apps for kiosk mode
            val userWhitelistedPackages = stealthModeAllowedApps.map {
                it.packageName
            }

            val whitelistedPackages = mutableListOf(packageName)
            if (defaultDialerPackage != null) {
                whitelistedPackages.add(defaultDialerPackage)
            }
            whitelistedPackages.addAll(userWhitelistedPackages)

            // Use toSet() to remove any duplicates before setting the packages
            devicePolicyManager.setLockTaskPackages(
                adminComponentName,
                whitelistedPackages.toSet().toTypedArray()
            )
        }


        setContent {
            DeepFreezerTheme {
                val settingsState by settingsViewModel.uiState.collectAsState()
                isAuthRequired = settingsState.isAppLockEnabled

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (settingsState.isStealthModeEnabled) {
                        // Enter lock task mode when showing the stealth screen
                        LaunchedEffect(Unit) {
                            startLockTask()
                        }
                        StealthModeScreen(
                            onDisableStealthMode = {
                                // Use biometrics to exit stealth mode
                                authenticateToExitStealthMode()
                            },
                            onLaunchApp = { targetPackage ->
                                try {
                                    val intent = packageManager.getLaunchIntentForPackage(targetPackage)
                                    if (intent != null) {
                                        startActivity(intent)
                                    } else {
                                        Log.w("StealthMode", "Could not launch $targetPackage, app not found.")
                                    }
                                } catch (e: Exception) {
                                    Log.e("StealthMode", "Error launching $targetPackage", e)
                                }
                            }
                        )
                    } else {
                        // Ensure we are not in lock task mode if stealth mode is off
                        LaunchedEffect(Unit) {
                            stopLockTask()
                        }

                        if (isAuthenticated || !isAuthRequired) {
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
    }

    private fun authenticateToExitStealthMode() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // On success, disable stealth mode
                    settingsViewModel.setStealthMode(false)
                    stopLockTask()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("Auth", "Authentication error: $errString")
                    // You might want to show a toast or message here
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Disable Stealth Mode")
            .setSubtitle("Authenticate to unlock the phone")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
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