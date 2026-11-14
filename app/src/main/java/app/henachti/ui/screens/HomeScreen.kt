package app.henachti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.henachti.R
import app.henachti.data.Dedications
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import app.henachti.domain.LockState
import app.henachti.domain.TamperReason
import app.henachti.domain.UnlockReason
import app.henachti.ui.Arch
import app.henachti.ui.CountdownRing
import app.henachti.ui.DayArc
import app.henachti.ui.DarkButton
import app.henachti.ui.Eyebrow
import app.henachti.ui.GoldButton
import app.henachti.ui.HalfSpacer
import app.henachti.ui.LinkRow
import app.henachti.ui.MilestoneCard
import app.henachti.ui.OpeningLock
import app.henachti.ui.Palette
import app.henachti.ui.SnoozeDots
import app.henachti.ui.StatRow
import app.henachti.ui.StreakCalendar
import app.henachti.ui.TamperUi
import app.henachti.ui.TopBar
import app.henachti.ui.restDayTitle
import app.henachti.service.BlockerService

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
    val name = prefs.userName
    val greeting = if (name.isBlank()) policy.greeting(now) else "${policy.greeting(now)}, $name"
    val armed = prefs.setupDone

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(
            title = greeting,
            onSettings = onSettings,
            leading = { TimeChip(IsraelTime.hhmm(now)) },
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Eyebrow(policy.hebrewDate(state.day), Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))

            HeroCard(state, policy, now, onVerify, onSettings, onFix)

            Dedications.label(prefs)?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    "ההנחה של היום מוקדשת $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.GoldDeep,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val streak = remember(now) { policy.streak(now) }
            var milestoneSeen by remember { mutableIntStateOf(prefs.milestoneSeen) }
            MilestoneCard(
                streak = streak,
                seen = milestoneSeen,
                onDismiss = { reached ->
                    prefs.milestoneSeen = reached
                    milestoneSeen = reached
                },
                modifier = Modifier.padding(top = 14.dp),
            )

            Spacer(Modifier.height(14.dp))
            StreakCalendar(policy, prefs, now)
            Spacer(Modifier.height(10.dp))
            StatRow(
                painterResource(R.drawable.ic_sparkle), "אימותים מאז ההתקנה", "${prefs.verifiedCount}",
                dot = if (state is LockState.Unlocked && state.reason == UnlockReason.VERIFIED) Palette.Success else null,
            )

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                DarkButton("הסידור", onPrayers, Modifier.weight(1f), icon = painterResource(R.drawable.ic_book))
                HalfSpacer()
                DarkButton("לזכות", onDedication, Modifier.weight(1f), vector = Icons.Outlined.FavoriteBorder)
            }

            Spacer(Modifier.height(14.dp))
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

@Composable
private fun TimeChip(time: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.CreamDeep)
            .border(1.dp, Palette.Sand, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(time, style = MaterialTheme.typography.titleSmall, color = Palette.InkSoft)
    }
}

@Composable
private fun HeroCard(
    state: LockState,
    policy: DayPolicy,
    now: Long,
    onVerify: () -> Unit,
    onSettings: () -> Unit,
    onFix: (TamperReason) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Cream)
            .border(1.5.dp, Palette.Gold, shape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state is LockState.Snoozed) {
            // during a snooze the arch gives way to the half hour running out
            CountdownRing(until = state.until, total = BlockerService.SNOOZE_MS, diameter = 132.dp)
        } else {
            val verified = state is LockState.Unlocked && state.reason == UnlockReason.VERIFIED
            Arch(width = 116.dp, height = 144.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        verified || state is LockState.Locked ->
                            OpeningLock(open = verified, modifier = Modifier.size(46.dp))
                        else -> Icon(
                            painterResource(R.drawable.ic_sparkle), contentDescription = null,
                            tint = Palette.Gold, modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("וּקְשַׁרְתָּם לְאוֹת", style = MaterialTheme.typography.labelSmall, color = Palette.GoldSoft)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        when (state) {
            is LockState.Unlocked -> when (state.reason) {
                UnlockReason.VERIFIED -> {
                    HeroText("הנחת. היום שלך.", "האימות עבר, והטלפון פתוח עד מחר בבוקר.")
                    Spacer(Modifier.height(16.dp))
                    DarkButton("אושר להיום", {}, Modifier.fillMaxWidth(), vector = Icons.Filled.Check)
                }
                UnlockReason.NO_TEFILLIN_DAY -> {
                    HeroText(restDayTitle(policy.noTefillinReason(state.day)), "אין הנחת תפילין היום, והמנעול נח.")
                }
                UnlockReason.BEFORE_START -> {
                    HeroText("עוד מוקדם", "המנעול נדרך ב-${IsraelTime.hhmm(policy.lockStart(state.day))}. עד אז הטלפון פתוח, ואפשר כבר לאמת.")
                    Spacer(Modifier.height(16.dp))
                    DayArc(policy.misheyakir(state.day), policy.sunset(state.day), now)
                    Spacer(Modifier.height(16.dp))
                    GoldButton("לאימות", onVerify, Modifier.fillMaxWidth())
                }
                UnlockReason.AFTER_SUNSET -> HeroText("השמש שקעה", "זמן התפילין של היום עבר. מחר בבוקר מתחילים נקי.")
                UnlockReason.SHABBAT_BEGAN -> HeroText("שבת שלום", "המנעול נח עד מוצאי שבת.")
                UnlockReason.SETUP_INCOMPLETE -> {
                    HeroText("עוד לא דרוך", "כמה מתגים, ומכאן הכול עובד לבד.")
                    Spacer(Modifier.height(16.dp))
                    GoldButton("להרשאות", onSettings, Modifier.fillMaxWidth())
                }
            }
            is LockState.Locked -> {
                HeroText("התפילין מחכות", "הטלפון נפתח אחרי האימות במצלמה.")
                Spacer(Modifier.height(16.dp))
                DayArc(policy.misheyakir(state.day), policy.sunset(state.day), now)
                Spacer(Modifier.height(16.dp))
                GoldButton("לאימות", onVerify, Modifier.fillMaxWidth())
                if (state.snoozesPerDay > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SnoozeDots(state.snoozesLeft, state.snoozesPerDay)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (state.snoozesLeft) {
                                0 -> "הדחיות של היום נגמרו"
                                1 -> "נשארה דחייה אחת של חצי שעה"
                                else -> "נשארו ${state.snoozesLeft} דחיות של חצי שעה"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Muted,
                        )
                    }
                }
            }
            is LockState.Snoozed -> {
                HeroText("חצי שעה של חסד", "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז המנעול חוזר.")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SnoozeDots((state.max - state.used).coerceAtLeast(0), state.max)
                    Spacer(Modifier.width(8.dp))
                    Text("דחייה ${state.used} מתוך ${state.max}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
                Spacer(Modifier.height(16.dp))
                GoldButton("לאימות", onVerify, Modifier.fillMaxWidth())
            }
            is LockState.Tampered -> {
                HeroText(TamperUi.title(state.reason), TamperUi.body(state.reason))
                Spacer(Modifier.height(16.dp))
                GoldButton(TamperUi.action(state.reason), { onFix(state.reason) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeroText(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft, textAlign = TextAlign.Center)
}
