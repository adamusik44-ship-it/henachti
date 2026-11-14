package app.henachti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.henachti.data.Dedications
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import app.henachti.domain.LockState
import app.henachti.domain.TamperReason
import app.henachti.domain.UnlockReason
import app.henachti.ui.CardShape
import app.henachti.ui.GoldButton
import app.henachti.ui.Hairline
import app.henachti.ui.MilestoneCard
import app.henachti.ui.Palette
import app.henachti.ui.SectionRow
import app.henachti.ui.SnoozeDots
import app.henachti.ui.StreakCalendar
import app.henachti.ui.SunMascot
import app.henachti.ui.SunMood
import app.henachti.ui.TamperUi
import app.henachti.ui.restDayGreeting
import app.henachti.ui.restDayTitle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Home, playful and calm: one big word or number, the sun whose face is the day's state, one card
 * for today's task and one button. Below, a small stack of colourful cards (siddur, dedication,
 * streak; the streak card opens the dot calendar). Nothing else competes for attention.
 */
@Composable
fun HomeScreen(
    prefs: Prefs,
    policy: DayPolicy,
    state: LockState,
    now: Long,
    running: Boolean,
    onVerify: () -> Unit,
    onPrayers: () -> Unit,
    onDedication: () -> Unit,
    onSettings: () -> Unit,
    onFix: (TamperReason) -> Unit,
) {
    val armed = prefs.setupDone
    val today = remember(now) { Instant.ofEpochMilli(now).atZone(IsraelTime.zone).toLocalDate() }
    val misheyakir = remember(now) { policy.misheyakir(today) }
    val sunset = remember(now) { policy.sunset(today) }
    val streak = remember(now) { policy.streak(now) }
    var milestoneSeen by remember { mutableIntStateOf(prefs.milestoneSeen) }
    var calendarOpen by remember { mutableStateOf(false) }
    val look = lookFor(policy, prefs, state, now, today, misheyakir, sunset, streak, onVerify, onPrayers, onSettings, onFix)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // the name in the middle, settings at the end; an equal space at the start keeps it centred
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(48.dp))
            Text("וקשרתם", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Palette.Ink, CircleShape)
                    .clickable(role = Role.Button, onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = Palette.Ink, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(look.label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (state is LockState.Snoozed) {
            SnoozeClock(state.until)
        } else {
            Text(look.big, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        SunMascot(
            look.mood,
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 230.dp, height = 160.dp),
        )

        TaskCard(look, Modifier.padding(horizontal = 20.dp))
        look.action?.let { a ->
            Spacer(Modifier.height(14.dp))
            GoldButton(a.text, a.onClick, Modifier.fillMaxWidth().padding(horizontal = 20.dp), background = a.color)
        }
        val snooze = when (state) {
            is LockState.Locked -> if (state.snoozesPerDay > 0) state.snoozesLeft to state.snoozesPerDay else null
            is LockState.Snoozed -> (state.max - state.used).coerceAtLeast(0) to state.max
            else -> null
        }
        snooze?.let { (left, max) ->
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                SnoozeDots(left, max)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (left) {
                        0 -> "הדחיות של היום נגמרו"
                        1 -> "נשארה דחייה אחת של חצי שעה"
                        else -> "נשארו $left דחיות של חצי שעה"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Muted,
                )
            }
        }

        MilestoneCard(
            streak = streak,
            seen = milestoneSeen,
            onDismiss = { reached ->
                prefs.milestoneSeen = reached
                milestoneSeen = reached
            },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
        )

        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(236.dp)
        ) {
            StackCard("הסידור", "שחרית, מנחה, ערבית וברכת המזון", Palette.Blush, Palette.Wine, -2f, 0.dp, onPrayers)
            StackCard("לזכות", Dedications.label(prefs) ?: "להקדיש את ההנחה של היום", Palette.Wine, Palette.Cream, 1.5f, 66.dp, onDedication)
            StreakCard(streak, calendarOpen, 132.dp) { calendarOpen = !calendarOpen }
        }
        if (calendarOpen) {
            StreakCalendar(policy, prefs, now, Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp))
        }

        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 24.dp)) {
            Hairline()
            SectionRow(
                title = when {
                    !armed -> "המנעול לא דרוך"
                    running -> "המנעול דרוך"
                    else -> "המנעול דרוך, השירות ישן"
                },
                subtitle = when {
                    !armed -> "בלי דריכה הטלפון לא ננעל בבוקר"
                    running -> "נפתח רק אחרי התפילין של היום"
                    else -> "כאן מעירים אותו"
                },
                onClick = onSettings,
                dot = if (armed && running) Palette.Success else Palette.Coral,
            )
            Hairline()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** What Home shows for a state: the small label, the big word, the sun's mood, the task card, the button. */
private class Look(
    val label: String,
    val big: String,
    val mood: SunMood,
    val cardColor: Color,
    val cardText: Color,
    val kicker: String,
    val title: String,
    val body: String? = null,
    val tags: List<String> = emptyList(),
    val tagColor: Color = Palette.Blush,
    val tagText: Color = Palette.Wine,
    val action: Action? = null,
)

private class Action(val text: String, val onClick: () -> Unit, val color: Color)

private fun countdown(now: Long, sunset: Long): Pair<String, String> {
    val totalMin = (((sunset - now) + 59_999L) / 60_000L).toInt()
    return when {
        totalMin <= 0 -> "השמש כבר שקעה" to "נעול"
        totalMin >= 60 -> "עד השקיעה" to String.format(Locale.US, "%d:%02d", totalMin / 60, totalMin % 60)
        else -> "דקות עד השקיעה" to "$totalMin"
    }
}

private fun lookFor(
    policy: DayPolicy,
    prefs: Prefs,
    state: LockState,
    now: Long,
    today: LocalDate,
    misheyakir: Long,
    sunset: Long,
    streak: Int,
    onVerify: () -> Unit,
    onPrayers: () -> Unit,
    onSettings: () -> Unit,
    onFix: (TamperReason) -> Unit,
): Look {
    val night = now < misheyakir || now >= sunset
    val streakTag = if (streak == 1) "יום אחד ברצף" else "$streak ימים ברצף"
    return when (state) {
        is LockState.Tampered -> Look(
            label = "משהו כובה", big = "רגע", mood = SunMood.WORRIED,
            cardColor = Palette.Coral, cardText = Palette.Cream,
            kicker = "המנעול צריך תיקון", title = TamperUi.title(state.reason), body = TamperUi.body(state.reason),
            action = Action(TamperUi.action(state.reason), { onFix(state.reason) }, Palette.Ink),
        )
        is LockState.Snoozed -> Look(
            label = "עד שהמנעול חוזר", big = "", mood = SunMood.SLEEPY,
            cardColor = Palette.Blush, cardText = Palette.Wine,
            kicker = "דחייה ${state.used} מתוך ${state.max}", title = "הכול פתוח עד ${IsraelTime.hhmm(state.until)}",
            tags = listOf("ואז המנעול חוזר"), tagColor = Palette.Cream, tagText = Palette.Wine,
            action = Action("לאימות במצלמה", onVerify, Palette.Ink),
        )
        is LockState.Locked -> {
            val (label, big) = countdown(now, sunset)
            Look(
                label = label, big = big, mood = SunMood.WAITING,
                cardColor = Palette.Teal, cardText = Palette.Cream,
                kicker = "המשימה של היום", title = "להניח תפילין",
                tags = listOf("אפשר מ-${IsraelTime.hhmm(misheyakir)}", "7 כריכות"),
                action = Action("לאימות במצלמה", onVerify, Palette.Ink),
            )
        }
        is LockState.Unlocked -> when (state.reason) {
            UnlockReason.VERIFIED -> Look(
                label = if (prefs.verifiedAt > 0L) "הנחת היום ב-${IsraelTime.hhmm(prefs.verifiedAt)}" else "הנחת היום",
                big = "פתוח", mood = if (night) SunMood.NIGHT else SunMood.HAPPY,
                cardColor = Palette.Lime, cardText = Palette.Ink,
                kicker = "היום", title = "הטלפון פתוח עד מחר בבוקר",
                tags = listOf(streakTag), tagColor = Palette.Cream, tagText = Palette.Teal,
                action = Action("לסידור", onPrayers, Palette.Teal),
            )
            UnlockReason.BEFORE_START -> {
                val (label, big) = countdown(now, sunset)
                Look(
                    label = label, big = big, mood = SunMood.WAITING,
                    cardColor = Palette.Teal, cardText = Palette.Cream,
                    kicker = "המשימה של היום", title = "להניח תפילין",
                    tags = listOf("המנעול נדרך ב-${IsraelTime.hhmm(policy.lockStart(state.day))}", "אפשר כבר לאמת"),
                    action = Action("לאימות במצלמה", onVerify, Palette.Ink),
                )
            }
            UnlockReason.NO_TEFILLIN_DAY -> {
                val reason = policy.noTefillinReason(state.day)
                Look(
                    label = "אין הנחה היום",
                    big = restDayGreeting(reason, afterSunset = now >= policy.sunset(state.day)),
                    mood = if (night) SunMood.NIGHT else SunMood.CALM,
                    cardColor = Palette.Wine, cardText = Palette.Cream,
                    kicker = restDayTitle(reason), title = "המנעול נח היום",
                    action = Action("לסידור", onPrayers, Palette.Teal),
                )
            }
            UnlockReason.SHABBAT_BEGAN -> Look(
                label = "השמש שקעה", big = "שבת שלום", mood = SunMood.NIGHT,
                cardColor = Palette.Wine, cardText = Palette.Cream,
                kicker = "ערב שבת", title = "המנעול נח עד מוצאי שבת",
                action = Action("לסידור", onPrayers, Palette.Teal),
            )
            UnlockReason.AFTER_SUNSET -> {
                val next = if (now < misheyakir) misheyakir else policy.misheyakir(today.plusDays(1))
                Look(
                    label = if (now < misheyakir) "אפשר להניח מ-" else "מחר אפשר להניח מ-",
                    big = IsraelTime.hhmm(next), mood = SunMood.NIGHT,
                    cardColor = Palette.Teal, cardText = Palette.Cream,
                    kicker = "היום", title = "זמן התפילין של היום עבר",
                    tags = listOf("מחר בבוקר מתחילים נקי"),
                    action = Action("לסידור", onPrayers, Palette.Teal),
                )
            }
            UnlockReason.SETUP_INCOMPLETE -> Look(
                label = "המנעול עוד לא דרוך", big = "שלום", mood = SunMood.WAITING,
                cardColor = Palette.Coral, cardText = Palette.Cream,
                kicker = "צעד אחד", title = "בלי דריכה הטלפון לא ננעל בבוקר",
                action = Action("לדריכת המנעול", onSettings, Palette.Ink),
            )
        }
    }
}

@Composable
private fun TaskCard(look: Look, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(look.cardColor)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(look.kicker, style = MaterialTheme.typography.labelMedium, color = look.cardText.copy(alpha = 0.8f))
        Spacer(Modifier.height(2.dp))
        Text(look.title, style = MaterialTheme.typography.headlineMedium, color = look.cardText)
        look.body?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = look.cardText)
        }
        if (look.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row {
                look.tags.forEach { t ->
                    Text(
                        t,
                        style = MaterialTheme.typography.labelMedium,
                        color = look.tagText,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(look.tagColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** The snooze's half hour as a big live clock. */
@Composable
private fun SnoozeClock(until: Long) {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (true) {
            t = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val left = (until - t).coerceAtLeast(0L)
    Text(
        String.format(Locale.US, "%d:%02d", left / 60_000L, (left / 1_000L) % 60L),
        style = MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One of the stacked cards: a bold title, one line, a little square; slightly tilted like a real stack. */
@Composable
private fun StackCard(title: String, subtitle: String, bg: Color, fg: Color, tilt: Float, top: Dp, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = top)
            .fillMaxWidth()
            .height(98.dp)
            .graphicsLayer { rotationZ = tilt }
            .clip(CardShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = fg)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(24.dp)
                .border(2.5.dp, fg, RoundedCornerShape(7.dp))
        )
    }
}

/** The streak on the top card of the stack, with the number in a ring; tapping opens the dot calendar. */
@Composable
private fun StreakCard(streak: Int, open: Boolean, top: Dp, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = top)
            .fillMaxWidth()
            .height(104.dp)
            .clip(CardShape)
            .background(Palette.Coral)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (streak == 1) "יום אחד ברצף" else "$streak ימים ברצף", style = MaterialTheme.typography.headlineMedium, color = Palette.Cream)
            Text(
                if (open) "לחיצה סוגרת את הלוח" else "לחיצה פותחת את הלוח. שבת וחג לא שוברים",
                style = MaterialTheme.typography.labelMedium, color = Palette.Cream.copy(alpha = 0.9f),
            )
        }
        Box(
            Modifier
                .size(66.dp)
                .border(6.dp, Palette.Cream.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$streak", style = MaterialTheme.typography.headlineMedium, color = Palette.Cream)
        }
    }
}
