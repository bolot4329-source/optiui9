package com.ultracore.app.tweaks

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.ultracore.app.core.ErrorClassifier
import com.ultracore.app.shizuku.ShellResult
import com.ultracore.app.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MessageKind { OK, WARN, ERROR }

data class TweakMessage(val text: String, val kind: MessageKind)

/**
 * Quick system tweaks through standard AOSP commands only.
 * Every change first saves the original value ([TweakBackup]) so it can be reset.
 */
class TweaksRepository(context: Context) {

    private val appContext = context.applicationContext
    private val backup = TweakBackup(appContext)

    // ---------- generic get / put / restore ----------

    suspend fun read(target: SettingTarget): String? {
        val r = ShizukuManager.execResult(target.getCommand, TIMEOUT)
        return if (r.isSuccess) normalizeSettingValue(r.stdout) else null
    }

    /** Writes a value; the original is backed up before the first change. */
    suspend fun write(target: SettingTarget, value: String): ShellResult {
        if (!backup.has(target)) {
            val original = ShizukuManager.execResult(target.getCommand, TIMEOUT)
            if (!original.isSuccess) return original
            backup.saveIfAbsent(target, normalizeSettingValue(original.stdout))
        }
        return checked(ShizukuManager.execResult(target.putCommand(value), TIMEOUT))
    }

    /** Restores the backed-up original. Without a backup there is nothing to undo. */
    suspend fun restore(target: SettingTarget): ShellResult {
        val original = backup.get(target) ?: return ShellResult(0, "", "", truncated = false, timedOut = false)
        val command = when {
            // Deleting private_dns_mode does not switch DNS back on some firmware until a reboot:
            // write the Android default explicitly instead.
            original == TweakBackup.UNSET && target.id == DNS_MODE.id -> target.putCommand("opportunistic")
            original == TweakBackup.UNSET -> target.deleteCommand
            else -> target.putCommand(original)
        }
        val result = checked(ShizukuManager.execResult(command, TIMEOUT))
        if (result.isSuccess) backup.remove(target)
        return result
    }

    fun hasBackup(vararg targets: SettingTarget): Boolean = targets.any { backup.has(it) }

    /** Some firmware prints a SecurityException but exits with 0 — treat it as a failure. */
    private fun checked(r: ShellResult): ShellResult {
        val t = r.stdout.lowercase()
        return if (r.isSuccess && ("securityexception" in t || "permission denial" in t)) {
            ShellResult(1, r.stdout, r.stderr, r.truncated, false)
        } else {
            r
        }
    }

    fun errorMessage(r: ShellResult): TweakMessage {
        val e = ErrorClassifier.classify(r)
        val text = if (e.detail.isNotBlank()) "${e.message}\n${e.detail}" else e.message
        return TweakMessage(text, MessageKind.ERROR)
    }

    // ---------- animations ----------

    suspend fun readAnimationScale(): String? = read(ANIMATION_KEYS.first())

    suspend fun setAnimationScale(scale: String): TweakMessage {
        for (target in ANIMATION_KEYS) {
            val r = write(target, scale)
            if (!r.isSuccess) return errorMessage(r)
        }
        return TweakMessage("Скорость анимаций: ${scaleLabel(scale)}", MessageKind.OK)
    }

    suspend fun resetAnimations(): TweakMessage {
        for (target in ANIMATION_KEYS) {
            val r = restore(target)
            if (!r.isSuccess) return errorMessage(r)
        }
        return TweakMessage("Анимации возвращены к исходным", MessageKind.OK)
    }

    // ---------- refresh rate ----------

    private fun display(): Display? =
        appContext.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    /**
     * Refresh rates worth locking, e.g. [60, 120]. Adaptive (LTPO) screens also list 1–48 Hz modes
     * for still images; locking the whole UI to those would make it stutter, so they are hidden.
     */
    fun supportedRefreshRates(): List<Int> =
        display()?.supportedModes
            ?.map { it.refreshRate.roundToInt() }
            ?.filter { it >= MIN_LOCKABLE_HZ }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    fun currentRefreshRate(): Int? = display()?.refreshRate?.roundToInt()

    suspend fun readRefreshLock(): Int? {
        val peak = read(PEAK_REFRESH)?.toFloatOrNull() ?: return null
        val min = read(MIN_REFRESH)?.toFloatOrNull() ?: return null
        return if (abs(peak - min) < 0.5f) peak.roundToInt() else null
    }

    suspend fun lockRefreshRate(hz: Int): TweakMessage {
        val value = "$hz.0"
        for (target in listOf(PEAK_REFRESH, MIN_REFRESH)) {
            val r = write(target, value)
            if (!r.isSuccess) return errorMessage(r)
        }
        // Check what the screen actually does: some firmware manages the rate with its own setting.
        delay(VERIFY_DELAY_MS)
        val actual = currentRefreshRate()
        return if (actual == null || abs(actual - hz) <= 1) {
            TweakMessage("Частота закреплена: $hz Гц", MessageKind.OK)
        } else {
            TweakMessage(
                "Команда принята, но экран работает на $actual Гц. На этом телефоне частотой, " +
                    "похоже, управляет собственная настройка прошивки — выберите режим в системном меню «Дисплей».",
                MessageKind.WARN,
            )
        }
    }

    suspend fun resetRefreshRate(): TweakMessage {
        for (target in listOf(PEAK_REFRESH, MIN_REFRESH)) {
            val r = restore(target)
            if (!r.isSuccess) return errorMessage(r)
        }
        return TweakMessage("Частота экрана возвращена к исходной", MessageKind.OK)
    }

    // ---------- phantom process killer ----------

    /** true = the limit is switched off (by us or in developer options). */
    suspend fun readPhantomDisabled(): Boolean {
        val monitor = read(PHANTOM_MONITOR)
        val max = read(PHANTOM_MAX)?.toLongOrNull()
        return monitor == "false" || (max != null && max >= PHANTOM_UNLIMITED.toLong())
    }

    suspend fun disablePhantomKiller(): TweakMessage {
        // Same switch as «Для разработчиков → Отключить ограничения дочерних процессов».
        val monitor = write(PHANTOM_MONITOR, "false")
        // Fallback for firmware without that switch: raise the limit and stop server sync overwriting it.
        val sync = write(SettingTarget.DeviceConfigSyncMode, "persistent")
        val max = write(PHANTOM_MAX, PHANTOM_UNLIMITED)
        return when {
            monitor.isSuccess && max.isSuccess -> TweakMessage("Ограничение фоновых процессов отключено", MessageKind.OK)
            monitor.isSuccess || max.isSuccess -> TweakMessage(
                "Отключено частично. Проверьте в «Для разработчиков» пункт об ограничениях дочерних процессов.",
                MessageKind.WARN,
            )
            !sync.isSuccess -> errorMessage(sync)
            else -> errorMessage(max)
        }
    }

    suspend fun resetPhantomKiller(): TweakMessage {
        val results = listOf(PHANTOM_MONITOR, PHANTOM_MAX, SettingTarget.DeviceConfigSyncMode).map { restore(it) }
        val failed = results.firstOrNull { !it.isSuccess }
        return if (failed == null) {
            TweakMessage("Ограничение фоновых процессов возвращено", MessageKind.OK)
        } else {
            errorMessage(failed)
        }
    }

    // ---------- private DNS ----------

    data class DnsState(val mode: String?, val host: String?)

    suspend fun readDns(): DnsState = DnsState(read(DNS_MODE), read(DNS_HOST))

    suspend fun setDnsMode(mode: String): TweakMessage {
        val r = write(DNS_MODE, mode)
        if (!r.isSuccess) return errorMessage(r)
        return TweakMessage(if (mode == "off") "Частный DNS выключен" else "Частный DNS: автоматически", MessageKind.OK)
    }

    suspend fun setDnsHost(host: String): TweakMessage {
        val h = host.trim().lowercase()
        if (!HOST_REGEX.matches(h)) return TweakMessage("Введите адрес сервера, например dns.adguard-dns.com", MessageKind.ERROR)
        val r1 = write(DNS_HOST, h)
        if (!r1.isSuccess) return errorMessage(r1)
        val r2 = write(DNS_MODE, "hostname")
        if (!r2.isSuccess) return errorMessage(r2)
        return TweakMessage(
            "Частный DNS: $h. Если сайты перестали открываться — сервер недоступен, нажмите «Сбросить».",
            MessageKind.OK,
        )
    }

    suspend fun resetDns(): TweakMessage {
        // Host first: if the original mode was «hostname», it must switch on with the right host.
        for (target in listOf(DNS_HOST, DNS_MODE)) {
            val r = restore(target)
            if (!r.isSuccess) return errorMessage(r)
        }
        return TweakMessage("Частный DNS возвращён к исходному", MessageKind.OK)
    }

    fun hasDnsBackup() = hasBackup(DNS_MODE, DNS_HOST)

    /** Restores every tweak that has a backup (used by «Вернуть телефон как было»). */
    suspend fun restoreAll(): List<Pair<String, String>> {
        val failures = mutableListOf<Pair<String, String>>()
        for (target in ALL_TARGETS) {
            if (!backup.has(target)) continue
            val r = restore(target)
            if (!r.isSuccess) failures += target.id to errorMessage(r).text
        }
        return failures
    }

    fun hasAnyBackup(): Boolean = ALL_TARGETS.any { backup.has(it) }

    fun hasAnimationBackup() = hasBackup(*ANIMATION_KEYS.toTypedArray())
    fun hasRefreshBackup() = hasBackup(PEAK_REFRESH, MIN_REFRESH)
    fun hasPhantomBackup() = hasBackup(PHANTOM_MONITOR, PHANTOM_MAX, SettingTarget.DeviceConfigSyncMode)

    companion object {
        private const val TIMEOUT = 15
        private const val VERIFY_DELAY_MS = 1_500L
        private const val MIN_LOCKABLE_HZ = 60
        private const val PHANTOM_UNLIMITED = "2147483647"

        val ANIMATION_SCALES = listOf("0.0", "0.25", "0.5", "0.75", "1.0")

        /** Well-known private DNS servers (DNS-over-TLS hostnames). */
        val DNS_PRESETS = listOf(
            "dns.adguard-dns.com" to "AdGuard — блокирует рекламу",
            "one.one.one.one" to "Cloudflare",
            "dns.google" to "Google",
        )
        private val HOST_REGEX = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

        private val ANIMATION_KEYS = listOf(
            SettingTarget.Setting("global", "window_animation_scale"),
            SettingTarget.Setting("global", "transition_animation_scale"),
            SettingTarget.Setting("global", "animator_duration_scale"),
        )
        private val PEAK_REFRESH = SettingTarget.Setting("system", "peak_refresh_rate")
        private val MIN_REFRESH = SettingTarget.Setting("system", "min_refresh_rate")
        private val PHANTOM_MONITOR = SettingTarget.Setting("global", "settings_enable_monitor_phantom_procs")
        private val PHANTOM_MAX = SettingTarget.DeviceConfig("activity_manager", "max_phantom_processes")
        private val DNS_MODE = SettingTarget.Setting("global", "private_dns_mode")
        private val DNS_HOST = SettingTarget.Setting("global", "private_dns_specifier")

        private val ALL_TARGETS: List<SettingTarget> = ANIMATION_KEYS + listOf(
            PEAK_REFRESH, MIN_REFRESH, PHANTOM_MONITOR, PHANTOM_MAX, SettingTarget.DeviceConfigSyncMode, DNS_HOST, DNS_MODE,
        )

        fun scaleLabel(scale: String?): String {
            val value = scale?.toFloatOrNull() ?: return "по умолчанию"
            return if (value == 0f) "выкл." else "${value.toString().removeSuffix(".0")}x"
        }
    }
}
