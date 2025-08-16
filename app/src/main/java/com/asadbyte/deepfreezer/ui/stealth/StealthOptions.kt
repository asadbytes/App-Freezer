package com.asadbyte.deepfreezer.ui.stealth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.ui.graphics.vector.ImageVector

data class AppItem(val label: String, val packageName: String, val icon: ImageVector)

val stealthModeAllowedApps = listOf(
    AppItem("Camera", "com.sec.android.app.camera", Icons.Default.PhotoCamera),
    AppItem("Dialer", "com.samsung.android.dialer", Icons.Default.Call),
    AppItem("Calendar", "com.samsung.android.calendar", Icons.Default.CalendarToday),
    AppItem("Flashlight", "com.simplemobiletools.alabana.groveton.flashlight.sam6", Icons.Outlined.Highlight),
    AppItem("Browser", "com.opera.browser", Icons.Default.Web),
    AppItem("Calculator", "com.sec.android.app.popupcalculator", Icons.Outlined.Calculate)
)