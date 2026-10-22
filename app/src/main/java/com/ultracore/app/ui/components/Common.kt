package com.ultracore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.CardRadius
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonCyanDim
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextSecondary

/** Small info banner: tonal tint of [color] instead of a border (Material 3 Expressive). */
@Composable
fun InfoBanner(text: String, color: Color = NeonAmber, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = Color.Transparent,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(Icons.Outlined.Info, ExpressiveShapes.Cookie, color, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

/** Big pill button; its corners tighten with a spring while pressed (Expressive shape morph). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 14.dp else 28.dp, expressiveSpring(), label = "btnCorner")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(corner),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyan,
            contentColor = AmoledBlack,
            // Disabled = outline on black, never a grey fill.
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextSecondary,
        ),
        border = if (enabled) null else BorderStroke(1.dp, CardBorder),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Secondary pill button. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonRed,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.6f) else CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        Text(text)
    }
}

/**
 * Small pill action next to a row (e.g. «Запуск»): tonal accent — calmer than the screen's one
 * filled [PrimaryButton], never grey; outlined when disabled.
 */
@Composable
fun SmallActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 10.dp else 20.dp, expressiveSpring(), label = "smallBtnCorner")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(corner),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyanDim,
            contentColor = NeonCyan,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextSecondary,
        ),
        border = BorderStroke(1.dp, if (enabled) NeonCyan.copy(alpha = 0.38f) else CardBorder),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
