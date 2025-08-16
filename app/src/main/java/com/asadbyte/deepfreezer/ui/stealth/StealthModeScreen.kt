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
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Stealth Mode",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.inverseOnSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stealth Mode Active",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.padding(horizontal = 32.dp),
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

            Spacer(modifier = Modifier.weight(1f)) // Pushes the button to the bottom

            Button(
                onClick = onDisableStealthMode,
                modifier = Modifier.padding(bottom = 32.dp),
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