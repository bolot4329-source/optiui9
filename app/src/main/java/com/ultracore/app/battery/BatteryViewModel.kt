package com.ultracore.app.battery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BatteryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BatteryRepository(app)

    private val _passport = MutableStateFlow(BatteryPassport())
    val passport: StateFlow<BatteryPassport> = _passport.asStateFlow()

    private var shellPart: BatteryRepository.ShellPart? = null
    private var publicPart: BatteryRepository.PublicPart? = null

    init {
        // Public values are cheap: refresh every 5 s while this screen's ViewModel lives.
        viewModelScope.launch {
            while (true) {
                publicPart = runCatching { repository.readPublic() }.getOrNull()
                publish()
                delay(PUBLIC_REFRESH_MS)
            }
        }
        // Shell values: once when Shizuku connects, then on «Обновить».
        viewModelScope.launch {
            ShizukuManager.state.collect { if (it is ShizukuState.Connected && shellPart == null) refreshShell() }
        }
    }

    fun refresh() {
        publicPart = runCatching { repository.readPublic() }.getOrNull()
        publish()
        refreshShell()
    }

    private fun refreshShell() {
        if (_passport.value.loadingShell) return
        viewModelScope.launch {
            _passport.update { it.copy(loadingShell = true) }
            shellPart = runCatching { repository.readShell() }
                .getOrElse { BatteryRepository.ShellPart(emptyList(), emptyList(), it.message ?: "Ошибка") }
            _passport.update { it.copy(loadingShell = false) }
            publish()
        }
    }

    private fun publish() {
        val pub = publicPart
        val shell = shellPart
        // Android API first; a shell value is added only if that title is not shown yet.
        // Exception: some firmware reports 0 through the API (e.g. cycle count) while the
        // real number is in dumpsys/sysfs — then the non-zero shell value replaces the zero.
        val values = buildList {
            pub?.values?.let { addAll(it) }
            shell?.values?.forEach { v ->
                val index = indexOfFirst { it.title == v.title }
                when {
                    index < 0 -> add(v)
                    this[index].value.trim() == "0" && v.value.trim() != "0" -> set(index, v)
                }
            }
        }
        _passport.update {
            it.copy(
                level = pub?.level,
                statusText = pub?.statusText.orEmpty(),
                values = values,
                extras = shell?.extras.orEmpty(),
                shellNote = shell?.note,
            )
        }
    }

    companion object {
        private const val PUBLIC_REFRESH_MS = 5_000L
    }
}
