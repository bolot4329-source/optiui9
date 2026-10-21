package com.ultracore.app.sandbox

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SandboxState(
    /** Apps kept frozen; unfrozen only while the user uses them. */
    val packages: List<String> = emptyList(),
    /** Unfrozen right now and waiting to be frozen again (survives app restarts). */
    val pending: List<String> = emptyList(),
    /** Delay after the screen turns off: 0, 30 or 120 seconds. */
    val delaySeconds: Int = 30,
    /** How sandboxed apps sleep: [MODE_SUSPEND] (icon stays, grey) or [MODE_DISABLE] (icon hidden). */
    val mode: String = MODE_SUSPEND,
) {
    companion object {
        const val MODE_SUSPEND = "suspend"
        const val MODE_DISABLE = "disable"
    }
}

/** filesDir/sandbox.json */
class SandboxStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "sandbox.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SandboxState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (SandboxState) -> SandboxState) {
        val updated = transform(_state.value)
        _state.value = updated
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SandboxState.serializer(), updated))
            tmp.renameTo(file)
        }
    }

    private fun read(): SandboxState = runCatching {
        if (file.exists()) json.decodeFromString(SandboxState.serializer(), file.readText()) else SandboxState()
    }.getOrDefault(SandboxState())
}
