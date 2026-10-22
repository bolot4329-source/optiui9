package com.ultracore.app.ui.components

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
import com.ultracore.app.ui.theme.CardRadius

/** Material 3 Expressive card: tonal container on the black background, large soft corners, no border. */
@Composable
fun UltraCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = CardBlack,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}
