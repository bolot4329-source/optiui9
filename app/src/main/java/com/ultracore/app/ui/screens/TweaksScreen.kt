package com.ultracore.app.ui.screens

import com.ultracore.app.ui.components.ExpressiveLoadingIndicator
import com.ultracore.app.ui.components.LocalBottomBarInset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.tweaks.MessageKind
import com.ultracore.app.tweaks.TweakId
import com.ultracore.app.tweaks.TweakMessage
import com.ultracore.app.tweaks.TweaksRepository
import com.ultracore.app.tweaks.TweaksState
import com.ultracore.app.tweaks.TweaksViewModel
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import com.ultracore.app.ui.components.ExpressiveShapes
import com.ultracore.app.ui.components.ExpressiveTones
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.PrimaryButton
import com.ultracore.app.ui.components.ShapeIcon
import com.ultracore.app.ui.components.MorphChip
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/** «Главная → Твики»: quick system tweaks, each with «Сбросить». */
@Composable
fun TweaksScreen(viewModel: TweaksViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = LocalBottomBarInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.connected) {
            item { InfoBanner("Подключите Shizuku — твики меняют системные настройки через него") }
        }

        item {
            TweakCard(
                title = "Скорость анимаций",
                icon = Icons.Rounded.Animation,
                shape = ExpressiveShapes.Flower,
                tone = ExpressiveTones.Pink,
                description = "Меньше — интерфейс отзывчивее. Сейчас: ${TweaksRepository.scaleLabel(state.animationScale)}",
                id = TweakId.ANIMATIONS,
                state = state,
                onReset = viewModel::resetAnimations,
            ) {
                Options(
                    options = TweaksRepository.ANIMATION_SCALES,
                    selected = TweaksRepository.ANIMATION_SCALES.firstOrNull {
                        it.toFloat() == (state.animationScale?.toFloatOrNull() ?: 1f)
                    },
                    label = { TweaksRepository.scaleLabel(it) },
                    enabled = state.connected && TweakId.ANIMATIONS !in state.busy,
                    onSelect = viewModel::setAnimationScale,
                )
            }
        }

        item {
            val now = (state.currentRefresh?.let { "Сейчас $it Гц, " } ?: "") +
                (state.refreshLock?.let { "закреплена на $it Гц" } ?: "частотой управляет система")
            TweakCard(
                title = "Частота экрана",
                icon = Icons.Rounded.Speed,
                shape = ExpressiveShapes.Sunny,
                tone = ExpressiveTones.Sky,
                description = "${now.replaceFirstChar { it.uppercase() }}. Постоянная высокая частота расходует больше заряда.",
                id = TweakId.REFRESH,
                state = state,
                onReset = viewModel::resetRefreshRate,
            ) {
                if (state.refreshRates.size < 2) {
                    Text(
                        "Экран поддерживает только одну частоту — менять нечего",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    Options(
                        options = state.refreshRates,
                        selected = state.refreshLock,
                        label = { "$it Гц" },
                        enabled = state.connected && TweakId.REFRESH !in state.busy,
                        onSelect = viewModel::lockRefreshRate,
                    )
                }
            }
        }

        item {
            val status = when (state.phantomDisabled) {
                true -> "отключено"
                false -> "включено (стандарт Android)"
                null -> "неизвестно"
            }
            TweakCard(
                title = "Ограничение фоновых процессов",
                icon = Icons.Rounded.Layers,
                shape = ExpressiveShapes.Pentagon,
                tone = ExpressiveTones.Orange,
                description = "Android 12+ закрывает «лишние» фоновые процессы — из-за этого обрываются терминалы " +
                    "и долгие задачи. Сейчас: $status.",
                id = TweakId.PHANTOM,
                state = state,
                onReset = viewModel::resetPhantomKiller,
            ) {
                PrimaryButton(
                    text = "Отключить ограничение",
                    onClick = viewModel::disablePhantomKiller,
                    enabled = state.connected && state.phantomDisabled != true && TweakId.PHANTOM !in state.busy,
                )
            }
        }

        item {
            DnsCard(state, viewModel)
        }

        item {
            Text(
                "Очистка кэша приложений — на вкладке «Оптимизация».",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsCard(state: TweaksState, viewModel: TweaksViewModel) {
    var host by rememberSaveable(state.dnsHost) { mutableStateOf(state.dnsHost.orEmpty()) }
    val busy = TweakId.DNS in state.busy
    val enabled = state.connected && !busy
    val current = when (state.dnsMode) {
        "off" -> "выключен"
        "hostname" -> "сервер ${state.dnsHost.orEmpty()}"
        null, "opportunistic" -> "автоматически"
        else -> state.dnsMode
    }
    TweakCard(
        title = "Частный DNS",
        icon = Icons.Rounded.Shield,
        shape = ExpressiveShapes.Cookie,
        tone = ExpressiveTones.Green,
        description = "Защищённый DNS для всего телефона. С сервером AdGuard реклама блокируется во всех приложениях и браузерах. Сейчас: $current.",
        id = TweakId.DNS,
        state = state,
        onReset = viewModel::resetDns,
    ) {
        Options(
            options = listOf("off", "opportunistic"),
            selected = state.dnsMode?.takeIf { it != "hostname" } ?: if (state.dnsMode == null) "opportunistic" else null,
            label = { if (it == "off") "Выкл." else "Авто" },
            enabled = enabled,
            onSelect = viewModel::setDnsMode,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state.connected,
            label = { Text("Свой сервер (DNS-over-TLS)") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = CardBorder, cursorColor = NeonCyan),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TweaksRepository.DNS_PRESETS.forEach { (preset, label) ->
                MorphChip(text = label.substringBefore(" —"), selected = host == preset, onClick = { host = preset })
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = "Использовать этот сервер",
            onClick = { viewModel.setDnsHost(host) },
            enabled = enabled && host.isNotBlank(),
        )
    }
}

@Composable
private fun TweakCard(
    title: String,
    icon: ImageVector,
    shape: Shape,
    tone: Color,
    description: String,
    id: TweakId,
    state: TweaksState,
    onReset: () -> Unit,
    content: @Composable () -> Unit,
) {
    val busy = id in state.busy
    UltraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(icon, shape, tone, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            if (busy) {
                ExpressiveLoadingIndicator(size = 26.dp, color = NeonCyan)
            } else if (id in state.canReset) {
                TextButton(onClick = onReset, enabled = state.connected) {
                    Text("Сбросить", color = NeonAmber)
                }
            }
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        content()
        state.messages[id]?.let { MessageText(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Options(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    // Chips wrap onto the next line instead of hiding off-screen.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            MorphChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun MessageText(message: TweakMessage) {
    val color = when (message.kind) {
        MessageKind.OK -> NeonGreen
        MessageKind.WARN -> NeonAmber
        MessageKind.ERROR -> NeonRed
    }
    Text(
        message.text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}
