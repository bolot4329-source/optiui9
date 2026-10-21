package com.ultracore.app.terminal

import kotlinx.serialization.Serializable

enum class LineKind { INPUT, OUTPUT, ERROR, INFO }

data class TermLine(val id: Long, val text: String, val kind: LineKind)

/** Several shell commands run one after another with one tap. */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val commands: List<String>,
    /** The favourite macro is launched by the quick-settings tile. Only one at a time. */
    val favorite: Boolean = false,
)

/** One entry of assets/commands.json. `{name}` in [command] is replaced with a parameter value. */
@Serializable
data class CommandEntry(
    val category: String,
    val title: String,
    val description: String,
    val command: String,
    val params: List<CommandParam> = emptyList(),
    /** Changes or deletes something — ask before running. */
    val danger: Boolean = false,
    /** Runs until «Стоп» (live logs). */
    val endless: Boolean = false,
)

@Serializable
data class CommandParam(val name: String, val hint: String, val default: String = "")
