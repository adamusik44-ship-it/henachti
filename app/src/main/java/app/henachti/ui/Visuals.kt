package app.henachti.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.henachti.R
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** "שעתיים ו-20 דקות", "40 דקות", "שעה". Rounded up to the minute. */
fun remainingText(ms: Long): String {
    val totalMin = ((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    val hours = when (h) {
        0 -> ""
        1 -> "שעה"
        2 -> "שעתיים"
        else -> "$h שעות"
    }
    val mins = when (m) {
        0 -> ""
        1 -> "דקה"
        else -> "$m דקות"
    }
    return when {
        h == 0 -> mins
        m == 0 -> hours
        else -> "$hours ו-$mins"
    }
}

fun daysText(n: Int): String = if (n == 1) "יום אחד" else "$n ימים"

/** Greeting for a day without tefillin. Tisha B'Av is a fast day, so it gets no "שמח". */
fun restDayTitle(reason: String?): String = when (reason) {
    "שבת" -> "שבת שלום"
    "יום טוב" -> "חג שמח"
    "חול המועד" -> "מועדים לשמחה"
    "תשעה באב" -> "תשעה באב"
    else -> "יום מנוחה"
}

/**
 * The tefillin window of the day as an arc: from misheyakir (right, where Hebrew starts)
 * to sunset (left). The gold part is the time that has passed, the dot is "now".
 */
@Composable
fun DayArc(start: Long, end: Long, now: Long, modifier: Modifier = Modifier) {
    val span = (end - start).coerceAtLeast(1L)
    val fraction = ((now - start).toFloat() / span).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val stroke = 2.5.dp.toPx()
            val dot = 7.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height - dot
            val rx = size.width / 2f - dot
            val ry = size.height - dot * 2f
            val topLeft = Offset(cx - rx, cy - ry)
            val oval = Size(rx * 2f, ry * 2f)
            drawArc(
                Palette.Sand, 180f, 180f, false, topLeft, oval,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()), 0f),
                ),
            )
            if (fraction > 0f) {
                drawArc(
                    Palette.Gold, 0f, -180f * fraction, false, topLeft, oval,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            val angle = Math.toRadians(-180.0 * fraction)
            val p = Offset(cx + rx * cos(angle).toFloat(), cy + ry * sin(angle).toFloat())
            drawCircle(Palette.Gold, dot, p)
            drawCircle(Palette.ChocolateDeep, dot * 0.42f, p)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("מ-${IsraelTime.hhmm(start)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Spacer(Modifier.weight(1f))
            Text("שקיעה ${IsraelTime.hhmm(end)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        }
        Spacer(Modifier.height(6.dp))
        val left = end - now
        Text(
            if (left > 60_000L) "עוד ${remainingText(left)} עד השקיעה" else "השקיעה עברה",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.GoldDeep,
            textAlign = TextAlign.Center,
        )
    }
}

/** Snoozes as dots: full = still available, empty ring = used. */
@Composable
fun SnoozeDots(left: Int, max: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i ->
            val full = i < left
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (full) Palette.Gold else Color.Transparent)
                    .border(1.5.dp, if (full) Palette.Gold else Palette.Sand, CircleShape)
            )
        }
    }
}

/** The half hour of a snooze as a ring that empties, with minutes and seconds in the middle. */
@Composable
fun CountdownRing(until: Long, total: Long, modifier: Modifier = Modifier, diameter: Dp = 116.dp) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val left = (until - now).coerceAtLeast(0L)
    val fraction = (left.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val ring = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Palette.Sand, 0f, 360f, false, Offset(inset, inset), ring, style = Stroke(width = stroke))
            if (fraction > 0f) {
                drawArc(
                    Palette.Gold, -90f, -360f * fraction, false, Offset(inset, inset), ring,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                String.format(Locale.US, "%d:%02d", left / 60_000L, (left / 1_000L) % 60L),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("עד שהמנעול חוזר", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

/** A lock drawn in gold whose shackle lifts open once `open` turns true. */
@Composable
fun OpeningLock(open: Boolean, modifier: Modifier = Modifier, tint: Color = Palette.Gold) {
    val lift = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) lift.animateTo(1f, tween(durationMillis = 700)) else lift.snapTo(0f)
    }
    Canvas(modifier.size(60.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 3.dp.toPx()
        val bodyTop = h * 0.46f
        val bodyLeft = w * 0.18f
        val bodyW = w * 0.64f
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, h * 0.48f),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawCircle(tint, 3.5.dp.toPx(), Offset(w / 2f, bodyTop + h * 0.22f))

        val shackleW = w * 0.40f
        val left = (w - shackleW) / 2f
        val up = lift.value * h * 0.16f
        val arcTop = h * 0.10f - up
        drawArc(
            tint, 180f, 180f, false, Offset(left, arcTop), Size(shackleW, shackleW),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val legTop = arcTop + shackleW / 2f
        // the left leg stays in the body (the hinge); the right one leaves it when the lock opens
        drawLine(tint, Offset(left, legTop), Offset(left, bodyTop), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(
            tint, Offset(left + shackleW, legTop), Offset(left + shackleW, bodyTop - up),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
    }
}

/** Thin progress bar for the first-run steps; fills from the right. */
@Composable
fun StepBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = ((step + 1).toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Palette.Sand)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Palette.Gold)
        )
    }
}

private enum class DayKind { VERIFIED, MISSED, REST, TODAY, QUIET }

/**
 * The streak as a dot calendar: the last five weeks, Sunday on the right.
 * Gold = verified, empty ring = a tefillin day that was missed, small grey dot = Shabbat or a
 * holiday (never breaks the streak), gold ring = today, faint = future or before the lock was armed.
 */
@Composable
fun StreakCalendar(policy: DayPolicy, prefs: Prefs, now: Long, modifier: Modifier = Modifier) {
    val streak = remember(now) { policy.streak(now) }
    val weeks = remember(now) {
        val today = policy.currentDay(now)
        val verified = prefs.verifiedDays()
        val armedDay: LocalDate? = if (prefs.armedAt > 0L)
            Instant.ofEpochMilli(prefs.armedAt).atZone(IsraelTime.zone).toLocalDate() else null
        val firstSunday = today.minusDays((today.dayOfWeek.value % 7).toLong()).minusWeeks(4)
        (0 until 5).map { w ->
            (0 until 7).map { d ->
                val day = firstSunday.plusDays((w * 7 + d).toLong())
                when {
                    day.toString() in verified -> DayKind.VERIFIED
                    day.isAfter(today) -> DayKind.QUIET
                    !policy.isTefillinDay(day) -> DayKind.REST
                    day == today -> DayKind.TODAY
                    armedDay == null || day.isBefore(armedDay) -> DayKind.QUIET
                    else -> DayKind.MISSED
                }
            }
        }
    }

    SoftCard(modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_flame), contentDescription = null, tint = Palette.GoldDeep, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("הרצף שלך", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(daysText(streak), style = MaterialTheme.typography.titleLarge, color = Palette.GoldDeep)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("א", "ב", "ג", "ד", "ה", "ו", "ש").forEach { letter ->
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    week.forEach { kind ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DayDot(kind) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                DayDot(DayKind.VERIFIED)
                Spacer(Modifier.width(5.dp))
                Text("הנחת", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Spacer(Modifier.width(14.dp))
                DayDot(DayKind.REST)
                Spacer(Modifier.width(5.dp))
                Text("שבת וחג", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Spacer(Modifier.width(14.dp))
                DayDot(DayKind.MISSED)
                Spacer(Modifier.width(5.dp))
                Text("פוספס", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun DayDot(kind: DayKind) {
    when (kind) {
        DayKind.VERIFIED -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Palette.Gold)
        )
        DayKind.MISSED -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(1.5.dp, Palette.Sand, CircleShape)
        )
        DayKind.TODAY -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(2.dp, Palette.Gold, CircleShape)
        )
        DayKind.REST -> Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Palette.Sand)
        )
        DayKind.QUIET -> Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Palette.Sand.copy(alpha = 0.6f))
        )
    }
}

private val MILESTONES = listOf(
    7 to "שבוע שלם ברצף.",
    30 to "חודש שלם ברצף.",
    100 to "מאה ימים ברצף.",
    365 to "שנה שלמה ברצף.",
)

/** A quiet gold card the first time the streak reaches 7, 30, 100 or 365 days. */
@Composable
fun MilestoneCard(streak: Int, seen: Int, onDismiss: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reached = MILESTONES.lastOrNull { streak >= it.first && it.first > seen } ?: return
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Chocolate)
            .border(1.5.dp, Palette.Gold, shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ציון דרך", style = MaterialTheme.typography.labelMedium, color = Palette.GoldSoft)
        Spacer(Modifier.height(6.dp))
        Text(reached.second, style = MaterialTheme.typography.headlineSmall, color = Palette.Cream, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "${reached.first} ימים של הנחה בלי הפסקה. שבת וחג לא שוברים את הרצף.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Sand,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { onDismiss(reached.first) }) {
            Text("תודה", color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
