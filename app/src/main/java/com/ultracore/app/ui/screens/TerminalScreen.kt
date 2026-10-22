package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.LocalBottomBarInset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.terminal.CommandBook
import com.ultracore.app.terminal.CommandEntry
import com.ultracore.app.terminal.LineKind
import com.ultracore.app.terminal.Macro
import com.ultracore.app.terminal.TerminalViewModel
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.SubTabs
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/** Bottom tab «Терминал»: Консоль | Команды | Макросы. */
@Composable
fun TerminalSection(viewModel: TerminalViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SubTabs(listOf("Консоль", "Команды", "Макросы"), selected = tab, onSelect = { tab = it }) { index ->
        when (index) {
            0 -> ConsoleScreen(viewModel)
            1 -> CommandBookScreen(viewModel, openConsole = { tab = 0 })
            else -> MacrosScreen(viewModel, openConsole = { tab = 0 })
        }
    }
}

// ---------------------------------------------------------------- console

@Composable
private fun ConsoleScreen(viewModel: TerminalViewModel) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(viewModel::export)
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    // Stay above the glass bar; when the keyboard is open, stay above the keyboard instead.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val barInset = LocalBottomBarInset.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = maxOf(barInset, imeBottom + 12.dp)),
    ) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = AmoledBlack,
            border = BorderStroke(1.dp, CardBorder),
        ) {
            if (lines.isEmpty()) {
                Box(Modifier.padding(14.dp)) {
                    Text(
                        if (connected) {
                            "Введите команду внизу или выберите готовую во вкладке «Команды».\n" +
                                "Команды выполняются от имени shell через Shizuku."
                        } else {
                            "Подключите Shizuku, чтобы выполнять команды."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(lines, key = { it.id }) { line ->
                        Text(
                            line.text,
                            color = lineColor(line.kind),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (line.kind == LineKind.INPUT) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { clipboard.setText(AnnotatedString(viewModel.allText())) }, enabled = lines.isNotEmpty()) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать вывод", tint = iconTint(lines.isNotEmpty()))
            }
            IconButton(onClick = { exportLauncher.launch("ultracore-terminal.txt") }, enabled = lines.isNotEmpty()) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = "Сохранить в .txt", tint = iconTint(lines.isNotEmpty()))
            }
            IconButton(onClick = viewModel::clear, enabled = lines.isNotEmpty() && !running) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Очистить", tint = iconTint(lines.isNotEmpty() && !running))
            }
            Spacer(Modifier.weight(1f))
            if (running) {
                TextButton(onClick = viewModel::stop) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, tint = NeonRed)
                    Spacer(Modifier.width(4.dp))
                    Text("Стоп", color = NeonRed)
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEach { s ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AmoledBlack,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.clickable { viewModel.setInput(s) },
                    ) {
                        Text(
                            s,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::setInput,
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = connected,
                placeholder = { Text("команда, например pm list packages -3", fontSize = 13.sp) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSend = { viewModel.submit() }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CardBorder,
                    cursorColor = NeonCyan,
                ),
            )
            IconButton(onClick = viewModel::submit, enabled = connected && !running && input.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Выполнить",
                    tint = iconTint(connected && !running && input.isNotBlank(), NeonCyan),
                )
            }
        }
    }
}

private fun lineColor(kind: LineKind): Color = when (kind) {
    LineKind.INPUT -> NeonCyan
    LineKind.OUTPUT -> TextPrimary
    LineKind.ERROR -> NeonRed
    LineKind.INFO -> NeonAmber
}

private fun iconTint(enabled: Boolean, active: Color = TextPrimary): Color = if (enabled) active else CardBorder

// ---------------------------------------------------------------- command book

@Composable
private fun CommandBookScreen(viewModel: TerminalViewModel, openConsole: () -> Unit) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CommandEntry?>(null) }
    val grouped = remember(viewModel.commands) { viewModel.commands.groupBy { it.category } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (grouped.isEmpty()) {
            item { InfoBanner("Каталог команд не загрузился", color = NeonRed) }
        }
        grouped.forEach { (category, entries) ->
            item(key = "header-$category") {
                Text(
                    category,
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonCyan,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(entries, key = { "${it.category}/${it.title}" }) { entry ->
                UltraCard(Modifier.clickable { selected = entry }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        if (entry.danger) Text("изменяет", style = MaterialTheme.typography.labelSmall, color = NeonAmber)
                    }
                    Text(entry.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        entry.command,
                        color = NeonCyan.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    selected?.let { entry ->
        CommandDialog(
            entry = entry,
            canRun = connected && !running,
            onDismiss = { selected = null },
            onRun = { command ->
                selected = null
                viewModel.run(listOf(command))
                openConsole()
            },
            onInsert = { command ->
                selected = null
                viewModel.setInput(command)
                openConsole()
            },
        )
    }
}

@Composable
private fun CommandDialog(
    entry: CommandEntry,
    canRun: Boolean,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    val values = remember(entry) { mutableStateMapOf<String, String>().apply { entry.params.forEach { put(it.name, it.default) } } }
    val missing = entry.params.any { values[it.name].isNullOrBlank() }
    val command = CommandBook.fill(entry, values)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                entry.params.forEach { p ->
                    OutlinedTextField(
                        value = values[p.name].orEmpty(),
                        onValueChange = { values[p.name] = it.trim() },
                        label = { Text(p.hint) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                }
                Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NeonCyan)
                if (entry.danger) {
                    Text(
                        "Эта команда изменяет систему или данные. Выполняйте, только если понимаете результат.",
                        color = NeonAmber,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (entry.endless) {
                    Text("Работает, пока не нажмёте «Стоп».", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRun(command) }, enabled = canRun && !missing) {
                Text(if (entry.danger) "Выполнить всё равно" else "Выполнить")
            }
        },
        dismissButton = {
            TextButton(onClick = { onInsert(command) }) { Text("В консоль") }
        },
    )
}

// ---------------------------------------------------------------- macros

@Composable
private fun MacrosScreen(viewModel: TerminalViewModel, openConsole: () -> Unit) {
    val macros by viewModel.macros.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Macro?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf<Macro?>(null) }
    var confirmDelete by remember { mutableStateOf<Macro?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            InfoBanner(
                "Макрос — несколько команд одной кнопкой, выполняются по очереди до первой ошибки. " +
                    "Макрос со ★ запускается плиткой «Макрос» в шторке: добавьте её через карандаш в быстрых настройках.",
                color = NeonCyan,
            )
        }
        item {
            TextButton(onClick = { creating = true }) { Text("+ Новый макрос", color = NeonCyan) }
        }
        items(macros, key = { it.id }) { macro ->
            UltraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            macro.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "${macro.commands.size} ${commandsWord(macro.commands.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(macro.id) }) {
                        Icon(
                            if (macro.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Для плитки в шторке",
                            tint = if (macro.favorite) NeonAmber else TextSecondary,
                        )
                    }
                    IconButton(onClick = { editing = macro }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Изменить", tint = TextSecondary)
                    }
                    IconButton(onClick = { confirmDelete = macro }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = TextSecondary)
                    }
                }
                macro.commands.take(3).forEach {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NeonCyan.copy(alpha = 0.8f), maxLines = 1)
                }
                if (macro.commands.size > 3) Text("…", color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmRun = macro }, enabled = connected && !running) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = if (connected && !running) NeonGreen else CardBorder)
                    Spacer(Modifier.width(4.dp))
                    Text("Запустить", color = if (connected && !running) NeonGreen else TextSecondary)
                }
            }
        }
    }

    if (creating || editing != null) {
        MacroEditor(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, commands, favorite ->
                val error = viewModel.saveMacro(editing?.id, name, commands, favorite)
                if (error == null) {
                    creating = false
                    editing = null
                }
                error
            },
        )
    }

    confirmRun?.let { macro ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text("Запустить «${macro.name}»?") },
            text = {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    macro.commands.forEachIndexed { i, c ->
                        Text("${i + 1}. $c", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRun = null
                    viewModel.run(macro.commands, title = macro.name)
                    openConsole()
                }) { Text("Запустить") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("Отмена") } },
        )
    }

    confirmDelete?.let { macro ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить «${macro.name}»?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMacro(macro.id)
                    confirmDelete = null
                }) { Text("Удалить", color = NeonRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun MacroEditor(
    initial: Macro?,
    onDismiss: () -> Unit,
    onSave: (name: String, commands: String, favorite: Boolean) -> String?,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var commands by remember(initial) { mutableStateOf(initial?.commands?.joinToString("\n").orEmpty()) }
    var favorite by remember(initial) { mutableStateOf(initial?.favorite ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый макрос" else "Изменить макрос") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = commands,
                    onValueChange = { commands = it },
                    label = { Text("Команды — по одной в строке") },
                    minLines = 4,
                    maxLines = 10,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan),
                    )
                    Text("★ Запускать плиткой в шторке", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let { Text(it, color = NeonRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onSave(name, commands, favorite) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun commandsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "команд"
        mod10 == 1 -> "команда"
        mod10 in 2..4 -> "команды"
        else -> "команд"
    }
}
