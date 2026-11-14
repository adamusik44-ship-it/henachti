package app.henachti.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The sun: the app's one character. The whole app runs on its path from misheyakir to sunset,
 * and its face says where the day stands, so it carries meaning rather than decoration.
 */
enum class SunMood { WAITING, HAPPY, SLEEPY, CALM, WORRIED, NIGHT }

/** The sun (or, at night, a sleeping moon) sitting behind a rounded hill. */
@Composable
fun SunMascot(mood: SunMood, modifier: Modifier = Modifier, hill: Color? = Palette.Lime) {
    // clipped, so the hill ends at the mascot's own bottom edge and never covers what comes below
    Canvas(modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        val r = min(w, h) * 0.30f
        val c = Offset(w / 2f, h * 0.56f)
        if (mood == SunMood.NIGHT) moon(c, r) else sun(c, r, mood)
        if (hill != null) {
            drawOval(hill, topLeft = Offset(-w * 0.12f, h * 0.80f), size = Size(w * 1.24f, h * 0.70f))
        }
    }
}

private val Face = Color(0xFF1D1B19)

private fun DrawScope.sun(c: Offset, r: Float, mood: SunMood) {
    // rays: up, the two diagonals, left and right
    val ray = r * 0.15f
    for (deg in listOf(-90.0, -135.0, -45.0, 180.0, 0.0)) {
        val a = Math.toRadians(deg)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            Palette.Sun,
            Offset(c.x + r * 1.24f * ca, c.y + r * 1.24f * sa),
            Offset(c.x + r * 1.52f * ca, c.y + r * 1.52f * sa),
            strokeWidth = ray, cap = StrokeCap.Round,
        )
    }
    drawCircle(Palette.Sun, r, c)

    val eyeDx = r * 0.33f
    val eyeY = c.y - r * 0.12f
    val line = r * 0.085f
    // cheeks
    for (sx in listOf(-1f, 1f)) {
        drawOval(
            Palette.Blush,
            topLeft = Offset(c.x + sx * r * 0.58f - r * 0.17f, c.y + r * 0.12f),
            size = Size(r * 0.34f, r * 0.20f),
        )
    }
    // eyes
    for (sx in listOf(-1f, 1f)) {
        val ex = c.x + sx * eyeDx
        when (mood) {
            SunMood.WAITING, SunMood.WORRIED -> drawCircle(Face, r * 0.11f, Offset(ex, eyeY))
            SunMood.HAPPY, SunMood.CALM -> drawArc(
                Face, 200f, 140f, false,
                topLeft = Offset(ex - r * 0.15f, eyeY - r * 0.08f), size = Size(r * 0.30f, r * 0.26f),
                style = Stroke(width = line, cap = StrokeCap.Round),
            )
            SunMood.SLEEPY -> drawLine(
                Face, Offset(ex - r * 0.13f, eyeY + r * 0.02f), Offset(ex + r * 0.13f, eyeY + r * 0.02f),
                strokeWidth = line, cap = StrokeCap.Round,
            )
            SunMood.NIGHT -> Unit
        }
    }
    if (mood == SunMood.WORRIED) {
        // raised inner brows
        for (sx in listOf(-1f, 1f)) {
            val ex = c.x + sx * eyeDx
            drawLine(
                Face, Offset(ex - sx * r * 0.15f, eyeY - r * 0.22f), Offset(ex + sx * r * 0.10f, eyeY - r * 0.30f),
                strokeWidth = line * 0.9f, cap = StrokeCap.Round,
            )
        }
    }
    // mouth
    val my = c.y + r * 0.22f
    when (mood) {
        SunMood.HAPPY -> drawArc(
            Palette.Wine, 0f, 180f, true,
            topLeft = Offset(c.x - r * 0.24f, my - r * 0.16f), size = Size(r * 0.48f, r * 0.36f),
        )
        SunMood.SLEEPY -> drawCircle(Face, r * 0.07f, Offset(c.x, my + r * 0.04f), style = Stroke(width = line * 0.8f))
        SunMood.WORRIED -> drawLine(
            Face, Offset(c.x - r * 0.16f, my + r * 0.04f), Offset(c.x + r * 0.16f, my + r * 0.04f),
            strokeWidth = line, cap = StrokeCap.Round,
        )
        else -> drawArc(
            Face, 20f, 140f, false,
            topLeft = Offset(c.x - r * 0.18f, my - r * 0.14f), size = Size(r * 0.36f, r * 0.26f),
            style = Stroke(width = line, cap = StrokeCap.Round),
        )
    }
}

/** A sleeping crescent moon for the night. */
private fun DrawScope.moon(c: Offset, r: Float) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x - r * 0.55f, c.y - r * 0.30f), r * 0.86f)) }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(crescent, Palette.GoldSoft)
    // a closed eye and a small smile on the lit side
    val ex = c.x + r * 0.42f
    val ey = c.y - r * 0.05f
    drawArc(
        Face, 20f, 140f, false,
        topLeft = Offset(ex - r * 0.13f, ey - r * 0.10f), size = Size(r * 0.26f, r * 0.20f),
        style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
    )
    drawArc(
        Face, 20f, 140f, false,
        topLeft = Offset(ex - r * 0.10f, ey + r * 0.20f), size = Size(r * 0.22f, r * 0.16f),
        style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
    )
    // two small stars
    drawCircle(Palette.GoldSoft, r * 0.06f, Offset(c.x - r * 1.15f, c.y - r * 0.85f))
    drawCircle(Palette.GoldSoft, r * 0.04f, Offset(c.x + r * 1.20f, c.y - r * 1.05f))
}
