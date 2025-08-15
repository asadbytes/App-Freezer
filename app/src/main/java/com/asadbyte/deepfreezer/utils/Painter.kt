package com.asadbyte.deepfreezer.utils

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter

@Composable
fun rememberDrawablePainter(drawable: Drawable): Painter {
    return rememberAsyncImagePainter(model = drawable)
}