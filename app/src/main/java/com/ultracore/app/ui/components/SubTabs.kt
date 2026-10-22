package com.ultracore.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonCyanDim
import com.ultracore.app.ui.theme.TextSecondary

/**
 * Tabs inside one bottom-bar section, e.g. «Очистка | Сортировка»: a pill-shaped segmented
 * control with a liquid sliding indicator (Material 3 Expressive connected button group).
 */
@Composable
fun SubTabs(
    titles: List<String>,
    modifier: Modifier = Modifier,
    /** Pass [selected] + [onSelect] when the parent needs to switch tabs itself. */
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    content: @Composable (index: Int) -> Unit,
) {
    var internalSelected by rememberSaveable { mutableIntStateOf(0) }
    val current = selected ?: internalSelected
    val select: (Int) -> Unit = { index -> if (onSelect != null) onSelect(index) else internalSelected = index }

    // Each tab keeps its own saved state (scroll position, expanded cards) while another is shown.
    val holder = rememberSaveableStateHolder()
    Column(modifier) {
        SegmentedPill(
            titles = titles,
            selected = current,
            onSelect = select,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        )
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(ExpressiveMotion.spatialDefault()) { w -> dir * w / 6 } +
                        fadeIn(ExpressiveMotion.effects())) togetherWith
                        (slideOutHorizontally(ExpressiveMotion.effects()) { w -> -dir * w / 8 } +
                            fadeOut(ExpressiveMotion.effects()))
                },
                label = "subTabContent",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                holder.SaveableStateProvider(tab) { content(tab) }
            }
            // Soft edge instead of a hard cut where content scrolls under the tabs (the background is pure black).
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent))),
            )
        }
    }
}

@Composable
private fun SegmentedPill(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (left, right) = rememberLiquidEdges(selected)
    BoxWithConstraints(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(23.dp)),
    ) {
        val inset = 4.dp
        val itemWidth = (maxWidth - inset * 2) / titles.size
        // Liquid indicator: two springy edges.
        Box(
            Modifier
                .padding(vertical = inset)
                .offset(x = inset + itemWidth * left)
                .width(itemWidth * (right - left))
                .fillMaxHeight()
                .clip(RoundedCornerShape(19.dp))
                .background(NeonCyanDim)
                .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(19.dp)),
        )
        Row(Modifier.fillMaxSize().padding(horizontal = inset)) {
            titles.forEachIndexed { index, title ->
                val isSelected = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = if (isSelected) NeonCyan else TextSecondary,
                        fontSize = if (titles.size > 3) 12.5.sp else 14.sp,
                        letterSpacing = if (titles.size > 3) (-0.2).sp else 0.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}
