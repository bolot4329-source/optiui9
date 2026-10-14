package com.ultracore.app.cleaner

enum class JunkCategory(val title: String, val description: String) {
    OrphanData(
        "Остатки удалённых приложений",
        "Папки в Android/data и Android/obb, для которых приложение уже не установлено",
    ),
    Thumbnails(
        "Кэш миниатюр",
        "Папки .thumbnails — галерея создаст их заново",
    ),
    LogsAndTemp(
        "Логи и временные файлы",
        "*.log, *.tmp, *.temp в общей памяти",
    ),
    EmptyFolders(
        "Пустые папки",
        "Папки без файлов (стандартные папки Android не трогаются)",
    ),
    Duplicates(
        "Дубликаты",
        "Одинаковые файлы от 1 МБ (SHA-256). Самый старый экземпляр остаётся",
    ),
}

data class JunkItem(
    val path: String,
    val sizeBytes: Long,
    val category: JunkCategory,
    val isDirectory: Boolean,
    /** Android/data and Android/obb are only reachable through Shizuku (UID 2000). */
    val viaShizuku: Boolean = false,
    val note: String? = null,
)

data class ScanResult(
    val items: Map<JunkCategory, List<JunkItem>>,
    val warnings: List<String>,
    val durationMs: Long,
) {
    val allItems: List<JunkItem> get() = items.values.flatten()
}

data class ScanProgress(
    val stage: String,
    val filesScanned: Int,
    val currentPath: String,
)

data class CleanResult(
    val deleted: Int,
    val failed: Int,
    val freedBytes: Long,
    val errors: List<String>,
)
