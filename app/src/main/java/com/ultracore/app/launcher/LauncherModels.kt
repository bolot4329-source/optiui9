package com.ultracore.app.launcher

import android.content.pm.ActivityInfo
import kotlinx.serialization.Serializable

/** How an activity can be opened without root. */
enum class LaunchMode {
    /** Exported, no permission: a normal Intent from UltraCore. */
    DIRECT,
    /** Exported but protected by a permission: `am start` as shell (works if shell has it). */
    SHELL,
    /** Not exported: Android allows only root (or the app itself) to open it. */
    ROOT_ONLY,
}

data class ActivityEntry(
    val packageName: String,
    val className: String,
    val exported: Boolean,
    val permission: String?,
    val enabled: Boolean,
    /** Kept to load the label lazily (loading labels for thousands of activities is slow). */
    val info: ActivityInfo,
) {
    val shortName: String get() = className.removePrefix(packageName).ifEmpty { className }
    val mode: LaunchMode
        get() = when {
            !exported -> LaunchMode.ROOT_ONLY
            permission != null -> LaunchMode.SHELL
            else -> LaunchMode.DIRECT
        }
}

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val enabled: Boolean,
    val activities: List<ActivityEntry>,
)

enum class LauncherFilter(val title: String) {
    ALL("Все"),
    USER("Пользовательские"),
    SYSTEM("Системные"),
    HIDDEN("Закрытые экраны"),
}

@Serializable
data class FavoriteActivity(
    val packageName: String,
    val className: String,
    val label: String,
    val appLabel: String,
)
