package app.henachti.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.henachti.data.Dedications
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.LockState
import app.henachti.domain.TamperReason
import app.henachti.service.BlockerService
import app.henachti.widget.StatusWidget
import kotlinx.coroutines.delay

/**
 * Full-screen gate shown on top of any blocked app. Back does nothing here;
 * the only ways out are the camera, the single daily snooze, or the home screen.
 */
class LockActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val uiState = mutableStateOf<LockState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            HenachtiTheme {
                val state = uiState.value
                if (state != null) {
                    LockScreen(
                        state = state,
                        hebrewDate = policy.hebrewDate(state.day),
                        dedication = Dedications.label(prefs),
                        windowStart = policy.misheyakir(state.day),
                        windowEnd = policy.sunset(state.day),
                        onVerify = { startActivity(Intent(this, VerifyActivity::class.java)) },
                        onSnooze = { snooze(state) },
                        onHome = { goHome() },
                        onFix = { reason -> TamperUi.launchFix(this, reason) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val state = policy.state()
        if (state is LockState.Unlocked) {
            finish()
            return
        }
        uiState.value = state
    }

    private fun snooze(state: LockState) {
        if (state !is LockState.Locked || !state.snoozeAvailable) return
        val until = System.currentTimeMillis() + BlockerService.SNOOZE_MS
        prefs.startSnooze(state.day, until)
        BlockerService.instance?.onSnoozeStarted(until)
        StatusWidget.updateAll(this)
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}


@Composable
private fun LockScreen(
    state: LockState,
    hebrewDate: String,
    dedication: String?,
    windowStart: Long,
    windowEnd: Long,
    onVerify: () -> Unit,
    onSnooze: () -> Unit,
    onHome: () -> Unit,
    onFix: (TamperReason) -> Unit,
) {
    val tampered = state as? LockState.Tampered
    val snoozed = state as? LockState.Snoozed
    val locked = state as? LockState.Locked
    val snoozeAvailable = locked?.snoozeAvailable == true
    val left = locked?.snoozesLeft ?: 0
    val max = locked?.snoozesPerDay ?: 0

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val title = when {
        tampered != null -> TamperUi.title(tampered.reason)
        snoozed != null -> "זה נפתח רק אחרי תפילין."
        else -> "עוד לא הנחת תפילין היום."
    }
    val body = when {
        tampered != null -> TamperUi.body(tampered.reason)
        snoozed != null ->
            "דחייה ${snoozed.used} מתוך ${snoozed.max}. הכול פתוח חוץ מההגדרות, חנות האפליקציות והסרת אפליקציות, " +
                "והן נפתחות אחרי האימות."
        max == 0 -> "אין דחיות. הדרך היחידה להמשיך היא להניח."
        snoozeAvailable -> "הטלפון סגור עד שמראים למצלמה את האמה עם הכריכות."
        else -> "הדחיות של היום נגמרו. הדרך היחידה להמשיך היא להניח."
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Eyebrow(hebrewDate)
        Spacer(Modifier.height(20.dp))
        Arch(width = 108.dp, height = 132.dp) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft, textAlign = TextAlign.Center)
        if (dedication != null && tampered == null) {
            Spacer(Modifier.height(8.dp))
            Text("ההנחה של היום מוקדשת $dedication", style = MaterialTheme.typography.bodySmall, color = Palette.GoldDeep, textAlign = TextAlign.Center)
        }

        if (tampered == null) {
            Spacer(Modifier.height(22.dp))
            if (snoozed != null) {
                CountdownRing(until = snoozed.until, total = BlockerService.SNOOZE_MS, diameter = 112.dp)
            } else {
                DayArc(start = windowStart, end = windowEnd, now = now, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        if (tampered != null) {
            GoldButton(TamperUi.action(tampered.reason), { onFix(tampered.reason) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        } else {
            GoldButton("הנחתי. אמת במצלמה", onVerify, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            if (snoozed == null && max > 0) {
                GhostButton(
                    text = if (snoozeAvailable) "עוד חצי שעה" else "הדחיות נגמרו להיום",
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = snoozeAvailable,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SnoozeDots(left = left, max = max)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (left) {
                            0 -> "לא נשארו דחיות"
                            1 -> "נשארה דחייה אחת"
                            else -> "נשארו $left דחיות"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Muted,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("למסך הבית", color = Palette.Muted, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(28.dp))
    }
}
