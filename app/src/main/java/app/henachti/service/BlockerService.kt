package app.henachti.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.LockState
import app.henachti.ui.LockActivity

/**
 * Watches which app is in the foreground. Whenever a non-allowed app comes to the front
 * while the day is still locked, the lock screen is put on top of it.
 *
 * The service reads no screen content: only the package name of the window that changed.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastLaunchAt = 0L
    private var currentPkg: String? = null
    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val allowed = HashSet<String>()
    private val recheck = Runnable { currentPkg?.let { evaluate(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        policy = DayPolicy(prefs)
        refreshAllowlist()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        currentPkg = pkg
        evaluate(pkg)
    }

    /** Rebuilds the set of packages that are never blocked (launcher, dialer, keyboard, clock, this app). */
    fun refreshAllowlist() {
        val set = HashSet<String>()
        set += packageName
        set += SYSTEM_ALLOWED

        val pm = packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { set += it.activityInfo.packageName }
        pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.let { set += it.packageName }

        val dial = Intent(Intent.ACTION_DIAL)
        pm.queryIntentActivities(dial, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { set += it.activityInfo.packageName }

        runCatching {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.forEach { set += it.packageName }
        }

        set += prefs.extraAllowedSet()
        synchronized(allowed) {
            allowed.clear()
            allowed += set
        }
    }

    /** Called by the lock screen when the once-a-day snooze starts, so the lock returns on time. */
    fun onSnoozeStarted(until: Long) = scheduleRecheck(until)

    private fun evaluate(pkg: String) {
        if (synchronized(allowed) { pkg in allowed }) return
        when (val state = policy.state()) {
            is LockState.Locked -> showLock()
            is LockState.Snoozed -> {
                if (pkg in RESTRICTED_DURING_SNOOZE) showLock()
                scheduleRecheck(state.until)
            }
            is LockState.Unlocked -> Unit
        }
    }

    private fun scheduleRecheck(at: Long) {
        handler.removeCallbacks(recheck)
        val delay = (at - System.currentTimeMillis() + 500L).coerceAtLeast(500L)
        handler.postDelayed(recheck, delay)
    }

    private fun showLock() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < 700L) return
        lastLaunchAt = now
        val intent = Intent(this, LockActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        startActivity(intent)
    }

    companion object {
        const val SNOOZE_MS = 30L * 60L * 1000L

        @Volatile
        var instance: BlockerService? = null
            private set

        /** Always reachable, even while locked: calls, alarms, system UI, permission dialogs. */
        private val SYSTEM_ALLOWED = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.alarmclock",
            "com.google.android.gms",
        )

        /** Stay closed during the snooze so the lock cannot be disabled or uninstalled from inside it. */
        private val RESTRICTED_DURING_SNOOZE = setOf(
            "com.android.settings",
            "com.android.settings.intelligence",
            "com.samsung.android.app.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.vending",
            "com.samsung.android.lool",
        )
    }
}
