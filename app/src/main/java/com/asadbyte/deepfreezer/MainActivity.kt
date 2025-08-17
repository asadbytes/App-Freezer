package com.asadbyte.deepfreezer

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
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
import androidx.lifecycle.lifecycleScope
import com.asadbyte.deepfreezer.ui.freeze.AppFreezerApp
import com.asadbyte.deepfreezer.ui.freeze.DeviceAdminReceiver
import com.asadbyte.deepfreezer.ui.freeze.FreezeViewModel
import com.asadbyte.deepfreezer.ui.settings.SettingsViewModel
import com.asadbyte.deepfreezer.ui.stealth.StealthModeScreen
import com.asadbyte.deepfreezer.ui.stealth.stealthModeAllowedApps
import com.asadbyte.deepfreezer.ui.theme.DeepFreezerTheme
import com.asadbyte.deepfreezer.utils.AppLauncher
import com.asadbyte.deepfreezer.utils.AppTroubleshooter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val freezeViewModel: FreezeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var isAuthenticated by mutableStateOf(false)
    private var isAuthRequired by mutableStateOf(false)

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var appLauncher: AppLauncher
    private lateinit var appTroubleshooter: AppTroubleshooter

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        freezeViewModel.refreshAdminStatus()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appLauncher = AppLauncher(this)
        appTroubleshooter = AppTroubleshooter(this, appLauncher)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, DeviceAdminReceiver::class.java)

        // Whitelist apps for Lock Task Mode
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val defaultDialerPackage = telecomManager.defaultDialerPackage

            // Load the custom list from SharedPreferences, with a fallback to the default list
            val settingsPrefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            val defaultPackages = stealthModeAllowedApps.map { it.packageName }.toSet()
            val whitelistedPackagesFromPrefs = settingsPrefs.getStringSet("stealth_whitelisted_apps", defaultPackages) ?: defaultPackages


            val whitelistedPackages = mutableListOf(packageName)
            if (defaultDialerPackage != null) {
                whitelistedPackages.add(defaultDialerPackage)
            }
            whitelistedPackages.addAll(whitelistedPackagesFromPrefs)

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
                                handleAppLaunch(targetPackage)
                            },
                            isLoading = settingsState.isLoading,
                            allApps = settingsState.allApps,
                            whitelistedPackages = settingsState.whitelistedStealthApps
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

    /**
     * Secure app launch handler that maintains stealth mode security
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleAppLaunch(targetPackage: String) {
        lifecycleScope.launch {
            try {
                Log.d("StealthMode", "Attempting secure launch for $targetPackage")

                // Use the secure lock task mode launcher
                val success = appLauncher.launchAppInLockTaskMode(targetPackage)

                if (success) {
                    Log.d("StealthMode", "Successfully launched $targetPackage while maintaining security")
                } else {
                    Log.w("StealthMode", "Secure launch failed for $targetPackage")

                    // Get app name for user-friendly message
                    val appName = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(targetPackage, 0)
                        ).toString()
                    } catch (e: Exception) {
                        targetPackage.substringAfterLast('.')
                    }

                    // Show a brief message - don't break stealth mode with dialogs
                    showToast("Unable to launch $appName")
                }

            } catch (e: Exception) {
                Log.e("StealthMode", "Error in secure launch for $targetPackage", e)
                showToast("Launch error occurred")
            }
        }
    }

    /**
     * Show toast message on main thread
     */
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show troubleshoot dialog with solutions/alternatives
     */
    private fun showAppTroubleshootDialog(packageName: String, items: List<Any>) {
        runOnUiThread {
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) {
                packageName
            }

            val message = when (items.firstOrNull()) {
                is com.asadbyte.deepfreezer.utils.Alternative -> {
                    "Cannot launch $appName. Try these alternatives:\n\n" +
                            items.joinToString("\n") { "• ${(it as com.asadbyte.deepfreezer.utils.Alternative).title}" }
                }
                is com.asadbyte.deepfreezer.utils.Solution -> {
                    "Failed to launch $appName. Try these solutions:\n\n" +
                            items.joinToString("\n") { "• ${(it as com.asadbyte.deepfreezer.utils.Solution).title}" }
                }
                else -> "Could not launch $appName"
            }

            AlertDialog.Builder(this)
                .setTitle("App Launch Issue")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setNeutralButton("App Settings") { _, _ ->
                    // Open app settings
                    lifecycleScope.launch {
                        appTroubleshooter.attemptAlternativeLaunch(packageName)
                    }
                }
                .show()
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