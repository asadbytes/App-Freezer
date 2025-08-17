package com.asadbyte.deepfreezer.ui.stealth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asadbyte.deepfreezer.domain.AppInfo
import com.asadbyte.deepfreezer.ui.freeze.AppItem
import com.asadbyte.deepfreezer.utils.rememberDrawablePainter

@Composable
fun StealthModeScreen(
    onDisableStealthMode: () -> Unit,
    onLaunchApp: (String) -> Unit,
    isLoading: Boolean,
    allApps: List<AppInfo>,
    whitelistedPackages: Set<String>
) {
    val appsToShow = remember(allApps, whitelistedPackages) {
        allApps.filter { it.packageName in whitelistedPackages }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .systemBarsPadding(), // Ensures content is not hidden behind system bars
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp) // Add overall vertical padding
        ) {
            // --- Header Section ---
            Spacer(modifier = Modifier.height(32.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Stealth Mode",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.inverseOnSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stealth Mode Active",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            Spacer(modifier = Modifier.height(48.dp))

            // --- Content Section (Scrollable) ---
            if (isLoading) {
                // The loader should also occupy the weighted space
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            } else {
                // Apply weight directly to the grid, making it fill available space and scroll
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f) // KEY CHANGE: This makes the grid take up the remaining space
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(appsToShow, key = { it.packageName }) { app ->
                        AppIconButton(
                            appInfo = app,
                            onLaunchApp = { onLaunchApp(app.packageName) }
                        )
                    }
                }
            }

            // --- Footer Section ---
            Spacer(modifier = Modifier.height(24.dp)) // A fixed spacer provides breathing room
            Button(
                onClick = onDisableStealthMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text("Disable Stealth Mode")
            }
        }
    }
}

@Composable
private fun AppIconButton(
    appInfo: AppInfo,
    onLaunchApp: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable { onLaunchApp() }
    ) {
        appInfo.icon?.let {
            Icon(
                painter = rememberDrawablePainter(it),
                contentDescription = appInfo.appName,
                modifier = Modifier.size(48.dp),
                tint = Color.Unspecified
            )
        } ?: Icon( // A fallback icon if the app's icon is null
            imageVector = Icons.Default.BrokenImage,
            contentDescription = appInfo.appName,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.inverseOnSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = appInfo.appName, // Use the actual app name
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}