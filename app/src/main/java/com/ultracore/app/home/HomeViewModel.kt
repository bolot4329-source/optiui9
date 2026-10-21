package com.ultracore.app.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.core.CapabilityChecker
import com.ultracore.app.core.CapabilityReport
import com.ultracore.app.manager.AppManagerRepository
import com.ultracore.app.manager.ChangeReverter
import com.ultracore.app.tweaks.TweaksRepository
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val checker = CapabilityChecker(app)

    val report: StateFlow<CapabilityReport?> = checker.report
    val shizukuState: StateFlow<ShizukuState> = ShizukuManager.state

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    init {
        // First check runs automatically as soon as Shizuku connects (and after system updates).
        viewModelScope.launch {
            ShizukuManager.state.collect { state ->
                if (state is ShizukuState.Connected && checker.needsCheck) runCheck()
            }
        }
    }

    private val _resetBusy = MutableStateFlow<String?>(null)
    val resetBusy: StateFlow<String?> = _resetBusy.asStateFlow()

    private val _resetReport = MutableStateFlow<String?>(null)
    val resetReport: StateFlow<String?> = _resetReport.asStateFlow()

    fun dismissResetReport() {
        _resetReport.value = null
    }

    /**
     * «Вернуть телефон как было»: undoes app changes (freeze, pause, removal, background limits,
     * sandbox) and every tweak. Meant to be pressed before uninstalling UltraCore.
     */
    fun resetEverything() {
        if (_resetBusy.value != null) return
        viewModelScope.launch {
            _resetBusy.value = "Приложения…"
            val apps = ChangeReverter.revertApps(AppManagerRepository(getApplication())) { _resetBusy.value = it }
            _resetBusy.value = "Твики…"
            val tweaks = TweaksRepository(getApplication()).restoreAll()
            _resetBusy.value = null
            val failures = apps.failures + tweaks
            _resetReport.value = if (failures.isEmpty()) {
                "Готово: всё, что изменил UltraCore, возвращено. Теперь его можно удалить."
            } else {
                "Возвращено: ${apps.done}. Не получилось:\n" + failures.joinToString("\n") { "• ${it.first}: ${it.second}" }
            }
        }
    }

    fun recheck() {
        viewModelScope.launch { runCheck() }
    }

    private suspend fun runCheck() {
        if (_checking.value) return
        _checking.value = true
        try {
            checker.check()
        } finally {
            _checking.value = false
        }
    }
}
