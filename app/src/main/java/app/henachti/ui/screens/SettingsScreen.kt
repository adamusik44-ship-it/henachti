package app.henachti.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.henachti.R
import app.henachti.data.Prefs
import app.henachti.domain.Cities
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import app.henachti.domain.LockState
import app.henachti.domain.UnlockReason
import app.henachti.service.BlockerService
import app.henachti.service.Guard
import app.henachti.ui.Eyebrow
import app.henachti.ui.FormField
import app.henachti.ui.GhostButton
import app.henachti.ui.GoldButton
import app.henachti.ui.Hairline
import app.henachti.ui.HenachtiSwitch
import app.henachti.ui.LinkRow
import app.henachti.ui.Palette
import app.henachti.ui.TamperUi
import app.henachti.ui.TopBar

/** Status, a link to the permission switches, preferences and the orderly exit. Preferences lock while the day is locked. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    policy: DayPolicy,
    now: Long,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onVerify: () -> Unit,
    onPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val state = remember(now) { policy.state(now) }
    val day = state.day
    val editable = state is LockState.Unlocked
    val armed = prefs.setupDone

    var userName by remember { mutableStateOf(prefs.userName) }
    var city by remember { mutableIntStateOf(prefs.cityIndex) }
    var chol by remember { mutableStateOf(prefs.cholHamoedTefillin) }
    var strict by remember { mutableStateOf(prefs.strictAfterSunset) }
    var extra by remember { mutableStateOf(prefs.extraAllowed) }
    var exitAt by remember(now) { mutableLongStateOf(prefs.exitRequestedAt) }

    val granted = remember(now) { grantedCount(context) }
    val cameraOn = remember(now) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val running = remember(now) { BlockerService.instance != null }
    val adbOn = remember(now) { Guard.adbEnabled(context) }
    val trial = prefs.inTrialWindow(now)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("הגדרות", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {

            // ---- status ----
            Eyebrow("מצב עכשיו")
            Spacer(Modifier.height(6.dp))
            Text(statusTitle(state), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(statusBody(state, policy), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            Spacer(Modifier.height(14.dp))
            if (state is LockState.Tampered) {
                GoldButton(TamperUi.action(state.reason), { TamperUi.launchFix(context, state.reason) }, Modifier.fillMaxWidth())
            } else if (armed) {
                GoldButton("לאימות", onVerify, Modifier.fillMaxWidth(), enabled = cameraOn)
            }
            if (armed && !running && state !is LockState.Tampered) {
                Spacer(Modifier.height(10.dp))
                Text("שירות המנעול ישן.", style = MaterialTheme.typography.bodyMedium, color = Palette.Danger)
                Spacer(Modifier.height(8.dp))
                GhostButton("הער את השירות", { BlockerService.ensureRunning(context); onRefresh() }, Modifier.fillMaxWidth())
            }

            // ---- permissions ----
            Section("הרשאות ומנעול")
            LinkRow(
                title = if (armed) "המנעול דרוך" else "המנעול לא דרוך",
                subtitle = "$granted מתוך 6 מתגים דלוקים. לחץ לפתיחה.",
                onClick = onPermissions,
                leading = {
                    Icon(
                        painterResource(R.drawable.ic_shield), contentDescription = null,
                        tint = if (armed && running) Palette.Success else Palette.GoldDeep,
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
            if (armed && trial) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "ב-24 השעות הראשונות אפשר לשחרר את המנעול מיד, כדי שבדיקות לא ינעלו אותך. אחר כך רק דרך היציאה המסודרת.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("שחרר את המנעול (תקופת ניסיון)", {
                    prefs.disarm()
                    Guard.removeAdmin(context)
                    onRefresh()
                }, Modifier.fillMaxWidth())
            }

            // ---- name ----
            Section("השם שלך")
            Text("ככה הברכה במסך הראשי תפנה אליך.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            Spacer(Modifier.height(8.dp))
            FormField(userName, { userName = it; prefs.userName = it }, "למשל: מיכאל")

            // ---- preferences ----
            Section("העדפות")
            Text("עיר, לזמני משיכיר ושקיעה", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Cities.all.forEachIndexed { index, c ->
                    TextButton(onClick = { city = index; prefs.cityIndex = index; onRefresh() }, enabled = editable) {
                        Text(
                            c.name,
                            color = if (index == city) Palette.Ink else Palette.Muted,
                            fontWeight = if (index == city) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow("מניחים תפילין בחול המועד", "ברירת המחדל בארץ: לא מניחים, והמנעול נח.", chol, editable) {
                chol = it; prefs.cholHamoedTefillin = it; onRefresh()
            }
            SwitchRow("נשאר נעול גם אחרי השקיעה", "אם לא הנחת עד השקיעה, הטלפון נשאר סגור עד הבוקר. בכניסת שבת וחג הוא נפתח בכל מקרה.", strict, editable) {
                strict = it; prefs.strictAfterSunset = it; onRefresh()
            }
            Spacer(Modifier.height(12.dp))
            Text("אפליקציות שפתוחות תמיד", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "שם חבילה בכל שורה. טלפון, מסך הבית, מקלדת ושעון מעורר פתוחים תמיד גם בלי זה. הגדרות, חנות והסרת אפליקציות אי אפשר להוסיף.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(8.dp))
            FormField(extra, { extra = it }, "com.example.app", enabled = editable, minLines = 2)
            Spacer(Modifier.height(10.dp))
            GhostButton("שמור רשימה", { prefs.extraAllowed = extra; BlockerService.instance?.refreshAllowlist() }, Modifier.fillMaxWidth(), enabled = editable)
            if (!editable) {
                Spacer(Modifier.height(8.dp))
                Text("ההעדפות נפתחות לעריכה אחרי שמניחים תפילין.", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }

            // ---- orderly exit ----
            if (armed) {
                Section("יציאה מסודרת")
                if (exitAt == 0L) {
                    Text(
                        "רוצה להפסיק? בקשת הסרה נכנסת לתוקף אחרי 48 שעות. עד אז הכול ממשיך כרגיל, ואפשר לבטל בכל רגע.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton("בקש הסרה (48 שעות)", {
                        prefs.exitRequestedAt = System.currentTimeMillis()
                        exitAt = prefs.exitRequestedAt
                        onRefresh()
                    }, Modifier.fillMaxWidth(), enabled = editable)
                } else {
                    Text(
                        "ההסרה תיכנס לתוקף ב-${IsraelTime.dateTime(exitAt + DayPolicy.EXIT_COOLDOWN_MS)}. עד אז המנעול ממשיך כרגיל.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Danger,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton("בטל את בקשת ההסרה", {
                        prefs.exitRequestedAt = 0L
                        exitAt = 0L
                        onRefresh()
                    }, Modifier.fillMaxWidth())
                }
            }

            // ---- today ----
            Section("היום")
            Text(
                "משיכיר ${IsraelTime.hhmm(policy.misheyakir(day))}  ·  שקיעה ${IsraelTime.hhmm(policy.sunset(day))}",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Text("אימותים מאז ההתקנה: ${prefs.verifiedCount}", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            if (adbOn) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "ניפוי USB פעיל. דרך מחשב אפשר להסיר את האפליקציה. כבה אותו באפשרויות למפתחים אם אתה רוצה שזה יהיה אטום.",
                    style = MaterialTheme.typography.labelMedium, color = Palette.Danger,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "דרך חירום: הפעלה מחדש במצב בטוח (Safe Mode) מכבה אפליקציות צד שלישי ומאפשרת להסיר את האפליקציה.",
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(26.dp))
    Hairline()
    Spacer(Modifier.height(18.dp))
    Eyebrow(title)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }
        Spacer(Modifier.width(12.dp))
        HenachtiSwitch(checked = checked, onChange = onChange, enabled = enabled)
    }
    Spacer(Modifier.height(14.dp))
}

private fun statusTitle(state: LockState): String = when (state) {
    is LockState.Locked -> "נעול עד שמניחים."
    is LockState.Snoozed -> "חצי שעה של חסד."
    is LockState.Tampered -> TamperUi.title(state.reason)
    is LockState.Unlocked -> when (state.reason) {
        UnlockReason.SETUP_INCOMPLETE -> "המנעול עוד לא דרוך."
        UnlockReason.NO_TEFILLIN_DAY -> "היום לא מניחים."
        UnlockReason.VERIFIED -> "הנחת היום."
        UnlockReason.AFTER_SUNSET -> "השמש שקעה."
        UnlockReason.SHABBAT_BEGAN -> "שבת או חג נכנסו."
    }
}

private fun statusBody(state: LockState, policy: DayPolicy): String = when (state) {
    is LockState.Locked ->
        if (state.snoozeAvailable) "הטלפון סגור. נשארה חצי שעה אחת של דחייה להיום."
        else "הטלפון סגור. הדחייה של היום כבר נוצלה."
    is LockState.Snoozed -> "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז המנעול חוזר."
    is LockState.Tampered -> TamperUi.body(state.reason)
    is LockState.Unlocked -> when (state.reason) {
        UnlockReason.SETUP_INCOMPLETE -> "הדלק את המתגים במסך ההרשאות ולחץ \"דרוך את המנעול\"."
        UnlockReason.NO_TEFILLIN_DAY -> "${policy.noTefillinReason(state.day) ?: ""}. המנעול נח."
        UnlockReason.VERIFIED -> "הטלפון פתוח עד משיכיר של מחר."
        UnlockReason.AFTER_SUNSET -> "זמן התפילין של היום עבר. הטלפון פתוח עד מחר בבוקר."
        UnlockReason.SHABBAT_BEGAN -> "המנעול נח. שבת שלום."
    }
}
