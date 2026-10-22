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
            if (temp != Int.MIN_VALUE) add(TITLE_TEMPERATURE, String.format(Locale.US, "%.1f °C", temp / 10.0))

            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            if (voltage > 0) add("Напряжение", "$voltage мВ")

            healthName(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))?.let { add("Состояние", it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
                // AOSP fills 0 when the health HAL does not report cycles: 0 means «unknown», not «new».
                if (cycles > 0) add(TITLE_CYCLES, cycles.toString())
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
            val average = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: Int.MIN_VALUE
            val discharging = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_DISCHARGING
            val ma = if (isMicroAmps(current, average, discharging)) current / 1000 else current
            add(TITLE_CURRENT, "$ma мА")
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
            val seen = HashSet<String>()
            for ((key, rawValue) in parseKeyValues(dump.stdout)) {
                if (!seen.add(key)) continue
                val value = rawValue.removeSurrounding("[", "]").trim()
                val recognized = recognize(key, value)
                if (recognized != null) {
                    values.add(recognized)
                } else {
                    extras.add(key to rawValue)
                }
            }
        } else {
            note = "dumpsys battery: " + ErrorClassifier.classify(dump).message
        }

        val sys = ShizukuManager.execResult(SYSFS_COMMAND, 10)
        if (sys.isSuccess) {
            val map = parseKeyValues(sys.stdout, separator = '=').distinctBy { it.first }.toMap()
            val cycles = map["cycle_count"]?.toIntOrNull()?.takeIf { it > 0 }
                ?: map["battery_cycle"]?.toIntOrNull()?.takeIf { it > 0 }
            cycles?.let { values.add(BatteryValue(TITLE_CYCLES, it.toString(), BatterySource.SYSFS)) }
            val full = map["charge_full"]?.toLongOrNull()?.takeIf { it > 0 }
            val design = map["charge_full_design"]?.toLongOrNull()?.takeIf { it > 0 }
            if (full != null) values.add(BatteryValue("Полная ёмкость", "${full / 1000} мАч", BatterySource.SYSFS))
            if (design != null) values.add(BatteryValue("Паспортная ёмкость", "${design / 1000} мАч", BatterySource.SYSFS))
            if (full != null && design != null && values.none { it.title == TITLE_HEALTH }) {
                val health = (full * 100.0 / design).roundToInt()
                values.add(BatteryValue(TITLE_HEALTH, "$health %", BatterySource.CALCULATED))
            }
        }

        // The most direct value of each kind first: an explicit «state of health» beats a capacity ratio,
        // a real cycle counter beats one estimated from accumulated usage.
        values.sortBy { it.priority }
        return ShellPart(values, extras, note)
    }

    /**
     * "key: value" lines → pairs. Section headers ("Current Battery Service state:") are skipped, and so are
     * event log lines some firmware appends ("10-20 22:47:01.477 Sending …", "[Tag]…"): a key must start
     * with a letter and look like a name, not like a sentence.
     */
    private fun parseKeyValues(text: String, separator: Char = ':'): List<Pair<String, String>> =
        text.lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf(separator)
                if (i <= 0) return@mapNotNull null
                val key = line.substring(0, i).trim()
                val value = line.substring(i + 1).trim()
                when {
                    key.isEmpty() || value.isEmpty() -> null
                    !KEY_REGEX.matches(key) -> null
                    else -> key to value
                }
            }
            .toList()

    /**
     * Generic recognition by the meaning of the key, not by brand.
     * Keys already covered by the public API (level, voltage, temperature…) go to extras.
     */
    private fun recognize(key: String, value: String): BatteryValue? {
        val k = key.lowercase().replace("_", " ")
        val number = value.substringBefore(' ').toDoubleOrNull() ?: return null
        fun v(title: String, shown: String, priority: Int, source: BatterySource = BatterySource.DUMPSYS) =
            BatteryValue(title, shown, source, priority)
        return when {
            "cycle" in k && number > 0 -> v(TITLE_CYCLES, number.toInt().toString(), 0)
            // Accumulated discharge in percent of a full charge (e.g. «saved battery usage»): 100 % = 1 cycle.
            ("battery usage" in k || "batteryusage" in k || "discharge level" in k) && number >= 100 ->
                v(TITLE_CYCLES, "≈ ${(number / 100).roundToInt()}", 1, BatterySource.CALCULATED)
            ("soh" in k || "state of health" in k || "health percent" in k) && number in 1.0..100.0 ->
                v(TITLE_HEALTH, "${number.roundToInt()} %", 0)
            "asoc" in k && number in 1.0..100.0 -> v(TITLE_HEALTH, "${number.roundToInt()} %", 1)
            "design" in k && ("capacity" in k || "charge" in k) && number > 0 -> v("Паспортная ёмкость", value, 0)
            ("full capacity" in k || "charge full" in k || "fullcapacity" in k) && number > 0 -> v("Полная ёмкость", value, 0)
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
        const val TITLE_CURRENT = "Ток"
        const val TITLE_TEMPERATURE = "Температура"

        private val KEY_REGEX = Regex("^[A-Za-z][A-Za-z0-9 _()./-]{0,59}$")

        /** Learned once per process: does this phone report current in µA (Android standard) or mA? */
        @Volatile
        private var microAmps: Boolean? = null

        /** Discharge readings in a row that looked like mA; one odd reading (e.g. right after unplugging) is not enough. */
        @Volatile
        private var smallDischargeReadings = 0

        /**
         * BATTERY_PROPERTY_CURRENT_NOW must be in µA, but some firmware reports mA.
         * - |value| ≥ 10 000 can only be µA (nobody draws or charges 10 A);
         * - discharging with the app on screen draws at least tens of mA, so 50…4 999 three times in a row means mA.
         * Until one of these is seen, the old rule is used.
         */
        internal fun isMicroAmps(now: Int, average: Int, discharging: Boolean): Boolean {
            val n = if (now == Int.MIN_VALUE) 0L else abs(now.toLong())
            val avg = if (average == Int.MIN_VALUE) 0L else abs(average.toLong())
            when {
                n >= 10_000L || avg >= 10_000L -> microAmps = true
                microAmps == null && discharging && n in 50L..4_999L && (avg == 0L || avg in 50L..4_999L) -> {
                    smallDischargeReadings++
                    if (smallDischargeReadings >= 3) microAmps = false
                }
                else -> smallDischargeReadings = 0
            }
            return microAmps ?: (n >= 10_000L)
        }
        private const val PLUGGED_DOCK = 8 // BatteryManager.BATTERY_PLUGGED_DOCK (API 33)

        /** Every power supply (battery, bms, fg…): the first readable value of each file wins. */
        private const val SYSFS_COMMAND =
            "for d in /sys/class/power_supply/*; do " +
                "for f in cycle_count battery_cycle charge_full charge_full_design; do " +
                "[ -r \"\$d/\$f\" ] && echo \"\$f=\$(cat \"\$d/\$f\" 2>/dev/null)\"; " +
                "done; done; true"
    }
}
