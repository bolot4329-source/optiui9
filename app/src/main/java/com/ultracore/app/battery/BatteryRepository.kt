package com.ultracore.app.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ultracore.app.core.ErrorClassifier
import com.ultracore.app.shizuku.ShizukuManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Battery passport from three sources, from most to least standard:
 * 1. Public Android API (no Shizuku needed).
 * 2. `dumpsys battery` through Shizuku — parsed generically, any firmware.
 * 3. /sys/class/power_supply/battery — only if the firmware lets shell read it.
 */
class BatteryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    data class PublicPart(val level: Int?, val statusText: String, val values: List<BatteryValue>)
    data class ShellPart(val values: List<BatteryValue>, val extras: List<Pair<String, String>>, val note: String?)

    fun readPublic(): PublicPart {
        val intent: Intent? = ContextCompat.registerReceiver(
            appContext,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val values = mutableListOf<BatteryValue>()
        fun add(title: String, value: String) = values.add(BatteryValue(title, value, BatterySource.ANDROID))

        var level: Int? = null
        var statusText = ""
        if (intent != null) {
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (raw >= 0 && scale > 0) level = raw * 100 / scale

            statusText = listOfNotNull(
                statusName(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
                plugName(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)),
            ).joinToString(" · ")

            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (temp != Int.MIN_VALUE) add("Температура", String.format(Locale.US, "%.1f °C", temp / 10.0))

            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            if (voltage > 0) add("Напряжение", "$voltage мВ")

            healthName(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))?.let { add("Состояние", it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
                if (cycles >= 0) add(TITLE_CYCLES, cycles.toString())
            }

            intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
                ?.takeIf { it.isNotBlank() }
                ?.let { add("Технология", it) }
        }

        val chargeUah = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        if (chargeUah > 0) {
            val mah = chargeUah / 1000
            add("Заряд сейчас", "$mah мАч")
            if (level != null && level >= 20) {
                // Approximate real capacity: charge now / level. Rough, but works on any phone.
                val full = (mah * 100.0 / level).roundToInt()
                values.add(BatteryValue("Оценка полной ёмкости", "≈ $full мАч", BatterySource.CALCULATED))
            }
        }

        val current = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        if (current != Int.MIN_VALUE && current != 0) {
            // Most phones report µA, some report mA.
            val ma = if (abs(current) >= 10_000) current / 1000 else current
            add("Ток", "$ma мА")
        }

        return PublicPart(level, statusText, values)
    }

    suspend fun readShell(): ShellPart {
        if (!ShizukuManager.isConnected) {
            return ShellPart(emptyList(), emptyList(), "Подключите Shizuku, чтобы увидеть расширенные данные")
        }

        val values = mutableListOf<BatteryValue>()
        val extras = mutableListOf<Pair<String, String>>()
        var note: String? = null

        val dump = ShizukuManager.execResult("dumpsys battery", 15)
        if (dump.isSuccess) {
            for ((key, value) in parseKeyValues(dump.stdout)) {
                val title = recognize(key, value)
                if (title != null) {
                    val shown = if (title == TITLE_HEALTH && '%' !in value) "$value %" else value
                    values.add(BatteryValue(title, shown, BatterySource.DUMPSYS))
                } else {
                    extras.add(key to value)
                }
            }
        } else {
            note = "dumpsys battery: " + ErrorClassifier.classify(dump).message
        }

        val sys = ShizukuManager.execResult(SYSFS_COMMAND, 10)
        if (sys.isSuccess) {
            val map = parseKeyValues(sys.stdout, separator = '=').toMap()
            map["cycle_count"]?.toIntOrNull()?.takeIf { it >= 0 }?.let {
                values.add(BatteryValue(TITLE_CYCLES, it.toString(), BatterySource.SYSFS))
            }
            val full = map["charge_full"]?.toLongOrNull()?.takeIf { it > 0 }
            val design = map["charge_full_design"]?.toLongOrNull()?.takeIf { it > 0 }
            if (full != null) values.add(BatteryValue("Полная ёмкость", "${full / 1000} мАч", BatterySource.SYSFS))
            if (design != null) values.add(BatteryValue("Паспортная ёмкость", "${design / 1000} мАч", BatterySource.SYSFS))
            if (full != null && design != null) {
                val health = (full * 100.0 / design).roundToInt()
                values.add(BatteryValue(TITLE_HEALTH, "$health %", BatterySource.CALCULATED))
            }
        }

        return ShellPart(values, extras, note)
    }

    /** "key: value" lines → pairs. Section headers ("Current Battery Service state:") are skipped. */
    private fun parseKeyValues(text: String, separator: Char = ':'): List<Pair<String, String>> =
        text.lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf(separator)
                if (i <= 0) return@mapNotNull null
                val key = line.substring(0, i).trim()
                val value = line.substring(i + 1).trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toList()

    /**
     * Generic recognition by the meaning of the key, not by brand.
     * Keys already covered by the public API (level, voltage, temperature…) go to extras.
     */
    private fun recognize(key: String, value: String): String? {
        val k = key.lowercase().replace("_", " ")
        val number = value.substringBefore(' ').toDoubleOrNull()
        return when {
            "cycle" in k && number != null -> TITLE_CYCLES
            ("asoc" in k || "soh" in k || "state of health" in k || "health percent" in k) &&
                number != null && number in 1.0..100.0 -> TITLE_HEALTH
            "design" in k && ("capacity" in k || "charge" in k) && number != null -> "Паспортная ёмкость"
            ("full capacity" in k || "charge full" in k || "fullcapacity" in k) && number != null -> "Полная ёмкость"
            else -> null
        }
    }

    private fun statusName(status: Int): String? = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Заряжается"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Разряжается"
        BatteryManager.BATTERY_STATUS_FULL -> "Заряжена"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Не заряжается"
        else -> null
    }

    private fun plugName(plugged: Int): String? = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "от сети"
        BatteryManager.BATTERY_PLUGGED_USB -> "от USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "беспроводная"
        PLUGGED_DOCK -> "от док-станции"
        else -> null
    }

    private fun healthName(health: Int): String? = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Батарея изношена"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Неисправность"
        BatteryManager.BATTERY_HEALTH_COLD -> "Переохлаждение"
        else -> null
    }

    companion object {
        const val TITLE_CYCLES = "Циклы зарядки"
        const val TITLE_HEALTH = "Здоровье батареи"
        private const val PLUGGED_DOCK = 8 // BatteryManager.BATTERY_PLUGGED_DOCK (API 33)

        private const val SYSFS_COMMAND =
            "cd /sys/class/power_supply/battery 2>/dev/null && " +
                "for f in cycle_count charge_full charge_full_design; do " +
                "[ -r \$f ] && echo \"\$f=\$(cat \$f 2>/dev/null)\"; done; true"
    }
}
