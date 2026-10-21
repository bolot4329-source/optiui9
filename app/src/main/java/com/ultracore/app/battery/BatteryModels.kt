package com.ultracore.app.battery

/** Where a value came from — shown next to it so the user knows how reliable it is. */
enum class BatterySource(val label: String) {
    ANDROID("Android"),
    DUMPSYS("dumpsys"),
    SYSFS("sysfs"),
    CALCULATED("расчёт"),
}

data class BatteryValue(val title: String, val value: String, val source: BatterySource)

data class BatteryPassport(
    val level: Int? = null,
    val statusText: String = "",
    /** Recognized values: Android API first, then shell sources. */
    val values: List<BatteryValue> = emptyList(),
    /** Everything else the firmware reports in `dumpsys battery`, raw. */
    val extras: List<Pair<String, String>> = emptyList(),
    /** Why shell data is missing (no Shizuku, firmware refused…), or null. */
    val shellNote: String? = null,
    val loadingShell: Boolean = false,
)
