package com.ultracore.app.cleaner

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.SystemClock
import com.ultracore.app.shizuku.ShellMarkers
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.storage.StoragePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Phase 3: finds junk and deletes what the user selected.
 * Public storage is walked from the app process (MANAGE_EXTERNAL_STORAGE);
 * Android/data and Android/obb are inspected and cleaned through Shizuku (UID 2000).
 */
class CleanerRepository(private val context: Context) {

    // ------------------------------------------------------------------ scan

    suspend fun scan(onProgress: (ScanProgress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val job = coroutineContext[Job]
        val warnings = mutableListOf<String>()
        val acc = WalkAccumulator()

        if (Environment.isExternalStorageManager()) {
            onProgress(ScanProgress("Сканирование общей памяти", 0, StoragePaths.ROOT))
            walk(File(StoragePaths.ROOT), inHidden = false, acc = acc, job = job, onProgress = onProgress)
        } else {
            warnings += "Нет доступа к файлам — общая память не проверялась"
        }

        val duplicates = if (acc.dupCandidates.size > 1) {
            findDuplicates(acc.dupCandidates, job, onProgress)
        } else {
            emptyList()
        }

        val orphans = if (ShizukuManager.isConnected) {
            onProgress(ScanProgress("Проверка Android/data и Android/obb", acc.files, StoragePaths.ANDROID_DATA))
            try {
                findOrphans()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warnings += "Android/data: ${t.message ?: t.javaClass.simpleName}"
                emptyList()
            }
        } else {
            warnings += "Shizuku не подключён — Android/data и Android/obb не проверялись"
            emptyList()
        }

        ScanResult(
            items = linkedMapOf(
                JunkCategory.OrphanData to orphans.sortedByDescending { it.sizeBytes },
                JunkCategory.Thumbnails to acc.thumbnails.sortedByDescending { it.sizeBytes },
                JunkCategory.LogsAndTemp to acc.logs.sortedByDescending { it.sizeBytes },
                JunkCategory.EmptyFolders to acc.emptyDirs.sortedBy { it.path.lowercase() },
                JunkCategory.Duplicates to duplicates,
            ),
            warnings = warnings,
            durationMs = SystemClock.elapsedRealtime() - started,
        )
    }

    private class FileEntry(val path: String, val size: Long, val lastModified: Long)

    private class WalkAccumulator {
        var files = 0
        val thumbnails = mutableListOf<JunkItem>()
        val logs = mutableListOf<JunkItem>()
        val emptyDirs = mutableListOf<JunkItem>()
        val dupCandidates = mutableListOf<FileEntry>()
    }

    /** Returns true if [dir] contains at least one file somewhere inside (or must be kept). */
    private fun walk(
        dir: File,
        inHidden: Boolean,
        acc: WalkAccumulator,
        job: Job?,
        onProgress: (ScanProgress) -> Unit,
    ): Boolean {
        job?.ensureActive()
        val children = dir.listFiles() ?: return true // unreadable → never treat as empty

        var hasContent = false
        val emptyChildren = mutableListOf<File>()

        for (child in children) {
            if (child.isDirectory) {
                val path = child.absolutePath
                if (path == StoragePaths.ANDROID_DIR) {
                    hasContent = true // Android/ is handled through Shizuku
                    continue
                }
                if (child.name.equals(".thumbnails", ignoreCase = true)) {
                    acc.thumbnails += JunkItem(path, dirSize(child), JunkCategory.Thumbnails, isDirectory = true)
                    hasContent = true
                    continue
                }
                val childHasContent = walk(
                    dir = child,
                    inHidden = inHidden || child.name.startsWith("."),
                    acc = acc,
                    job = job,
                    onProgress = onProgress,
                )
                if (childHasContent || path in StoragePaths.PROTECTED_DIRS) {
                    hasContent = true
                } else {
                    emptyChildren += child
                }
            } else {
                hasContent = true
                acc.files++
                if (acc.files % PROGRESS_EVERY == 0) {
                    onProgress(ScanProgress("Сканирование общей памяти", acc.files, StoragePaths.relative(dir.path)))
                }
                classifyFile(child, inHidden, acc)
            }
        }

        // Report only the topmost empty folders: if this folder is empty too,
        // the parent will report it and deleting it removes the children as well.
        if (hasContent) {
            emptyChildren.forEach {
                acc.emptyDirs += JunkItem(it.absolutePath, 0, JunkCategory.EmptyFolders, isDirectory = true)
            }
        }
        return hasContent
    }

    private fun classifyFile(file: File, inHidden: Boolean, acc: WalkAccumulator) {
        val name = file.name
        val lower = name.lowercase()
        if (lower.endsWith(".log") || lower.endsWith(".tmp") || lower.endsWith(".temp")) {
            acc.logs += JunkItem(file.absolutePath, file.length(), JunkCategory.LogsAndTemp, isDirectory = false)
            return
        }
        if (inHidden || name.startsWith(".")) return
        val size = file.length()
        if (size >= DUPLICATE_MIN_BYTES) {
            acc.dupCandidates += FileEntry(file.absolutePath, size, file.lastModified())
        }
    }

    private fun dirSize(dir: File, depth: Int = 0): Long {
        if (depth > 32) return 0
        val children = dir.listFiles() ?: return 0
        var total = 0L
        for (c in children) {
            total += if (c.isDirectory) dirSize(c, depth + 1) else c.length()
        }
        return total
    }

    // ------------------------------------------------------------------ duplicates

    private fun findDuplicates(
        candidates: List<FileEntry>,
        job: Job?,
        onProgress: (ScanProgress) -> Unit,
    ): List<JunkItem> {
        val sizeGroups = candidates.groupBy { it.size }.values.filter { it.size > 1 }
        val total = sizeGroups.sumOf { it.size }
        var checked = 0
        val result = mutableListOf<JunkItem>()

        for (group in sizeGroups) {
            job?.ensureActive()
            checked += group.size
            onProgress(ScanProgress("Поиск дубликатов: $checked из $total", checked, StoragePaths.relative(group.first().path)))

            // Cheap filter first (start + end of the file), then a full SHA-256.
            val byPartial = group
                .mapNotNull { e -> partialHash(e.path)?.let { it to e } }
                .groupBy({ it.first }, { it.second })
                .values
                .filter { it.size > 1 }

            for (partialGroup in byPartial) {
                job?.ensureActive()
                val byFull = partialGroup
                    .mapNotNull { e -> fullHash(e.path, job)?.let { it to e } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .filter { it.size > 1 }

                for (same in byFull) {
                    val keeper = same.minWith(compareBy<FileEntry>({ it.lastModified }, { it.path.length }))
                    same.filter { it !== keeper }.forEach { copy ->
                        result += JunkItem(
                            path = copy.path,
                            sizeBytes = copy.size,
                            category = JunkCategory.Duplicates,
                            isDirectory = false,
                            note = "Оригинал: ${StoragePaths.relative(keeper.path)}",
                        )
                    }
                }
            }
        }
        return result.sortedByDescending { it.sizeBytes }
    }

    private fun partialHash(path: String): String? = try {
        RandomAccessFile(path, "r").use { raf ->
            val md = MessageDigest.getInstance("SHA-256")
            val len = raf.length()
            val buf = ByteArray(PARTIAL_CHUNK)
            val head = raf.read(buf, 0, minOf(PARTIAL_CHUNK.toLong(), len).toInt())
            if (head > 0) md.update(buf, 0, head)
            if (len > PARTIAL_CHUNK * 2L) {
                raf.seek(len - PARTIAL_CHUNK)
                val tail = raf.read(buf, 0, PARTIAL_CHUNK)
                if (tail > 0) md.update(buf, 0, tail)
            }
            md.digest().toHex()
        }
    } catch (_: Exception) {
        null
    }

    private fun fullHash(path: String, job: Job?): String? = try {
        File(path).inputStream().buffered(FULL_BUFFER).use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(FULL_BUFFER)
            var counter = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                if (++counter % 64 == 0) job?.ensureActive()
            }
            md.digest().toHex()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ Android/data & obb

    private suspend fun findOrphans(): List<JunkItem> {
        val installed = installedPackages()
        val result = mutableListOf<JunkItem>()
        for (base in listOf(StoragePaths.ANDROID_DATA, StoragePaths.ANDROID_OBB)) {
            val entries = ShizukuManager.listDirectory(base)
            for (entry in entries) {
                if (!entry.endsWith("/")) continue
                val name = entry.removeSuffix("/")
                if (!StoragePaths.PACKAGE_REGEX.matches(name)) continue
                if (name in installed) continue
                val path = "$base/$name"
                result += JunkItem(
                    path = path,
                    sizeBytes = shellDirSize(path),
                    category = JunkCategory.OrphanData,
                    isDirectory = true,
                    viaShizuku = true,
                    note = if (base == StoragePaths.ANDROID_OBB) "obb · $name" else "data · $name",
                )
            }
        }
        return result
    }

    /**
     * Everything that counts as "installed": PackageManager (incl. disabled and
     * uninstalled-with-data-kept) plus `pm list packages -u` from the shell.
     * Archived apps keep their data, so they must never be reported as orphans.
     */
    @Suppress("DEPRECATION")
    private suspend fun installedPackages(): Set<String> {
        val names = HashSet<String>()
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        context.packageManager.getInstalledPackages(flags).forEach { names += it.packageName }

        val shell = ShizukuManager.exec("pm list packages -u")
        if (ShellMarkers.isFailure(shell)) {
            throw IllegalStateException("не удалось получить список пакетов")
        }
        shell.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .forEach { names += it.removePrefix("package:").trim() }

        if (names.size < MIN_SANE_PACKAGES) {
            // Something is off (e.g. package visibility); better to report nothing than delete app data.
            throw IllegalStateException("список установленных приложений подозрительно короткий (${names.size})")
        }
        return names
    }

    private suspend fun shellDirSize(path: String): Long {
        val out = ShizukuManager.exec("du -sk -- '${path.replace("'", "'\\''")}'")
        val kb = out.trim().lineSequence().firstOrNull()?.split('\t', ' ')?.firstOrNull()?.toLongOrNull()
        return (kb ?: 0L) * 1024L
    }

    // ------------------------------------------------------------------ delete

    suspend fun delete(items: List<JunkItem>, onProgress: (done: Int, total: Int) -> Unit): CleanResult =
        withContext(Dispatchers.IO) {
            var deleted = 0
            var failed = 0
            var freed = 0L
            val errors = mutableListOf<String>()
            val removedFiles = mutableListOf<String>()

            items.forEachIndexed { index, item ->
                ensureActive()
                val ok = try {
                    if (item.viaShizuku) {
                        ShizukuManager.deletePath(item.path)
                    } else {
                        deleteLocal(item)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    deleted++
                    freed += item.sizeBytes
                    if (!item.isDirectory) removedFiles += item.path
                } else {
                    failed++
                    if (errors.size < MAX_ERRORS) errors += StoragePaths.relative(item.path)
                }
                onProgress(index + 1, items.size)
            }

            // Tell MediaStore the deleted files are gone, so the gallery doesn't show ghosts.
            if (removedFiles.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, removedFiles.toTypedArray(), null, null)
            }
            CleanResult(deleted, failed, freed, errors)
        }

    private fun deleteLocal(item: JunkItem): Boolean {
        val file = File(item.path)
        if (!file.exists()) return true
        val canonical = file.canonicalPath
        if (!canonical.startsWith("${StoragePaths.ROOT}/")) return false
        if (canonical in StoragePaths.PROTECTED_DIRS) return false
        if (canonical.startsWith("${StoragePaths.ANDROID_DIR}/") || canonical == StoragePaths.ANDROID_DIR) return false
        return when (item.category) {
            // An "empty" folder may have received files since the scan — delete only if still empty.
            JunkCategory.EmptyFolders -> isStillEmpty(file) && file.deleteRecursively()
            else -> if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    private fun isStillEmpty(dir: File): Boolean {
        val children = dir.listFiles() ?: return false
        return children.all { it.isDirectory && isStillEmpty(it) }
    }

    private companion object {
        const val PROGRESS_EVERY = 250
        const val DUPLICATE_MIN_BYTES = 1L * 1024 * 1024
        const val PARTIAL_CHUNK = 64 * 1024
        const val FULL_BUFFER = 1024 * 1024
        const val MAX_ERRORS = 30
        const val MIN_SANE_PACKAGES = 50
    }
}
