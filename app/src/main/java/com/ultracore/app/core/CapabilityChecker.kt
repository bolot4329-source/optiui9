package com.ultracore.app.core

import android.content.Context
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuMode
import com.ultracore.app.shizuku.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Checks on the phone itself which features work, instead of guessing by brand.
 * Every probe is harmless: it only reads, or writes back the value that is already set.
 *
 * The report is cached in filesDir/capabilities.json and re-checked after a system update
 * (Build.FINGERPRINT changed) or when the user taps «Проверить заново».
 */
class CapabilityChecker(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    private val _report = MutableStateFlow(readCached())
    val report: StateFlow<CapabilityReport?> = _report.asStateFlow()

    /** Cached report is valid only for the current firmware. */
    val needsCheck: Boolean
        get() = _report.value?.fingerprint != DeviceInfo.fingerprint

    suspend fun check(): CapabilityReport = mutex.withLock {
        val results = Capability.entries.map { probe(it) }
        val report = CapabilityReport(DeviceInfo.fingerprint, System.currentTimeMillis(), results)
        _report.value = report
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(CapabilityReport.serializer(), report)) }
        }
        report
    }

    private suspend fun probe(capability: Capability): CapabilityResult {
        if (!ShizukuManager.isConnected) {
            return CapabilityResult(capability, CapabilityStatus.UNAVAILABLE, "Нужен подключённый Shizuku")
        }
        return try {
            when (capability) {
                Capability.SHELL -> fromResult(capability, run("id"))
                Capability.SETTINGS_WRITE -> probeSettingsWrite()
                Capability.PACKAGE_LIST -> {
                    val r = run("pm list packages -s")
                    if (r.isSuccess && r.stdout.lineSequence().count { it.startsWith("package:") } > 0) {
                        ok(capability)
                    } else {
                        fromResult(capability, r)
                    }
                }
                Capability.BATTERY_DUMPSYS -> {
                    val r = run("dumpsys battery")
                    when {
                        !r.isSuccess -> fromResult(capability, r)
                        r.stdout.contains(":") -> ok(capability)
                        else -> CapabilityResult(capability, CapabilityStatus.PARTIAL, "Прошивка отдаёт мало данных")
                    }
                }
                Capability.DEVICE_CONFIG -> fromResult(capability, run("device_config get activity_manager max_phantom_processes"))
                Capability.ROOT_FEATURES -> if (ShizukuManager.mode == ShizukuMode.ROOT) {
                    ok(capability)
                } else {
                    CapabilityResult(
                        capability,
                        CapabilityStatus.UNAVAILABLE,
                        "Shizuku запущен без Root: скрытие приложений и запуск закрытых экранов недоступны",
                    )
                }
            }
        } catch (t: Throwable) {
            CapabilityResult(capability, CapabilityStatus.UNAVAILABLE, t.message ?: "Ошибка проверки")
        }
    }

    /** Reads a harmless global setting and writes the same value back. */
    private suspend fun probeSettingsWrite(): CapabilityResult {
        val key = "animator_duration_scale"
        val read = run("settings get global $key")
        if (!read.isSuccess) return fromResult(Capability.SETTINGS_WRITE, read)
        val current = read.stdout.trim()
        val value = if (current.isEmpty() || current == "null") "1.0" else current
        val write = run("settings put global $key $value")
        if (current.isEmpty() || current == "null") {
            // The key was unset: restore that state.
            run("settings delete global $key")
        }
        return fromResult(Capability.SETTINGS_WRITE, write)
    }

    private suspend fun run(command: String): ShellResult = ShizukuManager.execResult(command, PROBE_TIMEOUT_SECONDS)

    private fun ok(capability: Capability) = CapabilityResult(capability, CapabilityStatus.AVAILABLE)

    private fun fromResult(capability: Capability, result: ShellResult): CapabilityResult {
        if (result.isSuccess && !looksLikeError(result.stdout)) return ok(capability)
        val error = ErrorClassifier.classify(
            if (result.isSuccess) ShellResult(1, result.stdout, result.stderr, result.truncated, false) else result
        )
        val status = when (error.kind) {
            ErrorKind.PERMISSION, ErrorKind.UNSUPPORTED, ErrorKind.SHIZUKU -> CapabilityStatus.UNAVAILABLE
            else -> CapabilityStatus.PARTIAL
        }
        return CapabilityResult(capability, status, error.message)
    }

    /** Some firmware prints a SecurityException but still exits with code 0. */
    private fun looksLikeError(stdout: String): Boolean {
        val t = stdout.lowercase()
        return "securityexception" in t || "permission denial" in t
    }

    private fun readCached(): CapabilityReport? = runCatching {
        if (!file.exists()) null else json.decodeFromString(CapabilityReport.serializer(), file.readText())
    }.getOrNull()

    companion object {
        private const val FILE_NAME = "capabilities.json"
        private const val PROBE_TIMEOUT_SECONDS = 15
    }
}
