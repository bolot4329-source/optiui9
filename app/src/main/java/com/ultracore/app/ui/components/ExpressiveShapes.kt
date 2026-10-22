package com.ultracore.app.ui.components

import androidx.compose.foundation.background
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooth «blob» shape from the Material 3 Expressive shape set: a circle whose radius waves
 * [lobes] times around (r = 1 − depth·(1 − cos nθ)/2). Few deep lobes give a clover,
 * many shallow ones a cookie or a sunny burst.
 */
class PolarShape(
    private val lobes: Int,
    private val depth: Float,
    private val rotationDegrees: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val steps = 240
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rot = rotationDegrees / 180f * PI.toFloat()
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * PI.toFloat()
            val r = 1f - depth * (1f - cos(lobes * (t - rot))) / 2f
            val x = cx + cx * r * cos(t)
            val y = cy + cy * r * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** A few shapes from the Material 3 Expressive catalogue. */
object ExpressiveShapes {
    val Cookie: Shape = PolarShape(lobes = 9, depth = 0.12f)
    val Sunny: Shape = PolarShape(lobes = 12, depth = 0.10f)
    val Flower: Shape = PolarShape(lobes = 8, depth = 0.22f, rotationDegrees = 22.5f)
    val Clover: Shape = PolarShape(lobes = 4, depth = 0.36f, rotationDegrees = 45f)
    val Pentagon: Shape = PolarShape(lobes = 5, depth = 0.16f, rotationDegrees = -90f)
    val Soft: Shape = PolarShape(lobes = 4, depth = 0.08f, rotationDegrees = 45f)
    val Arch: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 18, bottomEndPercent = 18)
    val Pill: Shape = RoundedCornerShape(50)
}

/** Saturated tones used for shaped icons, as in the Expressive examples. */
object ExpressiveTones {
    val Primary: Color get() = NeonCyan
    val Green = Color(0xFF6DDC8B)
    val Orange = Color(0xFFFFB77C)
    val Pink = Color(0xFFFFA8C8)
    val Lime = Color(0xFFD7EE6C)
    val Sky = Color(0xFF8CCBFF)
    val Red = Color(0xFFFF8A80)
    val Amber = Color(0xFFFFCF6B)
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
