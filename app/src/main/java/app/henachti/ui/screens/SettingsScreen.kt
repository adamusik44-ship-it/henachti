package app.henachti.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import app.henachti.ui.Palette
import app.henachti.ui.TamperUi
import app.henachti.ui.TopBar

/** Status, permissions, arming, preferences and the orderly exit. Preferences lock while the day is locked. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    policy: DayPolicy,
    now: Long,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onVerify: () -> Unit,
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

    val usageOn = remember(now) { Guard.hasUsageAccess(context) }
    val overlayOn = remember(now) { Guard.canDrawOverlays(context) }
    val adminOn = remember(now) { Guard.isAdminActive(context) }
    val notifOn = remember(now) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val cameraOn = remember(now) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(now) {
        (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }
    val running = remember(now) { BlockerService.instance != null }
    val adbOn = remember(now) { Guard.adbEnabled(context) }
    val readyToArm = usageOn && overlayOn && adminOn
    val trial = prefs.inTrialWindow(now)

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

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
                GoldButton("אימות במצלמה", onVerify, Modifier.fillMaxWidth(), enabled = cameraOn)
            }
            if (armed && !running && state !is LockState.Tampered) {
                Spacer(Modifier.height(10.dp))
                Text("שירות החסימה לא רץ כרגע.", style = MaterialTheme.typography.bodyMedium, color = Palette.Danger)
                Spacer(Modifier.height(8.dp))
                GhostButton("הפעל את השירות", { BlockerService.ensureRunning(context); onRefresh() }, Modifier.fillMaxWidth())
            }

            Section("השם שלך")
            Text("ככה האפליקציה תפנה אליך במסך הראשי.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            Spacer(Modifier.height(8.dp))
            FormField(userName, { userName = it; prefs.userName = it }, "צדיק")

            // ---- setup ----
            Section("הגדרה ראשונית")
            SetupRow(usageOn, "גישה לנתוני שימוש",
                "כך האפליקציה יודעת איזו אפליקציה פתוחה עכשיו. ברשימה שתיפתח מצא את \"הנחתי\" והפעל.",
                "פתח גישה לנתוני שימוש") { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            SetupRow(overlayOn, "הצגה מעל אפליקציות אחרות",
                "כך מסך הנעילה קופץ מעל האפליקציה שנפתחה.",
                "אשר הצגה מעל אפליקציות") {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            SetupRow(adminOn, "הגנה מפני הסרה",
                "בלי זה אפשר למחוק את האפליקציה בלחיצה ארוכה. ההפעלה דורשת אישור במסך מערכת. הסרה מסודרת אפשרית מכאן, אחרי 48 שעות.",
                "הפעל הגנה") { open(Guard.addAdminIntent(context)) }
            SetupRow(notifOn, "התראות",
                "התראה שקטה אחת מחזיקה את שירות החסימה ער ומראה את המצב.",
                "אשר התראות") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            SetupRow(cameraOn, "מצלמה", "לאימות חי בלבד. לאפליקציה אין גישה לגלריה.", "אשר גישה") {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
            SetupRow(batteryOk, "סוללה", "בלי אופטימיזציית סוללה השירות לא נרדם באמצע היום.", "בטל אופטימיזציה") {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }

            // ---- arming ----
            Section("הפעלת החסימה")
            Text(
                "הזיהוי נעשה בתוך הטלפון: המצלמה סופרת את הכריכות השחורות על האמה, שבע לפחות. " +
                    "בלי חשבון, בלי שרת, בלי אינטרנט ובלי תשלום.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(10.dp))
            if (!armed) {
                if (!readyToArm) {
                    Text("קודם שלוש ההרשאות הראשונות למעלה.", style = MaterialTheme.typography.bodyMedium, color = Palette.GoldDeep)
                    Spacer(Modifier.height(8.dp))
                }
                GoldButton("הפעל את החסימה", {
                    prefs.arm()
                    BlockerService.ensureRunning(context)
                    onRefresh()
                }, Modifier.fillMaxWidth(), enabled = readyToArm)
            } else {
                Text("החסימה פעילה.", style = MaterialTheme.typography.bodyMedium, color = Palette.Success)
                if (trial) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ב-24 השעות הראשונות אפשר לבטל מיד, כדי שבדיקות לא ינעלו אותך. אחר כך רק דרך היציאה המסודרת.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton("בטל הפעלה (תקופת ניסיון)", {
                        prefs.disarm()
                        Guard.removeAdmin(context)
                        onRefresh()
                    }, Modifier.fillMaxWidth())
                }
            }

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
            SwitchRow("מניחים תפילין בחול המועד", "ברירת המחדל בארץ: לא מניחים, והטלפון פתוח.", chol, editable) {
                chol = it; prefs.cholHamoedTefillin = it; onRefresh()
            }
            SwitchRow("נשאר נעול גם אחרי השקיעה", "אם לא הנחת עד השקיעה, הטלפון נשאר סגור עד הבוקר. בכניסת שבת וחג הוא נפתח בכל מקרה.", strict, editable) {
                strict = it; prefs.strictAfterSunset = it; onRefresh()
            }
            Spacer(Modifier.height(12.dp))
            Text("אפליקציות שמותרות תמיד", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "שם חבילה בכל שורה. טלפון, מסך הבית, מקלדת ושעון מעורר מותרים תמיד גם בלי זה. הגדרות, חנות והסרת אפליקציות אי אפשר להוסיף.",
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
                        "ההסרה תיכנס לתוקף ב-${IsraelTime.dateTime(exitAt + DayPolicy.EXIT_COOLDOWN_MS)}. עד אז החסימה ממשיכה כרגיל.",
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
            Text("אימותים עד היום: ${prefs.verifiedCount}", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
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
private fun SetupRow(done: Boolean, title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (done) "✓" else "○",
                color = if (done) Palette.Success else Palette.GoldDeep,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        if (!done) {
            Spacer(Modifier.height(8.dp))
            GhostButton(action, onAction, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Cream,
                checkedTrackColor = Palette.Chocolate,
                uncheckedThumbColor = Palette.Muted,
                uncheckedTrackColor = Palette.CreamDeep,
            ),
        )
    }
    Spacer(Modifier.height(14.dp))
}

private fun statusTitle(state: LockState): String = when (state) {
    is LockState.Locked -> "נעול עד שמניחים."
    is LockState.Snoozed -> "דחייה פעילה."
    is LockState.Tampered -> TamperUi.title(state.reason)
    is LockState.Unlocked -> when (state.reason) {
        UnlockReason.SETUP_INCOMPLETE -> "עוד לא מופעל."
        UnlockReason.NO_TEFILLIN_DAY -> "היום לא מניחים."
        UnlockReason.VERIFIED -> "הנחת היום."
        UnlockReason.AFTER_SUNSET -> "השמש שקעה."
        UnlockReason.SHABBAT_BEGAN -> "שבת או חג נכנסו."
    }
}

private fun statusBody(state: LockState, policy: DayPolicy): String = when (state) {
    is LockState.Locked ->
        if (state.snoozeAvailable) "הטלפון חסום. נשארה חצי שעה אחת של דחייה להיום."
        else "הטלפון חסום. הדחייה של היום כבר נוצלה."
    is LockState.Snoozed -> "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז נסגר שוב."
    is LockState.Tampered -> TamperUi.body(state.reason)
    is LockState.Unlocked -> when (state.reason) {
        UnlockReason.SETUP_INCOMPLETE -> "השלם את ההגדרה הראשונית למטה ולחץ \"הפעל את החסימה\"."
        UnlockReason.NO_TEFILLIN_DAY -> "${policy.noTefillinReason(state.day) ?: ""}. הטלפון פתוח."
        UnlockReason.VERIFIED -> "הטלפון פתוח עד משיכיר של מחר."
        UnlockReason.AFTER_SUNSET -> "עבר זמן תפילין להיום. הטלפון פתוח עד מחר בבוקר."
        UnlockReason.SHABBAT_BEGAN -> "הטלפון פתוח. שבת שלום."
    }
}
