package app.henachti.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * All persistent state of the app. Plain SharedPreferences in app-private storage;
 * the API key never leaves the phone and is never part of the APK.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("henachti", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString(KEY_API, "") ?: ""
        set(value) = sp.edit().putString(KEY_API, value.trim()).apply()

    /** The lock is armed only once an API key exists; before that the phone is never blocked. */
    val setupDone: Boolean get() = apiKey.isNotBlank()

    var cityIndex: Int
        get() = sp.getInt(KEY_CITY, 0)
        set(value) = sp.edit().putInt(KEY_CITY, value).apply()

    /** Israeli default: no tefillin on Chol HaMoed. */
    var cholHamoedTefillin: Boolean
        get() = sp.getBoolean(KEY_CHOL_HAMOED, false)
        set(value) = sp.edit().putBoolean(KEY_CHOL_HAMOED, value).apply()

    /** Keep the phone locked after sunset if tefillin were not put on that day. */
    var strictAfterSunset: Boolean
        get() = sp.getBoolean(KEY_STRICT, true)
        set(value) = sp.edit().putBoolean(KEY_STRICT, value).apply()

    var extraAllowed: String
        get() = sp.getString(KEY_EXTRA, "") ?: ""
        set(value) = sp.edit().putString(KEY_EXTRA, value).apply()

    /** User additions to the allowlist. System packages that could disable the lock are never accepted. */
    fun extraAllowedSet(): Set<String> =
        extraAllowed.split('\n', ',', ' ', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in PROTECTED_PACKAGES }
            .toSet()

    /** When the user asked for an orderly removal (epoch millis), or 0. */
    var exitRequestedAt: Long
        get() = sp.getLong(KEY_EXIT_AT, 0L)
        set(value) = sp.edit().putLong(KEY_EXIT_AT, value).apply()

    /** Switches the lock off completely (used only once the exit cool-down has elapsed). */
    fun disarm() {
        sp.edit().putString(KEY_API, "").putLong(KEY_EXIT_AT, 0L).apply()
    }

    /** Set when the API rejected the key (401/402/403) so the key can be fixed even while locked. */
    var lastAuthError: Boolean
        get() = sp.getBoolean(KEY_AUTH_ERR, false)
        set(value) = sp.edit().putBoolean(KEY_AUTH_ERR, value).apply()

    var verifiedCount: Int
        get() = sp.getInt(KEY_COUNT, 0)
        set(value) = sp.edit().putInt(KEY_COUNT, value).apply()

    fun isVerified(day: LocalDate): Boolean =
        sp.getString(KEY_VERIFIED_DAY, null) == day.toString()

    fun markVerified(day: LocalDate) {
        sp.edit()
            .putString(KEY_VERIFIED_DAY, day.toString())
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .apply()
    }

    val verifiedAt: Long get() = sp.getLong(KEY_VERIFIED_AT, 0L)

    fun snoozeUsed(day: LocalDate): Boolean =
        sp.getString(KEY_SNOOZE_DAY, null) == day.toString()

    val snoozeUntil: Long get() = sp.getLong(KEY_SNOOZE_UNTIL, 0L)

    fun startSnooze(day: LocalDate, until: Long) {
        sp.edit()
            .putString(KEY_SNOOZE_DAY, day.toString())
            .putLong(KEY_SNOOZE_UNTIL, until)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }

        private const val KEY_API = "api_key"
        private const val KEY_CITY = "city_index"
        private const val KEY_CHOL_HAMOED = "chol_hamoed_tefillin"
        private const val KEY_STRICT = "strict_after_sunset"
        private const val KEY_EXTRA = "extra_allowed"
        private const val KEY_AUTH_ERR = "last_auth_error"
        private const val KEY_COUNT = "verified_count"
        private const val KEY_VERIFIED_DAY = "verified_day"
        private const val KEY_VERIFIED_AT = "verified_at"
        private const val KEY_SNOOZE_DAY = "snooze_day"
        private const val KEY_SNOOZE_UNTIL = "snooze_until"
        private const val KEY_EXIT_AT = "exit_requested_at"

        /** Packages that can switch the lock off; they are blocked while locked and cannot be allowlisted. */
        val PROTECTED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.settings.intelligence",
            "com.samsung.android.app.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
            "com.samsung.android.lool",
            "com.samsung.android.sm",
            "com.samsung.knox.securefolder",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.huawei.systemmanager",
        )
    }
}
