package com.ultracore.app.sorter

import android.content.Context
import android.media.MediaScannerConnection
import com.ultracore.app.storage.StoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Phase 4: rule-based file organizer with Dry Run and one-tap rollback.
 * Never overwrites: name clashes get " (1)", " (2)"… suffixes.
 */
class SorterRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val rulesSerializer = ListSerializer(SortRule.serializer())
    private val historySerializer = ListSerializer(SortBatch.serializer())
    private val fileLock = Mutex()

    private val rulesFile: File get() = File(context.filesDir, "sort_rules.json")
    private val historyFile: File get() = File(context.filesDir, "sort_history.json")

    // ------------------------------------------------------------------ rules

    suspend fun loadRules(): List<SortRule> = withContext(Dispatchers.IO) {
        fileLock.withLock {
            val file = rulesFile
            if (!file.exists()) return@withLock defaultRules()
            try {
                json.decodeFromString(rulesSerializer, file.readText())
            } catch (_: Exception) {
                defaultRules()
            }
        }
    }

    suspend fun saveRules(rules: List<SortRule>) = withContext(Dispatchers.IO) {
        fileLock.withLock { writeAtomic(rulesFile, json.encodeToString(rulesSerializer, rules)) }
    }

    fun defaultRules(): List<SortRule> = listOf(
        SortRule(
            id = "installers",
            name = "Установщики APK",
            sourceDir = "Download",
            extensions = listOf("apk", "apks", "xapk", "apkm"),
            targetDir = "Download/Installers",
        ),
        SortRule(
            id = "documents",
            name = "Документы",
            sourceDir = "Download",
            extensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "csv", "epub", "fb2"),
            targetDir = "Documents/Downloads/{ext}",
        ),
        SortRule(
            id = "images",
            name = "Картинки из загрузок",
            sourceDir = "Download",
            extensions = listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp"),
            targetDir = "Pictures/Downloads/{yyyy}/{MM}",
        ),
        SortRule(
            id = "videos",
            name = "Видео из загрузок",
            sourceDir = "Download",
            extensions = listOf("mp4", "mkv", "mov", "webm", "avi", "3gp"),
            targetDir = "Movies/Downloads/{yyyy}/{MM}",
        ),
        SortRule(
            id = "audio",
            name = "Аудио",
            sourceDir = "Download",
            extensions = listOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac"),
            targetDir = "Music/Downloads",
        ),
        SortRule(
            id = "archives",
            name = "Архивы",
            sourceDir = "Download",
            extensions = listOf("zip", "rar", "7z", "tar", "gz", "xz"),
            targetDir = "Download/Archives",
        ),
        SortRule(
            id = "screenshots",
            name = "Скриншоты по месяцам",
            sourceDir = "DCIM/Screenshots",
            extensions = listOf("png", "jpg", "jpeg", "webp", "mp4"),
            targetDir = "DCIM/Screenshots/{yyyy}/{MM}",
            enabled = false,
        ),
    )

    /** Returns an error message for the editor, or null if the rule is valid. */
    fun validate(rule: SortRule): String? {
        if (rule.name.isBlank()) return "Укажите название"
        if (StoragePaths.resolveInside(rule.sourceDir) == null) return "Недопустимая папка-источник"
        val probeTarget = rule.targetDir
            .replace("{yyyy}", "2026").replace("{MM}", "01").replace("{dd}", "01").replace("{ext}", "ext")
        if (StoragePaths.resolveInside(probeTarget) == null) return "Недопустимая целевая папка"
        if (rule.extensions.none { it.isNotBlank() }) return "Укажите хотя бы одно расширение (или *)"
        if (rule.nameRegex.isNotBlank()) {
            try {
                Regex(rule.nameRegex)
            } catch (_: Exception) {
                return "Ошибка в регулярном выражении"
            }
        }
        return null
    }

    // ------------------------------------------------------------------ dry run

    suspend fun buildPlan(rules: List<SortRule>): SortPlan = withContext(Dispatchers.IO) {
        val moves = mutableListOf<PlannedMove>()
        val warnings = mutableListOf<String>()
        val claimed = HashSet<String>()   // a file is handled by the first matching rule only
        val reserved = HashSet<String>()  // destinations already taken inside this plan

        for (rule in rules) {
            if (!rule.enabled) continue
            ensureActive()

            val error = validate(rule)
            if (error != null) {
                warnings += "«${rule.name}»: $error"
                continue
            }
            val source = StoragePaths.resolveInside(rule.sourceDir)
            if (source == null || !source.isDirectory) {
                warnings += "«${rule.name}»: папки ${rule.sourceDir} нет"
                continue
            }
            val files = source.listFiles()
            if (files == null) {
                warnings += "«${rule.name}»: нет доступа к ${rule.sourceDir}"
                continue
            }

            val exts = rule.extensions.map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }.toSet()
            val anyExt = "*" in exts
            val regex = if (rule.nameRegex.isBlank()) null else Regex(rule.nameRegex, RegexOption.IGNORE_CASE)

            for (file in files.sortedBy { it.name.lowercase() }) {
                if (!file.isFile) continue
                val name = file.name
                if (name.startsWith(".")) continue // hidden and in-progress downloads (.pending-…)
                if (file.absolutePath in claimed) continue
                if (!anyExt && file.extension.lowercase() !in exts) continue
                if (regex != null && !regex.containsMatchIn(name)) continue

                val targetDir = StoragePaths.resolveInside(expandTemplate(rule.targetDir, file)) ?: continue
                if (targetDir.path == file.parentFile?.canonicalPath) continue // already in place

                val target = StoragePaths.uniqueTarget(targetDir, name, reserved)
                reserved += target.path
                claimed += file.absolutePath
                moves += PlannedMove(
                    ruleName = rule.name,
                    from = file.absolutePath,
                    to = target.path,
                    sizeBytes = file.length(),
                    renamed = target.name != name,
                )
            }
        }
        SortPlan(moves, warnings)
    }

    private fun expandTemplate(template: String, file: File): String {
        val cal = Calendar.getInstance().apply { timeInMillis = file.lastModified() }
        val ext = file.extension.lowercase().ifEmpty { "other" }
        return template
            .replace("{yyyy}", String.format(Locale.US, "%04d", cal.get(Calendar.YEAR)))
            .replace("{MM}", String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1))
            .replace("{dd}", String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH)))
            .replace("{ext}", ext)
    }

    // ------------------------------------------------------------------ execute

    suspend fun execute(plan: SortPlan, onProgress: (done: Int, total: Int) -> Unit): SortResult =
        withContext(Dispatchers.IO) {
            val records = mutableListOf<MoveRecord>()
            val createdDirs = LinkedHashSet<String>()
            val errors = mutableListOf<String>()
            var failed = 0
            try {
                plan.moves.forEachIndexed { index, move ->
                    ensureActive()
                    val src = File(move.from)
                    val plannedDst = File(move.to)
                    val dir = plannedDst.parentFile
                    when {
                        !src.exists() -> {
                            failed++
                            errors += "Файл уже исчез: ${src.name}"
                        }
                        dir == null -> {
                            failed++
                            errors += "Неверный путь: ${move.to}"
                        }
                        else -> {
                            collectMissingDirs(dir, createdDirs)
                            if (!dir.isDirectory && !dir.mkdirs()) {
                                failed++
                                errors += "Не удалось создать ${StoragePaths.relative(dir.path)}"
                            } else {
                                // Something may have appeared there since the preview — never overwrite.
                                val dst = if (plannedDst.exists()) StoragePaths.uniqueTarget(dir, src.name, emptySet()) else plannedDst
                                if (moveFile(src, dst)) {
                                    records += MoveRecord(src.absolutePath, dst.absolutePath)
                                } else {
                                    failed++
                                    errors += "Не удалось переместить ${src.name}"
                                }
                            }
                        }
                    }
                    onProgress(index + 1, plan.moves.size)
                }
            } finally {
                // Even if cancelled halfway, remember what was moved so it can be rolled back.
                withContext(NonCancellable) {
                    if (records.isNotEmpty()) {
                        val batch = SortBatch(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            moves = records.toList(),
                            createdDirs = createdDirs.filter { File(it).isDirectory },
                        )
                        appendBatch(batch)
                        scanMedia(records.flatMap { listOf(it.from, it.to) })
                    }
                }
            }
            SortResult(moved = records.size, failed = failed, errors = errors.take(MAX_ERRORS))
        }

    private fun collectMissingDirs(dir: File, into: MutableSet<String>) {
        var d: File? = dir
        while (d != null && !d.exists() && d.path != StoragePaths.ROOT) {
            into += d.path
            d = d.parentFile
        }
    }

    private fun moveFile(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        return try {
            Files.move(src.toPath(), dst.toPath())
            true
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ history & undo

    suspend fun loadHistory(): List<SortBatch> = withContext(Dispatchers.IO) {
        fileLock.withLock { readHistoryUnlocked() }
    }

    private fun readHistoryUnlocked(): List<SortBatch> {
        val file = historyFile
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(historySerializer, file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun appendBatch(batch: SortBatch) {
        fileLock.withLock {
            val updated = (listOf(batch) + readHistoryUnlocked()).take(MAX_HISTORY)
            writeAtomic(historyFile, json.encodeToString(historySerializer, updated))
        }
    }

    /** Moves every file of the batch back. Files that can't be restored stay in the history. */
    suspend fun undo(batchId: String): SortResult = withContext(Dispatchers.IO) {
        val batch = loadHistory().firstOrNull { it.id == batchId }
            ?: return@withContext SortResult(0, 0, listOf("Запись в журнале не найдена"))

        var restored = 0
        val notRestored = mutableListOf<MoveRecord>()
        val errors = mutableListOf<String>()
        val touched = mutableListOf<String>()

        for (record in batch.moves.asReversed()) {
            val current = File(record.to)
            val original = File(record.from)
            val originalDir = original.parentFile
            if (!current.exists() || originalDir == null) {
                notRestored += record
                errors += "Нет файла: ${current.name}"
                continue
            }
            if (!originalDir.isDirectory) originalDir.mkdirs()
            val dst = if (original.exists()) StoragePaths.uniqueTarget(originalDir, original.name, emptySet()) else original
            if (moveFile(current, dst)) {
                restored++
                touched += current.path
                touched += dst.path
            } else {
                notRestored += record
                errors += "Не удалось вернуть: ${current.name}"
            }
        }

        // Remove folders the batch created, if they are empty now (deepest first).
        batch.createdDirs.sortedByDescending { it.length }.forEach { path ->
            val d = File(path)
            if (d.isDirectory && d.list()?.isEmpty() == true) d.delete()
        }

        fileLock.withLock {
            val history = readHistoryUnlocked().toMutableList()
            val index = history.indexOfFirst { it.id == batchId }
            if (index >= 0) {
                if (notRestored.isEmpty()) history.removeAt(index)
                else history[index] = batch.copy(moves = notRestored.asReversed())
                writeAtomic(historyFile, json.encodeToString(historySerializer, history))
            }
        }
        if (touched.isNotEmpty()) scanMedia(touched)
        SortResult(moved = restored, failed = notRestored.size, errors = errors.take(MAX_ERRORS))
    }

    // ------------------------------------------------------------------ helpers

    private fun scanMedia(paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    private companion object {
        const val MAX_HISTORY = 20
        const val MAX_ERRORS = 30
    }
}
