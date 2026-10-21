package com.ultracore.app.ui.components

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
    modifier: Modifier = Modifier,
) {
    // When everything is ready the header shrinks to one line; tap to see details.
    // If something needs attention it is always shown in full.
    val allOk = shizukuState is ShizukuState.Connected && storageGranted
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (allOk && !expanded) {
        CompactHeader(shizukuState as ShizukuState.Connected, onExpand = { expanded = true }, modifier = modifier)
        return
    }

    UltraCard(modifier = modifier) {
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
            if (allOk) {
                IconButton(onClick = { expanded = false }) {
                    Icon(Icons.Outlined.ExpandLess, contentDescription = "Свернуть", tint = TextSecondary)
                }
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

        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = CardBorder)

        // --- Shell diagnostics ---
        val connected = shizukuState is ShizukuState.Connected
        val running = shellTest is ShellTestState.Running
        OutlinedButton(
            onClick = onRunShellTest,
            enabled = connected && !running,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, if (connected) NeonCyan else CardBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NeonCyan,
                )
            } else {
                Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Тест Shell")
        }
        if (!connected) {
            Text(
                text = "Доступно после подключения Shizuku",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
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
private fun CompactHeader(state: ShizukuState.Connected, onExpand: () -> Unit, modifier: Modifier) {
    UltraCard(modifier = modifier.clickable(onClick = onExpand)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "UltraCore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = DeviceInfo.shortTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(NeonGreen, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Shizuku · ${state.mode.label}",
                style = MaterialTheme.typography.labelMedium,
                color = NeonGreen,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ExpandMore, contentDescription = "Подробнее", tint = TextSecondary)
        }
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
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = NeonAmber,
            )
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
