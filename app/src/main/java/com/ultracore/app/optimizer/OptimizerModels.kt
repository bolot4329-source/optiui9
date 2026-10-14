package com.ultracore.app.optimizer

import java.util.Locale

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

/** Values from /proc/meminfo, in bytes. */
data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val swapUsedBytes: Long get() = (swapTotalBytes - swapFreeBytes).coerceAtLeast(0)
    val swapUsedFraction: Float get() = if (swapTotalBytes > 0) swapUsedBytes.toFloat() / swapTotalBytes else 0f
}

data class Telemetry(val storage: StorageInfo?, val memory: MemoryInfo?)

/** Result of one optimization module, shown on its card and in the boost summary. */
data class OptimizationResult(
    val tool: OptimizerTool,
    val success: Boolean,
    val summary: String,
)

enum class OptimizerTool(val title: String) {
    TrimCaches("Сброс кэша приложений"),
    FreeRam("Освобождение ОЗУ"),
    LogsAndTrim("Очистка логов и TRIM"),
    ArtCompile("ART-компиляция приложений"),
    DexoptJob("Глубокая DEX-оптимизация"),
}

/** Events emitted while compiling user apps one by one. */
sealed interface CompileEvent {
    data class Progress(val current: Int, val total: Int, val packageName: String) : CompileEvent
    data class Finished(val result: OptimizationResult) : CompileEvent
}

data class ShellLogEntry(
    val timestamp: Long,
    val command: String?,
    val message: String,
    val durationMs: Long?,
    val success: Boolean,
)

fun formatBytes(bytes: Long): String {
    val abs = kotlin.math.abs(bytes).toDouble()
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    val kb = 1024.0
    return when {
        abs >= gb -> String.format(Locale.US, "%.2f ГБ", bytes / gb)
        abs >= mb -> String.format(Locale.US, "%.0f МБ", bytes / mb)
        abs >= kb -> String.format(Locale.US, "%.0f КБ", bytes / kb)
        else -> "$bytes Б"
    }
}

fun formatDuration(ms: Long): String =
    if (ms < 1_000) "$ms мс"
    else if (ms < 60_000) String.format(Locale.US, "%.1f с", ms / 1000.0)
    else "${ms / 60_000} мин ${(ms % 60_000) / 1000} с"
