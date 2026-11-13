package app.henachti.domain

import android.content.Context
import app.henachti.data.Prefs
import app.henachti.service.Guard
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

data class City(val name: String, val lat: Double, val lon: Double, val elevation: Double)

object Cities {
    val all = listOf(
        City("ירושלים", 31.7683, 35.2137, 750.0),
        City("תל אביב", 32.0853, 34.7818, 5.0),
        City("חיפה", 32.7940, 34.9896, 30.0),
        City("באר שבע", 31.2530, 34.7915, 260.0),
        City("נתניה", 32.3215, 34.8532, 30.0),
        City("אשדוד", 31.8044, 34.6553, 20.0),
        City("פתח תקווה", 32.0840, 34.8878, 40.0),
        City("אילת", 29.5577, 34.9519, 10.0),
    )
}

enum class UnlockReason { SETUP_INCOMPLETE, NO_TEFILLIN_DAY, VERIFIED, BEFORE_START, AFTER_SUNSET, SHABBAT_BEGAN }

/** Something the lock relies on was switched off while armed. Everything stays closed until fixed. */
enum class TamperReason { MANUAL_TIME, NO_USAGE_ACCESS, NO_OVERLAY, NO_ADMIN }

/** The "halachic day" runs from misheyakir to the next misheyakir, always in Israel time. */
sealed class LockState {
    abstract val day: LocalDate

    data class Unlocked(val reason: UnlockReason, override val day: LocalDate) : LockState()

    data class Locked(override val day: LocalDate, val snoozesLeft: Int, val snoozesPerDay: Int) : LockState() {
        val snoozeAvailable: Boolean get() = snoozesLeft > 0
    }

    data class Snoozed(override val day: LocalDate, val until: Long, val used: Int, val max: Int) : LockState()

    data class Tampered(val reason: TamperReason, override val day: LocalDate) : LockState()
}

/** All times are computed in Asia/Jerusalem regardless of the device zone, so a zone change moves nothing. */
object IsraelTime {
    val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    val tz: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")

    fun hhmm(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }.format(Date(millis))

    fun dateTime(millis: Long): String =
        SimpleDateFormat("d.M, HH:mm", Locale.US).apply { timeZone = tz }.format(Date(millis))
}

class DayPolicy(private val context: Context) {

    private val prefs: Prefs = Prefs.get(context)

    private fun geo(): GeoLocation {
        val c = Cities.all[prefs.cityIndex.coerceIn(0, Cities.all.lastIndex)]
        return GeoLocation(c.name, c.lat, c.lon, c.elevation, IsraelTime.tz)
    }

    fun jewish(date: LocalDate): JewishCalendar = JewishCalendar().apply {
        inIsrael = true
        setGregorianDate(date.year, date.monthValue - 1, date.dayOfMonth)
    }

    fun hebrewDate(date: LocalDate): String =
        HebrewDateFormatter().apply { isHebrewFormat = true }.format(jewish(date))

    /** Null when tefillin are worn that day; otherwise a short Hebrew reason. */
    fun noTefillinReason(date: LocalDate): String? {
        val jc = jewish(date)
        return when {
            jc.isAssurBemelacha -> if (jc.dayOfWeek == 7) "שבת" else "יום טוב"
            jc.isCholHamoed && !prefs.cholHamoedTefillin -> "חול המועד"
            jc.yomTovIndex == JewishCalendar.TISHA_BEAV -> "תשעה באב"
            else -> null
        }
    }

    fun isTefillinDay(date: LocalDate): Boolean = noTefillinReason(date) == null

    private fun zmanim(date: LocalDate): ComplexZmanimCalendar =
        ComplexZmanimCalendar(geo()).apply {
            calendar = GregorianCalendar(IsraelTime.tz).apply {
                clear()
                set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            }
        }

    private fun fallback(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(IsraelTime.zone).toInstant().toEpochMilli()

    /** Earliest time for tefillin (misheyakir, 10.2 degrees below the horizon). */
    fun misheyakir(date: LocalDate): Long =
        zmanim(date).misheyakir10Point2Degrees?.time ?: fallback(date, LocalTime.of(5, 0))

    fun sunset(date: LocalDate): Long =
        zmanim(date).sunset?.time ?: fallback(date, LocalTime.of(18, 0))

    /** When the lock arms that morning: misheyakir, or the user's chosen time if it is later. */
    fun lockStart(date: LocalDate): Long {
        val earliest = misheyakir(date)
        val chosen = prefs.lockStartMinutes
        if (chosen < 0) return earliest
        val at = fallback(date, LocalTime.of(chosen / 60, chosen % 60))
        return maxOf(earliest, at)
    }

    /** The tefillin-day that `now` belongs to: before today's misheyakir we are still in yesterday's night. */
    fun currentDay(now: Long = System.currentTimeMillis()): LocalDate {
        val today = Instant.ofEpochMilli(now).atZone(IsraelTime.zone).toLocalDate()
        return if (now < misheyakir(today)) today.minusDays(1) else today
    }

    /** Consecutive verified tefillin-days ending today (or yesterday if today is still open). Shabbat and holidays do not break it. */
    fun streak(now: Long = System.currentTimeMillis()): Int {
        val days = prefs.verifiedDays()
        var d = currentDay(now)
        if (isTefillinDay(d) && d.toString() !in days) d = d.minusDays(1)
        var count = 0
        var guard = 0
        while (guard++ < 400) {
            if (!isTefillinDay(d)) {
                d = d.minusDays(1)
                continue
            }
            if (d.toString() in days) {
                count++
                d = d.minusDays(1)
            } else break
        }
        return count
    }

    /** Time-of-day greeting in Israel time. */
    fun greeting(now: Long = System.currentTimeMillis()): String {
        val hour = Instant.ofEpochMilli(now).atZone(IsraelTime.zone).hour
        return when (hour) {
            in 5..11 -> "בוקר אור"
            in 12..16 -> "צהריים טובים"
            in 17..21 -> "ערב טוב"
            else -> "לילה טוב"
        }
    }

    fun state(now: Long = System.currentTimeMillis()): LockState {
        val d = currentDay(now)
        if (!prefs.setupDone) return LockState.Unlocked(UnlockReason.SETUP_INCOMPLETE, d)

        // Orderly exit: a removal request becomes effective only after the cool-down.
        val exitAt = prefs.exitRequestedAt
        if (exitAt > 0L && now >= exitAt + EXIT_COOLDOWN_MS) {
            prefs.disarm()
            Guard.removeAdmin(context)
            return LockState.Unlocked(UnlockReason.SETUP_INCOMPLETE, d)
        }

        // Anything the lock depends on that was switched off counts as tampering.
        if (!Guard.autoTimeOn(context)) return LockState.Tampered(TamperReason.MANUAL_TIME, d)
        if (!Guard.hasUsageAccess(context)) return LockState.Tampered(TamperReason.NO_USAGE_ACCESS, d)
        if (!Guard.canDrawOverlays(context)) return LockState.Tampered(TamperReason.NO_OVERLAY, d)
        if (Guard.ADMIN_SUPPORTED && !Guard.isAdminActive(context)) return LockState.Tampered(TamperReason.NO_ADMIN, d)

        if (!isTefillinDay(d)) return LockState.Unlocked(UnlockReason.NO_TEFILLIN_DAY, d)
        if (prefs.isVerified(d)) return LockState.Unlocked(UnlockReason.VERIFIED, d)
        // The user may arm the lock later than misheyakir; verifying earlier still counts for the day.
        if (now < lockStart(d)) return LockState.Unlocked(UnlockReason.BEFORE_START, d)

        if (now >= sunset(d)) {
            if (!prefs.strictAfterSunset) return LockState.Unlocked(UnlockReason.AFTER_SUNSET, d)
            // Shabbat or Yom Tov has begun: there is nothing left to put on, release the phone.
            if (!isTefillinDay(d.plusDays(1))) return LockState.Unlocked(UnlockReason.SHABBAT_BEGAN, d)
        }

        val max = prefs.snoozesPerDay
        val used = prefs.snoozesUsed(d)
        if (used > 0 && now < prefs.snoozeUntil) {
            return LockState.Snoozed(d, prefs.snoozeUntil, used, max)
        }
        return LockState.Locked(d, snoozesLeft = (max - used).coerceAtLeast(0), snoozesPerDay = max)
    }

    companion object {
        const val EXIT_COOLDOWN_MS = 48L * 60L * 60L * 1000L
    }
}
