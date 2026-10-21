package com.ultracore.app.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.core.CapabilityChecker
import com.ultracore.app.core.CapabilityReport
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
