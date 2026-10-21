package com.ultracore.app.shizuku

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
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
            var started: Started? = null
            var monitor: Thread? = null
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                try {
                    val st = startShell(command, mergeStderr = true)
                    started = st
                    val p = st.process

                    // Kills the command when the app closes its side («Стоп») — even if the command
                    // prints nothing — or after the 30-minute limit.
                    monitor = thread(isDaemon = true, name = "ultracore-stream-monitor") {
                        val deadline = System.currentTimeMillis() + STREAM_MAX_SECONDS * 1_000
                        try {
                            while (p.isAlive) {
                                if (System.currentTimeMillis() > deadline || readerClosed(writeSide)) {
                                    killTree(st)
                                    break
                                }
                                Thread.sleep(MONITOR_INTERVAL_MS)
                            }
                        } catch (_: InterruptedException) {
                            // Command finished normally.
                        }
                    }

                    val buf = ByteArray(4096)
                    var lastByte: Byte = '\n'.code.toByte()
                    val input = p.inputStream
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n) // throws IOException when the app closed its side
                        out.flush()
                        if (n > 0) lastByte = buf[n - 1]
                    }
                    val code = p.waitFor()
                    // Marker goes on its own line without adding an empty line after the output.
                    val prefix = if (lastByte == '\n'.code.toByte()) "" else "\n"
                    out.write("$prefix${ShellMarkers.EXIT_CODE} $code\n".toByteArray())
                } catch (_: IOException) {
                    // The app closed the pipe ("Стоп") — kill the command.
                    started?.let { killTree(it) }
                } catch (t: Throwable) {
                    started?.let { killTree(it) }
                    runCatching {
                        out.write("${ShellMarkers.LAUNCH_ERROR} ${t.message ?: t.javaClass.simpleName}\n".toByteArray())
                    }
                } finally {
                    monitor?.interrupt()
                    started?.let { st -> runCatching { st.process.inputStream.close() } }
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

        private const val MONITOR_INTERVAL_MS = 500L
        private const val PID_MARKER = "@@ULTRACORE_PID@@"

        /** A started `sh` and its pid (needed to kill the whole command, not only `sh`). */
        class Started(val process: Process, val pid: Int?)

        /**
         * Starts `sh -c` and reads its pid: the script first prints a marker line with `$$`
         * to stderr, which is consumed here and never reaches the output.
         */
        fun startShell(command: String, mergeStderr: Boolean): Started {
            val script = "echo $PID_MARKER\$\$ 1>&2\n$command"
            val process = ProcessBuilder("sh", "-c", script)
                .redirectErrorStream(mergeStderr)
                .start()
            process.outputStream.close()
            val pidStream = if (mergeStderr) process.inputStream else process.errorStream
            val pid = readMarkerLine(pidStream)?.removePrefix(PID_MARKER)?.trim()?.toIntOrNull()
            return Started(process, pid)
        }

        /** Reads one line byte by byte (no buffering, so the rest of the stream stays intact). */
        private fun readMarkerLine(stream: java.io.InputStream): String? {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 64) {
                val b = stream.read()
                if (b < 0) break
                if (b == '\n'.code) break
                bytes.write(b)
            }
            val line = bytes.toString()
            return if (line.startsWith(PID_MARKER)) line else null
        }

        /** Kills the command's children (pipelines, logcat…) and then `sh` itself. */
        fun killTree(started: Started) {
            started.pid?.let { pid ->
                runCatching {
                    ProcessBuilder("sh", "-c", "pkill -9 -P $pid").start().waitFor(2, TimeUnit.SECONDS)
                }
            }
            runCatching { started.process.destroyForcibly() }
            runCatching { started.process.inputStream.close() }
            runCatching { started.process.errorStream.close() }
        }

        /** True when nobody reads the pipe any more (the app pressed «Стоп» or died). */
        private fun readerClosed(writeSide: ParcelFileDescriptor): Boolean = try {
            val poll = StructPollfd().apply {
                fd = writeSide.fileDescriptor
                events = OsConstants.POLLOUT.toShort()
            }
            Os.poll(arrayOf(poll), 0)
            (poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP)) != 0
        } catch (_: Exception) {
            false
        }

        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        /** Runs a command with separate stdout/stderr and a timeout. Never throws. */
        fun runShellResult(command: String, timeoutSeconds: Long): ShellResult {
            return try {
                val started = startShell(command, mergeStderr = false)
                val process = started.process

                val stdout = StringBuffer()
                val stderr = StringBuffer()
                val outReader = drain(process.inputStream, stdout, "ultracore-stdout")
                val errReader = drain(process.errorStream, stderr, "ultracore-stderr")

                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) killTree(started)
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
                val started = startShell(command, mergeStderr = true)
                val process = started.process

                val buffer = StringBuffer()
                val reader = thread(isDaemon = true, name = "ultracore-shell-reader") {
                    try {
                        process.inputStream.bufferedReader().use { r ->
                            val chunk = CharArray(4096)
                            while (true) {
                                val n = r.read(chunk)
                                if (n < 0) break
                                if (buffer.length < MAX_OUTPUT_CHARS) {
                                    buffer.append(chunk, 0, minOf(n, MAX_OUTPUT_CHARS - buffer.length))
                                }
                            }
                        }
                    } catch (_: IOException) {
                        // Stream closed after destroyForcibly(); nothing to do.
                    }
                }

                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) killTree(started)
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
