package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.LocalBottomBarInset
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.cleaner.CleanResult
import com.ultracore.app.cleaner.CleanerUiState
import com.ultracore.app.cleaner.CleanerViewModel
import com.ultracore.app.cleaner.JunkCategory
import com.ultracore.app.cleaner.JunkItem
import com.ultracore.app.cleaner.ScanProgress
import com.ultracore.app.cleaner.ScanResult
import com.ultracore.app.optimizer.formatBytes
import com.ultracore.app.optimizer.formatDuration
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import com.ultracore.app.storage.StoragePaths
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.PrimaryButton
import com.ultracore.app.ui.components.SecondaryButton
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

private const val MAX_SHOWN_PER_CATEGORY = 300

@Composable
fun CleanerScreen(viewModel: CleanerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()

    var storageGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) {
        storageGranted = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }

    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!storageGranted) {
            item { InfoBanner("Нет доступа к файлам — выдайте его в карточке сверху, иначе общая память не проверяется.") }
        }
        if (shizuku !is ShizukuState.Connected) {
            item { InfoBanner("Shizuku не подключён — Android/data и Android/obb проверены не будут.") }
        }

        when (val s = state) {
            CleanerUiState.Idle -> item { StartCard(onScan = viewModel::scan) }

            is CleanerUiState.Scanning -> item { ScanningCard(s.progress, onCancel = viewModel::cancel) }

            is CleanerUiState.Failed -> item {
                UltraCard {
                    Text("Ошибка: ${s.message}", color = NeonRed, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Сканировать снова", onClick = viewModel::scan)
                }
            }

            is CleanerUiState.Cleaning -> item { CleaningCard(s.done, s.total) }

            is CleanerUiState.Done -> item { DoneCard(s.result, onRescan = viewModel::scan, onClose = viewModel::reset) }

            is CleanerUiState.Results -> {
                val scan = s.scan
                item {
                    SummaryCard(
                        scan = scan,
                        selected = selected,
                        onDelete = { confirmDelete = true },
                        onRescan = viewModel::scan,
                    )
                }
                JunkCategory.entries.forEach { category ->
                    val list = scan.items[category].orEmpty()
                    val isExpanded = category in expanded
                    item(key = "category_${category.name}") {
                        CategoryCard(
                            category = category,
                            items = list,
                            selected = selected,
                            expanded = isExpanded,
                            onToggleAll = { viewModel.toggleCategory(category) },
                            onExpand = { viewModel.toggleExpanded(category) },
                        )
                    }
                    if (isExpanded && list.isNotEmpty()) {
                        items(list.take(MAX_SHOWN_PER_CATEGORY), key = { "item_" + it.path }) { junk ->
                            JunkRow(
                                item = junk,
                                checked = junk.path in selected,
                                onToggle = { viewModel.toggleItem(junk.path) },
                            )
                        }
                        if (list.size > MAX_SHOWN_PER_CATEGORY) {
                            item(key = "more_${category.name}") {
                                Text(
                                    text = "…и ещё ${list.size - MAX_SHOWN_PER_CATEGORY} (тоже будут учтены)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val results = state as? CleanerUiState.Results
    if (confirmDelete && results != null) {
        val chosen = results.scan.allItems.filter { it.path in selected }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardBlack,
            title = { Text("Удалить выбранное?", color = TextPrimary) },
            text = {
                Text(
                    text = "Будет удалено объектов: ${chosen.size} (${formatBytes(chosen.sumOf { it.sizeBytes })}).\n" +
                        "Это действие нельзя отменить.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSelected()
                }) { Text("Удалить", color = NeonRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun StartCard(onScan: () -> Unit) {
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Глубокая очистка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Сначала только поиск — ничего не удаляется, пока вы не подтвердите.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        JunkCategory.entries.forEach {
            Text("• ${it.title}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Сканировать", onClick = onScan, icon = Icons.Outlined.CleaningServices)
    }
}

@Composable
private fun ScanningCard(progress: ScanProgress, onCancel: () -> Unit) {
    UltraCard {
        Text(progress.stage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeonCyan)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = NeonCyan,
            trackColor = CardBorder,
        )
        Spacer(Modifier.height(8.dp))
        Text("Просмотрено файлов: ${progress.filesScanned}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (progress.currentPath.isNotEmpty()) {
            Text(
                text = progress.currentPath,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Остановить", onClick = onCancel)
    }
}

@Composable
private fun CleaningCard(done: Int, total: Int) {
    UltraCard {
        Text("Удаление… $done из $total", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) done.toFloat() / total else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = NeonCyan,
            trackColor = CardBorder,
        )
    }
}

@Composable
private fun DoneCard(result: CleanResult, onRescan: () -> Unit, onClose: () -> Unit) {
    UltraCard {
        Text("Готово", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NeonGreen)
        Spacer(Modifier.height(6.dp))
        Text(
            "Удалено: ${result.deleted} · освобождено ${formatBytes(result.freedBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
        if (result.failed > 0) {
            Text("Не удалось удалить: ${result.failed}", style = MaterialTheme.typography.bodySmall, color = NeonRed)
            result.errors.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Сканировать снова", onClick = onRescan)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Закрыть", onClick = onClose, color = TextSecondary)
    }
}

@Composable
private fun SummaryCard(
    scan: ScanResult,
    selected: Set<String>,
    onDelete: () -> Unit,
    onRescan: () -> Unit,
) {
    val all = scan.allItems
    val chosen = all.filter { it.path in selected }
    val chosenBytes = chosen.sumOf { it.sizeBytes }
    UltraCard {
        Text("Найдено", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Text(
            text = formatBytes(all.sumOf { it.sizeBytes }),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = NeonCyan,
        )
        Text(
            "Объектов: ${all.size} · поиск занял ${formatDuration(scan.durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        scan.warnings.forEach {
            Spacer(Modifier.height(4.dp))
            Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = NeonRed)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (chosen.isEmpty()) "Ничего не выбрано" else "Удалить выбранное · ${formatBytes(chosenBytes)}",
            onClick = onDelete,
            enabled = chosen.isNotEmpty(),
            icon = Icons.Outlined.DeleteForever,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Сканировать заново", onClick = onRescan, color = TextSecondary)
    }
}

@Composable
private fun CategoryCard(
    category: JunkCategory,
    items: List<JunkItem>,
    selected: Set<String>,
    expanded: Boolean,
    onToggleAll: () -> Unit,
    onExpand: () -> Unit,
) {
    val selectedCount = items.count { it.path in selected }
    val toggle = when {
        items.isEmpty() || selectedCount == 0 -> ToggleableState.Off
        selectedCount == items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(
                state = toggle,
                onClick = onToggleAll,
                enabled = items.isNotEmpty(),
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonCyan,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = AmoledBlack,
                ),
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = items.isNotEmpty(), onClick = onExpand),
            ) {
                Text(category.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = if (items.isEmpty()) "Ничего не найдено"
                    else "${items.size} шт. · ${formatBytes(items.sumOf { it.sizeBytes })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (items.isEmpty()) TextSecondary else NeonCyan,
                )
                Text(category.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (items.isNotEmpty()) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Показать",
                    tint = TextSecondary,
                    modifier = Modifier.clickable(onClick = onExpand),
                )
            }
        }
    }
}

@Composable
private fun JunkRow(item: JunkItem, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBlack,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonCyan,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = AmoledBlack,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = StoragePaths.relative(item.path),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.note?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (item.category == JunkCategory.EmptyFolders) "—" else formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
    }
}
