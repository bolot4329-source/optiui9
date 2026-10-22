package com.ultracore.app.ui.components

import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icons already drawn at a given pixel size: scrolling back never reloads or blinks. ~200 icons ≈ 12 MB max. */
private val iconCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * App icon loaded in the background at the real pixel size of [size] (sharp on any density).
 * Works for frozen apps and for system apps removed for the user (MATCH_UNINSTALLED_PACKAGES).
 */
@Composable
fun AppIcon(packageName: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val key = "$packageName@$px"
    var bitmap by remember(key) { mutableStateOf(iconCache.get(key)) }
    if (bitmap == null) {
        LaunchedEffect(key) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(packageName, flags)
                    }
                    info.loadIcon(pm).toBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                iconCache.put(key, loaded)
                bitmap = loaded
            }
        }
    }
    Box(Modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
