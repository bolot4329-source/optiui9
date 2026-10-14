package com.ultracore.app.sorter

import kotlinx.serialization.Serializable

/**
 * A sorting rule. Folders are relative to /storage/emulated/0.
 * [targetDir] supports {yyyy}, {MM}, {dd} (file date) and {ext} (file extension).
 * Only top-level files of [sourceDir] are sorted (subfolders are left alone).
 */
@Serializable
data class SortRule(
    val id: String,
    val name: String,
    val sourceDir: String,
    val extensions: List<String>,
    val nameRegex: String = "",
    val targetDir: String,
    val enabled: Boolean = true,
)

@Serializable
data class MoveRecord(val from: String, val to: String)

/** One executed sorting batch — everything needed to undo it. */
@Serializable
data class SortBatch(
    val id: String,
    val timestamp: Long,
    val moves: List<MoveRecord>,
    val createdDirs: List<String> = emptyList(),
)

data class PlannedMove(
    val ruleName: String,
    val from: String,
    val to: String,
    val sizeBytes: Long,
    val renamed: Boolean,
)

data class SortPlan(
    val moves: List<PlannedMove>,
    val warnings: List<String>,
)

data class SortResult(
    val moved: Int,
    val failed: Int,
    val errors: List<String>,
)
