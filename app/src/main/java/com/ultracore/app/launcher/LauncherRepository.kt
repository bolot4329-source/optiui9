package com.ultracore.app.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Lists installed apps and their activities.
 * Activities are read package by package: asking for all of them at once can exceed
 * the Binder limit on phones with many apps.
 */
class LauncherRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    suspend fun scan(onProgress: (done: Int, total: Int) -> Unit): List<AppEntry> = withContext(Dispatchers.IO) {
        val packages = pm.installedPackagesCompat(PackageManager.MATCH_DISABLED_COMPONENTS)
        val result = ArrayList<AppEntry>(packages.size)
        packages.forEachIndexed { index, pkg ->
            coroutineContext.ensureActive()
            if (index % 20 == 0) onProgress(index, packages.size)
            val info = runCatching {
                pm.packageInfoCompat(pkg.packageName, PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
            }.getOrNull() ?: return@forEachIndexed
            val app = info.applicationInfo ?: return@forEachIndexed
            val activities = info.activities.orEmpty().map { a ->
                ActivityEntry(
                    packageName = info.packageName,
                    className = a.name,
                    exported = a.exported,
                    permission = a.permission,
                    enabled = a.enabled && app.enabled,
                    info = a,
                )
            }.sortedWith(compareBy({ it.mode.ordinal }, { it.shortName.lowercase() }))
            if (activities.isEmpty()) return@forEachIndexed
            result.add(
                AppEntry(
                    packageName = info.packageName,
                    label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                    isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    enabled = app.enabled,
                    activities = activities,
                ),
            )
        }
        onProgress(packages.size, packages.size)
        result.sortedBy { it.label.lowercase() }
    }

    /** Activity label, or null when it has none of its own (then the class name is shown). */
    fun activityLabel(entry: ActivityEntry): String? {
        val a = entry.info
        if (a.labelRes == 0 && a.nonLocalizedLabel == null) return null
        return runCatching { a.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.installedPackagesCompat(flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getInstalledPackages(flags)
        }

    @Suppress("DEPRECATION")
    private fun PackageManager.packageInfoCompat(name: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageInfo(name, flags)
        }
}

/** Favourite activities. filesDir/launcher_favorites.json */
class FavoritesStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "launcher_favorites.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serializer = ListSerializer(FavoriteActivity.serializer())

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<FavoriteActivity>> = _items.asStateFlow()

    fun isFavorite(pkg: String, cls: String) = _items.value.any { it.packageName == pkg && it.className == cls }

    @Synchronized
    fun toggle(fav: FavoriteActivity) {
        val exists = isFavorite(fav.packageName, fav.className)
        val updated = if (exists) {
            _items.value.filterNot { it.packageName == fav.packageName && it.className == fav.className }
        } else {
            _items.value + fav
        }
        _items.value = updated
        runCatching { file.writeText(json.encodeToString(serializer, updated)) }
    }

    private fun read(): List<FavoriteActivity> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())
}
