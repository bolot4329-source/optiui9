package com.ultracore.app.optimizer

import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.ultracore.app.shizuku.ShellMarkers
import com.ultracore.app.shizuku.ShizukuManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Telemetry + optimization modules executed through Shizuku (UID 2000).
 * Every executed command is written to [log] with its duration and result.
 */
class SystemOptimizerRepository {

    private val _log = MutableStateFlow<List<ShellLogEntry>>(emptyList())
    val log: StateFlow<List<ShellLogEntry>> = _log.asStateFlow()

    // ---------------------------------------------------------------- telemetry

    suspend fun readTelemetry(): Telemetry = withContext(Dispatchers.IO) {
        Telemetry(storage = readStorage(), memory = readMemory())
    }

    /** /data partition (the same partition that backs /storage/emulated/0). */
    fun readStorage(): StorageInfo? {
        val candidates = listOf(
            Environment.getDataDirectory().path,
            Environment.getExternalStorageDirectory().path,
        )
        for (path in candidates) {
            try {
                val stat = StatFs(path)
                return StorageInfo(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
            } catch (_: IllegalArgumentException) {
                // try next path
            }
        }
        return null
    }

    /** Parses /proc/meminfo (values are in kB). */
    fun readMemory(): MemoryInfo? {
        return try {
            val values = HashMap<String, Long>()
            File("/proc/meminfo").forEachLine { line ->
                val key = line.substringBefore(':', missingDelimiterValue = "").trim()
                if (key.isEmpty()) return@forEachLine
                val number = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                if (number != null) values[key] = number * 1024L
            }
            val total = values["MemTotal"] ?: return null
            MemoryInfo(
                totalBytes = total,
                availableBytes = values["MemAvailable"] ?: values["MemFree"] ?: 0L,
                swapTotalBytes = values["SwapTotal"] ?: 0L,
                swapFreeBytes = values["SwapFree"] ?: 0L,
            )
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- modules

    /** 1) pm trim-caches: asks the system to free as much cache as possible. */
    suspend fun trimCaches(): OptimizationResult {
        val before = readStorage()?.freeBytes
        val r = exec("pm trim-caches 999G", timeoutSeconds = 180)
        delay(800)
        val after = readStorage()?.freeBytes
        val result = if (!r.ok) {
            fail(OptimizerTool.TrimCaches, r)
        } else if (before != null && after != null) {
            val freed = (after - before).coerceAtLeast(0)
            OptimizationResult(OptimizerTool.TrimCaches, true, "Освобождено ${formatBytes(freed)} кэша")
        } else {
            OptimizationResult(OptimizerTool.TrimCaches, true, "Кэш очищен")
        }
        info(result)
        return result
    }

    /** 2) ART compile of every user app (speed-profile, forced), one package at a time. */
    fun compileUserApps(): Flow<CompileEvent> = flow {
        val started = SystemClock.elapsedRealtime()
        val list = exec("pm list packages -3", timeoutSeconds = 60)
        if (!list.ok) {
            val result = fail(OptimizerTool.ArtCompile, list)
            info(result)
            emit(CompileEvent.Finished(result))
            return@flow
        }

        val packages = list.output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { PACKAGE_REGEX.matches(it) }
            .distinct()
            .sorted()
            .toList()

        val total = packages.size
        if (total == 0) {
            val result = OptimizationResult(OptimizerTool.ArtCompile, true, "Пользовательских приложений не найдено")
            info(result)
            emit(CompileEvent.Finished(result))
            return@flow
        }

        var ok = 0
        var failed = 0
        packages.forEachIndexed { index, pkg ->
            currentCoroutineContext().ensureActive()
            emit(CompileEvent.Progress(current = index, total = total, packageName = pkg))
            val r = exec(
                "cmd package compile -m speed-profile -f $pkg",
                timeoutSeconds = 300,
                logIt = false,
            )
            if (r.ok && r.output.contains("Success", ignoreCase = true)) {
                ok++
            } else {
                failed++
                addLog(r.command, "Ошибка: $pkg — ${ShellMarkers.clean(r.output).lineSequence().lastOrNull().orEmpty()}", r.durationMs, false)
            }
        }
        emit(CompileEvent.Progress(current = total, total = total, packageName = ""))

        val elapsed = SystemClock.elapsedRealtime() - started
        val summary = buildString {
            append("Оптимизировано $ok из $total приложений за ${formatDuration(elapsed)}")
            if (failed > 0) append(" · ошибок: $failed")
        }
        val result = OptimizationResult(OptimizerTool.ArtCompile, failed < total, summary)
        info(result)
        emit(CompileEvent.Finished(result))
    }

    /** 3) Background dexopt job for all packages. Can take several minutes. */
    suspend fun runDexoptJob(): OptimizationResult {
        val r = exec("cmd package bg-dexopt-job", timeoutSeconds = 600)
        val result = if (r.ok) {
            OptimizationResult(OptimizerTool.DexoptJob, true, "DEX-оптимизация завершена за ${formatDuration(r.durationMs)}")
        } else {
            fail(OptimizerTool.DexoptJob, r)
        }
        info(result)
        return result
    }

    /** TRIM: tells the flash storage which blocks are free. Useful after deleting a lot of files. */
    suspend fun trim(): OptimizationResult {
        val r = exec("sm fstrim", timeoutSeconds = 600)
        val result = if (r.ok) {
            OptimizationResult(OptimizerTool.Trim, true, "TRIM выполнен за ${formatDuration(r.durationMs)}")
        } else {
            fail(OptimizerTool.Trim, r)
        }
        info(result)
        return result
    }

    // ---------------------------------------------------------------- log

    fun clearLog() {
        _log.value = emptyList()
    }

    fun note(message: String, success: Boolean = true) {
        addLog(command = null, message = message, durationMs = null, success = success)
    }

    private fun info(result: OptimizationResult) {
        addLog(command = null, message = "${result.tool.title}: ${result.summary}", durationMs = null, success = result.success)
    }

    private fun addLog(command: String?, message: String, durationMs: Long?, success: Boolean) {
        val entry = ShellLogEntry(System.currentTimeMillis(), command, message, durationMs, success)
        _log.update { (it + entry).takeLast(MAX_LOG_ENTRIES) }
    }

    private fun fail(tool: OptimizerTool, r: CommandResult): OptimizationResult {
        val reason = ShellMarkers.clean(r.output).lineSequence().lastOrNull { it.isNotBlank() }
            ?: r.output.lineSequence().lastOrNull { it.isNotBlank() }
            ?: "неизвестная ошибка"
        return OptimizationResult(tool, false, "Ошибка: $reason")
    }

    // ---------------------------------------------------------------- shell

    private data class CommandResult(
        val command: String,
        val output: String,
        val durationMs: Long,
        val ok: Boolean,
    )

    private suspend fun exec(command: String, timeoutSeconds: Int, logIt: Boolean = true): CommandResult {
        val start = SystemClock.elapsedRealtime()
        val output = try {
            if (!ShizukuManager.isConnected) {
                "${ShellMarkers.LAUNCH_ERROR} Shizuku не подключён"
            } else {
                ShizukuManager.execWithTimeout(command, timeoutSeconds)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            "${ShellMarkers.LAUNCH_ERROR} ${t.message ?: t.javaClass.simpleName}"
        }
        val duration = SystemClock.elapsedRealtime() - start
        val ok = !ShellMarkers.isFailure(output)
        if (logIt) {
            val shown = ShellMarkers.clean(output).ifBlank { if (ok) "OK" else output.trim() }
            addLog(command, shown.take(MAX_LOG_OUTPUT_CHARS), duration, ok)
        }
        return CommandResult(command, output, duration, ok)
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 300
        const val MAX_LOG_OUTPUT_CHARS = 2_000
        val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    }
}
