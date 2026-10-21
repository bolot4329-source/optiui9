package com.ultracore.app.optimizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is running right now. [boost] is non-null only for the One-Tap mode. */
data class RunningState(
    val tool: OptimizerTool?,
    val boost: BoostProgress? = null,
)

data class BoostProgress(
    val stepIndex: Int,          // 0-based
    val totalSteps: Int,
    val stepTitle: String,
)

data class CompileProgress(val current: Int, val total: Int, val packageName: String) {
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
}

class OptimizerViewModel : ViewModel() {

    private val repository = SystemOptimizerRepository()

    val connected: StateFlow<Boolean> = ShizukuManager.state
        .map { it is ShizukuState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShizukuManager.state.value is ShizukuState.Connected)

    private val _telemetry = MutableStateFlow(Telemetry(null, null))
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    private val _running = MutableStateFlow<RunningState?>(null)
    val running: StateFlow<RunningState?> = _running.asStateFlow()

    private val _compileProgress = MutableStateFlow<CompileProgress?>(null)
    val compileProgress: StateFlow<CompileProgress?> = _compileProgress.asStateFlow()

    private val _results = MutableStateFlow<Map<OptimizerTool, OptimizationResult>>(emptyMap())
    val results: StateFlow<Map<OptimizerTool, OptimizationResult>> = _results.asStateFlow()

    private val _boostSummary = MutableStateFlow<List<OptimizationResult>?>(null)
    val boostSummary: StateFlow<List<OptimizationResult>?> = _boostSummary.asStateFlow()

    val log: StateFlow<List<ShellLogEntry>> = repository.log

    private var job: Job? = null

    init {
        refreshTelemetry()
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            _telemetry.value = repository.readTelemetry()
        }
    }

    fun clearLog() = repository.clearLog()

    /** Runs a single module from its card. */
    fun runTool(tool: OptimizerTool) {
        if (_running.value != null) return
        job = viewModelScope.launch {
            _running.value = RunningState(tool)
            try {
                val result = runModule(tool)
                saveResult(result)
            } catch (e: CancellationException) {
                repository.note("${tool.title}: остановлено пользователем${progressSuffix()}", success = false)
                throw e
            } finally {
                _running.value = null
                _compileProgress.value = null
                refreshTelemetry()
            }
        }
    }

    /** One tap = only what really helps: TRIM of the storage → background app optimization. */
    fun runUltraBoost() {
        if (_running.value != null) return
        val steps = listOf(
            OptimizerTool.Trim,
            OptimizerTool.DexoptJob,
        )
        job = viewModelScope.launch {
            _boostSummary.value = null
            val collected = mutableListOf<OptimizationResult>()
            repository.note("▶ Обслуживание: старт")
            try {
                steps.forEachIndexed { index, tool ->
                    _running.value = RunningState(
                        tool = tool,
                        boost = BoostProgress(index, steps.size, tool.title),
                    )
                    val result = runModule(tool)
                    saveResult(result)
                    collected += result
                }
                repository.note("■ Обслуживание завершено")
            } catch (e: CancellationException) {
                repository.note("Обслуживание остановлено пользователем${progressSuffix()}", success = false)
                throw e
            } finally {
                _boostSummary.value = collected.toList()
                _running.value = null
                _compileProgress.value = null
                refreshTelemetry()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun runModule(tool: OptimizerTool): OptimizationResult = when (tool) {
        OptimizerTool.TrimCaches -> repository.trimCaches()
        OptimizerTool.Trim -> repository.trim()
        OptimizerTool.DexoptJob -> repository.runDexoptJob()
        OptimizerTool.ArtCompile -> runCompile()
    }

    private suspend fun runCompile(): OptimizationResult {
        var finished: OptimizationResult? = null
        repository.compileUserApps().collect { event ->
            when (event) {
                is CompileEvent.Progress ->
                    _compileProgress.value = CompileProgress(event.current, event.total, event.packageName)
                is CompileEvent.Finished -> finished = event.result
            }
        }
        _compileProgress.value = null
        return finished ?: OptimizationResult(OptimizerTool.ArtCompile, false, "Компиляция не завершена")
    }

    private fun progressSuffix(): String =
        _compileProgress.value?.let { " (ART: ${it.current} из ${it.total})" }.orEmpty()

    private fun saveResult(result: OptimizationResult) {
        _results.update { it + (result.tool to result) }
    }
}
