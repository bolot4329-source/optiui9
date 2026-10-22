package com.ultracore.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.runtime.mutableStateOf
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

/**
 * Material 3 Expressive motion: «spatial» springs bounce (position, scale, corners, shapes),
 * «effects» springs never overshoot (colour, alpha).
 */
object ExpressiveMotion {
    fun <T> spatialDefault() = spring<T>(dampingRatio = 0.58f, stiffness = 380f)
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.65f, stiffness = 850f)
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.62f, stiffness = 260f)
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 500f)
}

/** Bouncy spring used across the app (spatial default). */
fun <T> expressiveSpring() = ExpressiveMotion.spatialDefault<T>()

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
            pressed -> 8.dp
            selected -> 12.dp
            else -> 24.dp
        },
        animationSpec = expressiveSpring(),
        label = "chipCorner",
    )
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, expressiveSpring(), label = "chipScale")
    val container by animateColorAsState(
        if (selected) NeonCyanDim else Color.Transparent,
        ExpressiveMotion.effects(),
        label = "chipColor",
    )
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
    ) {
        // A check mark springs in on the selected chip.
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(ExpressiveMotion.spatialFast()) + expandHorizontally(ExpressiveMotion.spatialFast()),
            exit = scaleOut(ExpressiveMotion.effects()) + shrinkHorizontally(ExpressiveMotion.effects()),
        ) {
            Row {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Switch for pure black: outlined black track when off, accent when on. */
@Composable
fun ExpressiveSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = NeonCyan,
            checkedBorderColor = NeonCyan,
            checkedThumbColor = Color.Black,
            uncheckedTrackColor = Color.Black,
            uncheckedBorderColor = CardBorder,
            uncheckedThumbColor = TextSecondary,
        ),
    )
}

/**
 * Two edges of a sliding indicator, each with its own spring: the edge in front moves faster
 * than the edge behind, so the indicator stretches while travelling and settles like a drop of
 * liquid. Returns left and right in «item units» (0..count).
 */
@Composable
fun rememberLiquidEdges(selected: Int): Pair<Float, Float> {
    // Direction is remembered until the next selection change, so a recomposition mid-flight
    // cannot swap the fast and slow springs.
    var previous by remember { mutableIntStateOf(selected) }
    var movingRight by remember { mutableStateOf(true) }
    if (selected != previous) {
        movingRight = selected > previous
        previous = selected
    }
    val fast = ExpressiveMotion.spatialFast<Float>()
    val slow = ExpressiveMotion.spatialSlow<Float>()
    val left by animateFloatAsState(selected.toFloat(), if (movingRight) slow else fast, label = "liquidLeft")
    val right by animateFloatAsState(selected + 1f, if (movingRight) fast else slow, label = "liquidRight")
    return left to right
}
