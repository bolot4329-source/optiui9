package com.ultracore.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ScrollableTabRow
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
        val tabs = @Composable {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title, maxLines = 1) },
                    selectedContentColor = NeonCyan,
                    unselectedContentColor = TextSecondary,
                )
            }
        }
        val rowModifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        if (titles.size > 3) {
            // Many tabs: let them scroll instead of squeezing long titles.
            ScrollableTabRow(
                selectedTabIndex = selected,
                containerColor = AmoledBlack,
                contentColor = NeonCyan,
                edgePadding = 8.dp,
                divider = {},
                modifier = rowModifier,
                tabs = tabs,
            )
        } else {
            TabRow(
                selectedTabIndex = selected,
                containerColor = AmoledBlack,
                contentColor = NeonCyan,
                divider = {},
                modifier = rowModifier,
                tabs = tabs,
            )
        }
        content(selected)
    }
}
