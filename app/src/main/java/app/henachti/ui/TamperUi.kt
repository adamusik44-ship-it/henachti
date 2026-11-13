package app.henachti.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.henachti.domain.TamperReason
import app.henachti.service.BlockerService
import app.henachti.service.Guard

/** Texts and fix-actions for the tamper states, shared by the lock screen and the main screen. */
object TamperUi {

    fun title(reason: TamperReason): String = when (reason) {
        TamperReason.MANUAL_TIME -> "השעון מוגדר ידנית."
        TamperReason.NO_USAGE_ACCESS -> "הוסרה הרשאת נתוני השימוש."
        TamperReason.NO_OVERLAY -> "הוסרה ההרשאה להצגה מעל אפליקציות."
        TamperReason.NO_ADMIN -> "ההגנה מפני הסרה כובתה."
    }

    fun body(reason: TamperReason): String = when (reason) {
        TamperReason.MANUAL_TIME ->
            "וקשרתם עובדת רק עם תאריך ושעה אוטומטיים, כדי שאי אפשר להזיז את היום. " +
                "הפעל אותם וחזור. עד אז הכול סגור."
        TamperReason.NO_USAGE_ACCESS ->
            "בלי ההרשאה הזאת האפליקציה לא רואה מה פתוח, אז היא נועלת הכול. החזר אותה וחזור."
        TamperReason.NO_OVERLAY ->
            "בלי ההרשאה הזאת מסך הנעילה לא יכול לעלות, אז המסך יכבה בכל פתיחה של אפליקציה. " +
                "החזר אותה וחזור."
        TamperReason.NO_ADMIN ->
            "ההגנה מונעת מחיקה של האפליקציה. הפעל אותה מחדש וחזור. " +
                "להסרה מסודרת יש כפתור באפליקציה, עם המתנה של 48 שעות."
    }

    fun action(reason: TamperReason): String = when (reason) {
        TamperReason.MANUAL_TIME -> "פתח הגדרות תאריך ושעה"
        TamperReason.NO_USAGE_ACCESS -> "פתח גישה לנתוני שימוש"
        TamperReason.NO_OVERLAY -> "פתח הצגה מעל אפליקציות"
        TamperReason.NO_ADMIN -> "הפעל הגנה מפני הסרה"
    }

    fun intent(context: Context, reason: TamperReason): Intent = when (reason) {
        TamperReason.MANUAL_TIME -> Intent(Settings.ACTION_DATE_SETTINGS)
        TamperReason.NO_USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        TamperReason.NO_OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        TamperReason.NO_ADMIN -> Guard.addAdminIntent(context)
    }

    /** Fragments of Settings activity class names that may stay open while fixing that problem. */
    fun fixScreenHints(reason: TamperReason): List<String> = when (reason) {
        TamperReason.MANUAL_TIME -> listOf("DateTime", "Date")
        TamperReason.NO_USAGE_ACCESS -> listOf("UsageAccess")
        TamperReason.NO_OVERLAY -> listOf("DrawOverlay", "Overlay")
        TamperReason.NO_ADMIN -> listOf("DeviceAdmin")
    }

    /** Opens the right Settings screen and gives the user a short grace window to use it. */
    fun launchFix(context: Context, reason: TamperReason) {
        BlockerService.startFixGrace()
        runCatching {
            context.startActivity(intent(context, reason).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
