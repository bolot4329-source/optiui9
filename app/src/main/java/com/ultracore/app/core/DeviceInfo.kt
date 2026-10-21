package com.ultracore.app.core

import android.os.Build

/**
 * Plain facts about the phone, taken from public android.os.Build.
 * Used only for display and to notice system updates — never to switch features on a brand.
 */
object DeviceInfo {
    val manufacturer: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model: String get() = Build.MODEL
    val androidVersion: String get() = Build.VERSION.RELEASE
    val sdkInt: Int get() = Build.VERSION.SDK_INT
    val buildId: String get() = Build.DISPLAY

    /** Changes after every system update; capability checks are re-run when it changes. */
    val fingerprint: String get() = Build.FINGERPRINT

    /** "Google Pixel 9 · Android 16" */
    val shortTitle: String
        get() {
            val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            return "$name · Android $androidVersion"
        }
}
