package app.henachti.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** The light of the hour, for the home panel: four warm browns, no neon. */
enum class DayPhase { NIGHT, DAWN, DAY, DUSK }

fun dayPhase(now: Long, misheyakir: Long, sunset: Long): DayPhase = when {
    now < misheyakir -> DayPhase.NIGHT
    now < misheyakir + 2L * 3_600_000L -> DayPhase.DAWN
    now < sunset - 75L * 60_000L -> DayPhase.DAY
    now < sunset + 45L * 60_000L -> DayPhase.DUSK
    else -> DayPhase.NIGHT
}

/** Top-to-bottom colours of the home panel for each phase of the day. */
fun phaseColors(phase: DayPhase): List<Color> = when (phase) {
    DayPhase.NIGHT -> listOf(Color(0xFF120E0B), Color(0xFF231B15))
    DayPhase.DAWN -> listOf(Color(0xFF231B14), Color(0xFF5A422A))
    DayPhase.DAY -> listOf(Color(0xFF2B2219), Color(0xFF433427))
    DayPhase.DUSK -> listOf(Color(0xFF1E1611), Color(0xFF6B452A))
}

/**
 * The tefillin window as a sky over a horizon line: misheyakir on the right (where Hebrew starts),
 * sunset on the left, a small mark for every whole hour, the gold path of the sun so far and the
 * sun itself. Before misheyakir a hollow ring waits on the right; after sunset it rests on the left.
 */
@Composable
fun SkyArc(start: Long, end: Long, now: Long, modifier: Modifier = Modifier) {
    val span = (end - start).coerceAtLeast(1L)
    val fraction = ((now - start).toFloat() / span).coerceIn(0f, 1f)
    Canvas(modifier) {
        val sun = 9.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height - 4.dp.toPx()
        val rx = size.width / 2f - sun * 1.6f
        val ry = size.height - sun * 2.6f
        val topLeft = Offset(cx - rx, cy - ry)
        val oval = Size(rx * 2f, ry * 2f)
        fun at(f: Float): Offset {
            val a = Math.toRadians(-180.0 * f)
            return Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }

        // horizon
        drawLine(
            Palette.GoldSoft.copy(alpha = 0.35f),
            Offset(0f, cy), Offset(size.width, cy),
            strokeWidth = 1.dp.toPx(),
        )
        // the whole window, dashed
        drawArc(
            Palette.Cream.copy(alpha = 0.26f), 180f, 180f, false, topLeft, oval,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 7.dp.toPx()), 0f),
            ),
        )
        // a mark on every whole hour (Israel is a whole number of hours from UTC)
        var hour = (start / 3_600_000L + 1L) * 3_600_000L
        while (hour < end) {
            val f = (hour - start).toFloat() / span
            drawCircle(
                if (f <= fraction) Palette.GoldSoft else Palette.Cream.copy(alpha = 0.35f),
                2.dp.toPx(), at(f),
            )
            hour += 3_600_000L
        }
        // the sun's path so far
        if (fraction > 0f) {
            drawArc(
                Palette.Gold, 0f, -180f * fraction, false, topLeft, oval,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        val p = at(fraction)
        if (fraction > 0f && fraction < 1f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Palette.Gold.copy(alpha = 0.38f), Color.Transparent),
                    center = p, radius = sun * 3.2f,
                ),
                sun * 3.2f, p,
            )
            drawCircle(Palette.Gold, sun, p)
            drawCircle(Palette.GoldSoft, sun * 0.45f, p)
        } else {
            drawCircle(Palette.Gold, sun * 0.8f, p, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
