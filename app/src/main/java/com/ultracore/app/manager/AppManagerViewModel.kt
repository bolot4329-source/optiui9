package com.ultracore.app.manager

import android.app.Application
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.launcher.ProxyActivity
import com.ultracore.app.sandbox.SandboxManager
import com.ultracore.app.sandbox.SandboxState
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuMode
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of a batch: shown in a dialog so failures are never silent. */
data class BatchReport(val title: String, val done: Int, val failures: List<Pair<String, String>>, val note: String? = null)

class AppManagerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppManagerRepository(app)

    private val _apps = MutableStateFlow<List<ManagedApp>>(emptyList())
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(AppFilter.USER)
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** Text of the current long operation, or null. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _report = MutableStateFlow<BatchReport?>(null)
    val report: StateFlow<BatchReport?> = _report.asStateFlow()

    val sandbox: StateFlow<SandboxState> = SandboxManager.state

    val connected: StateFlow<Boolean> = ShizukuManager.state
        .map { it is ShizukuState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShizukuManager.isConnected)

    val rootMode: StateFlow<Boolean> = ShizukuManager.state
        .map { ShizukuManager.mode == ShizukuMode.ROOT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val visibleApps: StateFlow<List<ManagedApp>> = combine(_apps, _query, _filter, SandboxManager.state) { apps, q, f, sb ->
        val query = q.trim().lowercase()
        apps.filter { a ->
            val byFilter = when (f) {
                AppFilter.USER -> !a.isSystem && a.installed
                AppFilter.SYSTEM -> a.isSystem && a.installed
                AppFilter.FROZEN -> !a.enabled && a.installed
                AppFilter.REMOVED -> !a.installed
                AppFilter.SANDBOX -> a.packageName in sb.packages
            }
            byFilter && (query.isEmpty() || query in a.label.lowercase() || query in a.packageName.lowercase())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _apps.value = runCatching { repository.loadApps() }.getOrDefault(_apps.value)
            _selected.value = _selected.value.filter { pkg -> _apps.value.any { it.packageName == pkg } }.toSet()
            _loading.value = false
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFilter(f: AppFilter) {
        _filter.value = f
        _selected.value = emptySet()
    }

    fun toggleSelected(pkg: String) {
        _selected.value = _selected.value.let { if (pkg in it) it - pkg else it + pkg }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun dismissReport() {
        _report.value = null
    }

    fun app(pkg: String): ManagedApp? = _apps.value.firstOrNull { it.packageName == pkg }

    /** Runs [op] for every package; protected apps are skipped with a reason. */
    fun batch(op: BatchOp, packages: Collection<String>) {
        if (_busy.value != null || packages.isEmpty()) return
        viewModelScope.launch {
            val failures = mutableListOf<Pair<String, String>>()
            var done = 0
            var lastPath: String? = null
            val targets = packages.mapNotNull { app(it) }
            targets.forEachIndexed { index, app ->
                _busy.value = "${op.title}: ${index + 1} из ${targets.size}"
                val skip = when {
                    op in setOf(BatchOp.FREEZE, BatchOp.UNINSTALL) && app.protectedReason != null ->
                        "защищено: ${app.protectedReason}"
                    op == BatchOp.UNINSTALL && !app.isSystem ->
                        "это обычное приложение — удалите его через «Удалить» в настройках телефона"
                    else -> null
                }
                if (skip != null) {
                    failures += app.label to skip
                    return@forEachIndexed
                }
                val r = when (op) {
                    BatchOp.FREEZE -> repository.freeze(app.packageName)
                    BatchOp.UNFREEZE -> repository.unfreeze(app.packageName)
                    BatchOp.UNINSTALL -> repository.uninstallForUser(app.packageName)
                    BatchOp.RESTORE -> repository.restore(app.packageName)
                    BatchOp.BACKUP -> repository.backup(app).also { if (it.ok) lastPath = it.message }
                }
                if (r.ok) done++ else failures += app.label to r.message
            }
            _busy.value = null
            _selected.value = emptySet()
            val note = when {
                op == BatchOp.BACKUP && done > 0 -> "Файлы в папке Download/UltraCore/Backups" + (lastPath?.let { "\n$it" } ?: "")
                op == BatchOp.UNINSTALL && done > 0 -> "Вернуть можно во вкладке «Удалённые» → «Восстановить»"
                else -> null
            }
            _report.value = BatchReport(op.verb, done, failures, note)
            refresh()
        }
    }

    fun install(uri: Uri, options: InstallOptions) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Установка…"
            val r = repository.install(uri, options) { step -> _busy.value = step }
            _busy.value = null
            _report.value = if (r.ok) {
                BatchReport("Установлено", 1, emptyList())
            } else {
                BatchReport("Установка", 0, listOf("Файл" to r.message))
            }
            refresh()
        }
    }

    // ------------------------------------------------------------------ sandbox

    fun addToSandbox(pkg: String) = single("В песочницу") {
        val app = app(pkg)
        if (app?.protectedReason != null) OpResult(false, "защищено: ${app.protectedReason}") else SandboxManager.add(pkg)
    }

    fun removeFromSandbox(pkg: String) = single("Из песочницы") { SandboxManager.remove(pkg) }

    fun launchSandboxed(pkg: String) = single("Запуск") { SandboxManager.launch(getApplication(), pkg) }

    fun setSandboxDelay(seconds: Int) = SandboxManager.setDelay(seconds)

    fun pinSandboxShortcut(pkg: String, label: String) {
        val context = getApplication<Application>()
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            _report.value = BatchReport("Ярлык", 0, listOf(label to "Рабочий стол не поддерживает ярлыки"))
            return
        }
        viewModelScope.launch {
            val icon = withContext(Dispatchers.IO) {
                runCatching {
                    IconCompat.createWithBitmap(context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192))
                }.getOrElse { IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon) }
            }
            val shortcut = ShortcutInfoCompat.Builder(context, "sandbox:$pkg")
                .setShortLabel(label.take(25))
                .setLongLabel("$label (песочница)")
                .setIcon(icon)
                .setIntent(ProxyActivity.sandboxIntent(context, pkg))
                .build()
            runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
        }
    }

    private fun single(title: String, action: suspend () -> OpResult) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "$title…"
            val r = runCatching { action() }.getOrElse { OpResult(false, it.message ?: "Ошибка") }
            _busy.value = null
            if (!r.ok) _report.value = BatchReport(title, 0, listOf(title to r.message))
            refresh()
        }
    }
}
