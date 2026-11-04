package de.vitalcoach.core

import java.time.Instant
import kotlin.math.sqrt

/** Persönlicher Referenzwert (Mittelwert, Standardabweichung, Anzahl Tage). */
data class Baseline(val mean: Double, val sd: Double, val n: Int)

internal object Stats {
    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()

    fun sd(values: List<Double>): Double? {
        if (values.size < 2) return null
        val m = values.average()
        return sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
    }

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 0) (s[mid - 1] + s[mid]) / 2.0 else s[mid]
    }

    fun baseline(values: List<Double>, minN: Int = 4): Baseline? {
        if (values.size < minN) return null
        return Baseline(values.average(), sd(values) ?: 0.0, values.size)
    }

    /** Steigung einer linearen Regression (y pro x-Einheit). */
    fun slope(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size < 3 || xs.size != ys.size) return null
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) null else num / den
    }
}

internal fun Double.clamp(lo: Double, hi: Double): Double = if (this < lo) lo else if (this > hi) hi else this

/** Binäre Suche: alle Elemente mit Zeitstempel in [from, to] aus einer nach Zeit sortierten Liste. */
internal fun <T> List<T>.sliceByTime(from: Instant, to: Instant, time: (T) -> Instant): List<T> {
    if (isEmpty() || to.isBefore(from)) return emptyList()
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (time(this[mid]).isBefore(from)) lo = mid + 1 else hi = mid
    }
    val startIdx = lo
    lo = startIdx
    hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (!time(this[mid]).isAfter(to)) lo = mid + 1 else hi = mid
    }
    return if (startIdx >= lo) emptyList() else subList(startIdx, lo)
}
