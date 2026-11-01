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
import app.henachti.ui.GoldButton
import app.henachti.ui.HenachtiSwitch
import app.henachti.ui.Palette
import app.henachti.ui.SoftCard
import app.henachti.ui.TopBar

/** Counts how many of the six switches are on. Used by Settings for its summary line. */
fun grantedCount(context: Context): Int {
    var n = 0
    if (Guard.hasUsageAccess(context)) n++
    if (Guard.canDrawOverlays(context)) n++
    if (Guard.isAdminActive(context)) n++
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
    val required = usageOn && overlayOn && adminOn

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
        TopBar(if (firstRun) "לפני שמתחילים" else "הרשאות ומנעול", onBack = if (firstRun) null else onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("שישה מתגים, ומכאן הכול עובד לבד.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "כל מתג פותח את מסך המערכת המתאים. כשחוזרים לכאן הוא כבר דלוק. שלושת הראשונים חובה.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(18.dp))

            ToggleRow(usageOn, "לראות איזו אפליקציה פתוחה",
                "בלי זה המנעול עיוור. ברשימה שתיפתח מצא את \"הנחתי\" והדלק.") {
                open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            ToggleRow(overlayOn, "להציג את מסך הנעילה מעל אפליקציות",
                "כך המסך קופץ מעל מה שפתחת.") {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            ToggleRow(adminOn, "הגנה מפני מחיקה",
                "אנדרואיד לא ייתן למחוק את האפליקציה בלחיצה ארוכה. יש מסך אישור של המערכת.") {
                open(Guard.addAdminIntent(context))
            }
            ToggleRow(cameraOn, "מצלמה", "לאימות החי של הכריכות. בלי גלריה.") {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
            ToggleRow(notifOn, "התראה שקטה", "מחזיקה את המנעול ער ומראה את המצב בשורת ההתראות.") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ToggleRow(batteryOk, "בלי חיסכון סוללה על האפליקציה", "כדי שהשירות לא יירדם באמצע היום.") {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }

            Spacer(Modifier.height(22.dp))
            if (!armed) {
                if (!required) {
                    Text(
                        "כשהשלושה הראשונים דלוקים, הכפתור נפתח.",
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
