package app.henachti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.henachti.R
import app.henachti.data.Dedications
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import app.henachti.domain.LockState
import app.henachti.domain.TamperReason
import app.henachti.domain.UnlockReason
import app.henachti.service.BlockerService
import app.henachti.service.findActivity
import app.henachti.ui.CountdownRing
import app.henachti.ui.GoldButton
import app.henachti.ui.Hairline
import app.henachti.ui.MilestoneCard
import app.henachti.ui.OpeningLock
import app.henachti.ui.Palette
import app.henachti.ui.SectionRow
import app.henachti.ui.SkyArc
import app.henachti.ui.SnoozeDots
import app.henachti.ui.StreakCalendar
import app.henachti.ui.TamperUi
import app.henachti.ui.dayPhase
import app.henachti.ui.phaseColors
import app.henachti.ui.restDayGreeting
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Home. The dark panel holds the day: one sign under the sky arc and one sentence per state.
 * Below it the page is plain: the streak as a sentence over the dot calendar, then three rows.
 * No cards, no icon tiles.
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
    LightStatusIcons()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        DayPanel(prefs, policy, state, now, onVerify, onSettings, onFix)

        Column(Modifier.padding(horizontal = 24.dp)) {
            val streak = remember(now) { policy.streak(now) }
            var milestoneSeen by remember { mutableIntStateOf(prefs.milestoneSeen) }
            MilestoneCard(
                streak = streak,
                seen = milestoneSeen,
                onDismiss = { reached ->
                    prefs.milestoneSeen = reached
                    milestoneSeen = reached
                },
                modifier = Modifier.padding(top = 22.dp),
            )

            Spacer(Modifier.height(26.dp))
            StreakCalendar(policy, prefs, now)

            Spacer(Modifier.height(26.dp))
            Hairline()
            SectionRow("הסידור", "שחרית, מנחה, ערבית וברכת המזון", onPrayers)
            Hairline()
            SectionRow("לזכות", Dedications.label(prefs) ?: "להקדיש את ההנחה של היום", onDedication)
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
                dot = if (armed && running) Palette.Success else Palette.GoldDeep,
            )
            Hairline()
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The panel is dark, so while Home is shown the status-bar icons turn light. */
@Composable
private fun LightStatusIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }
}

/**
 * The top of Home: a dark panel whose shade follows the hour, the sky arc of today's tefillin
 * window, the one sign that says where the day stands, one sentence, and the one action that matters now.
 * The clock is the phone's own, in the status bar above; it is not repeated here.
 */
@Composable
private fun DayPanel(
    prefs: Prefs,
    policy: DayPolicy,
    state: LockState,
    now: Long,
    onVerify: () -> Unit,
    onSettings: () -> Unit,
    onFix: (TamperReason) -> Unit,
) {
    val today = remember(now) { Instant.ofEpochMilli(now).atZone(IsraelTime.zone).toLocalDate() }
    // keyed on `now`, so a city change in Settings shows up on the next refresh
    val misheyakir = remember(now) { policy.misheyakir(today) }
    val sunset = remember(now) { policy.sunset(today) }
    val phase = dayPhase(now, misheyakir, sunset)
    val name = prefs.userName
    val greeting = if (name.isBlank()) policy.greeting(now) else "${policy.greeting(now)}, $name"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.verticalGradient(phaseColors(phase)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // greeting and date in the middle, settings at the end; an equal space at the start keeps it centred
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(greeting, style = MaterialTheme.typography.titleMedium, color = Palette.Cream, textAlign = TextAlign.Center)
                Text(policy.hebrewDate(state.day), style = MaterialTheme.typography.labelMedium, color = Palette.GoldSoft)
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, Palette.Cream.copy(alpha = 0.18f), CircleShape)
                    .clickable(role = Role.Button, onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = Palette.Cream, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
        ) {
            SkyArc(
                misheyakir, sunset, now,
                Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {
                        contentDescription =
                            "זמן ההנחה היום: מ-${IsraelTime.hhmm(misheyakir)} עד השקיעה ב-${IsraelTime.hhmm(sunset)}"
                    },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Centerpiece(prefs, policy, state, now, today, misheyakir, sunset)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("אפשר להניח מ-${IsraelTime.hhmm(misheyakir)}", style = MaterialTheme.typography.labelMedium, color = Palette.Sand.copy(alpha = 0.8f))
            Spacer(Modifier.weight(1f))
            Text("שקיעה ${IsraelTime.hhmm(sunset)}", style = MaterialTheme.typography.labelMedium, color = Palette.Sand.copy(alpha = 0.8f))
        }

        Spacer(Modifier.height(22.dp))
        val (sentence, detail) = panelTexts(policy, state)
        Text(sentence, style = MaterialTheme.typography.headlineSmall, color = Palette.Cream, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Sand, textAlign = TextAlign.Center)
        }

        when (state) {
            is LockState.Locked -> {
                Spacer(Modifier.height(20.dp))
                GoldButton("לאימות במצלמה", onVerify, Modifier.fillMaxWidth())
                if (state.snoozesPerDay > 0) {
                    Spacer(Modifier.height(12.dp))
                    SnoozeRow(state.snoozesLeft, state.snoozesPerDay)
                }
            }
            is LockState.Snoozed -> {
                Spacer(Modifier.height(20.dp))
                GoldButton("לאימות במצלמה", onVerify, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SnoozeRow((state.max - state.used).coerceAtLeast(0), state.max)
            }
            is LockState.Tampered -> {
                Spacer(Modifier.height(20.dp))
                GoldButton(TamperUi.action(state.reason), { onFix(state.reason) }, Modifier.fillMaxWidth())
            }
            is LockState.Unlocked -> when (state.reason) {
                UnlockReason.BEFORE_START -> {
                    Spacer(Modifier.height(20.dp))
                    GoldButton("לאימות במצלמה", onVerify, Modifier.fillMaxWidth())
                }
                UnlockReason.SETUP_INCOMPLETE -> {
                    Spacer(Modifier.height(20.dp))
                    GoldButton("לדריכת המנעול", onSettings, Modifier.fillMaxWidth())
                }
                else -> Unit
            }
        }
    }
}

/** What sits under the arc: the one number or sign that says where the day stands. */
@Composable
private fun Centerpiece(
    prefs: Prefs,
    policy: DayPolicy,
    state: LockState,
    now: Long,
    today: LocalDate,
    misheyakir: Long,
    sunset: Long,
) {
    val big = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp, lineHeight = 52.sp)
    val verified = state is LockState.Unlocked && state.reason == UnlockReason.VERIFIED
    val rest = state is LockState.Unlocked &&
        (state.reason == UnlockReason.NO_TEFILLIN_DAY || state.reason == UnlockReason.SHABBAT_BEGAN)
    when {
        verified -> {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(2.dp, Palette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(8.dp))
            Caption(if (prefs.verifiedAt > 0L) "הנחת ב-${IsraelTime.hhmm(prefs.verifiedAt)}" else "הנחת היום")
        }
        state is LockState.Snoozed -> CountdownRing(until = state.until, total = BlockerService.SNOOZE_MS, diameter = 118.dp, dark = true)
        rest -> {
            Icon(painterResource(R.drawable.ic_candle), contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Caption(
                if (state is LockState.Unlocked && state.reason == UnlockReason.SHABBAT_BEGAN) "שבת שלום"
                else restDayGreeting(policy.noTefillinReason(state.day), afterSunset = now >= policy.sunset(state.day))
            )
        }
        state is LockState.Unlocked && state.reason == UnlockReason.SETUP_INCOMPLETE -> {
            OpeningLock(open = false, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(6.dp))
            Caption("עוד לא דרוך")
        }
        state is LockState.Tampered -> {
            OpeningLock(open = false, modifier = Modifier.size(54.dp), tint = Palette.GoldSoft)
            Spacer(Modifier.height(6.dp))
            Caption("משהו כובה")
        }
        now < misheyakir -> {
            Text(IsraelTime.hhmm(misheyakir), style = big, color = Palette.Cream)
            Caption("אפשר להניח מהשעה הזאת")
        }
        now >= sunset -> {
            Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = Palette.GoldSoft, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Caption("מחר מ-${IsraelTime.hhmm(policy.misheyakir(today.plusDays(1)))}")
        }
        else -> {
            val totalMin = (((sunset - now) + 59_999L) / 60_000L).toInt()
            if (totalMin >= 60) {
                Text(String.format(Locale.US, "%d:%02d", totalMin / 60, totalMin % 60), style = big, color = Palette.Cream)
                Caption("שעות עד השקיעה")
            } else {
                Text("$totalMin", style = big, color = Palette.Cream)
                Caption("דקות עד השקיעה")
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.GoldSoft, textAlign = TextAlign.Center)
}

@Composable
private fun SnoozeRow(left: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SnoozeDots(left, max)
        Spacer(Modifier.width(8.dp))
        Text(
            when (left) {
                0 -> "הדחיות של היום נגמרו"
                1 -> "נשארה דחייה אחת של חצי שעה"
                else -> "נשארו $left דחיות של חצי שעה"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Sand,
        )
    }
}

/** One sentence per state; a second line only where the sign above cannot say it (a broken lock). */
private fun panelTexts(policy: DayPolicy, state: LockState): Pair<String, String?> = when (state) {
    is LockState.Unlocked -> when (state.reason) {
        UnlockReason.VERIFIED -> "הטלפון פתוח עד מחר בבוקר." to null
        UnlockReason.NO_TEFILLIN_DAY -> "אין הנחה היום, והמנעול נח." to null
        UnlockReason.BEFORE_START -> "המנעול נדרך ב-${IsraelTime.hhmm(policy.lockStart(state.day))}. אפשר כבר לאמת." to null
        UnlockReason.AFTER_SUNSET -> "זמן התפילין של היום עבר." to null
        UnlockReason.SHABBAT_BEGAN -> "המנעול נח עד מוצאי שבת." to null
        UnlockReason.SETUP_INCOMPLETE -> "בלי דריכה הטלפון לא ננעל בבוקר." to null
    }
    is LockState.Locked -> "התפילין מחכות." to null
    is LockState.Snoozed -> "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז המנעול חוזר." to null
    is LockState.Tampered -> TamperUi.title(state.reason) to TamperUi.body(state.reason)
}
