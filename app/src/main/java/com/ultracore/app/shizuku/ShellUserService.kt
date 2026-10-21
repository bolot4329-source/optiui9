package com.ultracore.app.shizuku

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs inside a separate process started by Shizuku as UID 2000 (shell).
 * Shell belongs to the ext_data_rw / ext_obb_rw groups, so it can read and clean
 * /storage/emulated/0/Android/data and /storage/emulated/0/Android/obb.
 *
 * Shizuku creates this class by reflection, so it must keep a public no-arg constructor.
 */
class ShellUserService : IShellService.Stub() {

    override fun execCommand(command: String?): String {
        if (command == null || command.isBlank()) return ""
        return runShell(command, DEFAULT_TIMEOUT_SECONDS)
    }

    override fun execCommandWithTimeout(command: String?, timeoutSeconds: Int): String {
        if (command == null || command.isBlank()) return ""
        val timeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS).toLong()
        return runShell(command, timeout)
    }

    override fun execResult(command: String?, timeoutSeconds: Int): ShellResult {
        if (command == null || command.isBlank()) {
            return ShellResult(0, "", "", truncated = false, timedOut = false)
        }
        return runShellResult(command, timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS).toLong())
    }

    override fun execStream(command: String?): ParcelFileDescriptor? {
        if (command == null || command.isBlank()) return null
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        thread(isDaemon = true, name = "ultracore-shell-stream") {
            var process: Process? = null
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                try {
                    process = ProcessBuilder("sh", "-c", command)
                        .redirectErrorStream(true)
                        .start()
                    val p = process!!
                    p.outputStream.close()

                    // Hard limit so a forgotten command can't run forever.
                    val watchdog = thread(isDaemon = true, name = "ultracore-stream-watchdog") {
                        try {
                            if (!p.waitFor(STREAM_MAX_SECONDS, TimeUnit.SECONDS)) p.destroyForcibly()
                        } catch (_: InterruptedException) {
                            // Command finished normally.
                        }
                    }

                    val buf = ByteArray(4096)
                    p.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n) // throws IOException when the app closed its side
                            out.flush()
                        }
                    }
                    val code = p.waitFor()
                    watchdog.interrupt()
                    out.write("\n${ShellMarkers.EXIT_CODE} $code\n".toByteArray())
                } catch (_: IOException) {
                    // The app closed the pipe ("Стоп") — kill the command.
                    process?.destroyForcibly()
                } catch (t: Throwable) {
                    process?.destroyForcibly()
                    runCatching {
                        out.write("${ShellMarkers.LAUNCH_ERROR} ${t.message ?: t.javaClass.simpleName}\n".toByteArray())
                    }
                }
            }
        }
        return readSide
    }

    override fun listDirectory(path: String?): List<String> {
        if (path == null || path.isBlank()) return emptyList()

        val children = try {
            File(path).listFiles()
        } catch (_: SecurityException) {
            null
        }
        if (children != null) {
            return children
                .map { if (it.isDirectory) it.name + "/" else it.name }
                .sortedBy { it.lowercase() }
        }

        // Fallback: toybox ls (-p adds "/" to directories, same format as above).
        return runShell("ls -1Ap -- ${shellQuote(path)}", LIST_TIMEOUT_SECONDS)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("ls:") && !it.startsWith(ShellMarkers.PREFIX) }
            .sortedBy { it.lowercase() }
            .toList()
    }

    override fun deletePath(path: String?): Boolean {
        if (path == null || path.isBlank()) return false

        val target = try {
            File(path).canonicalFile
        } catch (_: IOException) {
            return false
        }
        if (!isSafeToDelete(target)) return false
        if (!target.exists()) return true

        if (target.deleteRecursively()) return true

        // Some FUSE paths refuse java.io deletes; fall back to rm.
        runShell("rm -rf -- ${shellQuote(target.path)}", DELETE_TIMEOUT_SECONDS)
        return !target.exists()
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun isSafeToDelete(file: File): Boolean {
        val p = file.path.trimEnd('/')
        if (!p.startsWith("$STORAGE_ROOT/")) return false
        return p !in PROTECTED_PATHS
    }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 60L
        private const val MAX_TIMEOUT_SECONDS = 600
        private const val LIST_TIMEOUT_SECONDS = 20L
        private const val DELETE_TIMEOUT_SECONDS = 120L

        // Binder transactions are limited to ~1 MB; Strings are UTF-16, so cap output.
        private const val MAX_OUTPUT_CHARS = 200_000

        private const val STORAGE_ROOT = "/storage/emulated/0"

        private val PROTECTED_PATHS: Set<String> = setOf(
            STORAGE_ROOT,
            "$STORAGE_ROOT/Android",
            "$STORAGE_ROOT/Android/data",
            "$STORAGE_ROOT/Android/obb",
            "$STORAGE_ROOT/Android/media",
            "$STORAGE_ROOT/DCIM",
            "$STORAGE_ROOT/DCIM/Camera",
            "$STORAGE_ROOT/Download",
            "$STORAGE_ROOT/Documents",
            "$STORAGE_ROOT/Pictures",
            "$STORAGE_ROOT/Movies",
            "$STORAGE_ROOT/Music",
            "$STORAGE_ROOT/Alarms",
            "$STORAGE_ROOT/Notifications",
            "$STORAGE_ROOT/Podcasts",
            "$STORAGE_ROOT/Recordings",
            "$STORAGE_ROOT/Ringtones",
        )

        // Upper bound for execStream commands (30 min).
        private const val STREAM_MAX_SECONDS = 1_800L

        // execResult: each of stdout/stderr is capped separately (UTF-16 → ~2 bytes per char).
        private const val MAX_RESULT_CHARS = 120_000

        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        /** Runs a command with separate stdout/stderr and a timeout. Never throws. */
        fun runShellResult(command: String, timeoutSeconds: Long): ShellResult {
            return try {
                val process = ProcessBuilder("sh", "-c", command).start()
                process.outputStream.close()

                val stdout = StringBuffer()
                val stderr = StringBuffer()
                val outReader = drain(process.inputStream, stdout, "ultracore-stdout")
                val errReader = drain(process.errorStream, stderr, "ultracore-stderr")

                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                outReader.join(2_000)
                errReader.join(2_000)

                ShellResult(
                    exitCode = if (finished) process.exitValue() else ShellResult.CODE_TIMEOUT,
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                    truncated = stdout.length >= MAX_RESULT_CHARS || stderr.length >= MAX_RESULT_CHARS,
                    timedOut = !finished,
                )
            } catch (t: Throwable) {
                ShellResult(
                    exitCode = ShellResult.CODE_LAUNCH_ERROR,
                    stdout = "",
                    stderr = t.message ?: t.javaClass.simpleName,
                    truncated = false,
                    timedOut = false,
                )
            }
        }

        private fun drain(stream: java.io.InputStream, into: StringBuffer, name: String): Thread =
            thread(isDaemon = true, name = name) {
                try {
                    stream.bufferedReader().use { r ->
                        val chunk = CharArray(4096)
                        while (true) {
                            val n = r.read(chunk)
                            if (n < 0) break
                            if (into.length < MAX_RESULT_CHARS) {
                                into.append(chunk, 0, minOf(n, MAX_RESULT_CHARS - into.length))
                            }
                        }
                    }
                } catch (_: IOException) {
                    // Stream closed after destroyForcibly().
                }
            }

        fun runShell(command: String, timeoutSeconds: Long): String {
            return try {
                val process = ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process.outputStream.close()

                val buffer = StringBuffer()
                val reader = thread(isDaemon = true, name = "ultracore-shell-reader") {
                    try {
                        process.inputStream.bufferedReader().use { r ->
                            val chunk = CharArray(4096)
                            while (true) {
                                val n = r.read(chunk)
                                if (n < 0) break
                                if (buffer.length < MAX_OUTPUT_CHARS) buffer.append(chunk, 0, n)
                            }
                        }
                    } catch (_: IOException) {
                        // Stream closed after destroyForcibly(); nothing to do.
                    }
                }

                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                reader.join(2_000)

                val result = StringBuilder(buffer.toString().trimEnd())
                if (buffer.length >= MAX_OUTPUT_CHARS) {
                    result.append('\n').append(ShellMarkers.TRUNCATED)
                }
                if (!finished) {
                    result.append('\n').append(ShellMarkers.TIMEOUT).append(" (").append(timeoutSeconds).append(" с)")
                } else {
                    val code = process.exitValue()
                    if (code != 0) result.append('\n').append(ShellMarkers.EXIT_CODE).append(' ').append(code)
                }
                result.toString().trim()
            } catch (t: Throwable) {
                "${ShellMarkers.LAUNCH_ERROR} ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }
}
