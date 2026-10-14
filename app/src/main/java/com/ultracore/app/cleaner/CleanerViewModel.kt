package com.ultracore.app.cleaner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CleanerUiState {
    data object Idle : CleanerUiState
    data class Scanning(val progress: ScanProgress) : CleanerUiState
    data class Results(val scan: ScanResult) : CleanerUiState
    data class Cleaning(val done: Int, val total: Int) : CleanerUiState
    data class Done(val result: CleanResult) : CleanerUiState
    data class Failed(val message: String) : CleanerUiState
}

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CleanerRepository(application)

    private val _state = MutableStateFlow<CleanerUiState>(CleanerUiState.Idle)
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    /** Selected item paths. */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _expanded = MutableStateFlow<Set<JunkCategory>>(emptySet())
    val expanded: StateFlow<Set<JunkCategory>> = _expanded.asStateFlow()

    private var job: Job? = null

    private val busy: Boolean
        get() = _state.value is CleanerUiState.Scanning || _state.value is CleanerUiState.Cleaning

    fun scan() {
        if (busy) return
        job = viewModelScope.launch {
            _state.value = CleanerUiState.Scanning(ScanProgress("Подготовка", 0, ""))
            try {
                val result = repository.scan { progress ->
                    _state.value = CleanerUiState.Scanning(progress)
                }
                // Everything found is pre-selected; the user can uncheck before deleting.
                _selected.value = result.allItems.map { it.path }.toSet()
                _expanded.value = emptySet()
                _state.value = CleanerUiState.Results(result)
            } catch (e: CancellationException) {
                _state.value = CleanerUiState.Idle
                throw e
            } catch (t: Throwable) {
                _state.value = CleanerUiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun toggleItem(path: String) {
        _selected.update { if (path in it) it - path else it + path }
    }

    fun toggleCategory(category: JunkCategory) {
        val scan = (_state.value as? CleanerUiState.Results)?.scan ?: return
        val paths = scan.items[category].orEmpty().map { it.path }
        if (paths.isEmpty()) return
        _selected.update { current ->
            if (paths.all { it in current }) current - paths.toSet() else current + paths
        }
    }

    fun toggleExpanded(category: JunkCategory) {
        _expanded.update { if (category in it) it - category else it + category }
    }

    fun deleteSelected() {
        val scan = (_state.value as? CleanerUiState.Results)?.scan ?: return
        val chosen = _selected.value
        val items = scan.allItems.filter { it.path in chosen }
        if (items.isEmpty()) return
        job = viewModelScope.launch {
            _state.value = CleanerUiState.Cleaning(0, items.size)
            try {
                val result = repository.delete(items) { done, total ->
                    _state.value = CleanerUiState.Cleaning(done, total)
                }
                _selected.value = emptySet()
                _state.value = CleanerUiState.Done(result)
            } catch (e: CancellationException) {
                _state.value = CleanerUiState.Idle
                throw e
            } catch (t: Throwable) {
                _state.value = CleanerUiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun reset() {
        if (busy) return
        _selected.value = emptySet()
        _state.value = CleanerUiState.Idle
    }
}
