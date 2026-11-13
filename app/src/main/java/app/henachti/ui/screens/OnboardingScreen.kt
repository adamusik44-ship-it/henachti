package app.henachti.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.henachti.data.Prefs
import app.henachti.service.BlockerService
import app.henachti.service.Guard
import app.henachti.service.findActivity
import app.henachti.ui.GoldButton
import app.henachti.ui.HenachtiSwitch
import app.henachti.ui.Palette
import app.henachti.ui.SoftCard
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

/**
 * The first thing a new user sees: six switches. Each one opens the matching system screen;
 * when the user comes back it is already on. Three are required, three are recommended.
 */
@Composable
fun OnboardingScreen(
    prefs: Prefs,
    now: Long,
    firstRun: Boolean,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(if (firstRun) "לפני שמתחילים" else "הרשאות ומנעול", onBack = if (firstRun) null else onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (withAdmin) "שישה מתגים, ומכאן הכול עובד לבד." else "חמישה מתגים, ומכאן הכול עובד לבד.",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "כל מתג פותח את מסך המערכת המתאים. כשחוזרים לכאן הוא כבר דלוק. " +
                    if (withAdmin) "שלושת הראשונים חובה." else "שני הראשונים חובה.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(18.dp))

            ToggleRow(usageOn, "לראות איזו אפליקציה פתוחה",
                "בלי זה המנעול עיוור. ברשימה שתיפתח מצא את \"וקשרתם\" והדלק.") {
                open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            ToggleRow(overlayOn, "להציג את מסך הנעילה מעל אפליקציות",
                "כך המסך קופץ מעל מה שפתחת.") {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            if (withAdmin) {
                ToggleRow(adminOn, "הגנה מפני מחיקה",
                    "אנדרואיד לא ייתן למחוק את האפליקציה בלחיצה ארוכה. יש מסך אישור של המערכת.") {
                    if (activity != null) Guard.requestAdmin(activity) else open(Guard.addAdminIntent(context))
                }
            }
            ToggleRow(cameraOn, "מצלמה", "לאימות החי של הכריכות. בלי גלריה.") {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
            ToggleRow(notifOn, "התראה שקטה", "מחזיקה את המנעול ער ומראה את המצב בשורת ההתראות.") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ToggleRow(
                batteryOk, "בלי חיסכון סוללה על האפליקציה",
                if (Guard.IS_PLAY) "כדי שהשירות לא יירדם באמצע היום. ברשימה שתיפתח בחר \"כל האפליקציות\", מצא את \"וקשרתם\" וכבה את החיסכון."
                else "כדי שהשירות לא יירדם באמצע היום.",
            ) {
                // The direct request needs a permission Google Play restricts; the Play build opens the system list instead.
                if (Guard.IS_PLAY) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                else open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }

            Spacer(Modifier.height(22.dp))
            if (!armed) {
                if (!required) {
                    Text(
                        if (withAdmin) "כשהשלושה הראשונים דלוקים, הכפתור נפתח." else "כשהשניים הראשונים דלוקים, הכפתור נפתח.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.GoldDeep,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                GoldButton("דרוך את המנעול", {
                    prefs.arm()
                    BlockerService.ensureRunning(context)
                    onRefresh()
                    onDone()
                }, Modifier.fillMaxWidth(), enabled = required)
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
