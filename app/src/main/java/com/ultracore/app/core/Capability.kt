package com.ultracore.app.core

import kotlinx.serialization.Serializable

/** ✅ / ⚠️ / 🔒 */
@Serializable
enum class CapabilityStatus { AVAILABLE, PARTIAL, UNAVAILABLE }

/** Features whose availability is checked on the phone itself. */
@Serializable
enum class Capability(val title: String, val usedIn: String) {
    SHELL("Команды через Shizuku", "Основа всех функций, Терминал"),
    SETTINGS_WRITE("Изменение системных настроек", "Главная → Твики"),
    DEVICE_CONFIG("Флаги системы (device_config)", "Твики → ограничение фоновых процессов"),
    BATTERY_DUMPSYS("Расширенные данные батареи", "Главная → Батарея"),
    LOGS("Системный журнал (logcat)", "Терминал → Диагностика, Оптимизация"),
    PACKAGE_LIST("Список приложений (pm)", "Очистка, Приложения"),
    APP_FREEZE("Заморозка приложений", "Приложения → Управление, Песочница"),
    APP_REMOVE("Удаление системных приложений для пользователя", "Приложения → Управление"),
    APP_INSTALL("Установка APK через Shizuku", "Приложения → Управление → Установить"),
    ROOT_FEATURES("Функции, требующие Root", "Скрытие приложений, закрытые экраны"),
}

@Serializable
data class CapabilityResult(
    val capability: Capability,
    val status: CapabilityStatus,
    /** Short reason shown under the item; empty when everything works. */
    val reason: String = "",
)

@Serializable
data class CapabilityReport(
    /** Build.FINGERPRINT at the time of the check. */
    val fingerprint: String,
    val checkedAtMillis: Long,
    val results: List<CapabilityResult>,
    /** Bumped when the set of checks changes, so old cached reports are re-checked. */
    val version: Int = 1,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}
