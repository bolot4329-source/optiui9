package com.ultracore.app.core

import com.ultracore.app.shizuku.ShellResult

/** Why a command failed, in words a person can act on. */
enum class ErrorKind {
    NONE,
    /** The firmware blocks this command for ADB/shell. */
    PERMISSION,
    /** The command or option does not exist on this Android version or firmware. */
    UNSUPPORTED,
    /** Package installation error (INSTALL_FAILED_*). */
    INSTALL,
    TIMEOUT,
    /** Shizuku is not connected / the service died. */
    SHIZUKU,
    UNKNOWN,
}

data class ClassifiedError(
    val kind: ErrorKind,
    /** Short explanation for the user, in Russian. */
    val message: String,
    /** First meaningful line of the raw output, for details. */
    val detail: String,
)

/**
 * Turns raw shell output into an understandable reason.
 * Works only on generic AOSP error texts — no brand-specific rules.
 */
object ErrorClassifier {

    private val permissionMarkers = listOf(
        "securityexception",
        "permission denial",
        "permission denied",
        "not allowed",
        "requires android.permission",
        "does not have permission",
        "operation not permitted",
    )

    private val unsupportedMarkers = listOf(
        "unknown command",
        "inaccessible or not found",
        "not found",
        "unknown option",
        "unrecognized option",
        "no such file or directory",
        "can't find service",
        "error: unknown",
    )

    const val FIRMWARE_HINT =
        "Ваша прошивка ограничивает команды отладки. Проверьте в «Для разработчиков» " +
            "дополнительные переключатели отладки, а в настройках безопасности — функции, " +
            "блокирующие USB/ADB-команды."

    fun classify(result: ShellResult): ClassifiedError {
        if (result.isSuccess) return ClassifiedError(ErrorKind.NONE, "", "")
        if (result.timedOut) {
            return ClassifiedError(ErrorKind.TIMEOUT, "Команда не успела завершиться", "")
        }
        return classify(result.combined)
    }

    fun classify(output: String): ClassifiedError {
        val text = output.lowercase()
        val detail = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return when {
            "install_failed_version_downgrade" in text -> ClassifiedError(
                ErrorKind.INSTALL,
                "Система запретила откат версии. Без Root это возможно только для отладочных (debuggable) приложений.",
                detail,
            )
            "install_failed" in text || "install_parse_failed" in text -> ClassifiedError(
                ErrorKind.INSTALL, "Установка не удалась", detail,
            )
            permissionMarkers.any { it in text } -> ClassifiedError(
                ErrorKind.PERMISSION, FIRMWARE_HINT, detail,
            )
            unsupportedMarkers.any { it in text } -> ClassifiedError(
                ErrorKind.UNSUPPORTED, "Команда не поддерживается на этом телефоне", detail,
            )
            "shizuku" in text || "deadobject" in text -> ClassifiedError(
                ErrorKind.SHIZUKU, "Нет связи с Shizuku. Запустите Shizuku и нажмите «Подключить».", detail,
            )
            else -> ClassifiedError(ErrorKind.UNKNOWN, "Команда завершилась с ошибкой", detail)
        }
    }
}
