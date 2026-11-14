package app.henachti.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.henachti.R
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.IsraelTime
import app.henachti.domain.LockState
import app.henachti.domain.UnlockReason
import app.henachti.ui.MainActivity
import app.henachti.ui.restDayTitle

/**
 * Home-screen widget: today's status ("הנחת היום" or "עוד לא הנחת"), the snoozes left as dots,
 * and the streak. Plain RemoteViews, no extra library. Refreshed by the app whenever the state
 * changes (verify, snooze, the service noticing a new phase of the day) and by the system every 30 minutes.
 */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runCatching { render(context, appWidgetManager, appWidgetIds) }
    }

    companion object {
        /** Redraws every placed widget. Safe to call often; does nothing when none is placed. */
        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
                if (ids.isNotEmpty()) render(context, manager, ids)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext
            val policy = DayPolicy(app)
            val now = System.currentTimeMillis()
            val state = policy.state(now)
            val streak = policy.streak(now)
            val (title, sub) = texts(policy, state)

            val open = PendingIntent.getActivity(
                app, 1, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(app.packageName, R.layout.widget_status).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_sub, sub)
                setTextViewText(R.id.widget_streak, if (streak == 1) "רצף: יום אחד" else "רצף: $streak ימים")
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }

        private fun texts(policy: DayPolicy, state: LockState): Pair<String, String> = when (state) {
            is LockState.Unlocked -> when (state.reason) {
                UnlockReason.VERIFIED -> "הנחת היום" to "הטלפון פתוח עד מחר בבוקר"
                UnlockReason.NO_TEFILLIN_DAY -> restDayTitle(policy.noTefillinReason(state.day)) to "אין הנחה היום"
                UnlockReason.BEFORE_START -> "עוד לא הנחת" to "המנעול נדרך ב-${IsraelTime.hhmm(policy.lockStart(state.day))}"
                UnlockReason.AFTER_SUNSET -> "השמש שקעה" to "מחר בבוקר מתחילים נקי"
                UnlockReason.SHABBAT_BEGAN -> "שבת שלום" to "המנעול נח עד מוצאי שבת"
                UnlockReason.SETUP_INCOMPLETE -> "המנעול לא דרוך" to "כמה מתגים, ומכאן הכול לבד"
            }
            is LockState.Locked -> "עוד לא הנחת" to snoozeLine(state.snoozesLeft, state.snoozesPerDay)
            is LockState.Snoozed -> "בדחייה עד ${IsraelTime.hhmm(state.until)}" to snoozeLine(state.max - state.used, state.max)
            is LockState.Tampered -> "משהו כובה" to "פתח את האפליקציה לתיקון"
        }

        private fun snoozeLine(left: Int, max: Int): String {
            if (max <= 0) return "בלי דחיות"
            val l = left.coerceIn(0, max)
            val dots = "●".repeat(l) + "○".repeat(max - l)
            val words = when (l) {
                0 -> "הדחיות נגמרו"
                1 -> "נשארה דחייה אחת"
                else -> "נשארו $l דחיות"
            }
            return "$words  $dots"
        }
    }
}
