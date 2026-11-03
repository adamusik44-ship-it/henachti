package app.henachti.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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

    val title = when {
        tampered != null -> TamperUi.title(tampered.reason)
        snoozed != null -> "זה נפתח רק אחרי תפילין."
        else -> "עוד לא הנחת תפילין היום."
    }
    val body = when {
        tampered != null -> TamperUi.body(tampered.reason)
        snoozed != null ->
            "דחייה ${snoozed.used} מתוך ${snoozed.max}. הכול פתוח חוץ מההגדרות, חנות האפליקציות והסרת אפליקציות, " +
                "והן נפתחות אחרי האימות. הדחייה נגמרת ב-${IsraelTime.hhmm(snoozed.until)}."
        snoozeAvailable ->
            "הטלפון סגור עד שמראים את האמה עם הכריכות למצלמה. " +
                if (left == 1) "נשארה לך דחייה אחת של חצי שעה להיום." else "נשארו לך $left דחיות של חצי שעה להיום."
        max == 0 ->
            "אין דחיות. הדרך היחידה להמשיך היא להניח."
        else ->
            "הדחיות של היום נגמרו. הדרך היחידה להמשיך היא להניח."
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Eyebrow(hebrewDate)
        Spacer(Modifier.height(28.dp))
        Arch(width = 124.dp, height = 152.dp) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft, textAlign = TextAlign.Center)
        if (dedication != null && tampered == null) {
            Spacer(Modifier.height(10.dp))
            Text("ההנחה של היום מוקדשת $dedication", style = MaterialTheme.typography.bodySmall, color = Palette.GoldDeep, textAlign = TextAlign.Center)
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
                    text = if (snoozeAvailable) "עוד חצי שעה ($left מתוך $max)" else "הדחיות נגמרו להיום",
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = snoozeAvailable,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("למסך הבית", color = Palette.Muted, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(36.dp))
    }
}
