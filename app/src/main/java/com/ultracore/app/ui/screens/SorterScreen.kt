package com.ultracore.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable

@Composable
fun SorterScreen() {
    PhasePlaceholder(
        icon = Icons.Outlined.FolderOpen,
        title = "Сортировка",
        phase = "Фаза 4 · в разработке",
        description = "Умная раскладка файлов по папкам по вашим правилам.",
        plannedFeatures = listOf(
            "Правила: источник → маска/регулярка → целевая папка",
            "Медиа по папкам Год/Месяц",
            "Предпросмотр (Dry Run) и защита от перезаписи",
            "Журнал операций и откат в одно касание",
        ),
    )
}
