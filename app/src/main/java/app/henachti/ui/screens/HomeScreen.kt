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
    val name = prefs.userName.ifBlank { "צדיק" }
    val armed = prefs.setupDone

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(
            title = "${policy.greeting(now)}, $name",
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
            StatRow(painterResource(R.drawable.ic_flame), "ימים ברצף", "${policy.streak(now)}")
            Spacer(Modifier.height(10.dp))
            StatRow(
                painterResource(R.drawable.ic_sparkle), "סך הכול הנחות", "${prefs.verifiedCount}",
                dot = if (state is LockState.Unlocked && state.reason == UnlockReason.VERIFIED) Palette.Success else null,
            )

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                DarkButton("תפילות", onPrayers, Modifier.weight(1f), icon = painterResource(R.drawable.ic_book))
                HalfSpacer()
                DarkButton("הקדשה", onDedication, Modifier.weight(1f), vector = Icons.Outlined.FavoriteBorder)
            }

            Spacer(Modifier.height(14.dp))
            LinkRow(
                title = when {
                    !armed -> "ההגנה כבויה"
                    running -> "ההגנה פעילה"
                    else -> "ההגנה מופעלת, השירות לא רץ"
                },
                subtitle = when {
                    !armed -> "לחץ להשלמת ההגדרה הראשונית"
                    running -> "כל האפליקציות חסומות עד האימות של היום"
                    else -> "פתח את ההגדרות והפעל את השירות"
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
                Text("אור המצוות", style = MaterialTheme.typography.labelSmall, color = Palette.GoldSoft)
            }
        }
        Spacer(Modifier.height(18.dp))

        when (state) {
            is LockState.Unlocked -> when (state.reason) {
                UnlockReason.VERIFIED -> {
                    HeroText("איזה יופי, היום פתוח", "תפילין הונחו, ממשיכים באור")
                    Spacer(Modifier.height(16.dp))
                    DarkButton("הושלם", {}, Modifier.fillMaxWidth(), vector = Icons.Filled.Check)
                }
                UnlockReason.NO_TEFILLIN_DAY -> {
                    val reason = policy.noTefillinReason(state.day) ?: "היום"
                    HeroText(if (reason == "שבת") "שבת שלום" else "$reason שמח", "היום לא מניחים תפילין. הטלפון פתוח.")
                }
                UnlockReason.AFTER_SUNSET -> HeroText("השמש שקעה", "עבר זמן תפילין להיום. מחר בוקר מתחילים מחדש.")
                UnlockReason.SHABBAT_BEGAN -> HeroText("שבת שלום", "הטלפון פתוח עד מוצאי שבת.")
                UnlockReason.SETUP_INCOMPLETE -> {
                    HeroText("בוא נתחיל", "כמה הרשאות, לחיצה אחת, ומכאן הכול עובד לבד.")
                    Spacer(Modifier.height(16.dp))
                    GoldButton("הגדרה ראשונית", onSettings, Modifier.fillMaxWidth())
                }
            }
            is LockState.Locked -> {
                HeroText(
                    "עוד לא הנחת היום",
                    if (state.snoozeAvailable) "הטלפון נעול עד האימות. נשארה דחייה אחת של חצי שעה."
                    else "הטלפון נעול עד האימות. הדחייה של היום נוצלה.",
                )
                Spacer(Modifier.height(16.dp))
                GoldButton("אימות במצלמה", onVerify, Modifier.fillMaxWidth())
            }
            is LockState.Snoozed -> {
                HeroText("דחייה פעילה", "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז נסגר שוב.")
                Spacer(Modifier.height(16.dp))
                GoldButton("אימות במצלמה", onVerify, Modifier.fillMaxWidth())
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
