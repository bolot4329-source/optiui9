package com.ultracore.app.launcher

import android.app.Application
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracore.app.shizuku.ShizukuManager
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

data class ScanProgress(val done: Int, val total: Int)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LauncherRepository(app)
    private val favorites = FavoritesStore(app)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _progress = MutableStateFlow<ScanProgress?>(null)
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(LauncherFilter.USER)
    val filter: StateFlow<LauncherFilter> = _filter.asStateFlow()

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val favoriteItems: StateFlow<List<FavoriteActivity>> = favorites.items

    val rootMode: StateFlow<Boolean> = ShizukuManager.state
        .map { ShizukuManager.mode == com.ultracore.app.shizuku.ShizukuMode.ROOT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Apps after search and filter; activities inside are filtered too when the query matches a class. */
    val visibleApps: StateFlow<List<AppEntry>> = combine(_apps, _query, _filter) { apps, q, f ->
        val query = q.trim().lowercase()
        apps.mapNotNull { app ->
            val byFilter = when (f) {
                LauncherFilter.ALL -> app.activities
                LauncherFilter.USER -> if (app.isSystem) emptyList() else app.activities
                LauncherFilter.SYSTEM -> if (app.isSystem) app.activities else emptyList()
                LauncherFilter.HIDDEN -> app.activities.filter { !it.exported }
            }
            if (byFilter.isEmpty()) return@mapNotNull null
            if (query.isEmpty()) return@mapNotNull app.copy(activities = byFilter)
            val appMatches = query in app.label.lowercase() || query in app.packageName.lowercase()
            val acts = if (appMatches) byFilter else byFilter.filter { query in it.className.lowercase() }
            if (acts.isEmpty()) null else app.copy(activities = acts)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        rescan()
    }

    fun rescan() {
        if (_progress.value != null) return
        viewModelScope.launch {
            _progress.value = ScanProgress(0, 0)
            val list = runCatching {
                repository.scan { done, total -> _progress.value = ScanProgress(done, total) }
            }.getOrElse {
                _message.value = "Не удалось получить список приложений: ${it.message}"
                emptyList()
            }
            _apps.value = list
            _progress.value = null
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFilter(f: LauncherFilter) {
        _filter.value = f
    }

    fun toggleExpanded(pkg: String) {
        _expanded.value = _expanded.value.let { if (pkg in it) it - pkg else it + pkg }
    }

    fun activityLabel(entry: ActivityEntry): String? = repository.activityLabel(entry)

    fun isFavorite(entry: ActivityEntry) = favorites.isFavorite(entry.packageName, entry.className)

    fun toggleFavorite(entry: ActivityEntry, appLabel: String) {
        favorites.toggle(
            FavoriteActivity(entry.packageName, entry.className, activityLabel(entry) ?: entry.shortName, appLabel),
        )
    }

    fun launch(pkg: String, cls: String) {
        viewModelScope.launch {
            val r = ActivityLauncher.launch(getApplication(), pkg, cls)
            if (!r.ok) _message.value = r.message
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Pins a home-screen shortcut that opens the screen through [ProxyActivity]. */
    fun pinShortcut(pkg: String, cls: String, label: String) {
        val context = getApplication<Application>()
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            _message.value = "Рабочий стол не поддерживает добавление ярлыков"
            return
        }
        viewModelScope.launch {
            val icon = withContext(Dispatchers.IO) {
                runCatching {
                    IconCompat.createWithBitmap(context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192))
                }.getOrElse { IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon) }
            }
            val shortcut = ShortcutInfoCompat.Builder(context, "act:$pkg/$cls")
                .setShortLabel(label.take(25))
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(ProxyActivity.intent(context, pkg, cls))
                .build()
            val ok = runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }.getOrDefault(false)
            _message.value = if (ok) "Подтвердите добавление ярлыка на рабочий стол" else "Не удалось создать ярлык"
        }
    }
}
