package com.ultracore.app.sorter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.optimizer.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface SorterBusy {
    data object Planning : SorterBusy
    data class Moving(val done: Int, val total: Int) : SorterBusy
    data object Undoing : SorterBusy
}

/** Message shown after an operation. */
data class SorterMessage(val text: String, val success: Boolean, val details: List<String> = emptyList())

class SorterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SorterRepository(application)

    private val _rules = MutableStateFlow<List<SortRule>>(emptyList())
    val rules: StateFlow<List<SortRule>> = _rules.asStateFlow()

    private val _plan = MutableStateFlow<SortPlan?>(null)
    val plan: StateFlow<SortPlan?> = _plan.asStateFlow()

    private val _busy = MutableStateFlow<SorterBusy?>(null)
    val busy: StateFlow<SorterBusy?> = _busy.asStateFlow()

    private val _message = MutableStateFlow<SorterMessage?>(null)
    val message: StateFlow<SorterMessage?> = _message.asStateFlow()

    private val _history = MutableStateFlow<List<SortBatch>>(emptyList())
    val history: StateFlow<List<SortBatch>> = _history.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            _rules.value = repository.loadRules()
            _history.value = repository.loadHistory()
        }
    }

    // ------------------------------------------------------------------ rules

    fun newRuleTemplate(): SortRule = SortRule(
        id = UUID.randomUUID().toString(),
        name = "",
        sourceDir = "Download",
        extensions = emptyList(),
        targetDir = "Download/Sorted",
    )

    /** Returns an error text, or null when saved. */
    fun saveRule(rule: SortRule): String? {
        repository.validate(rule)?.let { return it }
        val current = _rules.value
        val updated = if (current.any { it.id == rule.id }) {
            current.map { if (it.id == rule.id) rule else it }
        } else {
            current + rule
        }
        updateRules(updated)
        return null
    }

    fun toggleRule(id: String) {
        updateRules(_rules.value.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteRule(id: String) {
        updateRules(_rules.value.filterNot { it.id == id })
    }

    fun resetRules() {
        updateRules(repository.defaultRules())
    }

    private fun updateRules(rules: List<SortRule>) {
        _rules.value = rules
        _plan.value = null // the preview no longer matches the rules
        viewModelScope.launch { repository.saveRules(rules) }
    }

    // ------------------------------------------------------------------ dry run & execute

    fun preview() {
        if (_busy.value != null) return
        job = viewModelScope.launch {
            _busy.value = SorterBusy.Planning
            _message.value = null
            try {
                _plan.value = repository.buildPlan(_rules.value)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _message.value = SorterMessage("Ошибка предпросмотра: ${t.message}", false)
            } finally {
                _busy.value = null
            }
        }
    }

    fun discardPlan() {
        _plan.value = null
    }

    fun execute() {
        val plan = _plan.value ?: return
        if (_busy.value != null || plan.moves.isEmpty()) return
        job = viewModelScope.launch {
            _busy.value = SorterBusy.Moving(0, plan.moves.size)
            try {
                val result = repository.execute(plan) { done, total ->
                    _busy.value = SorterBusy.Moving(done, total)
                }
                val bytes = plan.moves.sumOf { it.sizeBytes }
                _message.value = SorterMessage(
                    text = "Перемещено файлов: ${result.moved} (${formatBytes(bytes)})" +
                        if (result.failed > 0) " · ошибок: ${result.failed}" else "",
                    success = result.failed == 0,
                    details = result.errors,
                )
                _plan.value = null
            } catch (e: CancellationException) {
                _message.value = SorterMessage("Остановлено. Уже перемещённое можно откатить из журнала.", false)
                _plan.value = null
                throw e
            } finally {
                _busy.value = null
                withContext(NonCancellable) { _history.value = repository.loadHistory() }
            }
        }
    }

    fun undo(batchId: String) {
        if (_busy.value != null) return
        job = viewModelScope.launch {
            _busy.value = SorterBusy.Undoing
            try {
                val result = repository.undo(batchId)
                _message.value = SorterMessage(
                    text = "Возвращено файлов: ${result.moved}" +
                        if (result.failed > 0) " · не удалось: ${result.failed}" else "",
                    success = result.failed == 0,
                    details = result.errors,
                )
                _plan.value = null
            } finally {
                _busy.value = null
                withContext(NonCancellable) { _history.value = repository.loadHistory() }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun dismissMessage() {
        _message.value = null
    }
}
