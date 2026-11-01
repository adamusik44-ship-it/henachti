package app.henachti.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

/** Status, first-time setup and settings. Settings are editable only while the day is unlocked. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val tick = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(this)
        setContent {
            HenachtiTheme { MainScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        BlockerService.ensureRunning(this)
        BlockerService.instance?.refreshAllowlist()
        tick.longValue = System.currentTimeMillis()
    }

    private fun bump() {
        tick.longValue = System.currentTimeMillis()
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun batteryExempt(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

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

    private fun statusBody(state: LockState): String = when (state) {
        is LockState.Locked ->
            if (state.snoozeAvailable) "הטלפון חסום. נשארה חצי שעה אחת של דחייה להיום."
            else "הטלפון חסום. הדחייה של היום כבר נוצלה."
        is LockState.Snoozed -> "הכול פתוח עד ${IsraelTime.hhmm(state.until)}, ואז נסגר שוב."
        is LockState.Tampered -> TamperUi.body(state.reason)
        is LockState.Unlocked -> when (state.reason) {
            UnlockReason.SETUP_INCOMPLETE ->
                "השלם את ההגדרה הראשונית למטה ולחץ \"הפעל את החסימה\"."
            UnlockReason.NO_TEFILLIN_DAY ->
                "${policy.noTefillinReason(state.day) ?: ""}. הטלפון פתוח."
            UnlockReason.VERIFIED -> "הטלפון פתוח עד משיכיר של מחר."
            UnlockReason.AFTER_SUNSET -> "עבר זמן תפילין להיום. הטלפון פתוח עד מחר בבוקר."
            UnlockReason.SHABBAT_BEGAN -> "הטלפון פתוח. שבת שלום."
        }
    }

    @Composable
    private fun MainScreen() {
        val now = tick.longValue
        val state = remember(now) { policy.state(now) }
        val day = state.day
        val editable = state is LockState.Unlocked
        val armed = prefs.setupDone

        var armStatus by remember { mutableStateOf<String?>(null) }
        var city by remember { mutableIntStateOf(prefs.cityIndex) }
        var chol by remember { mutableStateOf(prefs.cholHamoedTefillin) }
        var strict by remember { mutableStateOf(prefs.strictAfterSunset) }
        var extra by remember { mutableStateOf(prefs.extraAllowed) }
        var exitAt by remember(now) { mutableLongStateOf(prefs.exitRequestedAt) }

        val usageOn = remember(now) { Guard.hasUsageAccess(this) }
        val overlayOn = remember(now) { Guard.canDrawOverlays(this) }
        val adminOn = remember(now) { Guard.isAdminActive(this) }
        val notifOn = remember(now) { notificationsGranted() }
        val running = remember(now) { BlockerService.instance != null }
        val cameraOn = remember(now) { cameraGranted() }
        val batteryOk = remember(now) { batteryExempt() }
        val adbOn = remember(now) { Guard.adbEnabled(this) }
        val readyToArm = usageOn && overlayOn && adminOn

        val cameraPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { bump() }
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { bump() }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(40.dp))
            Eyebrow(policy.hebrewDate(day))
            Spacer(Modifier.height(6.dp))
            Text("הנחתי", style = MaterialTheme.typography.displayLarge)
            Text("קודם תפילין. אחר כך הטלפון.", style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)

            Spacer(Modifier.height(24.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            // ---- status ----
            Eyebrow("מצב עכשיו")
            Spacer(Modifier.height(6.dp))
            Text(statusTitle(state), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(statusBody(state), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            Spacer(Modifier.height(16.dp))
            if (state is LockState.Tampered) {
                InkButton(
                    text = TamperUi.action(state.reason),
                    onClick = { TamperUi.launchFix(this@MainActivity, state.reason) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                InkButton(
                    text = "אימות במצלמה",
                    onClick = { startActivity(Intent(this@MainActivity, VerifyActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = armed && cameraOn,
                )
            }
            if (armed && !running && state !is LockState.Tampered) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "שירות החסימה לא רץ כרגע.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Danger,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("הפעל את השירות", {
                    BlockerService.ensureRunning(this@MainActivity)
                    bump()
                }, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            // ---- setup ----
            Eyebrow("הגדרה ראשונית")
            Spacer(Modifier.height(10.dp))

            SetupRow(
                done = usageOn,
                title = "גישה לנתוני שימוש",
                body = "כך האפליקציה יודעת איזו אפליקציה פתוחה עכשיו. ברשימה שתיפתח, מצא את \"הנחתי\" והפעל.",
                action = "פתח גישה לנתוני שימוש",
            ) {
                runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }

            SetupRow(
                done = overlayOn,
                title = "הצגה מעל אפליקציות אחרות",
                body = "כך מסך הנעילה קופץ מעל האפליקציה שנפתחה.",
                action = "אשר הצגה מעל אפליקציות",
            ) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }

            SetupRow(
                done = adminOn,
                title = "הגנה מפני הסרה",
                body = "בלי זה אפשר למחוק את האפליקציה בלחיצה ארוכה. ההפעלה דורשת אישור במסך מערכת. " +
                    "הסרה מסודרת אפשרית מתוך האפליקציה, אחרי 48 שעות.",
                action = "הפעל הגנה",
            ) {
                runCatching { startActivity(Guard.addAdminIntent(this@MainActivity)) }
            }

            SetupRow(
                done = notifOn,
                title = "התראות",
                body = "התראה שקטה אחת מחזיקה את שירות החסימה ער ומראה את המצב.",
                action = "אשר התראות",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            SetupRow(
                done = cameraOn,
                title = "מצלמה",
                body = "לאימות חי בלבד. לאפליקציה אין גישה לגלריה.",
                action = "אשר גישה",
            ) { cameraPermission.launch(Manifest.permission.CAMERA) }

            SetupRow(
                done = batteryOk,
                title = "סוללה",
                body = "בלי אופטימיזציית סוללה השירות לא נרדם באמצע היום.",
                action = "בטל אופטימיזציה",
            ) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }

            // ---- arming ----
            Spacer(Modifier.height(14.dp))
            Text("הפעלת החסימה", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "הזיהוי נעשה בתוך הטלפון: זיהוי כף יד ואצבעות, ובדיקת רצועות שחורות על האמה ועל כף היד. " +
                    "בלי חשבון, בלי שרת, בלי אינטרנט ובלי תשלום. ההפעלה היא מה שמתחיל את החסימה.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            if (!readyToArm && !armed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "קודם שלוש ההרשאות הראשונות למעלה.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Bronze,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (!armed) {
                InkButton(
                    text = "הפעל את החסימה",
                    enabled = readyToArm,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        prefs.arm()
                        armStatus = "החסימה פעילה."
                        BlockerService.ensureRunning(this@MainActivity)
                        bump()
                    },
                )
            } else {
                Text(
                    "החסימה פעילה.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Success,
                )
            }
            armStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Success)
            }
            if (!editable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ההגדרות נפתחות לעריכה אחרי שמניחים תפילין.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Muted,
                )
            }

            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            // ---- settings ----
            Eyebrow("הגדרות")
            Spacer(Modifier.height(10.dp))
            Text("עיר, לזמני משיכיר ושקיעה", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Cities.all.forEachIndexed { index, c ->
                    TextButton(
                        onClick = {
                            city = index
                            prefs.cityIndex = index
                            bump()
                        },
                        enabled = editable,
                    ) {
                        Text(
                            c.name,
                            color = if (index == city) Palette.Ink else Palette.Muted,
                            fontWeight = if (index == city) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SwitchRow(
                title = "מניחים תפילין בחול המועד",
                body = "ברירת המחדל בארץ: לא מניחים, והטלפון פתוח.",
                checked = chol,
                enabled = editable,
            ) {
                chol = it
                prefs.cholHamoedTefillin = it
                bump()
            }
            SwitchRow(
                title = "נשאר נעול גם אחרי השקיעה",
                body = "אם לא הנחת עד השקיעה, הטלפון נשאר סגור עד הבוקר. בכניסת שבת וחג הוא נפתח בכל מקרה.",
                checked = strict,
                enabled = editable,
            ) {
                strict = it
                prefs.strictAfterSunset = it
                bump()
            }

            Spacer(Modifier.height(14.dp))
            Text("אפליקציות שמותרות תמיד", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "שם חבילה בכל שורה. טלפון, מסך הבית, מקלדת ושעון מעורר מותרים תמיד גם בלי זה. " +
                    "הגדרות, חנות והסרת אפליקציות אי אפשר להוסיף.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                enabled = editable,
                minLines = 2,
                placeholder = { Text("com.example.app") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = "שמור רשימה",
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    prefs.extraAllowed = extra
                    BlockerService.instance?.refreshAllowlist()
                },
            )

            // ---- orderly exit ----
            if (armed) {
                Spacer(Modifier.height(28.dp))
                Hairline()
                Spacer(Modifier.height(20.dp))
                Eyebrow("יציאה מסודרת")
                Spacer(Modifier.height(6.dp))
                if (exitAt == 0L) {
                    Text(
                        "רוצה להפסיק? בקשת הסרה נכנסת לתוקף אחרי 48 שעות. עד אז הכול ממשיך כרגיל, " +
                            "ואפשר לבטל בכל רגע. אין דרך מהירה יותר, וזה בכוונה.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.InkSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        text = "בקש הסרה (48 שעות)",
                        enabled = editable,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefs.exitRequestedAt = System.currentTimeMillis()
                            exitAt = prefs.exitRequestedAt
                            bump()
                        },
                    )
                } else {
                    Text(
                        "ההסרה תיכנס לתוקף ב-${IsraelTime.dateTime(exitAt + DayPolicy.EXIT_COOLDOWN_MS)}. " +
                            "עד אז החסימה ממשיכה כרגיל.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Danger,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        text = "בטל את בקשת ההסרה",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefs.exitRequestedAt = 0L
                            exitAt = 0L
                            bump()
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            // ---- today ----
            Eyebrow("היום")
            Spacer(Modifier.height(6.dp))
            Text(
                "משיכיר ${IsraelTime.hhmm(policy.misheyakir(day))}  ·  שקיעה ${IsraelTime.hhmm(policy.sunset(day))}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            Text(
                "אימותים עד היום: ${prefs.verifiedCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            if (adbOn) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "ניפוי USB פעיל. דרך מחשב אפשר להסיר את האפליקציה. כבה אותו באפשרויות למפתחים אם אתה רוצה שזה יהיה אטום.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Danger,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "דרך חירום: הפעלה מחדש במצב בטוח (Safe Mode) מכבה אפליקציות צד שלישי ומאפשרת להסיר את האפליקציה.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    @Composable
    private fun fieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Palette.Ink,
        unfocusedBorderColor = Palette.Line,
        disabledBorderColor = Palette.Line,
        focusedTextColor = Palette.Ink,
        unfocusedTextColor = Palette.Ink,
        disabledTextColor = Palette.Muted,
        cursorColor = Palette.Ink,
    )

    @Composable
    private fun SetupRow(
        done: Boolean,
        title: String,
        body: String,
        action: String,
        onAction: () -> Unit,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓" else "○",
                    color = if (done) Palette.Success else Palette.Bronze,
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
    private fun SwitchRow(
        title: String,
        body: String,
        checked: Boolean,
        enabled: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
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
                    checkedThumbColor = Palette.Paper,
                    checkedTrackColor = Palette.Ink,
                    uncheckedThumbColor = Palette.Muted,
                    uncheckedTrackColor = Palette.PaperDeep,
                ),
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}
