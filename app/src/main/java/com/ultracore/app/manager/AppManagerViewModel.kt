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

    /** What UltraCore changed (for badges and «Вернуть всё»). */
    val changes: StateFlow<AppChanges> = AppChangeStore.state

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
                AppFilter.FROZEN -> (!a.enabled || a.suspended) && a.installed
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
                    op in setOf(BatchOp.FREEZE, BatchOp.UNINSTALL, BatchOp.SUSPEND, BatchOp.RESTRICT_BG) &&
                        app.protectedReason != null ->
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
                    BatchOp.SUSPEND -> repository.suspendApp(app.packageName)
                    BatchOp.UNSUSPEND -> repository.unsuspendApp(app.packageName)
                    BatchOp.RESTRICT_BG -> repository.restrictBackground(app.packageName)
                    BatchOp.ALLOW_BG -> repository.allowBackground(app.packageName)
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
                op == BatchOp.RESTRICT_BG && done > 0 -> "Приложения не будут работать в фоне, но уведомления продолжат приходить"
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

    fun setSandboxMode(mode: String) = SandboxManager.setMode(mode)

    fun wakeAllSandbox() = longTask("Разморозить все") { SandboxManager.wakeAll() }

    fun sleepAllSandbox() = longTask("Заморозить все") { SandboxManager.sleepAll() }

    /** Undoes everything UltraCore changed in apps. */
    fun revertAll() {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Возвращаю…"
            val r = ChangeReverter.revertApps(repository) { _busy.value = it }
            _busy.value = null
            _report.value = BatchReport("Возвращено", r.done, r.failures)
            refresh()
        }
    }

    // ------------------------------------------------------------------ export / import

    private val profileJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Current list of UltraCore's changes + sandbox, as a portable profile. */
    fun exportProfile(uri: Uri) {
        val c = AppChangeStore.state.value
        // Sandboxed apps are listed only under «sandbox»: they are also in the freeze/pause lists,
        // and importing them twice would apply two operations to the same app.
        val sb = SandboxManager.state.value.packages.toSet()
        val profile = UltraCoreProfile(
            frozen = c.frozen - sb,
            suspended = c.suspended - sb,
            removed = c.removed - sb,
            restricted = c.restricted.keys.filter { it !in sb },
            sandbox = sb.toList(),
        )
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(profileJson.encodeToString(UltraCoreProfile.serializer(), profile).toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            _report.value = if (ok) {
                BatchReport("Список сохранён", profile.total, emptyList(), "Приложений в списке: ${profile.total}")
            } else {
                BatchReport("Экспорт", 0, listOf("Файл" to "Не удалось записать файл"))
            }
        }
    }

    private val _pendingImport = MutableStateFlow<UltraCoreProfile?>(null)
    val pendingImport: StateFlow<UltraCoreProfile?> = _pendingImport.asStateFlow()

    fun readProfile(uri: Uri) {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        profileJson.decodeFromString(UltraCoreProfile.serializer(), it.readBytes().decodeToString())
                    }
                }.getOrNull()
            }
            if (profile == null || profile.format != UltraCoreProfile.FORMAT) {
                _report.value = BatchReport("Импорт", 0, listOf("Файл" to "Это не список UltraCore"))
            } else {
                _pendingImport.value = profile
            }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    /** Applies an imported profile; apps missing on this phone and protected apps are skipped. */
    fun applyImport() {
        val profile = _pendingImport.value ?: return
        _pendingImport.value = null
        if (_busy.value != null) return
        viewModelScope.launch {
            val failures = mutableListOf<Pair<String, String>>()
            var done = 0
            val steps = profile.removed.map { it to BatchOp.UNINSTALL } +
                profile.frozen.map { it to BatchOp.FREEZE } +
                profile.suspended.map { it to BatchOp.SUSPEND } +
                profile.restricted.map { it to BatchOp.RESTRICT_BG }
            steps.forEachIndexed { i, (pkg, op) ->
                _busy.value = "Применяю: ${i + 1} из ${steps.size + profile.sandbox.size}"
                val app = app(pkg)
                val r = when {
                    app == null -> OpResult(false, "нет на этом телефоне")
                    app.protectedReason != null -> OpResult(false, "защищено: ${app.protectedReason}")
                    op == BatchOp.UNINSTALL && !app.installed -> OpResult(true, "уже удалено")
                    op == BatchOp.UNINSTALL -> repository.uninstallForUser(pkg)
                    op == BatchOp.FREEZE -> repository.freeze(pkg)
                    op == BatchOp.SUSPEND -> repository.suspendApp(pkg)
                    else -> repository.restrictBackground(pkg)
                }
                if (r.ok) done++ else failures += (app?.label ?: pkg) to "${op.title}: ${r.message}"
            }
            profile.sandbox.forEachIndexed { i, pkg ->
                _busy.value = "Песочница: ${i + 1} из ${profile.sandbox.size}"
                val app = app(pkg)
                val r = when {
                    app == null -> OpResult(false, "нет на этом телефоне")
                    app.protectedReason != null -> OpResult(false, "защищено: ${app.protectedReason}")
                    else -> SandboxManager.add(pkg)
                }
                if (r.ok) done++ else failures += (app?.label ?: pkg) to "Песочница: ${r.message}"
            }
            _busy.value = null
            _report.value = BatchReport("Список применён", done, failures)
            refresh()
        }
    }

    private fun longTask(title: String, action: suspend () -> List<Pair<String, String>>) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "$title…"
            val failures = runCatching { action() }.getOrElse { listOf(title to (it.message ?: "Ошибка")) }
            _busy.value = null
            if (failures.isNotEmpty()) _report.value = BatchReport(title, 0, failures)
            refresh()
        }
    }

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
