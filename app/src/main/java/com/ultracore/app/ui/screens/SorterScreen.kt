package com.ultracore.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.optimizer.formatBytes
import com.ultracore.app.sorter.PlannedMove
import com.ultracore.app.sorter.SortBatch
import com.ultracore.app.sorter.SortPlan
import com.ultracore.app.sorter.SortRule
import com.ultracore.app.sorter.SorterBusy
import com.ultracore.app.sorter.SorterMessage
import com.ultracore.app.sorter.SorterViewModel
import com.ultracore.app.storage.StoragePaths
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.PrimaryButton
import com.ultracore.app.ui.components.SecondaryButton
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_SHOWN_MOVES = 300

@Composable
fun SorterScreen(viewModel: SorterViewModel = viewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var storageGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) {
        storageGranted = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }

    var editing by remember { mutableStateOf<SortRule?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var undoTarget by remember { mutableStateOf<SortBatch?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val currentPlan = plan
    val currentBusy = busy

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!storageGranted) {
            item { InfoBanner("Нет доступа к файлам — выдайте его в карточке сверху, иначе сортировка не сработает.") }
        }

        message?.let { msg ->
            item { MessageCard(msg, onDismiss = viewModel::dismissMessage) }
        }

        if (currentBusy != null) {
            item { BusyCard(currentBusy, onCancel = viewModel::cancel) }
        } else if (currentPlan != null) {
            item {
                PlanCard(
                    plan = currentPlan,
                    onExecute = viewModel::execute,
                    onDiscard = viewModel::discardPlan,
                )
            }
            items(currentPlan.moves.take(MAX_SHOWN_MOVES), key = { "move_" + it.from }) { move ->
                MoveRow(move)
            }
            if (currentPlan.moves.size > MAX_SHOWN_MOVES) {
                item {
                    Text(
                        "…и ещё ${currentPlan.moves.size - MAX_SHOWN_MOVES} файлов",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        } else {
            item {
                UltraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Умная сортировка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                "Сначала предпросмотр — файлы не трогаются, пока вы не нажмёте «Выполнить». Любую сортировку можно откатить.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Предпросмотр (Dry Run)",
                        onClick = viewModel::preview,
                        enabled = storageGranted && rules.any { it.enabled },
                        icon = Icons.Outlined.Visibility,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Правила",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmReset = true }, enabled = currentBusy == null) {
                    Text("Сбросить", color = TextSecondary)
                }
                TextButton(
                    onClick = {
                        editing = viewModel.newRuleTemplate()
                        editingIsNew = true
                    },
                    enabled = currentBusy == null,
                ) {
                    Text("+ Добавить", color = NeonCyan)
                }
            }
        }

        items(rules, key = { "rule_" + it.id }) { rule ->
            RuleCard(
                rule = rule,
                enabled = currentBusy == null,
                onToggle = { viewModel.toggleRule(rule.id) },
                onEdit = {
                    editing = rule
                    editingIsNew = false
                },
            )
        }

        item {
            Text(
                "Журнал операций",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    "Пока пусто. После сортировки здесь появится запись, которую можно откатить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            items(history, key = { "batch_" + it.id }) { batch ->
                HistoryCard(batch, enabled = currentBusy == null, onUndo = { undoTarget = batch })
            }
        }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            isNew = editingIsNew,
            onSave = { updated ->
                val error = viewModel.saveRule(updated)
                if (error == null) editing = null
                error
            },
            onDelete = {
                viewModel.deleteRule(rule.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    undoTarget?.let { batch ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            containerColor = CardBlack,
            title = { Text("Откатить сортировку?", color = TextPrimary) },
            text = {
                Text(
                    "${batch.moves.size} файлов вернутся на прежние места. Если там уже есть файл с тем же именем, к имени добавится номер.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    undoTarget = null
                    viewModel.undo(batch.id)
                }) { Text("Откатить", color = NeonCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { undoTarget = null }) { Text("Отмена", color = TextSecondary) }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = CardBlack,
            title = { Text("Сбросить правила?", color = TextPrimary) },
            text = { Text("Ваши правила заменятся стандартным набором. Файлы и журнал не затрагиваются.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetRules()
                }) { Text("Сбросить", color = NeonRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun MessageCard(message: SorterMessage, onDismiss: () -> Unit) {
    UltraCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.success) NeonGreen else NeonAmber,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextSecondary)
            }
        }
        message.details.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BusyCard(busy: SorterBusy, onCancel: () -> Unit) {
    UltraCard {
        when (busy) {
            SorterBusy.Planning -> {
                Text("Составляю план…", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = NeonCyan, trackColor = CardBorder)
            }
            is SorterBusy.Moving -> {
                Text("Перемещение… ${busy.done} из ${busy.total}", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (busy.total > 0) busy.done.toFloat() / busy.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = NeonCyan,
                    trackColor = CardBorder,
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Остановить", onClick = onCancel)
            }
            SorterBusy.Undoing -> {
                Text("Откат…", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = NeonCyan, trackColor = CardBorder)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: SortPlan, onExecute: () -> Unit, onDiscard: () -> Unit) {
    UltraCard {
        Text("Предпросмотр", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        if (plan.moves.isEmpty()) {
            Text("Нечего сортировать — всё уже на своих местах.", style = MaterialTheme.typography.bodyMedium, color = NeonGreen)
        } else {
            Text(
                text = "${plan.moves.size} файлов · ${formatBytes(plan.moves.sumOf { it.sizeBytes })}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
            )
            val renamed = plan.moves.count { it.renamed }
            if (renamed > 0) {
                Text("Будут переименованы из-за совпадения имён: $renamed", style = MaterialTheme.typography.bodySmall, color = NeonAmber)
            }
        }
        plan.warnings.forEach {
            Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = NeonRed)
        }
        Spacer(Modifier.height(14.dp))
        if (plan.moves.isNotEmpty()) {
            PrimaryButton("Выполнить", onClick = onExecute, icon = Icons.Outlined.PlayArrow)
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton(if (plan.moves.isEmpty()) "Закрыть" else "Отмена", onClick = onDiscard, color = TextSecondary)
    }
}

@Composable
private fun MoveRow(move: PlannedMove) {
    val targetDir = StoragePaths.relative(move.to.substringBeforeLast('/'))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBlack,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = move.from.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "→ $targetDir" + if (move.renamed) " (как ${move.to.substringAfterLast('/')})" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (move.renamed) NeonAmber else NeonCyan,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RuleCard(rule: SortRule, enabled: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onEdit),
            ) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rule.enabled) TextPrimary else TextSecondary,
                )
                Text(
                    "${rule.sourceDir} → ${rule.targetDir}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.enabled) NeonCyan else TextSecondary,
                )
                Text(
                    text = rule.extensions.joinToString(", ") +
                        if (rule.nameRegex.isNotBlank()) " · имя ~ ${rule.nameRegex}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AmoledBlack,
                    checkedTrackColor = NeonCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CardBlack,
                    uncheckedBorderColor = CardBorder,
                ),
            )
        }
    }
}

@Composable
private fun HistoryCard(batch: SortBatch, enabled: Boolean, onUndo: () -> Unit) {
    val date = remember(batch.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(batch.timestamp))
    }
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Перемещено файлов: ${batch.moves.size}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            TextButton(onClick = onUndo, enabled = enabled) {
                Text("Откатить", color = if (enabled) NeonCyan else TextSecondary)
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initial: SortRule,
    isNew: Boolean,
    onSave: (SortRule) -> String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var source by remember(initial.id) { mutableStateOf(initial.sourceDir) }
    var extensions by remember(initial.id) { mutableStateOf(initial.extensions.joinToString(", ")) }
    var regex by remember(initial.id) { mutableStateOf(initial.nameRegex) }
    var target by remember(initial.id) { mutableStateOf(initial.targetDir) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        title = { Text(if (isNew) "Новое правило" else "Правило", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorField("Название", name) { name = it }
                EditorField("Папка-источник (от корня памяти)", source) { source = it }
                EditorField("Расширения через запятую (* — любые)", extensions) { extensions = it }
                EditorField("Регулярка для имени (необязательно)", regex) { regex = it }
                EditorField("Целевая папка", target) { target = it }
                Text(
                    "В целевой папке можно использовать {yyyy}, {MM}, {dd} — дата файла, {ext} — расширение. " +
                        "Сортируются только файлы, лежащие прямо в папке-источнике.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NeonRed) }
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Удалить правило", color = NeonRed) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = initial.copy(
                    name = name.trim(),
                    sourceDir = source.trim().trim('/'),
                    extensions = extensions.split(',', ';', ' ')
                        .map { it.trim().trimStart('.').lowercase() }
                        .filter { it.isNotEmpty() }
                        .distinct(),
                    nameRegex = regex.trim(),
                    targetDir = target.trim().trim('/'),
                )
                error = onSave(rule)
            }) { Text("Сохранить", color = NeonCyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        },
    )
}

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = NeonCyan,
            unfocusedLabelColor = TextSecondary,
            cursorColor = NeonCyan,
        ),
    )
}
