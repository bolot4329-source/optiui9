package com.ultracore.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.CardRadius
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/**
 * Icon button in an outlined circle; the circle tightens into a rounded square while pressed
 * (Material 3 Expressive shape morph). Replaces bare IconButtons for visible actions like «Обновить».
 */
@Composable
fun ExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = NeonCyan,
    size: Dp = 40.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 12.dp else size / 2, expressiveSpring(), label = "iconBtnCorner")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .border(1.dp, if (enabled) tint.copy(alpha = 0.45f) else CardBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Thick capsule progress: tinted track (never grey), no gap and no stop dot, value animates smoothly.
 * The filled part is never shorter than a circle, so even 5 % reads as part of one bar.
 */
@Composable
fun CapsuleProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), ExpressiveMotion.effects(), label = "capsule")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color.copy(alpha = 0.16f), cornerRadius = radius)
        if (animated > 0f) {
            val width = (size.width * animated).coerceAtLeast(size.height)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color), endX = width),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** Small tag that names where a value came from; shown only when it is not the plain Android API. */
@Composable
fun SourceBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = NeonCyan,
        modifier = modifier
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Shape of a tile in a connected 2-column grid: big corners outside, tight corners where tiles meet. */
fun gridShape(index: Int, count: Int, columns: Int = 2): Shape {
    val outer = CardRadius
    val inner = 8.dp
    val rows = (count + columns - 1) / columns
    val row = index / columns
    val col = index % columns
    val first = row == 0
    val last = row == rows - 1
    val left = col == 0
    val right = col == columns - 1 || index == count - 1
    return RoundedCornerShape(
        topStart = if (first && left) outer else inner,
        topEnd = if (first && right) outer else inner,
        bottomStart = if (last && left) outer else inner,
        bottomEnd = if (last && right) outer else inner,
    )
}

data class Metric(val label: String, val value: String, val badge: String? = null)

/** Big-number tiles in a connected 2-column grid (Expressive «hero metrics»). */
@Composable
fun MetricGrid(metrics: List<Metric>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        metrics.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { colIndex, metric ->
                    val index = rowIndex * 2 + colIndex
                    UltraCard(Modifier.weight(1f), shape = gridShape(index, metrics.size)) {
                        MetricContent(metric)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricContent(metric: Metric) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            metric.label,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        metric.badge?.let {
            Spacer(Modifier.width(6.dp))
            SourceBadge(it)
        }
    }
    Spacer(Modifier.height(6.dp))
    val (number, unit) = splitNumber(metric.value)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            number,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            maxLines = 1,
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

private val NUMBER_WITH_UNIT = Regex("^(≈ ?)?(-?[\\d.,]+)\\s*(.*)$")

/** "31.7 °C" → ("31.7", "°C"); "≈ 494" → ("≈494", ""); anything else stays as is. */
private fun splitNumber(value: String): Pair<String, String> {
    val m = NUMBER_WITH_UNIT.matchEntire(value.trim()) ?: return value to ""
    val approx = if (m.groupValues[1].isNotEmpty()) "≈" else ""
    return approx + m.groupValues[2] to m.groupValues[3]
}

/**
 * Run ↔ Stop button: a filled accent circle with «send» while idle; while a command runs it morphs
 * into a red rounded square with «stop» (Material 3 Expressive shape + colour morph).
 */
@Composable
fun RunStopButton(
    running: Boolean,
    canRun: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by androidx.compose.animation.core.animateDpAsState(
        when {
            pressed -> 10.dp
            running -> 14.dp
            else -> size / 2
        },
        expressiveSpring(),
        label = "runStopCorner",
    )
    val enabled = running || canRun
    val container by androidx.compose.animation.animateColorAsState(
        when {
            running -> com.ultracore.app.ui.theme.NeonRed
            canRun -> NeonCyan
            else -> Color.Transparent
        },
        ExpressiveMotion.effects(),
        label = "runStopColor",
    )
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .androidxBackground(container, shape)
            .border(1.dp, if (enabled) Color.Transparent else CardBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = { if (running) onStop() else onRun() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = running,
            transitionSpec = {
                (androidx.compose.animation.scaleIn(ExpressiveMotion.spatialFast()) + androidx.compose.animation.fadeIn(ExpressiveMotion.effects())) togetherWith
                    (androidx.compose.animation.scaleOut(ExpressiveMotion.effects()) + androidx.compose.animation.fadeOut(ExpressiveMotion.effects()))
            },
            label = "runStopIcon",
        ) { isRunning ->
            Icon(
                if (isRunning) androidx.compose.material.icons.Icons.Rounded.Stop
                else androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Send,
                contentDescription = if (isRunning) "Остановить" else "Выполнить",
                tint = if (enabled) Color.Black.copy(alpha = 0.85f) else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun Modifier.androidxBackground(color: Color, shape: Shape): Modifier =
    this.then(Modifier.background(color, shape))
