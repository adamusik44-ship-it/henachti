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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.LockState
import app.henachti.service.BlockerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        policy = DayPolicy(prefs)

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
                        onVerify = { startActivity(Intent(this, VerifyActivity::class.java)) },
                        onSnooze = { snooze(state) },
                        onHome = { goHome() },
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

private fun clock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

@Composable
private fun LockScreen(
    state: LockState,
    hebrewDate: String,
    onVerify: () -> Unit,
    onSnooze: () -> Unit,
    onHome: () -> Unit,
) {
    val snoozed = state as? LockState.Snoozed
    val snoozeAvailable = (state as? LockState.Locked)?.snoozeAvailable == true

    val title = when {
        snoozed != null -> "זה נפתח רק אחרי תפילין."
        else -> "עוד לא הנחת תפילין היום."
    }
    val body = when {
        snoozed != null ->
            "בחצי השעה הכול פתוח חוץ מההגדרות, חנות האפליקציות והסרת אפליקציות. " +
                "הן נפתחות אחרי האימות. הדחייה נגמרת ב-${clock(snoozed.until)}."
        snoozeAvailable ->
            "הטלפון סגור עד שמראים את היד עם התפילין למצלמה. " +
                "יש חצי שעה אחת של דחייה ביום, ואחריה זהו."
        else ->
            "חצי השעה של היום כבר נוצלה. הדרך היחידה להמשיך היא להניח."
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(72.dp))
        Eyebrow(hebrewDate)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(18.dp))
        Hairline()
        Spacer(Modifier.height(18.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)

        Spacer(Modifier.weight(1f))

        InkButton("הנחתי. אמת במצלמה", onVerify, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (snoozed == null) {
            GhostButton(
                text = if (snoozeAvailable) "עוד חצי שעה (פעם אחת ביום)" else "עוד חצי שעה, נוצל היום",
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
                enabled = snoozeAvailable,
            )
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("למסך הבית", color = Palette.Muted, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(36.dp))
    }
}
