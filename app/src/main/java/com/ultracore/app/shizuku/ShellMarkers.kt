package com.ultracore.app.shizuku

/**
 * Service lines that ShellUserService appends to command output.
 * Shared by the service and the app so the app can tell success from failure.
 */
object ShellMarkers {
    const val PREFIX = "[UltraCore]"
    const val EXIT_CODE = "$PREFIX Код выхода:"
    const val TIMEOUT = "$PREFIX Команда прервана по тайм-ауту"
    const val LAUNCH_ERROR = "$PREFIX Ошибка запуска:"
    const val TRUNCATED = "$PREFIX Вывод обрезан"

    fun isFailure(output: String): Boolean =
        output.contains(EXIT_CODE) || output.contains(TIMEOUT) || output.contains(LAUNCH_ERROR)

    /** Output without service lines, for showing to the user. */
    fun clean(output: String): String =
        output.lineSequence()
            .filterNot { it.startsWith(PREFIX) }
            .joinToString("\n")
            .trim()
}
