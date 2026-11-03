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
import app.henachti.ui.CardShape
import app.henachti.ui.DarkButton
import app.henachti.ui.Eyebrow
import app.henachti.ui.GoldButton
import app.henachti.ui.HalfSpacer
import app.henachti.ui.LinkRow
import app.henachti.ui.Palette
import app.henachti.ui.StatRow
import app.henachti.ui.TamperUi
import app.henachti.ui.TopBar

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

            HeroCard(state, policy, onVerify, onSettings, onFix)

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

            Spacer(Modifier.height(14.dp))
            val streak = policy.streak(now)
            StatRow(painterResource(R.drawable.ic_flame), "הרצף שלך", if (streak == 1) "יום אחד" else "$streak ימים")
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
                    !armed -> "שישה מתגים, ומכאן הכול עובד לבד"
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
        Arch(width = 116.dp, height = 144.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_sparkle), contentDescription = null,
                    tint = Palette.Gold, modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("וּקְשַׁרְתָּם לְאוֹת", style = MaterialTheme.typography.labelSmall, color = Palette.GoldSoft)
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
                    val reason = policy.noTefillinReason(state.day) ?: "היום"
                    HeroText(if (reason == "שבת") "שבת שלום" else "$reason שמח", "אין הנחת תפילין היום, והמנעול נח.")
                }
                UnlockReason.BEFORE_START -> {
                    HeroText("עוד מוקדם", "המנעול נדרך ב-${IsraelTime.hhmm(policy.lockStart(state.day))}. עד אז הטלפון פתוח, ואפשר כבר לאמת.")
                    Spacer(Modifier.height(16.dp))
                    GoldButton("לאימות", onVerify, Modifier.fillMaxWidth())
                }
                UnlockReason.AFTER_SUNSET -> HeroText("השמש שקעה", "זמן התפילין של היום עבר. מחר בבוקר מתחילים נקי.")
                UnlockReason.SHABBAT_BEGAN -> HeroText("שבת שלום", "המנעול נח עד מוצאי שבת.")
                UnlockReason.SETUP_INCOMPLETE -> {
                    HeroText("עוד לא דרוך", "שישה מתגים, ומכאן הכול עובד לבד.")
                    Spacer(Modifier.height(16.dp))
                    GoldButton("להרשאות", onSettings, Modifier.fillMaxWidth())
                }
            }
            is LockState.Locked -> {
                HeroText(
                    "התפילין מחכות",
                    when {
                        state.snoozesLeft == 1 -> "הטלפון נפתח אחרי האימות במצלמה. נשארה לך דחייה אחת של חצי שעה."
                        state.snoozesLeft > 1 -> "הטלפון נפתח אחרי האימות במצלמה. נשארו לך ${state.snoozesLeft} דחיות של חצי שעה."
                        else -> "הטלפון נפתח אחרי האימות במצלמה. הדחיות של היום נגמרו."
                    },
                )
                Spacer(Modifier.height(16.dp))
                GoldButton("לאימות", onVerify, Modifier.fillMaxWidth())
            }
            is LockState.Snoozed -> {
                HeroText("חצי שעה של חסד", "דחייה ${state.used} מתוך ${state.max}. הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז המנעול חוזר.")
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
