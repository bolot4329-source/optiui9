package com.ultracore.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultracore.app.core.DeviceInfo
import com.ultracore.app.shizuku.ShizukuState
import com.ultracore.app.ui.ShellTestState
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

@Composable
fun SystemStatusHeader(
    shizukuState: ShizukuState,
    storageGranted: Boolean,
    shellTest: ShellTestState,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onReconnectShizuku: () -> Unit,
    onGrantStorage: () -> Unit,
    onRunShellTest: () -> Unit,
    /** Hoisted so the screen knows not to hide an opened header while scrolling. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The header is a one-line pill by default — also when something needs attention: the pill
    // then names the problem and carries the fix button. Tap it for the full details.
    // Springy morph between the one-line and the full header.
    AnimatedContent(
        targetState = !expanded,
        transitionSpec = {
            (fadeIn(ExpressiveMotion.effects()) + scaleIn(ExpressiveMotion.spatialDefault(), initialScale = 0.96f)) togetherWith
                fadeOut(ExpressiveMotion.effects()) using
                SizeTransform(clip = false) { _, _ -> ExpressiveMotion.spatialDefault() }
        },
        label = "header",
        modifier = modifier,
    ) { isCompact ->
        if (isCompact) {
            CompactHeader(
                shizukuState, storageGranted,
                onExpand = { onExpandedChange(true) },
                onOpenShizuku, onRequestShizuku, onReconnectShizuku, onGrantStorage,
            )
        } else {
            FullHeader(
                shizukuState, storageGranted, shellTest,
                onCollapse = { onExpandedChange(false) },
                onOpenShizuku, onRequestShizuku, onReconnectShizuku, onGrantStorage, onRunShellTest,
            )
        }
    }
}

/** What the compact pill says when something is not ready. */
private data class HeaderProblem(
    val title: String,
    val hint: String,
    val action: String,
    val onAction: () -> Unit,
    val tone: Color,
)

@Composable
private fun FullHeader(
    shizukuState: ShizukuState,
    storageGranted: Boolean,
    shellTest: ShellTestState,
    onCollapse: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onReconnectShizuku: () -> Unit,
    onGrantStorage: () -> Unit,
    onRunShellTest: () -> Unit,
) {
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "UltraCore",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = DeviceInfo.shortTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onCollapse) {
                Icon(Icons.Outlined.ExpandLess, contentDescription = "Свернуть", tint = TextSecondary)
            }
        }

        Spacer(Modifier.size(12.dp))

        // --- Shizuku ---
        val shizuku = describeShizuku(shizukuState)
        StatusRow(
            title = "Shizuku",
            status = shizuku.text,
            statusColor = shizuku.color,
            actionLabel = shizuku.actionLabel,
            onAction = when (shizukuState) {
                ShizukuState.NotInstalled, ShizukuState.NotRunning -> onOpenShizuku
                ShizukuState.PermissionDenied -> onRequestShizuku
                is ShizukuState.Error -> onReconnectShizuku
                else -> null
            },
            busy = shizukuState is ShizukuState.Connecting,
            done = shizukuState is ShizukuState.Connected,
        )
        if (shizukuState is ShizukuState.PermissionDenied) {
            Text(
                text = "Если окно запроса не появляется — откройте Shizuku → «Приложения» и включите UltraCore вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = CardBorder)

        // --- All files access ---
        StatusRow(
            title = "Доступ к файлам",
            status = if (storageGranted) "Выдан (MANAGE_EXTERNAL_STORAGE)" else "Не выдан",
            statusColor = if (storageGranted) NeonGreen else NeonRed,
            actionLabel = if (storageGranted) null else "Выдать доступ к файлам",
            onAction = if (storageGranted) null else onGrantStorage,
            done = storageGranted,
        )

        // --- Shell diagnostics (only makes sense once Shizuku is connected) ---
        val connected = shizukuState is ShizukuState.Connected
        val running = shellTest is ShellTestState.Running
        if (connected) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = CardBorder)
            OutlinedButton(
                onClick = onRunShellTest,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, NeonCyan),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
            ) {
                if (running) {
                    ExpressiveLoadingIndicator(size = 22.dp, color = NeonCyan)
                } else {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Тест Shell")
            }
        }

        when (shellTest) {
            is ShellTestState.Success -> ShellOutput(shellTest.output, TextPrimary)
            is ShellTestState.Failure -> ShellOutput("Ошибка: ${shellTest.message}", NeonRed)
            ShellTestState.Running -> ShellOutput("Выполняю…", TextSecondary)
            ShellTestState.Idle -> Unit
        }
    }
}

@Composable
private fun CompactHeader(
    state: ShizukuState,
    storageGranted: Boolean,
    onExpand: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onReconnectShizuku: () -> Unit,
    onGrantStorage: () -> Unit,
) {
    val problem: HeaderProblem? = when (state) {
        ShizukuState.NotInstalled -> HeaderProblem(
            "Shizuku не установлен", "Без него большинство функций недоступно", "Установить", onOpenShizuku, ExpressiveTones.Red,
        )
        ShizukuState.NotRunning -> HeaderProblem(
            "Shizuku не запущен", "После перезагрузки его нужно запускать заново", "Открыть", onOpenShizuku, ExpressiveTones.Red,
        )
        ShizukuState.PermissionDenied -> HeaderProblem(
            "Нет разрешения Shizuku", "Разрешите доступ для UltraCore", "Разрешить", onRequestShizuku, ExpressiveTones.Amber,
        )
        is ShizukuState.Error -> HeaderProblem(
            "Ошибка Shizuku", state.message, "Подключить", onReconnectShizuku, ExpressiveTones.Red,
        )
        ShizukuState.Connecting -> null
        is ShizukuState.Connected -> if (storageGranted) {
            null
        } else {
            HeaderProblem(
                "Нет доступа к файлам", "Нужен для очистки и сортировки", "Выдать", onGrantStorage, ExpressiveTones.Amber,
            )
        }
    }
    UltraCard(
        shape = RoundedCornerShape(50),
        onClick = onExpand,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            when {
                problem != null -> {
                    ShapeIcon(Icons.Rounded.PriorityHigh, ExpressiveShapes.Cookie, problem.tone, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    HeaderTexts(problem.title, problem.hint, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SmallActionButton(text = problem.action, onClick = problem.onAction)
                }
                state is ShizukuState.Connected -> {
                    HeaderTexts("UltraCore", DeviceInfo.shortTitle, Modifier.weight(1f))
                    ShapeIcon(Icons.Rounded.Check, ExpressiveShapes.Cookie, ExpressiveTones.Green, size = 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Shizuku · ${state.mode.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreen,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "Подробнее", tint = TextSecondary)
                }
                else -> {
                    ExpressiveLoadingIndicator(size = 32.dp, color = NeonAmber)
                    Spacer(Modifier.width(12.dp))
                    HeaderTexts("Подключение к Shizuku…", DeviceInfo.shortTitle, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeaderTexts(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ShizukuUi(val text: String, val color: Color, val actionLabel: String?)

private fun describeShizuku(state: ShizukuState): ShizukuUi = when (state) {
    ShizukuState.NotInstalled -> ShizukuUi("Не установлен", NeonRed, "Установить")
    ShizukuState.NotRunning -> ShizukuUi("Не запущен — после перезагрузки его нужно запускать заново", NeonRed, "Открыть Shizuku")
    ShizukuState.PermissionDenied -> ShizukuUi("Нет разрешения", NeonAmber, "Разрешить")
    ShizukuState.Connecting -> ShizukuUi("Подключение…", NeonAmber, null)
    is ShizukuState.Connected -> ShizukuUi("Подключено · ${state.mode.label} · ${state.uidInfo}", NeonGreen, null)
    is ShizukuState.Error -> ShizukuUi(state.message, NeonRed, "Подключить")
}

@Composable
private fun StatusRow(
    title: String,
    status: String,
    statusColor: Color,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    busy: Boolean = false,
    done: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(statusColor, CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            busy -> ExpressiveLoadingIndicator(size = 28.dp, color = NeonAmber)
            actionLabel != null && onAction != null -> Button(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = AmoledBlack,
                ),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
            done -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = NeonGreen,
            )
        }
    }
}

@Composable
private fun ShellOutput(text: String, color: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = AmoledBlack,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}
