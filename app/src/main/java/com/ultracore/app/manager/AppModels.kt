package com.ultracore.app.manager

data class ManagedApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** false = frozen (`pm disable-user`). */
    val enabled: Boolean,
    /** true = paused (`pm suspend`): icon stays (grey), widgets stay, the app cannot run. */
    val suspended: Boolean,
    /** false = removed for this user (`pm uninstall -k --user 0`), can be restored. */
    val installed: Boolean,
    val versionName: String,
    val sourceDir: String?,
    val splitDirs: List<String>,
    /** Why this app must not be frozen or removed, or null. */
    val protectedReason: String?,
)

enum class AppFilter(val title: String) {
    USER("Пользовательские"),
    SYSTEM("Системные"),
    FROZEN("Замороженные / на паузе"),
    REMOVED("Удалённые"),
    SANDBOX("Песочница"),
}

enum class BatchOp(val title: String, val verb: String) {
    FREEZE("Заморозить", "Заморожено"),
    UNFREEZE("Разморозить", "Разморожено"),
    SUSPEND("Пауза", "Поставлено на паузу"),
    UNSUSPEND("Снять паузу", "Пауза снята"),
    RESTRICT_BG("Ограничить фон", "Фон ограничен"),
    ALLOW_BG("Разрешить фон", "Фон разрешён"),
    UNINSTALL("Удалить для пользователя", "Удалено"),
    RESTORE("Восстановить", "Восстановлено"),
    BACKUP("Бэкап APK", "Сохранено"),
}

data class OpResult(val ok: Boolean, val message: String)

data class InstallOptions(
    val allowDowngrade: Boolean = false,
    val asPlayStore: Boolean = false,
)
