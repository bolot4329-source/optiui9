package com.ultracore.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.TextSecondary

/** Tabs inside one bottom-bar section, e.g. «Очистка | Сортировка». */
@Composable
fun SubTabs(
    titles: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        TabRow(
            selectedTabIndex = selected,
            containerColor = AmoledBlack,
            contentColor = NeonCyan,
            divider = {},
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                    selectedContentColor = NeonCyan,
                    unselectedContentColor = TextSecondary,
                )
            }
        }
        content(selected)
    }
}
