package com.ultracore.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.ultracore.app.ui.theme.CardBorder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonCyanDim
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/** Bouncy spring used across the app (Material 3 Expressive «spatial» motion). */
fun <T> expressiveSpring() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

/**
 * Toggle chip whose shape morphs on selection: round pill when off, squarer when on,
 * with a springy transition — the Material 3 Expressive toggle behaviour.
 */
@Composable
fun MorphChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 10.dp
            selected -> 14.dp
            else -> 24.dp
        },
        animationSpec = expressiveSpring(),
        label = "chipCorner",
    )
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, expressiveSpring(), label = "chipScale")
    val container by animateColorAsState(if (selected) NeonCyanDim else Color.Transparent, label = "chipColor")
    val content by animateColorAsState(
        when {
            !enabled -> TextSecondary
            selected -> NeonCyan
            else -> TextPrimary
        },
        label = "chipText",
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        border = if (selected) null else BorderStroke(1.dp, CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 14.sp) }
}

/**
 * Two edges of a sliding indicator, each with its own spring: the edge in front moves faster
 * than the edge behind, so the indicator stretches while travelling and settles like a drop of
 * liquid. Returns left and right in «item units» (0..count).
 */
@Composable
fun rememberLiquidEdges(selected: Int): Pair<Float, Float> {
    var previous by remember { mutableIntStateOf(selected) }
    val movingRight = selected >= previous
    val fast = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
    val slow = spring<Float>(dampingRatio = 0.68f, stiffness = 280f)
    val left by animateFloatAsState(selected.toFloat(), if (movingRight) slow else fast, label = "liquidLeft")
    val right by animateFloatAsState(selected + 1f, if (movingRight) fast else slow, label = "liquidRight")
    if (previous != selected) previous = selected
    return left to right
}
