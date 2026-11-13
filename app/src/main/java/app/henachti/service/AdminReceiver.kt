package app.henachti.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device administrator with a single policy (force-lock). Its real job is that Android refuses
 * to uninstall an active admin app, and deactivating it lives inside Settings, which the lock
 * keeps closed. The lock also notices when it is deactivated and treats that as tampering.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "כיבוי ההגנה ייחשב לניסיון עקיפה והטלפון יינעל עד שתוחזר. " +
            "להסרה מסודרת: פתח את וקשרתם ובקש הסרה, היא נכנסת לתוקף אחרי 48 שעות."

    override fun onDisabled(context: Context, intent: Intent) {
        BlockerService.instance?.updateNotification()
    }
}
