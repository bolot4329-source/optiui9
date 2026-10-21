package com.ultracore.app.terminal

import android.content.Context
import android.widget.Toast
import com.ultracore.app.core.ErrorClassifier
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Runs a macro without the UI — used by the quick-settings tile. */
object MacroRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var running = false

    fun runInBackground(context: Context, macro: Macro) {
        if (running) {
            toast(context, "Макрос уже выполняется")
            return
        }
        running = true
        scope.launch {
            try {
                toast(context, "▶ ${macro.name}")
                ShizukuManager.refresh()
                val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    ShizukuManager.state.first { it is ShizukuState.Connected }
                } != null
                if (!connected) {
                    toast(context, "Shizuku не подключён — запустите Shizuku")
                    return@launch
                }
                for (command in macro.commands) {
                    val result = ShizukuManager.execResult(command, COMMAND_TIMEOUT_S)
                    if (!result.isSuccess) {
                        toast(context, "✗ ${macro.name}: ${ErrorClassifier.classify(result).message}\n$command")
                        return@launch
                    }
                }
                toast(context, "✓ ${macro.name}: выполнено")
            } catch (t: Throwable) {
                toast(context, "✗ ${macro.name}: ${t.message ?: "ошибка"}")
            } finally {
                running = false
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
    }

    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val COMMAND_TIMEOUT_S = 120
}
