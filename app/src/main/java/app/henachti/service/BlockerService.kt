package app.henachti.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.henachti.R
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.domain.LockState
import app.henachti.ui.LockActivity
import app.henachti.ui.MainActivity

/**
 * Foreground service that watches which app is in front (UsageStats events, polled while the
 * screen is on) and puts the lock screen on top of any non-allowed app while the day is locked.
 *
 * No accessibility service: Play Protect blocks sideloaded apps that declare one.
 * Needs "usage access" (to see the foreground app) and "display over other apps"
 * (so the lock screen may be started from the background).
 */
class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private lateinit var usage: UsageStatsManager
    private val allowed = HashSet<String>()

    private var polling = false
    private var lastQueryEnd = 0L
    private var lastEventTs = 0L
    private var lastEvalAt = 0L
    private var lastLaunchAt = 0L
    private var currentPkg: String? = null

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            tick()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val recheck = Runnable { currentPkg?.let { evaluate(it, force = true) } }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopPolling()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startPolling()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        policy = DayPolicy(prefs)
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createChannel()
        startAsForeground()
        refreshAllowlist()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        instance = this
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!polling) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) startPolling()
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        stopPolling()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        lastQueryEnd = System.currentTimeMillis() - 3_000L
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(poll)
    }

    /** One poll: find the most recent foreground event and react to it. */
    private fun tick() {
        if (!hasUsageAccess(this)) return
        val now = System.currentTimeMillis()
        val begin = (lastQueryEnd - 1_500L).coerceAtLeast(0L)
        lastQueryEnd = now

        var latestPkg: String? = null
        var latestTs = lastEventTs
        val events = usage.queryEvents(begin, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != EVENT_FOREGROUND) continue
            if (event.timeStamp <= lastEventTs) continue
            if (event.timeStamp >= latestTs) {
                latestTs = event.timeStamp
                latestPkg = event.packageName
            }
        }

        if (latestPkg != null) {
            lastEventTs = latestTs
            if (latestPkg != currentPkg) {
                currentPkg = latestPkg
                evaluate(latestPkg, force = true)
                return
            }
        }
        // Even with no new event, re-check every few seconds: the snooze may have ended,
        // or misheyakir may have passed while the same app stayed open.
        if (now - lastEvalAt > REEVAL_MS) currentPkg?.let { evaluate(it, force = false) }
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
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.forEach { set += it.packageName }
        }

        set += prefs.extraAllowedSet()
        synchronized(allowed) {
            allowed.clear()
            allowed += set
        }
    }

    /** Called by the lock screen when the once-a-day snooze starts, so the lock returns on time. */
    fun onSnoozeStarted(until: Long) {
        scheduleRecheck(until)
        updateNotification()
    }

    private fun evaluate(pkg: String, force: Boolean) {
        lastEvalAt = System.currentTimeMillis()
        if (synchronized(allowed) { pkg in allowed }) return
        when (val state = policy.state()) {
            is LockState.Locked -> showLock(force)
            is LockState.Snoozed -> {
                if (pkg in RESTRICTED_DURING_SNOOZE) showLock(force)
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

    private fun showLock(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastLaunchAt < 2_000L) return
        if (now - lastLaunchAt < 700L) return
        lastLaunchAt = now
        val intent = Intent(this, LockActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        runCatching { startActivity(intent) }
    }

    // ---- foreground notification ----

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (val st = policy.state()) {
            is LockState.Locked -> getString(R.string.notif_locked)
            is LockState.Snoozed -> getString(R.string.notif_snoozed, clock(st.until))
            is LockState.Unlocked -> getString(R.string.notif_unlocked)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun clock(millis: Long): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(millis))

    companion object {
        const val SNOOZE_MS = 30L * 60L * 1000L
        private const val POLL_MS = 500L
        private const val REEVAL_MS = 5_000L
        private const val EVENT_FOREGROUND = 1 // UsageEvents.Event.MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
        private const val CHANNEL_ID = "henachti_service"
        private const val NOTIFICATION_ID = 7

        @Volatile
        var instance: BlockerService? = null
            private set

        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Starts (or pokes) the service when the lock is armed and the two special permissions exist. */
        fun ensureRunning(context: Context) {
            val prefs = Prefs.get(context)
            if (!prefs.setupDone || !hasUsageAccess(context) || !canDrawOverlays(context)) return
            val intent = Intent(context, BlockerService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

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
