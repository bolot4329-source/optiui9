package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.LocalBottomBarInset
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.manager.AppChanges
import com.ultracore.app.manager.AppFilter
import com.ultracore.app.manager.AppManagerViewModel
import com.ultracore.app.manager.BatchOp
import com.ultracore.app.manager.BatchReport
import com.ultracore.app.manager.InstallOptions
import com.ultracore.app.manager.ManagedApp
import com.ultracore.app.manager.UltraCoreProfile
import com.ultracore.app.sandbox.SandboxState
import com.ultracore.app.ui.components.AppIcon
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.MorphChip
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

/** «Приложения → Управление»: freeze, remove for user, backup, install, sandbox. */
@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel = viewModel()) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val sandbox by viewModel.sandbox.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val changes by viewModel.changes.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportProfile)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readProfile)
    }

    var actionsFor by remember { mutableStateOf<String?>(null) }
    var confirmBatch by remember { mutableStateOf<BatchOp?>(null) }
    var installUri by remember { mutableStateOf<Uri?>(null) }

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        installUri = uri
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + if (selected.isNotEmpty()) 150.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!connected) {
                item { InfoBanner("Подключите Shizuku — управление приложениями работает через него") }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary) },
                        placeholder = { Text("Название или пакет") },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = NeonCyan,
                        ),
                    )
                    IconButton(onClick = { pickApk.launch(arrayOf("*/*")) }, enabled = connected && busy == null) {
                        Icon(Icons.Outlined.Download, contentDescription = "Установить APK", tint = if (connected) NeonCyan else CardBorder)
                    }
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = NeonCyan)
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Обновить", tint = NeonCyan)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё", tint = TextPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Экспорт списка изменений") },
                                onClick = { menuOpen = false; exportLauncher.launch("ultracore-profile.json") },
                            )
                            DropdownMenuItem(
                                text = { Text("Импорт списка") },
                                enabled = connected,
                                onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "*/*")) },
                            )
                            DropdownMenuItem(
                                text = { Text("Вернуть всё, что изменил UltraCore", color = NeonAmber) },
                                enabled = connected,
                                onClick = { menuOpen = false; confirmRevert = true },
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFilter.entries.forEach { f -> Chip(f.title, f == filter) { viewModel.setFilter(f) } }
                }
            }

            when (filter) {
                AppFilter.SANDBOX -> item {
                    SandboxSettings(
                        delaySeconds = sandbox.delaySeconds,
                        pendingCount = sandbox.pending.size,
                        mode = sandbox.mode,
                        hasApps = sandbox.packages.isNotEmpty(),
                        enabled = connected && busy == null,
                        onDelay = viewModel::setSandboxDelay,
                        onMode = viewModel::setSandboxMode,
                        onWakeAll = viewModel::wakeAllSandbox,
                        onSleepAll = viewModel::sleepAllSandbox,
                    )
                }
                AppFilter.REMOVED -> item {
                    InfoBanner("Системные приложения, удалённые для пользователя. Их можно вернуть кнопкой «Восстановить».", color = TextSecondary)
                }
                AppFilter.SYSTEM -> item {
                    InfoBanner(
                        "Замораживайте и удаляйте системные приложения, только если знаете, за что они отвечают. " +
                            "Важные части системы защищены 🛡. Всё можно вернуть.",
                    )
                }
                else -> Unit
            }

            if (!loading && apps.isEmpty()) {
                item {
                    InfoBanner(
                        if (filter == AppFilter.SANDBOX) {
                            "Песочница пуста. Откройте приложение в любом списке и нажмите «В песочницу»."
                        } else {
                            "Ничего не найдено"
                        },
                        color = TextSecondary,
                    )
                }
            }

            items(apps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in selected,
                    inSandbox = app.packageName in sandbox.packages,
                    bgRestricted = app.packageName in changes.restricted,
                    onCheck = { viewModel.toggleSelected(app.packageName) },
                    onClick = { actionsFor = app.packageName },
                )
            }
        }

        if (selected.isNotEmpty()) {
            SelectionBar(
                count = selected.size,
                filter = filter,
                enabled = connected && busy == null,
                onOp = { confirmBatch = it },
                onClear = viewModel::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = LocalBottomBarInset.current - 8.dp),
            )
        }

        busy?.let { text ->
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = CardBlack,
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = NeonCyan)
                    Spacer(Modifier.width(14.dp))
                    Text(text, color = TextPrimary)
                }
            }
        }
    }

    actionsFor?.let { pkg ->
        val app = viewModel.app(pkg)
        if (app != null) {
            AppActionsDialog(
                app = app,
                inSandbox = pkg in sandbox.packages,
                bgRestricted = pkg in changes.restricted,
                connected = connected,
                onDismiss = { actionsFor = null },
                onOp = { op -> actionsFor = null; viewModel.batch(op, listOf(pkg)) },
                onSandboxAdd = { actionsFor = null; viewModel.addToSandbox(pkg) },
                onSandboxRemove = { actionsFor = null; viewModel.removeFromSandbox(pkg) },
                onSandboxLaunch = { actionsFor = null; viewModel.launchSandboxed(pkg) },
                onSandboxShortcut = { actionsFor = null; viewModel.pinSandboxShortcut(pkg, app.label) },
            )
        }
    }

    confirmBatch?.let { op ->
        AlertDialog(
            onDismissRequest = { confirmBatch = null },
            title = { Text("${op.title}: ${selected.size} прил.?") },
            text = {
                Text(
                    when (op) {
                        BatchOp.FREEZE -> "Приложения перестанут запускаться и пропадут с рабочего стола, пока вы их не разморозите. Защищённые будут пропущены."
                        BatchOp.SUSPEND -> "Иконки станут серыми, виджеты и данные останутся, но приложения не смогут работать и присылать уведомления. Защищённые будут пропущены."
                        BatchOp.RESTRICT_BG -> "Приложения не смогут работать в фоне и попадут в самый строгий режим энергосбережения. Push-уведомления продолжат приходить. Защищённые будут пропущены."
                        BatchOp.UNINSTALL -> "Системные приложения будут удалены только для вас. Их можно вернуть во вкладке «Удалённые». Обычные приложения и защищённые будут пропущены."
                        BatchOp.BACKUP -> "APK будут сохранены в Download/UltraCore/Backups."
                        else -> "Продолжить?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBatch = null
                    viewModel.batch(op, selected)
                }) { Text(op.title, color = if (op == BatchOp.UNINSTALL) NeonRed else NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { confirmBatch = null }) { Text("Отмена") } },
        )
    }

    installUri?.let { uri ->
        InstallDialog(
            onDismiss = { installUri = null },
            onInstall = { options ->
                installUri = null
                viewModel.install(uri, options)
            },
        )
    }

    if (confirmRevert) {
        AlertDialog(
            onDismissRequest = { confirmRevert = false },
            title = { Text("Вернуть всё?") },
            text = {
                Text(
                    "UltraCore разморозит, снимет паузу, восстановит удалённые системные приложения, " +
                        "разрешит фон и очистит песочницу — всё, что он изменил (с версии 2.1).\n\n${describe(changes)}",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRevert = false; viewModel.revertAll() }) { Text("Вернуть", color = NeonAmber) }
            },
            dismissButton = { TextButton(onClick = { confirmRevert = false }) { Text("Отмена") } },
        )
    }

    pendingImport?.let { profile -> ImportDialog(profile, onApply = viewModel::applyImport, onDismiss = viewModel::cancelImport) }

    report?.let { ReportDialog(it, viewModel::dismissReport) }
}

private fun describe(c: AppChanges): String =
    if (c.isEmpty) {
        "Сейчас в списке изменений ничего нет."
    } else {
        "Заморожено: ${c.frozen.size} · на паузе: ${c.suspended.size} · удалено: ${c.removed.size} · фон ограничен: ${c.restricted.size}"
    }

@Composable
private fun ImportDialog(profile: UltraCoreProfile, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Применить список?") },
        text = {
            Column {
                Text("Заморозить: ${profile.frozen.size}")
                Text("Поставить на паузу: ${profile.suspended.size}")
                Text("Удалить для пользователя: ${profile.removed.size}")
                Text("Ограничить фон: ${profile.restricted.size}")
                Text("В песочницу: ${profile.sandbox.size}")
                Text(
                    "Приложения, которых нет на этом телефоне, и защищённые будут пропущены. Всё можно вернуть через «Вернуть всё».",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    MorphChip(text = text, selected = selected, onClick = onClick)
}

@Composable
private fun AppRow(
    app: ManagedApp,
    checked: Boolean,
    inSandbox: Boolean,
    bgRestricted: Boolean,
    onCheck: () -> Unit,
    onClick: () -> Unit,
) {
    UltraCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheck() },
                colors = CheckboxDefaults.colors(checkedColor = NeonCyan, uncheckedColor = TextSecondary),
            )
            AppIcon(app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.enabled && app.installed) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val badges = buildList {
                    if (!app.installed) add("удалено" to NeonRed)
                    else if (!app.enabled) add("❄ заморожено" to NeonCyan)
                    if (app.installed && app.suspended) add("⏸ на паузе" to NeonCyan)
                    if (bgRestricted) add("🔋 фон ограничен" to NeonAmber)
                    if (inSandbox) add("песочница" to NeonAmber)
                    if (app.protectedReason != null) add("🛡 защищено" to NeonGreen)
                }
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badges.forEach { (t, c) -> Text(t, fontSize = 11.sp, color = c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    filter: AppFilter,
    enabled: Boolean,
    onOp: (BatchOp) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardBlack,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Выбрано: $count", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Снять выбор", color = TextSecondary) }
            }
            val ops = if (filter == AppFilter.REMOVED) {
                listOf(BatchOp.RESTORE)
            } else {
                listOf(
                    BatchOp.SUSPEND, BatchOp.UNSUSPEND, BatchOp.FREEZE, BatchOp.UNFREEZE,
                    BatchOp.RESTRICT_BG, BatchOp.ALLOW_BG, BatchOp.BACKUP, BatchOp.UNINSTALL,
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ops.forEach { op ->
                    OutlinedButton(
                        onClick = { onOp(op) },
                        enabled = enabled,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (op == BatchOp.UNINSTALL) NeonRed else NeonCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (op == BatchOp.UNINSTALL) NeonRed else NeonCyan),
                    ) { Text(op.title, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun AppActionsDialog(
    app: ManagedApp,
    inSandbox: Boolean,
    bgRestricted: Boolean,
    connected: Boolean,
    onDismiss: () -> Unit,
    onOp: (BatchOp) -> Unit,
    onSandboxAdd: () -> Unit,
    onSandboxRemove: () -> Unit,
    onSandboxLaunch: () -> Unit,
    onSandboxShortcut: () -> Unit,
) {
    val context = LocalContext.current
    val protected = app.protectedReason != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (app.versionName.isNotBlank()) Text("Версия ${app.versionName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (protected) {
                    Text("🛡 Защищено: ${app.protectedReason}", color = NeonGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.size(8.dp))

                if (!app.installed) {
                    ActionButton("Восстановить", enabled = connected) { onOp(BatchOp.RESTORE) }
                } else {
                    if (inSandbox) {
                        ActionButton("▶ Открыть из песочницы", NeonGreen, enabled = connected, onClick = onSandboxLaunch)
                        ActionButton("Ярлык песочницы на рабочий стол", enabled = true, onClick = onSandboxShortcut)
                        ActionButton("Убрать из песочницы (разморозить)", enabled = connected, onClick = onSandboxRemove)
                    } else {
                        if (app.enabled) {
                            ActionButton("❄ Заморозить (иконка скроется)", enabled = connected && !protected) { onOp(BatchOp.FREEZE) }
                        } else {
                            ActionButton("Разморозить", enabled = connected) { onOp(BatchOp.UNFREEZE) }
                        }
                        if (app.suspended) {
                            ActionButton("Снять паузу", enabled = connected) { onOp(BatchOp.UNSUSPEND) }
                        } else {
                            ActionButton("⏸ Пауза (иконка и виджеты остаются)", enabled = connected && !protected) { onOp(BatchOp.SUSPEND) }
                        }
                        if (bgRestricted) {
                            ActionButton("Разрешить работу в фоне", enabled = connected) { onOp(BatchOp.ALLOW_BG) }
                        } else {
                            ActionButton("🔋 Ограничить работу в фоне", NeonAmber, enabled = connected && !protected) { onOp(BatchOp.RESTRICT_BG) }
                        }
                        ActionButton("В песочницу (заморозка + запуск по ярлыку)", NeonAmber, enabled = connected && !protected, onClick = onSandboxAdd)
                    }
                    ActionButton("Бэкап APK", enabled = true) { onOp(BatchOp.BACKUP) }
                    if (app.isSystem) {
                        ActionButton("Удалить для пользователя", NeonRed, enabled = connected && !protected) { onOp(BatchOp.UNINSTALL) }
                    }
                    ActionButton("Открыть в настройках телефона", TextPrimary, enabled = true) {
                        onDismiss()
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun ActionButton(text: String, color: Color = NeonCyan, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = if (enabled) color else TextSecondary, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SandboxSettings(
    delaySeconds: Int,
    pendingCount: Int,
    mode: String,
    hasApps: Boolean,
    enabled: Boolean,
    onDelay: (Int) -> Unit,
    onMode: (String) -> Unit,
    onWakeAll: () -> Unit,
    onSleepAll: () -> Unit,
) {
    val context = LocalContext.current
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    var asked by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }

    UltraCard {
        Text("Песочница", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "Приложения здесь заморожены. Открывайте их через «Открыть из песочницы» или ярлык — " +
                "после выключения экрана они снова заморозятся.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        if (pendingCount > 0) {
            Text("Сейчас разморожено: $pendingCount", color = NeonAmber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Text("Как «усыплять» приложения:", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("⏸ Пауза", mode == SandboxState.MODE_SUSPEND) { onMode(SandboxState.MODE_SUSPEND) }
            Chip("❄ Заморозка", mode == SandboxState.MODE_DISABLE) { onMode(SandboxState.MODE_DISABLE) }
        }
        Text(
            if (mode == SandboxState.MODE_SUSPEND) {
                "Иконка остаётся на рабочем столе (серой), виджеты сохраняются."
            } else {
                "Иконка и виджеты исчезают, пока приложение спит. Открывайте его ярлыком песочницы."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Text("Усыпить после выключения экрана:", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "сразу", 30 to "30 с", 120 to "2 мин").forEach { (sec, label) ->
                Chip(label, sec == delaySeconds) { onDelay(sec) }
            }
        }
        if (needsNotificationPermission && !asked) {
            TextButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Разрешить уведомление песочницы", color = NeonCyan)
            }
        }
        if (hasApps) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = onWakeAll, enabled = enabled) { Text("Разбудить все", color = if (enabled) NeonGreen else TextSecondary) }
                TextButton(onClick = onSleepAll, enabled = enabled) { Text("Усыпить все", color = if (enabled) NeonCyan else TextSecondary) }
            }
            Text(
                "«Разбудить все» — перед поездкой или перезагрузкой, если Shizuku может быть недоступен.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { Text("Отключить экономию батареи для UltraCore", color = NeonCyan) }
        Text(
            "Чтобы прошивка не остановила песочницу в фоне, разрешите UltraCore и Shizuku работать без ограничений батареи.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun InstallDialog(onDismiss: () -> Unit, onInstall: (InstallOptions) -> Unit) {
    var downgrade by remember { mutableStateOf(false) }
    var asPlay by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Установить приложение") },
        text = {
            Column {
                Text("Поддерживаются .apk и архивы .apks / .xapk с несколькими APK внутри.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(downgrade, { downgrade = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Column {
                        Text("Разрешить откат версии", style = MaterialTheme.typography.bodyMedium)
                        Text("Экспериментально: без Root Android обычно запрещает", fontSize = 11.sp, color = NeonAmber)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(asPlay, { asPlay = it }, colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Column {
                        Text("Указать установщик Google Play", style = MaterialTheme.typography.bodyMedium)
                        Text("Только если прошивка блокирует установку", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onInstall(InstallOptions(downgrade, asPlay)) }) { Text("Установить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ReportDialog(report: BatchReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (report.failures.isEmpty()) "${report.title}: ${report.done}" else report.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (report.done > 0 && report.failures.isNotEmpty()) {
                    Text("Успешно: ${report.done}", color = NeonGreen)
                }
                report.failures.forEach { (name, reason) ->
                    Text(name, fontWeight = FontWeight.SemiBold, color = NeonRed, modifier = Modifier.padding(top = 6.dp))
                    Text(reason, style = MaterialTheme.typography.bodySmall)
                }
                report.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
