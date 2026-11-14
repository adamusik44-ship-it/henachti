package app.henachti.service

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import app.henachti.BuildConfig

/** The Activity behind a Compose or wrapped context, if there is one. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Device-level checks the lock depends on, and the device-admin hooks that make it hard to remove. */
object Guard {

    /** The Google Play build. It ships without device admin and without the battery-exemption permission. */
    val IS_PLAY: Boolean = BuildConfig.FLAVOR == "play"

    /**
     * Uninstall protection through device admin exists only in the direct (sideloaded) build.
     * Google Play has removed apps that block their own uninstallation, so the Play build leaves it out.
     */
    val ADMIN_SUPPORTED: Boolean = !IS_PLAY

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean =
        ADMIN_SUPPORTED && runCatching { dpm(context).isAdminActive(adminComponent(context)) }.getOrDefault(false)

    /** Turns the screen off and brings up the keyguard. Used only when the lock screen cannot be shown. */
    fun lockNow(context: Context) {
        if (!isAdminActive(context)) return
        runCatching { dpm(context).lockNow() }
    }

    fun removeAdmin(context: Context) {
        runCatching { dpm(context).removeActiveAdmin(adminComponent(context)) }
    }

    fun addAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "מונע הסרה של וקשרתם בלחיצה ארוכה. הסרה מסודרת אפשרית מתוך האפליקציה, אחרי 48 שעות."
            )

    /**
     * Opens the system's "activate device admin" screen. That screen closes itself at once when it
     * is started with FLAG_ACTIVITY_NEW_TASK ("Cannot start ADD_DEVICE_ADMIN as a new task"), so it
     * has to be started from an Activity, with no extra flags, and never from a singleInstance one.
     */
    fun requestAdmin(activity: Activity) {
        runCatching { activity.startActivity(addAdminIntent(activity)) }
    }

    /** Automatic date and time from the network. Without it the day could simply be moved. */
    /** Debug builds (screenshots on an emulator whose clock is set by hand) skip this check; release never does. */
    fun autoTimeOn(context: Context): Boolean =
        BuildConfig.DEBUG || Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1) == 1

    fun adbEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

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

    /** Everything the lock needs before it may be armed. */
    fun readyToArm(context: Context): Boolean =
        hasUsageAccess(context) && canDrawOverlays(context) && (!ADMIN_SUPPORTED || isAdminActive(context))
}
