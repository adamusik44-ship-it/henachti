package app.henachti.domain

import app.henachti.data.Prefs
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.GregorianCalendar
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

enum class UnlockReason { SETUP_INCOMPLETE, NO_TEFILLIN_DAY, VERIFIED, AFTER_SUNSET, SHABBAT_BEGAN }

/** The "halachic day" runs from misheyakir to the next misheyakir. */
sealed class LockState {
    abstract val day: LocalDate

    data class Unlocked(val reason: UnlockReason, override val day: LocalDate) : LockState()
    data class Locked(override val day: LocalDate, val snoozeAvailable: Boolean) : LockState()
    data class Snoozed(override val day: LocalDate, val until: Long) : LockState()
}

class DayPolicy(private val prefs: Prefs) {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val tz: TimeZone get() = TimeZone.getDefault()

    private fun geo(): GeoLocation {
        val c = Cities.all[prefs.cityIndex.coerceIn(0, Cities.all.lastIndex)]
        return GeoLocation(c.name, c.lat, c.lon, c.elevation, tz)
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
            calendar = GregorianCalendar(tz).apply {
                clear()
                set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            }
        }

    private fun fallback(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /** Earliest time for tefillin (misheyakir, 10.2 degrees below the horizon). */
    fun misheyakir(date: LocalDate): Long =
        zmanim(date).misheyakir10Point2Degrees?.time ?: fallback(date, LocalTime.of(5, 0))

    fun sunset(date: LocalDate): Long =
        zmanim(date).sunset?.time ?: fallback(date, LocalTime.of(18, 0))

    /** The tefillin-day that `now` belongs to: before today's misheyakir we are still in yesterday's night. */
    fun currentDay(now: Long = System.currentTimeMillis()): LocalDate {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (now < misheyakir(today)) today.minusDays(1) else today
    }

    fun state(now: Long = System.currentTimeMillis()): LockState {
        val d = currentDay(now)
        if (!prefs.setupDone) return LockState.Unlocked(UnlockReason.SETUP_INCOMPLETE, d)
        if (!isTefillinDay(d)) return LockState.Unlocked(UnlockReason.NO_TEFILLIN_DAY, d)
        if (prefs.isVerified(d)) return LockState.Unlocked(UnlockReason.VERIFIED, d)

        if (now >= sunset(d)) {
            if (!prefs.strictAfterSunset) return LockState.Unlocked(UnlockReason.AFTER_SUNSET, d)
            // Shabbat or Yom Tov has begun: there is nothing left to put on, release the phone.
            if (!isTefillinDay(d.plusDays(1))) return LockState.Unlocked(UnlockReason.SHABBAT_BEGAN, d)
        }

        if (prefs.snoozeUsed(d) && now < prefs.snoozeUntil) {
            return LockState.Snoozed(d, prefs.snoozeUntil)
        }
        return LockState.Locked(d, snoozeAvailable = !prefs.snoozeUsed(d))
    }
}
