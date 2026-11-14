package app.henachti.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.henachti.R
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import app.henachti.service.BlockerService
import app.henachti.service.Guard
import app.henachti.service.findActivity
import app.henachti.ui.GoldButton
import app.henachti.ui.HenachtiSwitch
import app.henachti.ui.OpeningLock
import app.henachti.ui.Palette
import app.henachti.ui.SoftCard
import app.henachti.ui.StepBar
import app.henachti.ui.TopBar

/** Six switches in the direct build, five on Google Play (no uninstall protection there). */
fun totalSwitches(): Int = if (Guard.ADMIN_SUPPORTED) 6 else 5

/** Counts how many of the switches are on. Used by Settings for its summary line. */
fun grantedCount(context: Context): Int {
    var n = 0
    if (Guard.hasUsageAccess(context)) n++
    if (Guard.canDrawOverlays(context)) n++
    if (Guard.ADMIN_SUPPORTED && Guard.isAdminActive(context)) n++
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) n++
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) n++
    if ((context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)) n++
    return n
}

/** One permission: whether it is on, whether the lock needs it, and how to switch it on. */
private class Perm(
    val on: Boolean,
    val required: Boolean,
    val title: String,
    val why: String,
    val action: String,
    val open: () -> Unit,
)

/**
 * First run: one permission per step with a progress bar, then the usual tefillin time, then arming.
 * From Settings (or once armed): the same switches as one list.
 */
@Composable
fun OnboardingScreen(
    prefs: Prefs,
    now: Long,
    firstRun: Boolean,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    startStep: Int = 0,
) {
    val context = LocalContext.current
    val usageOn = remember(now) { Guard.hasUsageAccess(context) }
    val overlayOn = remember(now) { Guard.canDrawOverlays(context) }
    val adminOn = remember(now) { Guard.isAdminActive(context) }
    val cameraOn = remember(now) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val notifOn = remember(now) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(now) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    }
    val armed = prefs.setupDone
    val withAdmin = Guard.ADMIN_SUPPORTED
    val required = usageOn && overlayOn && (adminOn || !withAdmin)

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }

    // System screens are opened from the Activity itself, with no extra flags. Some of them
    // (device admin above all) close at once when they are started as a new task.
    val activity = context.findActivity()
    fun open(intent: Intent) {
        runCatching {
            if (activity != null) activity.startActivity(intent)
            else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    val perms = buildList {
        add(
            Perm(
                usageOn, true, "לראות איזו אפליקציה פתוחה",
                "וקשרתם רואה איזו אפליקציה פתוחה כרגע, רק כדי להציג את מסך הנעילה מעל אפליקציות חסומות עד האימות של היום. " +
                    "המידע לא נשמר, לא נרשם ולא יוצא מהטלפון. ברשימה שתיפתח מצא את \"וקשרתם\" והדלק.",
                "פתח את הרשימה",
            ) { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )
        add(
            Perm(
                overlayOn, true, "להציג את מסך הנעילה מעל אפליקציות",
                "כך מסך הנעילה קופץ מעל מה שפתחת. ההרשאה הזאת לא נותנת לוקשרתם לקרוא את מה שמוצג על המסך.",
                "פתח את ההגדרה",
            ) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
        )
        if (withAdmin) {
            add(
                Perm(
                    adminOn, true, "הגנה מפני מחיקה",
                    "אנדרואיד לא ייתן למחוק את האפליקציה בלחיצה ארוכה. יש מסך אישור של המערכת.",
                    "הפעל הגנה",
                ) { if (activity != null) Guard.requestAdmin(activity) else open(Guard.addAdminIntent(context)) }
            )
        }
        add(
            Perm(
                cameraOn, false, "מצלמה",
                "לאימות החי של הכריכות. הפריימים נבדקים בזיכרון הטלפון ונמחקים מיד. אין גישה לגלריה, ושום תמונה לא נשמרת ולא נשלחת.",
                "אשר מצלמה",
            ) { cameraPermission.launch(Manifest.permission.CAMERA) }
        )
        add(
            Perm(
                notifOn, false, "התראה שקטה",
                "מחזיקה את המנעול ער ומראה את המצב בשורת ההתראות.",
                "אשר התראה",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
        add(
            Perm(
                batteryOk, false, "בלי חיסכון סוללה על האפליקציה",
                if (Guard.IS_PLAY) "כדי שהשירות לא יירדם באמצע היום. ברשימה שתיפתח בחר \"כל האפליקציות\", מצא את \"וקשרתם\" וכבה את החיסכון."
                else "כדי שהשירות לא יירדם באמצע היום.",
                "פתח את ההגדרה",
            ) {
                // The direct request needs a permission Google Play restricts; the Play build opens the system list instead.
                if (Guard.IS_PLAY) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                else open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }
        )
    }

    val arm = {
        prefs.arm()
        BlockerService.ensureRunning(context)
        onRefresh()
        onDone()
    }

    if (firstRun && !armed) {
        SetupSteps(prefs = prefs, perms = perms, required = required, onArm = arm, onLater = onDone, startStep = startStep)
    } else {
        PermissionList(perms, withAdmin, required, armed, firstRun, onArm = arm, onDone = onDone, onBack = onBack)
    }
}

@Composable
private fun SetupSteps(
    prefs: Prefs,
    perms: List<Perm>,
    required: Boolean,
    onArm: () -> Unit,
    onLater: () -> Unit,
    startStep: Int = 0,
) {
    val total = perms.size + 3 // welcome, the permissions, the usual time, arming
    var step by rememberSaveable { mutableIntStateOf(startStep.coerceIn(0, total - 1)) }
    val last = total - 1
    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("צעד ${step + 1} מתוך $total", style = MaterialTheme.typography.labelMedium, color = Palette.GoldText)
            Spacer(Modifier.weight(1f))
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("חזרה", color = Palette.Muted) }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        StepBar(step, total)
        Spacer(Modifier.height(28.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                step == 0 -> WelcomePage()
                step <= perms.size -> PermPage(perms[step - 1])
                step == last - 1 -> TimePage(prefs)
                else -> ArmPage(perms, required)
            }
        }

        Spacer(Modifier.height(12.dp))
        // every button says where it leads, never a bare "next"
        val toNext = when {
            step + 1 <= perms.size -> "להרשאה הבאה"
            step + 1 == last - 1 -> "לבחירת השעה"
            else -> "לדריכת המנעול"
        }
        when {
            step == 0 -> GoldButton("להרשאה הראשונה", { step++ }, Modifier.fillMaxWidth())
            step <= perms.size -> {
                val p = perms[step - 1]
                if (p.on) {
                    GoldButton(toNext, { step++ }, Modifier.fillMaxWidth())
                } else {
                    GoldButton(p.action, p.open, Modifier.fillMaxWidth())
                    if (!p.required) {
                        TextButton(onClick = { step++ }, modifier = Modifier.fillMaxWidth()) {
                            Text("דלג בינתיים", color = Palette.InkSoft)
                        }
                    }
                }
            }
            step == last - 1 -> GoldButton(toNext, { step++ }, Modifier.fillMaxWidth())
            else -> {
                GoldButton("דרוך את המנעול", onArm, Modifier.fillMaxWidth(), enabled = required)
                if (!required) {
                    val missing = perms.indexOfFirst { it.required && !it.on }
                    if (missing >= 0) {
                        TextButton(onClick = { step = missing + 1 }, modifier = Modifier.fillMaxWidth()) {
                            Text("חזרה להרשאה שחסרה", color = Palette.GoldText)
                        }
                    }
                }
            }
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text("אחר כך. קודם להסתכל", color = Palette.Muted)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // show tomorrow morning instead of describing it: locked, seven windings, open
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MorningFrame("בבוקר: נעול") { OpeningLock(open = false, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.width(12.dp))
            MorningFrame("שבע כריכות") { SevenDots() }
            Spacer(Modifier.width(12.dp))
            MorningFrame("פתוח עד מחר") { OpeningLock(open = true, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "מחר בבוקר הטלפון ייפתח רק אחרי שבע כריכות.",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "בכל צעד הרשאה אחת ומשפט אחד למה. בסוף בוחרים שעה ודורכים. " +
                "הכול נשאר בטלפון, בלי שרת ובלי חשבון.",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.InkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

/** A small phone screen for the welcome strip. */
@Composable
private fun MorningFrame(caption: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 84.dp, height = 116.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Chocolate)
                .border(1.5.dp, Palette.Gold, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
        Spacer(Modifier.height(8.dp))
        Text(caption, style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
    }
}

@Composable
private fun SevenDots() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        listOf(4, 3).forEach { n ->
            Row {
                repeat(n) {
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Palette.Gold)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermPage(p: Perm) {
    Column(Modifier.fillMaxWidth()) {
        Text(p.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(p.why, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
        Spacer(Modifier.height(8.dp))
        Text(
            if (p.required) "חובה, בלי זה המנעול לא עובד." else "מומלץ. אפשר לדלג ולהדליק אחר כך מההגדרות.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.GoldText,
        )
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusMark(p.on)
            Spacer(Modifier.width(10.dp))
            Text(
                if (p.on) "דלוק. אפשר להמשיך." else "עוד כבוי. הכפתור למטה פותח את מסך המערכת, וכשחוזרים לכאן זה כבר דלוק.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (p.on) Palette.Success else Palette.Muted,
            )
        }
    }
}

private val TIME_OPTIONS = listOf(
    -1 to "מזמן משיכיר",
    6 * 60 + 30 to "06:30",
    7 * 60 + 30 to "07:30",
    8 * 60 + 30 to "08:30",
    10 * 60 to "10:00",
)

@Composable
private fun TimePage(prefs: Prefs) {
    val context = LocalContext.current
    val policy = remember { DayPolicy(context) }
    val misheyakirToday = remember { IsraelTime.hhmm(policy.misheyakir(policy.currentDay())) }
    var chosen by remember { mutableIntStateOf(prefs.lockStartMinutes) }

    Column(Modifier.fillMaxWidth()) {
        Text("מתי אתה מניח בדרך כלל?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "בשעה הזאת המנעול נדרך. לפניה הטלפון פתוח, ואימות מוקדם כבר נספר ליום. אפשר לשנות בהגדרות.",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.InkSoft,
        )
        Spacer(Modifier.height(18.dp))
        TIME_OPTIONS.forEach { (minutes, label) ->
            val on = chosen == minutes
            SoftCard(
                Modifier.fillMaxWidth(),
                onClick = {
                    chosen = minutes
                    prefs.lockStartMinutes = minutes
                },
                gold = on,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        if (minutes < 0) {
                            Text("הכי מוקדם שמותר. היום: $misheyakirToday", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                    }
                    RadioDot(on)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (TIME_OPTIONS.none { it.first == chosen }) {
            Text(
                "כרגע: %02d:%02d, כפי שנבחר בהגדרות.".format(java.util.Locale.US, chosen / 60, chosen % 60),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.GoldText,
            )
        }
    }
}

@Composable
private fun ArmPage(perms: List<Perm>, required: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(if (required) "הכול מוכן." else "כמעט מוכן.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            if (required) "מהרגע שדורכים, הטלפון נפתח כל בוקר רק אחרי אימות במצלמה. ביום הראשון אפשר לכבות מיד."
            else "חסרה הרשאת חובה, ובלעדיה המנעול לא יכול לעבוד.",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.InkSoft,
        )
        Spacer(Modifier.height(18.dp))
        perms.forEach { p ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusMark(p.on)
                Spacer(Modifier.width(10.dp))
                Text(p.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (p.required) "חובה" else "מומלץ",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.required && !p.on) Palette.Danger else Palette.Muted,
                )
            }
        }
    }
}

@Composable
private fun StatusMark(on: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (on) Palette.Gold else Color.Transparent)
            .border(1.5.dp, if (on) Palette.Gold else Palette.Sand, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.ChocolateDeep, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun RadioDot(on: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (on) Palette.Gold else Palette.Sand, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Palette.Gold)
            )
        }
    }
}

/** The settings view: every switch on one list, as before. */
@Composable
private fun PermissionList(
    perms: List<Perm>,
    withAdmin: Boolean,
    required: Boolean,
    armed: Boolean,
    firstRun: Boolean,
    onArm: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(if (firstRun) "לפני שמתחילים" else "הרשאות ומנעול", onBack = if (firstRun) null else onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (withAdmin) "שישה מתגים, ומחר בבוקר המנעול עובד." else "חמישה מתגים, ומחר בבוקר המנעול עובד.",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "כל מתג פותח את מסך המערכת המתאים. כשחוזרים לכאן הוא כבר דלוק. " +
                    if (withAdmin) "שלושת הראשונים חובה." else "שני הראשונים חובה.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(18.dp))

            perms.forEach { p -> ToggleRow(p.on, p.title, p.why, p.open) }

            Spacer(Modifier.height(22.dp))
            if (!armed) {
                if (!required) {
                    Text(
                        if (withAdmin) "כשהשלושה הראשונים דלוקים, הכפתור נפתח." else "כשהשניים הראשונים דלוקים, הכפתור נפתח.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.GoldText,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                GoldButton("דרוך את המנעול", onArm, Modifier.fillMaxWidth(), enabled = required)
                if (firstRun) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("אחר כך. קודם להסתכל", color = Palette.Muted)
                    }
                }
            } else {
                Text("המנעול דרוך.", style = MaterialTheme.typography.titleMedium, color = Palette.Success)
                Spacer(Modifier.height(10.dp))
                GoldButton("למסך הראשי", onDone, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ToggleRow(on: Boolean, title: String, body: String, onToggle: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onToggle, gold = on) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            Spacer(Modifier.width(12.dp))
            HenachtiSwitch(checked = on, onChange = { onToggle() })
        }
    }
    Spacer(Modifier.height(10.dp))
}
