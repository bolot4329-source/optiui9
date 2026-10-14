package com.ultracore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.CardRadius

/** AMOLED card: #111113 fill, #1E1E24 hairline border, 24dp radius. */
@Composable
fun UltraCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = CardBlack,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}
