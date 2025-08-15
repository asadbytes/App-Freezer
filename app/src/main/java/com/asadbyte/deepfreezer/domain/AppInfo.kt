package com.asadbyte.deepfreezer.domain

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false,
    val isFrozen: Boolean = false
)
