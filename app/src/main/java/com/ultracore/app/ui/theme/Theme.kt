package com.ultracore.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material 3 Expressive: large, soft corners. */
val CardRadius = 28.dp

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(CardRadius),
    extraLarge = RoundedCornerShape(CardRadius),
)

/** Bolder titles and headlines, as in Material 3 Expressive «emphasized» styles. */
private fun expressiveTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** «Цвета из обоев» switch, stored in SharedPreferences. */
object ThemePrefs {
    private const val FILE = "ui"
    private const val KEY_DYNAMIC = "dynamic_colors"

    var dynamicColors by mutableStateOf(true)
        private set

    fun init(context: Context) {
        dynamicColors = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC, true)
    }

    fun setDynamic(context: Context, value: Boolean) {
        dynamicColors = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_DYNAMIC, value).apply()
    }
}

/** Wallpaper colors on a true-black (AMOLED) background. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    // Every container role is black, so no standard component (dialog, menu, switch track,
    // text field) can paint a grey underlay.
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    surfaceBright = Color.Black,
    surfaceDim = Color.Black,
)

private fun ColorScheme.toUltraColors() = UltraColors(
    background = Color.Black,
    // No grey underlays: every block lies directly on the AMOLED black.
    card = Color.Black,
    border = outlineVariant.copy(alpha = 0.7f),
    bar = Color(0xFF101012),
    accent = primary,
    accentContainer = primaryContainer,
    good = Color(0xFF86D99C),
    warn = Color(0xFFF2C46D),
    bad = error,
    text = onSurface,
    textDim = onSurfaceVariant,
)

private fun neonScheme(c: UltraColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.background,
    primaryContainer = c.accentContainer,
    onPrimaryContainer = c.accent,
    secondary = c.good,
    onSecondary = c.background,
    secondaryContainer = c.border,
    onSecondaryContainer = c.text,
    background = c.background,
    onBackground = c.text,
    surface = c.background,
    onSurface = c.text,
    surfaceVariant = c.card,
    onSurfaceVariant = c.textDim,
    surfaceContainerLowest = c.background,
    surfaceContainerLow = c.background,
    surfaceContainer = c.background,
    surfaceContainerHigh = c.background,
    surfaceContainerHighest = c.background,
    outline = c.border,
    outlineVariant = c.border,
    error = c.bad,
    onError = c.background,
)

@Composable
fun UltraCoreTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = ThemePrefs.dynamicColors
    val scheme = remember(dynamic) {
        if (dynamic) dynamicDarkColorScheme(context).toAmoled() else neonScheme(NeonColors)
    }
    val colors = remember(scheme, dynamic) { if (dynamic) scheme.toUltraColors() else NeonColors }
    // Children read the palette after this line, so this is not a backwards write.
    if (UltraPalette.current != colors) UltraPalette.current = colors

    MaterialTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        typography = expressiveTypography(),
        content = content,
    )
}
