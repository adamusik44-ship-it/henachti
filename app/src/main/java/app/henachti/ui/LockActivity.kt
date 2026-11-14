package app.henachti.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import app.henachti.domain.IsraelTime
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
        // the lock is a coloured full screen: draw under the bars, with light icons on it
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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


/**
 * The lock as a focus screen: wine red, one big sentence, the sun peeking over a hill (its face is
 * the state), one light pill to verify, and two quiet links. Nothing else.
 */
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

    val bg = if (tampered != null) Palette.Coral else Palette.Wine
    val title = when {
        tampered != null -> TamperUi.title(tampered.reason)
        snoozed != null -> "זה נפתח רק אחרי תפילין."
        else -> "עוד לא הנחת תפילין היום."
    }
    val body = when {
        tampered != null -> TamperUi.body(tampered.reason)
        snoozed != null -> "דחייה ${snoozed.used} מתוך ${snoozed.max}. ההגדרות, החנות והסרת אפליקציות נפתחות אחרי האימות."
        now >= windowEnd -> "הטלפון נפתח אחרי שבע כריכות. השקיעה כבר עברה."
        else -> "הטלפון נפתח אחרי שבע כריכות. השקיעה ב-${IsraelTime.hhmm(windowEnd)}."
    }
    val mood = when {
        tampered != null -> SunMood.WORRIED
        snoozed != null -> SunMood.SLEEPY
        now >= windowEnd -> SunMood.NIGHT
        else -> SunMood.WAITING
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        Text(hebrewDate, style = MaterialTheme.typography.labelMedium, color = Palette.Blush)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = if (tampered != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayLarge,
            color = Palette.Cream,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.Blush, textAlign = TextAlign.Center)
        if (dedication != null && tampered == null) {
            Spacer(Modifier.height(6.dp))
            Text("ההנחה של היום מוקדשת $dedication", style = MaterialTheme.typography.bodySmall, color = Palette.Blush, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.weight(1f))
        if (snoozed != null) {
            CountdownRing(until = snoozed.until, total = BlockerService.SNOOZE_MS, diameter = 150.dp, dark = true)
            Spacer(Modifier.height(16.dp))
        } else {
            SunMascot(mood, Modifier.size(width = 270.dp, height = 190.dp))
        }

        if (tampered != null) {
            GoldButton(
                TamperUi.action(tampered.reason), { onFix(tampered.reason) }, Modifier.fillMaxWidth(),
                background = Palette.Cream, content = Palette.Ink,
            )
        } else {
            GoldButton("הנחתי. אמת במצלמה", onVerify, Modifier.fillMaxWidth(), background = Palette.Cream, content = Palette.Wine)
            if (snoozed == null && max > 0) {
                TextButton(onClick = onSnooze, enabled = snoozeAvailable, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (snoozeAvailable) "עוד חצי שעה" else "הדחיות נגמרו להיום",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Cream,
                        )
                        Spacer(Modifier.width(10.dp))
                        SnoozeDots(left = left, max = max)
                    }
                }
            }
        }
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("למסך הבית", color = Palette.Blush, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(18.dp))
    }
}
