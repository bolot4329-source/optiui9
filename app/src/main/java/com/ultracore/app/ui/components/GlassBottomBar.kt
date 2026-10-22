package com.ultracore.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultracore.app.ui.theme.NeonCyan
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary

/** Space the floating bar takes at the bottom; scrolling screens add it to their bottom padding. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Compact floating bar, proportions like the system Phone app: ~56dp high, ~78dp per item. */
val GlassBarHeight = 56.dp
private val ItemWidth = 66.dp
private val Inset = 4.dp

data class GlassBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Floating pill navigation bar with a «liquid glass» look:
 * - the screen content behind it is blurred live ([backdrop] is the recorded content layer),
 * - a translucent tint keeps text readable, a light gradient rim imitates the glass edge,
 * - the selected item sits in a lighter glass pill that stretches between items like a drop.
 */
@Composable
fun GlassBottomBar(
    items: List<GlassBarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: GraphicsLayer,
    backdropOrigin: Offset,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 28.dp,
) {
    val shape = RoundedCornerShape(GlassBarHeight / 2)
    val blurLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    // Created once per radius: re-creating it every frame resets the GPU cache.
    val blurEffect = remember(blurRadius, density) {
        val r = with(density) { blurRadius.toPx() }
        BlurEffect(r, r, TileMode.Clamp)
    }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .width(ItemWidth * items.size + Inset * 2)
            .height(GlassBarHeight)
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .onGloballyPositioned { barOrigin = it.positionInRoot() }
            .drawBehind {
                // 1) Live blurred copy of whatever is behind the bar.
                if (blurLayer.renderEffect !== blurEffect) blurLayer.renderEffect = blurEffect
                blurLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                    val shift = barOrigin - backdropOrigin
                    translate(-shift.x, -shift.y) { drawLayer(backdrop) }
                }
                drawLayer(blurLayer)
                // 2) Tint so icons stay readable on any content.
                drawRect(Color.Black.copy(alpha = 0.58f))
                // 3) Soft light from above — the «glass» sheen.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.45f to Color.White.copy(alpha = 0.02f),
                        1f to Color.Transparent,
                    ),
                )
            }
            // 4) Glass rim: brighter on top, fading down.
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.38f),
                    0.5f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.18f),
                ),
                shape = shape,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(Inset)) {
            val itemWidth = maxWidth / items.size
            val (left, right) = rememberLiquidEdges(selected)
            val indicatorShape = RoundedCornerShape((GlassBarHeight - Inset * 2) / 2)
            // Selected «glass drop».
            Box(
                Modifier
                    .offset(x = itemWidth * left)
                    .width(itemWidth * (right - left))
                    .fillMaxHeight()
                    .clip(indicatorShape)
                    .background(
                        Brush.verticalGradient(
                            0f to NeonCyan.copy(alpha = 0.22f),
                            1f to Color.White.copy(alpha = 0.08f),
                        ),
                    )
                    .border(1.dp, NeonCyan.copy(alpha = 0.35f), indicatorShape),
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                items.forEachIndexed { index, item ->
                    BarItem(
                        item = item,
                        selected = index == selected,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarItem(item: GlassBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.86f
            selected -> 1.08f
            else -> 1f
        },
        expressiveSpring(),
        label = "barItemScale",
    )
    val color by animateColorAsState(if (selected) TextPrimary else TextSecondary, label = "barItemColor")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            item.label,
            color = color,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
