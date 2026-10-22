package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.ExpressiveLoadingIndicator
import com.ultracore.app.ui.components.LocalBottomBarInset
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.battery.BatteryValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ultracore.app.battery.BatteryRepository
import com.ultracore.app.battery.BatterySource
import com.ultracore.app.ui.components.CapsuleProgress
import com.ultracore.app.ui.components.ExpressiveIconButton
import com.ultracore.app.ui.components.Metric
import com.ultracore.app.ui.components.MetricGrid
import com.ultracore.app.ui.components.SourceBadge
import com.ultracore.app.battery.BatteryViewModel
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.ui.graphics.StrokeCap
import com.ultracore.app.ui.components.ExpressiveShapes
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.ShapeIcon
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/** Values shown as big tiles at the top (in this order); the passport list shows the rest. */
private val HERO_TITLES = listOf(
    BatteryRepository.TITLE_TEMPERATURE to "Температура",
    BatteryRepository.TITLE_CURRENT to "Ток",
    BatteryRepository.TITLE_HEALTH to "Здоровье",
    BatteryRepository.TITLE_CYCLES to "Циклы",
)
private val HERO_FALLBACKS = listOf(
    "Оценка полной ёмкости" to "Ёмкость",
    "Полная ёмкость" to "Ёмкость",
    "Напряжение" to "Напряжение",
)

/** «Главная → Батарея»: battery passport. */
@Composable
fun BatteryScreen(viewModel: BatteryViewModel = viewModel()) {
    val passport by viewModel.passport.collectAsStateWithLifecycle()
    var showExtras by rememberSaveable { mutableStateOf(false) }

    // Live values only while the screen is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollPublic() }
    }

    val byTitle = remember(passport.values) { passport.values.associateBy { it.title } }
    val hero = remember(byTitle) {
        val chosen = HERO_TITLES.filter { it.first in byTitle }.toMutableList()
        HERO_FALLBACKS.forEach { f ->
            if (chosen.size < 4 && f.first in byTitle && chosen.none { it.second == f.second }) chosen += f
        }
        chosen.take(4)
    }
    val heroTitles = remember(hero) { hero.map { it.first }.toSet() }
    val rest = remember(passport.values, heroTitles) { passport.values.filter { it.title !in heroTitles } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = LocalBottomBarInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero", contentType = "hero") {
            UltraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val charging = passport.statusText.startsWith("Заряжается")
                    ShapeIcon(
                        if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        if (charging) ExpressiveShapes.Sunny else ExpressiveShapes.Arch,
                        levelColor(passport.level),
                        size = 64.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            passport.level?.let { "$it %" } ?: "—",
                            style = MaterialTheme.typography.displayLarge,
                            color = levelColor(passport.level),
                        )
                        if (passport.statusText.isNotBlank()) {
                            Text(passport.statusText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    if (passport.loadingShell) {
                        ExpressiveLoadingIndicator(size = 32.dp, color = NeonCyan)
                    } else {
                        ExpressiveIconButton(Icons.Outlined.Refresh, "Обновить", onClick = viewModel::refresh)
                    }
                }
                passport.level?.let { level ->
                    Spacer(Modifier.height(14.dp))
                    CapsuleProgress(progress = level / 100f, color = levelColor(level))
                }
            }
        }

        if (hero.isNotEmpty()) {
            item(key = "metrics", contentType = "metrics") {
                MetricGrid(
                    hero.mapNotNull { (title, label) ->
                        byTitle[title]?.let { v -> Metric(label, v.value, v.source.badge()) }
                    },
                )
            }
        }

        if (rest.isNotEmpty()) {
            item(key = "passport", contentType = "passport") {
                UltraCard {
                    Text(
                        "Паспорт батареи",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        "Значения без пометки — из стандартного Android. dumpsys и sysfs — данные прошивки, расчёт — оценка.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    rest.forEachIndexed { index, value ->
                        if (index > 0) HorizontalDivider(color = CardBorder)
                        ValueRow(value)
                    }
                }
            }
        } else if (passport.values.isEmpty()) {
            item(key = "empty") { InfoBanner("Нет данных о батарее", color = TextSecondary) }
        }

        passport.shellNote?.let { note -> item(key = "note") { InfoBanner(note) } }

        if (passport.extras.isNotEmpty()) {
            item(key = "extrasHeader", contentType = "extrasHeader") {
                UltraCard(onClick = { showExtras = !showExtras }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Дополнительные данные от прошивки",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            Text(
                                "${passport.extras.size} полей из dumpsys battery, как есть",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Icon(
                            if (showExtras) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                }
            }
            if (showExtras) {
                items(passport.extras, key = { "extra:" + it.first }, contentType = { "extra" }) { (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text(
                            key,
                            modifier = Modifier.weight(1f),
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(value, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun BatterySource.badge(): String? = if (this == BatterySource.ANDROID) null else label

@Composable
private fun ValueRow(value: BatteryValue) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        value.source.badge()?.let {
            SourceBadge(it)
            Spacer(Modifier.width(8.dp))
        }
        Text(value.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

private fun levelColor(level: Int?) = when {
    level == null -> TextSecondary
    level <= 15 -> NeonRed
    level <= 30 -> NeonAmber
    else -> NeonGreen
}
