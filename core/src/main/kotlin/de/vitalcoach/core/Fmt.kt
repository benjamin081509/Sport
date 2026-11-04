package de.vitalcoach.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Deutsche Formatierung für Zahlen, Dauern und Uhrzeiten. */
object Fmt {
    private val DE = Locale.GERMANY
    private val DAY = DateTimeFormatter.ofPattern("EEE, d. MMM", DE)
    private val DAY_SHORT = DateTimeFormatter.ofPattern("d.M.", DE)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EE", DE)

    fun num(x: Double, decimals: Int = 0): String = String.format(DE, "%,.${decimals}f", x)

    fun int(x: Double): String = num(x, 0)

    fun int(x: Long): String = String.format(DE, "%,d", x)

    fun signed(x: Double, decimals: Int = 0): String {
        val s = num(abs(x), decimals)
        return when {
            x.roundTo(decimals) > 0 -> "+$s"
            x.roundTo(decimals) < 0 -> "−$s"
            else -> "±$s"
        }
    }

    private fun Double.roundTo(d: Int): Double {
        var f = 1.0
        repeat(d) { f *= 10 }
        return (this * f).roundToLong() / f
    }

    fun pct(fraction: Double, decimals: Int = 0): String = num(fraction * 100.0, decimals) + " %"

    fun duration(minutes: Double): String {
        val total = minutes.roundToInt().coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun hours(h: Double): String = num(h, if (h % 1.0 == 0.0) 0 else 1) + " h"

    /** Minuten ab 12:00 Uhr (720..2160) bzw. normale Tagesminuten → „23:15“. */
    fun clock(minutesFromNoonScale: Double): String {
        val m = (minutesFromNoonScale.roundToInt() % 1440 + 1440) % 1440
        return String.format(DE, "%02d:%02d", m / 60, m % 60)
    }

    fun day(d: LocalDate): String = DAY.format(d)

    fun dayShort(d: LocalDate): String = DAY_SHORT.format(d)

    fun weekday(d: LocalDate): String = WEEKDAY.format(d)

    fun pace(secPerKm: Double): String {
        val s = secPerKm.roundToInt()
        return String.format(DE, "%d:%02d /km", s / 60, s % 60)
    }
}
