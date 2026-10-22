package com.ultracore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.CardRadius

/**
 * Block on pure AMOLED black: no fill, a 1dp outline gives the structure
 * (Material 3 Expressive containment without grey surfaces).
 */
@Composable
fun UltraCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardRadius),
    outlined: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Black,
        border = if (outlined) BorderStroke(1.dp, CardBorder) else null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

/** Shape of item [index] in a connected group: 28dp outside corners, 8dp where items touch. */
fun groupShape(index: Int, count: Int): Shape {
    val outer = CardRadius
    val inner = 8.dp
    return when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/** Connected group (Expressive list pattern): items 2dp apart, outer corners round, inner ones tight. */
@Composable
fun <T> UltraCardGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    itemModifier: (T) -> Modifier = { Modifier },
    content: @Composable ColumnScope.(T) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            UltraCard(modifier = itemModifier(item), shape = groupShape(index, items.size)) { content(item) }
        }
    }
}

/** Border for AlertDialogs: they are black now, so the outline shows where the dialog is. */
val UltraDialogModifier: Modifier
    get() = Modifier.border(1.dp, CardBorder, RoundedCornerShape(CardRadius))
