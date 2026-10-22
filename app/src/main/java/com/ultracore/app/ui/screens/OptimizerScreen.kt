package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.LocalBottomBarInset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.optimizer.CompileProgress
import com.ultracore.app.optimizer.OptimizationResult
import com.ultracore.app.optimizer.OptimizerTool
import com.ultracore.app.optimizer.OptimizerViewModel
import com.ultracore.app.optimizer.RunningState
import com.ultracore.app.optimizer.ShellLogEntry
import com.ultracore.app.optimizer.Telemetry
import com.ultracore.app.optimizer.formatBytes
import com.ultracore.app.optimizer.formatDuration
import androidx.compose.ui.graphics.Shape
import com.ultracore.app.ui.components.ExpressiveShapes
import com.ultracore.app.ui.components.ExpressiveTones
import com.ultracore.app.ui.components.ShapeIcon
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.components.groupShape
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.CardRadius
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TELEMETRY_INTERVAL_MS = 3_000L

private val TOOL_ORDER = listOf(
    OptimizerTool.Trim,
    OptimizerTool.DexoptJob,
    OptimizerTool.ArtCompile,
    OptimizerTool.TrimCaches,
)

private fun OptimizerTool.icon(): ImageVector = when (this) {
    OptimizerTool.TrimCaches -> Icons.Outlined.CleaningServices
    OptimizerTool.Trim -> Icons.Outlined.DeleteSweep
    OptimizerTool.ArtCompile -> Icons.Outlined.Speed
    OptimizerTool.DexoptJob -> Icons.Outlined.Tune
}

private fun OptimizerTool.shape(): Shape = when (this) {
    OptimizerTool.Trim -> ExpressiveShapes.Cookie
    OptimizerTool.DexoptJob -> ExpressiveShapes.Flower
    OptimizerTool.ArtCompile -> ExpressiveShapes.Pentagon
    OptimizerTool.TrimCaches -> ExpressiveShapes.Clover
}

private fun OptimizerTool.tone(): Color = when (this) {
    OptimizerTool.Trim -> ExpressiveTones.Sky
    OptimizerTool.DexoptJob -> ExpressiveTones.Green
    OptimizerTool.ArtCompile -> ExpressiveTones.Orange
    OptimizerTool.TrimCaches -> ExpressiveTones.Pink
}

private fun OptimizerTool.description(): String = when (this) {
    OptimizerTool.Trim -> "sm fstrim — обслуживание памяти телефона. Полезно после удаления большого объёма файлов"
    OptimizerTool.DexoptJob -> "cmd package bg-dexopt-job — то же, что система делает ночью на зарядке. До 10 минут"
    OptimizerTool.ArtCompile -> "cmd package compile -m speed-profile -f для каждого приложения. Нужно редко: после крупного обновления системы"
    OptimizerTool.TrimCaches -> "pm trim-caches — удаляет кэш всех приложений. Только при нехватке места: потом приложения заново скачают данные и первое время будут медленнее"
}

@Composable
fun OptimizerScreen(viewModel: OptimizerViewModel = viewModel()) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val compile by viewModel.compileProgress.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val boostSummary by viewModel.boostSummary.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()

    // Live telemetry while the screen is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshTelemetry()
                delay(TELEMETRY_INTERVAL_MS)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TelemetryCard(telemetry, onRefresh = viewModel::refreshTelemetry) }

        if (!connected) {
            item {
                UltraCard {
                    Text(
                        text = "Shizuku не подключён — инструменты недоступны. Подключите его в карточке сверху.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonAmber,
                    )
                }
            }
        }

        item {
            BoostCard(
                connected = connected,
                running = running,
                compile = compile,
                summary = boostSummary,
                onStart = viewModel::runUltraBoost,
                onCancel = viewModel::cancel,
            )
        }

        item {
            Text(
                text = "Инструменты",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TOOL_ORDER.forEachIndexed { index, tool ->
            ToolCard(
                shape = groupShape(index, TOOL_ORDER.size),
                tool = tool,
                result = results[tool],
                isRunning = running?.tool == tool && running?.boost == null,
                anyRunning = running != null,
                connected = connected,
                compile = compile,
                onRun = { viewModel.runTool(tool) },
                onCancel = viewModel::cancel,
            )
                }
            }
        }

        item { ConsoleCard(log = log, onClear = viewModel::clearLog) }
    }
}

// ------------------------------------------------------------------ telemetry

@Composable
private fun TelemetryCard(telemetry: Telemetry, onRefresh: () -> Unit) {
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Телеметрия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Обновить", tint = NeonCyan)
            }
        }

        val mem = telemetry.memory
        if (mem != null) {
            Meter(
                label = "ОЗУ",
                detail = "${formatBytes(mem.usedBytes)} из ${formatBytes(mem.totalBytes)} · свободно ${formatBytes(mem.availableBytes)}",
                fraction = mem.usedFraction,
            )
            Spacer(Modifier.height(12.dp))
            if (mem.swapTotalBytes > 0) {
                Meter(
                    label = "Swap / ZRAM",
                    detail = "${formatBytes(mem.swapUsedBytes)} из ${formatBytes(mem.swapTotalBytes)}",
                    fraction = mem.swapUsedFraction,
                )
            } else {
                Meter(label = "Swap / ZRAM", detail = "отключён", fraction = 0f)
            }
            Spacer(Modifier.height(12.dp))
        }

        val storage = telemetry.storage
        if (storage != null) {
            Meter(
                label = "Хранилище /data",
                detail = "${formatBytes(storage.usedBytes)} из ${formatBytes(storage.totalBytes)} · свободно ${formatBytes(storage.freeBytes)}",
                fraction = storage.usedFraction,
            )
        }

        if (mem == null && storage == null) {
            Text("Загрузка…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Meter(label: String, detail: String, fraction: Float) {
    val color = when {
        fraction >= 0.9f -> NeonRed
        fraction >= 0.75f -> NeonAmber
        else -> NeonCyan
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        color = color,
        trackColor = CardBorder,
    )
    Spacer(Modifier.height(4.dp))
    Text(text = detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
}

// ------------------------------------------------------------------ boost

@Composable
private fun BoostCard(
    connected: Boolean,
    running: RunningState?,
    compile: CompileProgress?,
    summary: List<OptimizationResult>?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val boost = running?.boost
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = CardBlack,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(Icons.Outlined.Bolt, ExpressiveShapes.Sunny, ExpressiveTones.Primary, size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Обслуживание в 1 тап",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "TRIM памяти → фоновая оптимизация приложений",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (boost != null) {
                val stepFraction = if (running?.tool == OptimizerTool.ArtCompile) compile?.fraction ?: 0f else 0f
                val overall = ((boost.stepIndex + stepFraction) / boost.totalSteps).coerceIn(0f, 1f)
                Text(
                    text = "Шаг ${boost.stepIndex + 1} из ${boost.totalSteps}: ${boost.stepTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { overall },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = NeonCyan,
                    trackColor = CardBorder,
                )
                if (running?.tool == OptimizerTool.ArtCompile && compile != null) {
                    Spacer(Modifier.height(6.dp))
                    CompileLine(compile)
                }
                Spacer(Modifier.height(12.dp))
                StopButton(onCancel)
            } else {
                Button(
                    onClick = onStart,
                    enabled = connected && running == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = AmoledBlack,
                        disabledContainerColor = CardBorder,
                        disabledContentColor = TextSecondary,
                    ),
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить в 1 тап", fontWeight = FontWeight.Bold)
                }
            }

            if (summary != null && boost == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Итог",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                if (summary.isEmpty()) {
                    Text("Остановлено до первого результата", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                summary.forEach { r ->
                    Text(
                        text = (if (r.success) "✓ " else "✗ ") + r.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.success) NeonGreen else NeonRed,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tool cards

@Composable
private fun ToolCard(
    shape: androidx.compose.ui.graphics.Shape,
    tool: OptimizerTool,
    result: OptimizationResult?,
    isRunning: Boolean,
    anyRunning: Boolean,
    connected: Boolean,
    compile: CompileProgress?,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    UltraCard(shape = shape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(tool.icon(), tool.shape(), tool.tone(), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = tool.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            if (!isRunning) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRun,
                    enabled = connected && !anyRunning,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = AmoledBlack,
                        disabledContainerColor = CardBorder,
                        disabledContentColor = TextSecondary,
                    ),
                ) {
                    Text("Запуск", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (isRunning) {
            Spacer(Modifier.height(12.dp))
            if (tool == OptimizerTool.ArtCompile && compile != null) {
                LinearProgressIndicator(
                    progress = { compile.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = NeonCyan,
                    trackColor = CardBorder,
                )
                Spacer(Modifier.height(6.dp))
                CompileLine(compile)
                Spacer(Modifier.height(10.dp))
                StopButton(onCancel)
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = NeonCyan,
                    trackColor = CardBorder,
                )
                Spacer(Modifier.height(6.dp))
                Text("Выполняется…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        if (result != null && !isRunning) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.success) NeonGreen else NeonRed,
            )
        }
    }
}

@Composable
private fun CompileLine(compile: CompileProgress) {
    val text = if (compile.packageName.isNotEmpty()) {
        "Оптимизировано ${compile.current} из ${compile.total}: ${compile.packageName}"
    } else {
        "Оптимизировано ${compile.current} из ${compile.total}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StopButton(onCancel: () -> Unit) {
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, NeonRed),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
    ) {
        Text("Остановить")
    }
}

// ------------------------------------------------------------------ console

@Composable
private fun ConsoleCard(log: List<ShellLogEntry>, onClear: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    UltraCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Консоль выполнения (Shell Log)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = "${log.size}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = TextSecondary,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AmoledBlack,
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (log.isEmpty()) {
                        ConsoleText("Пока пусто. Запустите любой инструмент.", TextSecondary)
                    }
                    // Newest first.
                    log.asReversed().forEach { entry ->
                        val header = buildString {
                            append(timeFormat.format(Date(entry.timestamp)))
                            entry.durationMs?.let { append(" · ").append(formatDuration(it)) }
                        }
                        ConsoleText(header, TextSecondary)
                        entry.command?.let { ConsoleText("$ $it", NeonCyan) }
                        ConsoleText(entry.message, if (entry.success) TextPrimary else NeonRed)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            if (log.isNotEmpty()) {
                TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
                    Text("Очистить консоль", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ConsoleText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}
