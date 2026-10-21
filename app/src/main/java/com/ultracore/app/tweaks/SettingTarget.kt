package com.ultracore.app.tweaks

/** A system value we can read, change and restore with standard AOSP commands. */
sealed class SettingTarget(val id: String) {
    abstract val getCommand: String
    abstract fun putCommand(value: String): String
    abstract val deleteCommand: String

    /** `settings` table: global / system / secure. */
    class Setting(val table: String, val key: String) : SettingTarget("settings:$table:$key") {
        override val getCommand get() = "settings get $table $key"
        override fun putCommand(value: String) = "settings put $table $key $value"
        override val deleteCommand get() = "settings delete $table $key"
    }

    class DeviceConfig(val namespace: String, val key: String) : SettingTarget("device_config:$namespace:$key") {
        override val getCommand get() = "device_config get $namespace $key"
        override fun putCommand(value: String) = "device_config put $namespace $key $value"
        override val deleteCommand get() = "device_config delete $namespace $key"
    }

    /** Whether Android may overwrite device_config flags from the server. */
    data object DeviceConfigSyncMode : SettingTarget("device_config:sync_mode") {
        override val getCommand get() = "device_config get_sync_disabled_for_tests"
        override fun putCommand(value: String) = "device_config set_sync_disabled_for_tests $value"
        override val deleteCommand get() = "device_config set_sync_disabled_for_tests none"
    }
}

/** "null" / empty output means the value is not set. */
fun normalizeSettingValue(raw: String): String? {
    val v = raw.trim()
    return if (v.isEmpty() || v == "null") null else v
}
