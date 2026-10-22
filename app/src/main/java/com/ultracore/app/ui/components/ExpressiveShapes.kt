package com.ultracore.app.ui.components

import androidx.compose.ui.graphics.toArgb
import com.ultracore.app.ui.theme.ThemePrefs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ultracore.app.ui.theme.NeonCyan

/**
 * Shape from an official [RoundedPolygon] (androidx.graphics:graphics-shapes — the same library
 * Material 3 Expressive builds its shapes and LoadingIndicator on). The path is cached per size.
 */
class PolygonShape(polygon: RoundedPolygon, private val rotationDegrees: Float = 0f) : Shape {
    private val normalized = polygon.normalized()
    // One shape object is used at many sizes (22, 32, 44, 64 dp…): keep a path per size.
    // Outlines are created on the main thread, a plain map is enough.
    private val cache = HashMap<Size, Path>(4)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = cache.getOrPut(size) {
            val androidPath = normalized.toPath(android.graphics.Path())
            val matrix = android.graphics.Matrix()
            matrix.setScale(size.width, size.height)
            if (rotationDegrees != 0f) matrix.postRotate(rotationDegrees, size.width / 2f, size.height / 2f)
            androidPath.transform(matrix)
            if (cache.size >= MAX_CACHED) cache.clear()
            androidPath.asComposePath()
        }
        return Outline.Generic(path)
    }

    private companion object {
        const val MAX_CACHED = 12
    }
}

/** Polygons of the Material 3 Expressive shape catalogue (radius 1, centred at 0,0). */
object ExpressivePolygons {
    val Cookie: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 9, radius = 1f, innerRadius = 0.8f,
        rounding = CornerRounding(0.18f), innerRounding = CornerRounding(0.18f),
    )
    val Sunny: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 10, radius = 1f, innerRadius = 0.84f,
        rounding = CornerRounding(0.1f), innerRounding = CornerRounding(0.1f),
    )
    val Flower: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 8, radius = 1f, innerRadius = 0.62f,
        rounding = CornerRounding(0.3f), innerRounding = CornerRounding(0.08f),
    )
    val Clover: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 4, radius = 1f, innerRadius = 0.42f,
        rounding = CornerRounding(0.55f), innerRounding = CornerRounding(0.12f),
    )
    val Pentagon: RoundedPolygon = RoundedPolygon(numVertices = 5, radius = 1f, rounding = CornerRounding(0.28f))
    val Burst: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 12, radius = 1f, innerRadius = 0.72f,
        rounding = CornerRounding(0.06f), innerRounding = CornerRounding(0.06f),
    )
    val Soft: RoundedPolygon = RoundedPolygon(numVertices = 4, radius = 1f, rounding = CornerRounding(0.42f))
}

/** A few shapes from the Material 3 Expressive catalogue. */
object ExpressiveShapes {
    val Cookie: Shape = PolygonShape(ExpressivePolygons.Cookie, rotationDegrees = -90f)
    val Sunny: Shape = PolygonShape(ExpressivePolygons.Sunny, rotationDegrees = -90f)
    val Flower: Shape = PolygonShape(ExpressivePolygons.Flower, rotationDegrees = -90f)
    val Clover: Shape = PolygonShape(ExpressivePolygons.Clover, rotationDegrees = 45f)
    val Pentagon: Shape = PolygonShape(ExpressivePolygons.Pentagon, rotationDegrees = -90f)
    val Burst: Shape = PolygonShape(ExpressivePolygons.Burst)
    val Soft: Shape = PolygonShape(ExpressivePolygons.Soft, rotationDegrees = 45f)
    val Arch: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 18, bottomEndPercent = 18)
    val Pill: Shape = RoundedCornerShape(50)
}

private val LoadingSequence = listOf(
    ExpressivePolygons.Cookie,
    ExpressivePolygons.Clover,
    ExpressivePolygons.Sunny,
    ExpressivePolygons.Pentagon,
    ExpressivePolygons.Flower,
).map { it.normalized() }

/**
 * Material 3 Expressive LoadingIndicator: a filled shape that keeps morphing
 * cookie → clover → sunny → pentagon → flower while slowly rotating.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = NeonCyan,
) {
    val morphs = remember {
        LoadingSequence.indices.map { Morph(LoadingSequence[it], LoadingSequence[(it + 1) % LoadingSequence.size]) }
    }
    val transition = rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(morphs.size * 650, easing = LinearEasing)),
        label = "loadingPhase",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "loadingRotation",
    )
    val androidPath = remember { android.graphics.Path() }
    val composePath = remember(androidPath) { androidPath.asComposePath() }
    val matrix = remember { android.graphics.Matrix() }
    Canvas(modifier.size(size)) {
        val index = phase.toInt().coerceIn(0, morphs.lastIndex)
        val progress = FastOutSlowInEasing.transform((phase - index).coerceIn(0f, 1f))
        androidPath.rewind()
        morphs[index].toPath(progress, androidPath)
        matrix.reset()
        matrix.setScale(this.size.width, this.size.height)
        matrix.postRotate(rotation, this.size.width / 2f, this.size.height / 2f)
        androidPath.transform(matrix)
        drawPath(composePath, color)
    }
}

/**
 * Saturated tones used for shaped icons, as in the Expressive examples. With wallpaper colours on,
 * every tone is nudged towards the theme accent hue so the icons belong to the same palette.
 */
object ExpressiveTones {
    val Primary: Color get() = NeonCyan
    val Green: Color get() = tone(0xFF6DDC8B)
    val Orange: Color get() = tone(0xFFFFB77C)
    val Pink: Color get() = tone(0xFFFFA8C8)
    val Lime: Color get() = tone(0xFFD7EE6C)
    val Sky: Color get() = tone(0xFF8CCBFF)
    val Red: Color get() = tone(0xFFFF8A80)
    val Amber: Color get() = tone(0xFFFFCF6B)

    private var cachedAccent: Color = Color.Unspecified
    private var cachedDynamic: Boolean = false
    private val cache = HashMap<Long, Color>()

    private fun tone(argb: Long): Color {
        val base = Color(argb)
        val dynamic = ThemePrefs.dynamicColors
        if (!dynamic) return base
        val accent = NeonCyan
        if (accent != cachedAccent || dynamic != cachedDynamic) {
            cache.clear()
            cachedAccent = accent
            cachedDynamic = dynamic
        }
        return cache.getOrPut(argb) { base.harmonizeWith(accent) }
    }
}

/** Moves this colour's hue towards [target] by [fraction] (like MaterialColors.harmonize), keeps saturation/value. */
fun Color.harmonizeWith(target: Color, fraction: Float = 0.18f): Color {
    val src = FloatArray(3)
    val dst = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), src)
    android.graphics.Color.colorToHSV(target.toArgb(), dst)
    var diff = (dst[0] - src[0]) % 360f
    if (diff < -180f) diff += 360f
    if (diff > 180f) diff -= 360f
    src[0] = (src[0] + diff * fraction + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(src)).copy(alpha = alpha)
}

/** Icon on a filled expressive shape: saturated container, dark glyph. */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .background(color, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.82f), modifier = Modifier.size(size * 0.5f))
    }
}
