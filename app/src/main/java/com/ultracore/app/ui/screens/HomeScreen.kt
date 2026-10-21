package com.ultracore.app.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
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
