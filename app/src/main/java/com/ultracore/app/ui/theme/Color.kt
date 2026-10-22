package com.ultracore.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * App palette. The values come from the current theme (Material You colors from the wallpaper,
 * or the classic neon palette), so every screen recolours itself when the theme changes.
 * The getters read snapshot state: composables that use them recompose automatically.
 */
data class UltraColors(
    val background: Color,
    val card: Color,
    val border: Color,
    val bar: Color,
    val accent: Color,
    val accentContainer: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val text: Color,
    val textDim: Color,
)

object UltraPalette {
    var current: UltraColors by mutableStateOf(NeonColors)
}

/** The original UltraCore look. */
val NeonColors = UltraColors(
    background = Color(0xFF000000),
    card = Color(0xFF111113),
    border = Color(0xFF1E1E24),
    bar = Color(0xFF09090B),
    accent = Color(0xFF00E5FF),
    accentContainer = Color(0xFF00363D),
    good = Color(0xFF39FF88),
    warn = Color(0xFFFFC145),
    bad = Color(0xFFFF4D6A),
    text = Color(0xFFF2F2F5),
    textDim = Color(0xFF9A9AA5),
)

// Names kept from v1 so all screens keep compiling; they now follow the theme.
val AmoledBlack: Color get() = UltraPalette.current.background
val CardBlack: Color get() = UltraPalette.current.card
val CardBorder: Color get() = UltraPalette.current.border
val BarBlack: Color get() = UltraPalette.current.bar
val NeonCyan: Color get() = UltraPalette.current.accent
val NeonCyanDim: Color get() = UltraPalette.current.accentContainer
val NeonGreen: Color get() = UltraPalette.current.good
val NeonAmber: Color get() = UltraPalette.current.warn
val NeonRed: Color get() = UltraPalette.current.bad
val TextPrimary: Color get() = UltraPalette.current.text
val TextSecondary: Color get() = UltraPalette.current.textDim
