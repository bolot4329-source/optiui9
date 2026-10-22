package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.LocalBottomBarInset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.ultracore.app.ui.theme.ThemePrefs
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.core.CapabilityResult
import com.ultracore.app.core.CapabilityStatus
import com.ultracore.app.core.DeviceInfo
import com.ultracore.app.home.HomeViewModel
import com.ultracore.app.shizuku.ShizukuState
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/** «Главная → Обзор»: the phone and what works on it. Battery and tweaks arrive in Phase 2. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuState.collectAsStateWithLifecycle()
    val connected = shizuku is ShizukuState.Connected
    val resetBusy by viewModel.resetBusy.collectAsStateWithLifecycle()
    val resetReport by viewModel.resetReport.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeviceCard(shizuku) }

        item {
            UltraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Что работает на этом телефоне",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "Проверяется безопасно, ничего не меняя",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        report?.results?.takeIf { it.isNotEmpty() }?.let { results ->
                            val ok = results.count { it.status == CapabilityStatus.AVAILABLE }
                            val partial = results.count { it.status == CapabilityStatus.PARTIAL }
                            val locked = results.count { it.status == CapabilityStatus.UNAVAILABLE }
                            Row(Modifier.padding(top = 6.dp)) {
                                Text("✅ $ok", color = NeonGreen, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(14.dp))
                                Text("⚠️ $partial", color = NeonAmber, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(14.dp))
                                Text("🔒 $locked", color = NeonRed, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = NeonCyan)
                    } else {
                        IconButton(onClick = viewModel::recheck, enabled = connected) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Проверить заново",
                                tint = if (connected) NeonCyan else TextSecondary,
                            )
                        }
                    }
                }
            }
        }

        val results = report?.results.orEmpty()
        if (results.isEmpty()) {
            item {
                InfoBanner(
                    if (connected) "Проверяю возможности…" else "Подключите Shizuku — проверка запустится сама",
                    color = if (connected) NeonCyan else NeonAmber,
                )
            }
        } else {
            items(results, key = { it.capability.name }) { CapabilityRow(it) }
        }

        item {
            val context = LocalContext.current
            UltraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Цвета из обоев", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            if (ThemePrefs.dynamicColors) {
                                "Material You: оттенки подбираются под обои телефона"
                            } else {
                                "Классическая неоновая тема UltraCore"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = ThemePrefs.dynamicColors,
                        onCheckedChange = { ThemePrefs.setDynamic(context, it) },
                    )
                }
            }
        }

        item {
            UltraCard {
                Text("Вернуть телефон как было", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Отменяет всё, что изменил UltraCore: заморозку, паузу, удаление системных приложений, " +
                        "ограничения фона, песочницу и все твики. Нажмите перед удалением UltraCore — " +
                        "сами по себе изменения после удаления приложения не исчезнут.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.size(8.dp))
                if (resetBusy != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonAmber)
                        Spacer(Modifier.width(10.dp))
                        Text(resetBusy.orEmpty(), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    TextButton(onClick = { confirmReset = true }, enabled = connected) {
                        Text("Вернуть всё", color = if (connected) NeonAmber else TextSecondary)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Вернуть телефон как было?") },
            text = { Text("Все приложения будут разморожены и восстановлены, ограничения сняты, твики сброшены к исходным значениям.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; viewModel.resetEverything() }) { Text("Вернуть", color = NeonAmber) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }

    resetReport?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResetReport,
            title = { Text("Готово") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::dismissResetReport) { Text("OK") } },
        )
    }
}

@Composable
private fun DeviceCard(shizuku: ShizukuState) {
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${DeviceInfo.manufacturer} ${DeviceInfo.model}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "Android ${DeviceInfo.androidVersion} (API ${DeviceInfo.sdkInt})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    DeviceInfo.buildId,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (shizuku is ShizukuState.Connected) {
                    Text(
                        "Режим Shizuku: ${shizuku.mode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(result: CapabilityResult) {
    val (icon, color) = when (result.status) {
        CapabilityStatus.AVAILABLE -> Icons.Outlined.CheckCircle to NeonGreen
        CapabilityStatus.PARTIAL -> Icons.Outlined.WarningAmber to NeonAmber
        CapabilityStatus.UNAVAILABLE -> Icons.Outlined.Lock to NeonRed
    }
    UltraCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    result.capability.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    result.capability.usedIn,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                )
                if (result.reason.isNotBlank()) {
                    Text(
                        result.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
