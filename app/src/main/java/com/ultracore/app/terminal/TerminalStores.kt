package com.ultracore.app.terminal

import android.content.Context
import com.ultracore.app.shizuku.ShellUserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

private val storeJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

private fun writeAtomic(file: File, text: String) {
    runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}

/** Command history, newest first, unique, at most [MAX] entries. filesDir/terminal_history.json */
class HistoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "terminal_history.json")
    private val serializer = ListSerializer(String.serializer())

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    @Synchronized
    fun add(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        val updated = (listOf(cmd) + _items.value.filterNot { it == cmd }).take(MAX)
        _items.value = updated
        writeAtomic(file, storeJson.encodeToString(serializer, updated))
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        writeAtomic(file, storeJson.encodeToString(serializer, emptyList()))
    }

    private fun read(): List<String> = runCatching {
        if (file.exists()) storeJson.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    companion object {
        const val MAX = 200
    }
}

/** User macros. filesDir/macros.json. A harmless example is created on first start. */
class MacroStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "macros.json")
    private val serializer = ListSerializer(Macro.serializer())

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<Macro>> = _items.asStateFlow()

    fun favorite(): Macro? = _items.value.firstOrNull { it.favorite }

    @Synchronized
    fun save(macro: Macro) {
        val others = _items.value.filterNot { it.id == macro.id }
            .map { if (macro.favorite) it.copy(favorite = false) else it }
        val index = _items.value.indexOfFirst { it.id == macro.id }
        val updated = if (index < 0) others + macro else others.toMutableList().apply { add(index.coerceAtMost(size), macro) }
        persist(updated)
    }

    @Synchronized
    fun delete(id: String) = persist(_items.value.filterNot { it.id == id })

    @Synchronized
    fun toggleFavorite(id: String) {
        val makeFavorite = _items.value.firstOrNull { it.id == id }?.favorite == false
        persist(_items.value.map { it.copy(favorite = makeFavorite && it.id == id) })
    }

    private fun persist(list: List<Macro>) {
        _items.value = list
        writeAtomic(file, storeJson.encodeToString(serializer, list))
    }

    private fun read(): List<Macro> {
        if (!file.exists()) {
            val seed = listOf(
                Macro(
                    id = "example-diagnostics",
                    name = "Диагностика",
                    commands = listOf(
                        "getprop ro.build.version.release",
                        "dumpsys battery | grep -i level",
                        "df -h /data",
                    ),
                ),
            )
            writeAtomic(file, storeJson.encodeToString(serializer, seed))
            return seed
        }
        return runCatching { storeJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }
}

/** Ready-made commands from assets/commands.json. */
object CommandBook {
    fun load(context: Context): List<CommandEntry> = runCatching {
        context.assets.open("commands.json").bufferedReader().use {
            storeJson.decodeFromString(ListSerializer(CommandEntry.serializer()), it.readText())
        }
    }.getOrDefault(emptyList())

    /** Inserts parameter values, each safely quoted for the shell. */
    fun fill(entry: CommandEntry, values: Map<String, String>): String =
        entry.params.fold(entry.command) { cmd, p ->
            val value = values[p.name].orEmpty().ifBlank { p.default }
            cmd.replace("{${p.name}}", ShellUserService.shellQuote(value))
        }
}
