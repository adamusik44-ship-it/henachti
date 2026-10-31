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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.henachti.data.Prefs
import app.henachti.domain.Cities
import app.henachti.domain.DayPolicy
import app.henachti.domain.LockState
import app.henachti.domain.UnlockReason
import app.henachti.service.BlockerService
import app.henachti.verify.TefillinVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Status, first-time setup and settings. Settings are editable only while the day is unlocked. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val tick = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(prefs)
        setContent {
            HenachtiTheme { MainScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        BlockerService.ensureRunning(this)
        tick.longValue = System.currentTimeMillis()
    }

    private fun usageGranted(): Boolean = BlockerService.hasUsageAccess(this)

    private fun overlayGranted(): Boolean = BlockerService.canDrawOverlays(this)

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun serviceRunning(): Boolean = BlockerService.instance != null

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun batteryExempt(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun clock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    private fun statusTitle(state: LockState): String = when (state) {
        is LockState.Locked -> "נעול עד שמניחים."
        is LockState.Snoozed -> "דחייה פעילה."
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
        is LockState.Snoozed -> "הכול פתוח עד ${clock(state.until)}, ואז נסגר שוב."
        is LockState.Unlocked -> when (state.reason) {
            UnlockReason.SETUP_INCOMPLETE ->
                "השלם את ההגדרה הראשונית למטה. החסימה מתחילה ברגע שיש מפתח API ושתי ההרשאות המיוחדות ניתנו."
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
        val scope = rememberCoroutineScope()

        var apiKey by remember { mutableStateOf(prefs.apiKey) }
        var keyStatus by remember { mutableStateOf<String?>(null) }
        var testing by remember { mutableStateOf(false) }
        var city by remember { mutableIntStateOf(prefs.cityIndex) }
        var chol by remember { mutableStateOf(prefs.cholHamoedTefillin) }
        var strict by remember { mutableStateOf(prefs.strictAfterSunset) }
        var extra by remember { mutableStateOf(prefs.extraAllowed) }

        val usageOn = remember(now) { usageGranted() }
        val overlayOn = remember(now) { overlayGranted() }
        val notifOn = remember(now) { notificationsGranted() }
        val running = remember(now) { serviceRunning() }
        val cameraOn = remember(now) { cameraGranted() }
        val batteryOk = remember(now) { batteryExempt() }
        val keyEditable = editable || prefs.lastAuthError

        val cameraPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { tick.longValue = System.currentTimeMillis() }
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { tick.longValue = System.currentTimeMillis() }

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

            Eyebrow("מצב עכשיו")
            Spacer(Modifier.height(6.dp))
            Text(statusTitle(state), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(statusBody(state), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            if (prefs.setupDone && !running) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (usageOn && overlayOn) "שירות החסימה לא רץ כרגע. פתח את האפליקציה שוב או לחץ למטה."
                    else "חסרות הרשאות, אז בפועל שום דבר לא חסום. ראה הגדרה ראשונית.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Danger,
                )
                if (usageOn && overlayOn) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("הפעל את השירות", {
                        BlockerService.ensureRunning(this@MainActivity)
                        tick.longValue = System.currentTimeMillis()
                    }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(16.dp))
            InkButton(
                text = "אימות במצלמה",
                onClick = { startActivity(Intent(this@MainActivity, VerifyActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = prefs.setupDone && cameraOn,
            )

            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            Eyebrow("הגדרה ראשונית")
            Spacer(Modifier.height(10.dp))
            SetupRow(
                done = usageOn,
                title = "גישה לנתוני שימוש",
                body = "כך האפליקציה יודעת איזו אפליקציה פתוחה עכשיו. ברשימה שתיפתח, מצא את \"הנחתי\" והפעל.",
                action = "פתח הגדרות גישה לשימוש",
            ) {
                runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }

            SetupRow(
                done = overlayOn,
                title = "הצגה מעל אפליקציות אחרות",
                body = "כך מסך הנעילה יכול לקפוץ מעל האפליקציה שנפתחה.",
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
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("מפתח API של Anthropic", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "האימות נעשה על ידי מודל ראייה של Claude. המפתח נשמר רק בטלפון. " +
                    "העלות: כמה אגורות ליום. שמירת המפתח היא מה שמפעיל את החסימה.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                enabled = keyEditable,
                singleLine = true,
                placeholder = { Text("sk-ant-...") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = if (testing) "בודק..." else "שמור ובדוק",
                enabled = keyEditable && apiKey.isNotBlank() && !testing,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    testing = true
                    keyStatus = null
                    val candidate = apiKey.trim()
                    scope.launch {
                        val problem = withContext(Dispatchers.IO) { TefillinVerifier(candidate).testKey() }
                        testing = false
                        if (problem == null) {
                            prefs.apiKey = candidate
                            prefs.lastAuthError = false
                            keyStatus = "המפתח עובד ונשמר."
                            BlockerService.ensureRunning(this@MainActivity)
                            tick.longValue = System.currentTimeMillis()
                        } else {
                            keyStatus = problem
                        }
                    }
                },
            )
            keyStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("המפתח עובד")) Palette.Success else Palette.Danger,
                )
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
                            tick.longValue = System.currentTimeMillis()
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
                tick.longValue = System.currentTimeMillis()
            }
            SwitchRow(
                title = "נשאר נעול גם אחרי השקיעה",
                body = "אם לא הנחת עד השקיעה, הטלפון נשאר סגור עד הבוקר. בכניסת שבת וחג הוא נפתח בכל מקרה.",
                checked = strict,
                enabled = editable,
            ) {
                strict = it
                prefs.strictAfterSunset = it
                tick.longValue = System.currentTimeMillis()
            }

            Spacer(Modifier.height(14.dp))
            Text("אפליקציות שמותרות תמיד", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "שם חבילה בכל שורה. טלפון, מסך הבית, מקלדת ושעון מעורר מותרים תמיד גם בלי זה.",
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

            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            Eyebrow("היום")
            Spacer(Modifier.height(6.dp))
            Text(
                "משיכיר ${clock(policy.misheyakir(day))}  ·  שקיעה ${clock(policy.sunset(day))}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
            Text(
                "אימותים עד היום: ${prefs.verifiedCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkSoft,
            )
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
