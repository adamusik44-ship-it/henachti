package app.henachti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import app.henachti.ui.LinkRow
import app.henachti.ui.MilestoneCard
import app.henachti.ui.OpeningLock
import app.henachti.ui.Palette
import app.henachti.ui.SkyArc
import app.henachti.ui.SnoozeDots
import app.henachti.ui.SoftCard
import app.henachti.ui.StreakCalendar
import app.henachti.ui.TamperUi
import app.henachti.ui.dayPhase
import app.henachti.ui.phaseColors
import app.henachti.ui.restDayTitle
import java.time.Instant
import java.util.Locale

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

        Column(Modifier.padding(horizontal = 20.dp)) {
            val streak = remember(now) { policy.streak(now) }
            var milestoneSeen by remember { mutableIntStateOf(prefs.milestoneSeen) }
            MilestoneCard(
                streak = streak,
                seen = milestoneSeen,
                onDismiss = { reached ->
                    prefs.milestoneSeen = reached
                    milestoneSeen = reached
                },
                modifier = Modifier.padding(top = 18.dp),
            )

            Spacer(Modifier.height(18.dp))
            StreakCalendar(policy, prefs, now)

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Tile(
                    painterResource(R.drawable.ic_book), "הסידור", "תפילות לכל היום", onPrayers,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(12.dp))
                Tile(
                    rememberVectorPainter(Icons.Outlined.FavoriteBorder), "לזכות",
                    Dedications.label(prefs) ?: "להקדיש את ההנחה", onDedication,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            Spacer(Modifier.height(12.dp))
            LinkRow(
                title = when {
                    !armed -> "המנעול לא דרוך"
                    running -> "המנעול דרוך"
                    else -> "המנעול דרוך, השירות ישן"
                },
                subtitle = when {
                    !armed -> "כמה מתגים, ומכאן הכול עובד לבד"
                    running -> "נפתח רק אחרי התפילין של היום"
                    else -> "פתח את ההגדרות והער אותו"
                },
                onClick = onSettings,
                leading = {
                    Icon(
                        painterResource(R.drawable.ic_shield), contentDescription = null,
                        tint = if (armed && running) Palette.Success else Palette.GoldDeep,
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
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
 * The top of Home: a dark panel whose shade follows the hour (dawn, day, dusk, night), the sky arc
 * of today's tefillin window, a centrepiece that changes with the state, and the one action that matters now.
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
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // time on the right (start), greeting and date in the middle, settings on the left
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.Cream.copy(alpha = 0.08f))
                        .border(1.dp, Palette.Cream.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(IsraelTime.hhmm(now), style = MaterialTheme.typography.titleSmall, color = Palette.Sand)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(greeting, style = MaterialTheme.typography.titleLarge, color = Palette.Cream, textAlign = TextAlign.Center)
                Text(policy.hebrewDate(state.day), style = MaterialTheme.typography.labelMedium, color = Palette.GoldSoft)
            }
            Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Palette.Cream.copy(alpha = 0.08f))
                        .border(1.dp, Palette.Cream.copy(alpha = 0.16f), CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = Palette.Cream, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
        ) {
            SkyArc(misheyakir, sunset, now, Modifier.matchParentSize())
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
            Text("משיכיר ${IsraelTime.hhmm(misheyakir)}", style = MaterialTheme.typography.labelMedium, color = Palette.Sand.copy(alpha = 0.75f))
            Spacer(Modifier.weight(1f))
            Text("שקיעה ${IsraelTime.hhmm(sunset)}", style = MaterialTheme.typography.labelMedium, color = Palette.Sand.copy(alpha = 0.75f))
        }

        Spacer(Modifier.height(20.dp))
        val (title, body) = panelTexts(policy, state)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.Cream, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Sand, textAlign = TextAlign.Center)

        when (state) {
            is LockState.Locked -> {
                Spacer(Modifier.height(18.dp))
                GoldButton("לאימות במצלמה", onVerify, Modifier.fillMaxWidth())
                if (state.snoozesPerDay > 0) {
                    Spacer(Modifier.height(12.dp))
                    SnoozeRow(state.snoozesLeft, state.snoozesPerDay)
                }
            }
            is LockState.Snoozed -> {
                Spacer(Modifier.height(18.dp))
                GoldButton("לאימות במצלמה", onVerify, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SnoozeRow((state.max - state.used).coerceAtLeast(0), state.max)
            }
            is LockState.Tampered -> {
                Spacer(Modifier.height(18.dp))
                GoldButton(TamperUi.action(state.reason), { onFix(state.reason) }, Modifier.fillMaxWidth())
            }
            is LockState.Unlocked -> when (state.reason) {
                UnlockReason.BEFORE_START -> {
                    Spacer(Modifier.height(18.dp))
                    GoldButton("לאימות במצלמה", onVerify, Modifier.fillMaxWidth())
                }
                UnlockReason.SETUP_INCOMPLETE -> {
                    Spacer(Modifier.height(18.dp))
                    GoldButton("להרשאות", onSettings, Modifier.fillMaxWidth())
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
    today: java.time.LocalDate,
    misheyakir: Long,
    sunset: Long,
) {
    val big = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp, lineHeight = 58.sp)
    val verified = state is LockState.Unlocked && state.reason == UnlockReason.VERIFIED
    val rest = state is LockState.Unlocked &&
        (state.reason == UnlockReason.NO_TEFILLIN_DAY || state.reason == UnlockReason.SHABBAT_BEGAN)
    when {
        verified -> {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Palette.Gold.copy(alpha = 0.14f))
                    .border(2.dp, Palette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Caption(if (prefs.verifiedAt > 0L) "הנחת ב-${IsraelTime.hhmm(prefs.verifiedAt)}" else "הנחת היום")
        }
        state is LockState.Snoozed -> CountdownRing(until = state.until, total = BlockerService.SNOOZE_MS, diameter = 118.dp, dark = true)
        rest -> {
            Icon(painterResource(R.drawable.ic_candle), contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(8.dp))
            Caption(
                if (state is LockState.Unlocked && state.reason == UnlockReason.SHABBAT_BEGAN) "שבת שלום"
                else restDayTitle(policy.noTefillinReason(state.day))
            )
        }
        state is LockState.Unlocked && state.reason == UnlockReason.SETUP_INCOMPLETE -> {
            OpeningLock(open = false, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(6.dp))
            Caption("המנעול עוד לא דרוך")
        }
        state is LockState.Tampered -> {
            OpeningLock(open = false, modifier = Modifier.size(54.dp), tint = Palette.GoldSoft)
            Spacer(Modifier.height(6.dp))
            Caption("משהו כובה")
        }
        now < misheyakir -> {
            Text(IsraelTime.hhmm(misheyakir), style = big, color = Palette.Cream)
            Caption("משיכיר הבוקר")
        }
        now >= sunset -> {
            Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = Palette.GoldSoft, modifier = Modifier.size(42.dp))
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

private fun panelTexts(policy: DayPolicy, state: LockState): Pair<String, String> = when (state) {
    is LockState.Unlocked -> when (state.reason) {
        UnlockReason.VERIFIED -> "הנחת. היום שלך." to "האימות עבר, והטלפון פתוח עד מחר בבוקר."
        UnlockReason.NO_TEFILLIN_DAY -> restDayTitle(policy.noTefillinReason(state.day)) to "אין הנחת תפילין היום, והמנעול נח."
        UnlockReason.BEFORE_START ->
            "עוד מוקדם" to "המנעול נדרך ב-${IsraelTime.hhmm(policy.lockStart(state.day))}. עד אז הטלפון פתוח, ואפשר כבר לאמת."
        UnlockReason.AFTER_SUNSET -> "השמש שקעה" to "זמן התפילין של היום עבר. מחר בבוקר מתחילים נקי."
        UnlockReason.SHABBAT_BEGAN -> "שבת שלום" to "המנעול נח עד מוצאי שבת."
        UnlockReason.SETUP_INCOMPLETE -> "עוד לא דרוך" to "כמה מתגים, ומכאן הכול עובד לבד."
    }
    is LockState.Locked -> "התפילין מחכות" to "הטלפון נפתח אחרי האימות במצלמה."
    is LockState.Snoozed -> "חצי שעה של חסד" to "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז המנעול חוזר."
    is LockState.Tampered -> TamperUi.title(state.reason) to TamperUi.body(state.reason)
}

/** A square entry on Home: a gold mark on a chocolate dot, a title and one line. */
@Composable
private fun Tile(icon: Painter, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    SoftCard(modifier, onClick = onClick) {
        Column {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Palette.Chocolate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
        }
    }
}
