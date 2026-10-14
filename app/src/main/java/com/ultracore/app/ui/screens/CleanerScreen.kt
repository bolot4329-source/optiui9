package com.ultracore.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.runtime.Composable

@Composable
fun CleanerScreen() {
    PhasePlaceholder(
        icon = Icons.Outlined.CleaningServices,
        title = "Очистка",
        phase = "Фаза 3 · в разработке",
        description = "Поиск и удаление мусора с предпросмотром перед удалением.",
        plannedFeatures = listOf(
            "Осиротевшие папки в Android/data и Android/obb от удалённых приложений",
            "Пустые папки, .thumbnails, *.log, *.tmp",
            "Поиск дубликатов по хэшу (SHA-256)",
        ),
    )
}
