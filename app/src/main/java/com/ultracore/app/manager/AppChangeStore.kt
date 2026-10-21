package com.ultracore.app.manager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Background state before UltraCore restricted it, to restore exactly. */
@Serializable
data class BackgroundOriginal(val appop: String, val bucket: String)

/**
 * Everything UltraCore changed in apps (since 2.1). Used for «Вернуть всё», export and
 * «Вернуть телефон как было». Changes made by other tools or before 2.1 are not listed.
 */
@Serializable
data class AppChanges(
    val frozen: List<String> = emptyList(),
    val suspended: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val restricted: Map<String, BackgroundOriginal> = emptyMap(),
) {
    val isEmpty: Boolean get() = frozen.isEmpty() && suspended.isEmpty() && removed.isEmpty() && restricted.isEmpty()
    val total: Int get() = frozen.size + suspended.size + removed.size + restricted.size
}

/** filesDir/app_changes.json */
object AppChangeStore {
    private var file: File? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _state = MutableStateFlow(AppChanges())
    val state: StateFlow<AppChanges> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "app_changes.json")
        file = f
        _state.value = runCatching {
            if (f.exists()) json.decodeFromString(AppChanges.serializer(), f.readText()) else AppChanges()
        }.getOrDefault(AppChanges())
    }

    @Synchronized
    fun update(transform: (AppChanges) -> AppChanges) {
        val updated = transform(_state.value)
        _state.value = updated
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(AppChanges.serializer(), updated))
            tmp.renameTo(f)
        }
    }
}

/** Portable list of changes: export to a file, import on another phone or after a reset. */
@Serializable
data class UltraCoreProfile(
    val format: String = FORMAT,
    val version: Int = 1,
    val frozen: List<String> = emptyList(),
    val suspended: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val restricted: List<String> = emptyList(),
    val sandbox: List<String> = emptyList(),
) {
    val total: Int get() = frozen.size + suspended.size + removed.size + restricted.size + sandbox.size

    companion object {
        const val FORMAT = "ultracore-profile"
    }
}
