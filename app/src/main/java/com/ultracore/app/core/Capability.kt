package com.ultracore.app.core

import kotlinx.serialization.Serializable

/** ✅ / ⚠️ / 🔒 */
@Serializable
enum class CapabilityStatus { AVAILABLE, PARTIAL, UNAVAILABLE }

/** Features whose availability is checked on the phone itself. */
@Serializable
enum class Capability(val title: String) {
    SHELL("Команды через Shizuku"),
    SETTINGS_WRITE("Изменение системных настроек"),
    PACKAGE_LIST("Список приложений (pm)"),
    BATTERY_DUMPSYS("Расширенные данные батареи"),
    DEVICE_CONFIG("Флаги системы (device_config)"),
    ROOT_FEATURES("Функции, требующие Root"),
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
)
