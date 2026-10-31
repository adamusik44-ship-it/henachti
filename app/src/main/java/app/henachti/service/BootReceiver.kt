package app.henachti.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the blocker back after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            BlockerService.ensureRunning(context)
        }
    }
}
