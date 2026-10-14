package com.ultracore.app.storage

import java.io.File
import java.io.IOException

/** Shared paths and safety checks for public storage (/storage/emulated/0). */
object StoragePaths {
    const val ROOT = "/storage/emulated/0"
    const val ANDROID_DIR = "$ROOT/Android"
    const val ANDROID_DATA = "$ROOT/Android/data"
    const val ANDROID_OBB = "$ROOT/Android/obb"

    /** Standard folders that are never deleted, even when empty. */
    val PROTECTED_DIRS: Set<String> = setOf(
        ROOT,
        ANDROID_DIR,
        ANDROID_DATA,
        ANDROID_OBB,
        "$ROOT/Android/media",
        "$ROOT/DCIM",
        "$ROOT/DCIM/Camera",
        "$ROOT/DCIM/Screenshots",
        "$ROOT/Download",
        "$ROOT/Documents",
        "$ROOT/Pictures",
        "$ROOT/Pictures/Screenshots",
        "$ROOT/Movies",
        "$ROOT/Music",
        "$ROOT/Alarms",
        "$ROOT/Notifications",
        "$ROOT/Podcasts",
        "$ROOT/Recordings",
        "$ROOT/Ringtones",
        "$ROOT/Audiobooks",
    )

    val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    fun relative(path: String): String =
        if (path.startsWith("$ROOT/")) path.removePrefix("$ROOT/") else path

    /**
     * Resolves a folder given relative to the storage root (e.g. "Download/Installers").
     * Returns null if it escapes the root or points into Android/ (not reachable without Shizuku).
     */
    fun resolveInside(relativePath: String): File? {
        val clean = relativePath.trim().trim('/')
        if (clean.isEmpty()) return null
        val file = try {
            File(ROOT, clean).canonicalFile
        } catch (_: IOException) {
            return null
        }
        val p = file.path
        if (!p.startsWith("$ROOT/")) return null
        if (p == ANDROID_DIR || p.startsWith("$ANDROID_DIR/")) return null
        return file
    }

    /** A name in [dir] that doesn't exist yet and isn't in [reserved]: "a.jpg" → "a (1).jpg". */
    fun uniqueTarget(dir: File, name: String, reserved: Set<String>): File {
        val first = File(dir, name)
        if (!first.exists() && first.path !in reserved) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = File(dir, "$base ($i)$ext")
            if (!candidate.exists() && candidate.path !in reserved) return candidate
            i++
        }
    }
}
