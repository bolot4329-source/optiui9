package com.ultracore.app.tweaks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TweakId { ANIMATIONS, REFRESH, PHANTOM, DNS }

data class TweaksState(
    val connected: Boolean = false,
    val animationScale: String? = null,
    val refreshRates: List<Int> = emptyList(),
    val refreshLock: Int? = null,
    val currentRefresh: Int? = null,
    val phantomDisabled: Boolean? = null,
    val dnsMode: String? = null,
    val dnsHost: String? = null,
    val canReset: Set<TweakId> = emptySet(),
    val busy: Set<TweakId> = emptySet(),
    val messages: Map<TweakId, TweakMessage> = emptyMap(),
)

class TweaksViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = TweaksRepository(app)

    private val _state = MutableStateFlow(TweaksState(refreshRates = repository.supportedRefreshRates()))
    val state: StateFlow<TweaksState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ShizukuManager.state.collect { s ->
                val connected = s is ShizukuState.Connected
                _state.update { it.copy(connected = connected) }
                if (connected) reload()
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            if (!ShizukuManager.isConnected) return@launch
            val anim = runCatching { repository.readAnimationScale() }.getOrNull()
            val lock = runCatching { repository.readRefreshLock() }.getOrNull()
            val phantom = runCatching { repository.readPhantomDisabled() }.getOrNull()
            val dns = runCatching { repository.readDns() }.getOrNull()
            _state.update {
                it.copy(
                    animationScale = anim,
                    refreshLock = lock,
                    currentRefresh = repository.currentRefreshRate(),
                    phantomDisabled = phantom,
                    dnsMode = dns?.mode,
                    dnsHost = dns?.host,
                    canReset = resettable(),
                )
            }
        }
    }

    fun setAnimationScale(scale: String) = run(TweakId.ANIMATIONS) { repository.setAnimationScale(scale) }
    fun resetAnimations() = run(TweakId.ANIMATIONS) { repository.resetAnimations() }

    fun lockRefreshRate(hz: Int) = run(TweakId.REFRESH) { repository.lockRefreshRate(hz) }
    fun resetRefreshRate() = run(TweakId.REFRESH) { repository.resetRefreshRate() }

    fun disablePhantomKiller() = run(TweakId.PHANTOM) { repository.disablePhantomKiller() }
    fun resetPhantomKiller() = run(TweakId.PHANTOM) { repository.resetPhantomKiller() }

    fun setDnsMode(mode: String) = run(TweakId.DNS) { repository.setDnsMode(mode) }
    fun setDnsHost(host: String) = run(TweakId.DNS) { repository.setDnsHost(host) }
    fun resetDns() = run(TweakId.DNS) { repository.resetDns() }

    private fun run(id: TweakId, action: suspend () -> TweakMessage) {
        if (id in _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = it.busy + id) }
            val message = runCatching { action() }
                .getOrElse { TweakMessage(it.message ?: "Ошибка", MessageKind.ERROR) }
            _state.update { it.copy(busy = it.busy - id, messages = it.messages + (id to message)) }
            reload()
        }
    }

    private fun resettable(): Set<TweakId> = buildSet {
        if (repository.hasAnimationBackup()) add(TweakId.ANIMATIONS)
        if (repository.hasRefreshBackup()) add(TweakId.REFRESH)
        if (repository.hasPhantomBackup()) add(TweakId.PHANTOM)
        if (repository.hasDnsBackup()) add(TweakId.DNS)
    }
}
