package com.ultracore.app.terminal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.shizuku.ShellMarkers
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val history = HistoryStore(app)
    private val macroStore = MacroStore(app)

    val commands: List<CommandEntry> = CommandBook.load(app)
    val macros: StateFlow<List<Macro>> = macroStore.items

    val connected: StateFlow<Boolean> = ShizukuManager.state
        .map { it is ShizukuState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShizukuManager.state.value is ShizukuState.Connected)

    private val _lines = MutableStateFlow<List<TermLine>>(emptyList())
    val lines: StateFlow<List<TermLine>> = _lines.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** Up to 5 history entries starting with what is typed. */
    val suggestions: StateFlow<List<String>> = combine(_input, history.items) { text, items ->
        val t = text.trim()
        if (t.isEmpty()) emptyList() else items.filter { it.startsWith(t) && it != t }.take(5)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var job: Job? = null
    private var nextId = 0L
    private val pending = ArrayList<TermLine>()

    init {
        // Output arrives line by line; publish it in batches so the screen stays smooth.
        viewModelScope.launch {
            while (true) {
                delay(FLUSH_MS)
                flush()
            }
        }
    }

    fun setInput(text: String) {
        _input.value = text
    }

    fun submit() {
        val command = _input.value.trim()
        if (command.isEmpty() || _running.value) return
        _input.value = ""
        run(listOf(command))
    }

    /** Runs commands one by one; stops at the first error. */
    fun run(commands: List<String>, title: String? = null) {
        if (_running.value || commands.isEmpty()) return
        _running.value = true
        job = viewModelScope.launch {
            try {
                title?.let { append("▶ Макрос «$it»", LineKind.INFO) }
                commands.forEachIndexed { index, command ->
                    history.add(command)
                    append("$ $command", LineKind.INPUT)
                    val code = runOne(command)
                    if (code != 0 && index < commands.lastIndex) {
                        append("Макрос остановлен: команда завершилась с ошибкой", LineKind.ERROR)
                        return@launch
                    }
                }
            } finally {
                flush()
                _running.value = false
            }
        }
    }

    private suspend fun runOne(command: String): Int {
        if (!ShizukuManager.isConnected) {
            append("Shizuku не подключён", LineKind.ERROR)
            return -1
        }
        var exit: Int? = null
        try {
            ShizukuManager.stream(command).collect { line ->
                when {
                    line.startsWith(ShellMarkers.EXIT_CODE) ->
                        exit = line.removePrefix(ShellMarkers.EXIT_CODE).trim().toIntOrNull()
                    line.startsWith(ShellMarkers.LAUNCH_ERROR) -> {
                        append(line.removePrefix(ShellMarkers.PREFIX).trim(), LineKind.ERROR)
                        exit = -1
                    }
                    else -> append(line, LineKind.OUTPUT)
                }
            }
        } catch (e: CancellationException) {
            append("⏹ Остановлено", LineKind.INFO)
            throw e
        } catch (t: Throwable) {
            append("Ошибка: ${t.message ?: t.javaClass.simpleName}", LineKind.ERROR)
            return -1
        }
        val code = exit
        when {
            code == null -> append("Команда прервана (лимит 30 минут или обрыв связи)", LineKind.ERROR)
            code != 0 -> append("Код выхода $code", LineKind.ERROR)
        }
        return code ?: -1
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        pending.clear()
        _lines.value = emptyList()
    }

    fun allText(): String = _lines.value.joinToString("\n") { it.text }

    fun export(uri: Uri) {
        val text = allText()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            append(if (ok) "Вывод сохранён в файл" else "Не удалось сохранить файл", if (ok) LineKind.INFO else LineKind.ERROR)
            flush()
        }
    }

    // ---------- macros ----------

    fun saveMacro(id: String?, name: String, commandsText: String, favorite: Boolean): String? {
        val commands = commandsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (name.isBlank()) return "Введите название"
        if (commands.isEmpty()) return "Добавьте хотя бы одну команду (по одной в строке)"
        macroStore.save(Macro(id ?: UUID.randomUUID().toString(), name.trim(), commands, favorite))
        return null
    }

    fun deleteMacro(id: String) = macroStore.delete(id)

    fun toggleFavorite(id: String) = macroStore.toggleFavorite(id)

    private fun append(text: String, kind: LineKind) {
        pending.add(TermLine(nextId++, text, kind))
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        _lines.update { (it + batch).takeLast(MAX_LINES) }
    }

    companion object {
        private const val FLUSH_MS = 80L
        private const val MAX_LINES = 3_000
    }
}
