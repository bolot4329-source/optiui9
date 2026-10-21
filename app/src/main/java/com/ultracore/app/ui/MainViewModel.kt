package com.ultracore.app.ui

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ShellTestState {
    data object Idle : ShellTestState
    data object Running : ShellTestState
    data class Success(val output: String) : ShellTestState
    data class Failure(val message: String) : ShellTestState
}

class MainViewModel : ViewModel() {

    val shizukuState: StateFlow<ShizukuState> = ShizukuManager.state

    private val _storageGranted = MutableStateFlow(Environment.isExternalStorageManager())
    val storageGranted: StateFlow<Boolean> = _storageGranted.asStateFlow()

    private val _shellTest = MutableStateFlow<ShellTestState>(ShellTestState.Idle)
    val shellTest: StateFlow<ShellTestState> = _shellTest.asStateFlow()

    /** Called on every onResume: the user may have just granted something in Settings/Shizuku. */
    fun refreshPermissions() {
        _storageGranted.value = Environment.isExternalStorageManager()
        ShizukuManager.refresh()
    }

    fun requestShizukuPermission() = ShizukuManager.requestPermission()

    fun reconnectShizuku() = ShizukuManager.reconnect()

    fun runShellTest() {
        if (_shellTest.value is ShellTestState.Running) return
        viewModelScope.launch {
            _shellTest.value = ShellTestState.Running
            _shellTest.value = runCatching { ShizukuManager.exec(SHELL_TEST_COMMAND) }
                .fold(
                    onSuccess = { ShellTestState.Success(it.ifBlank { "(пустой вывод)" }) },
                    onFailure = { ShellTestState.Failure(it.message ?: it.javaClass.simpleName) },
                )
        }
    }

    companion object {
        const val SHELL_TEST_COMMAND =
            "id && getprop ro.build.version.release && getprop ro.build.display.id"
    }
}
