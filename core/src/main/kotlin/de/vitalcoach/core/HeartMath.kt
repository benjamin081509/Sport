package de.vitalcoach.core

import java.time.Duration
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Herzfrequenz-Berechnungen. Alle Formeln stammen aus der sportwissenschaftlichen Literatur:
 *  - HFmax: Tanaka, Monahan & Seals (2001), J Am Coll Cardiol
 *  - TRIMP: Banister (1991), Gewichtungsfaktoren nach Morton, Fitz-Clarke & Banister (1990)
 *  - Zonen: Karvonen-Methode (Herzfrequenzreserve)
 *  - Intensitätsminuten: ACSM (moderat 40–59 % HFR, intensiv ≥ 60 % HFR), WHO 2020
 */
object HeartMath {

    fun tanakaMaxHr(age: Int): Int = (208.0 - 0.7 * age).roundToInt()

    /**
     * Ruhepuls = niedrigster gleitender Mittelwert über [windowMin] Minuten (mind. 3 Messwerte,
     * Fenster mindestens halb gefüllt). Fallback: Mittel der drei niedrigsten Werte.
     */
    fun restingFromSamples(samples: List<HeartRateSample>, windowMin: Long = 30): Double? {
        val s = samples.filter { it.bpm in 30..200 }.sortedBy { it.time }
        if (s.size < 3) return null
        val window = Duration.ofMinutes(windowMin)
        val minSpanSec = window.seconds / 2
        var best = Double.MAX_VALUE
        var j = 0
        var sum = 0.0
        for (i in s.indices) {
            if (j < i) {
                j = i
                sum = 0.0
            }
            val limit = s[i].time.plus(window)
            while (j < s.size && !s[j].time.isAfter(limit)) {
                sum += s[j].bpm
                j++
            }
            val count = j - i
            val spanSec = Duration.between(s[i].time, s[j - 1].time).seconds
            if (count >= 3 && spanSec >= minSpanSec) best = min(best, sum / count)
            sum -= s[i].bpm
        }
        if (best != Double.MAX_VALUE) return best
        return s.map { it.bpm.toDouble() }.sorted().take(3).average()
    }

    /** Höchster Wert eines gleitenden 3er-Medians – filtert einzelne Messfehler-Spitzen heraus. */
    fun robustMax(samples: List<HeartRateSample>): Int? {
        val s = samples.filter { it.bpm in 40..230 }.sortedBy { it.time }
        if (s.size < 3) return null
        var best = 0
        for (i in 1 until s.size - 1) {
            val m = listOf(s[i - 1].bpm, s[i].bpm, s[i + 1].bpm).sorted()[1]
            best = max(best, m)
        }
        return best
    }

    /** Banister-TRIMP pro Minute bei gegebener Herzfrequenzreserve (0..1). */
    fun trimpPerMinute(hrr: Double, sex: Sex?): Double {
        val x = hrr.clamp(0.0, 1.0)
        val male = 0.64 * exp(1.92 * x)
        val female = 0.86 * exp(1.67 * x)
        val y = when (sex) {
            Sex.MALE -> male
            Sex.FEMALE -> female
            null -> (male + female) / 2.0
        }
        return x * y
    }

    fun zones(rest: Double, max: Int): List<HrZone> {
        fun bpm(p: Double) = (rest + p * (max - rest)).roundToInt()
        return listOf(
            HrZone(1, "Regeneration", bpm(0.50), bpm(0.60) - 1, "Sehr locker – aktive Erholung, Aufwärmen"),
            HrZone(2, "Grundlage", bpm(0.60), bpm(0.70) - 1, "Locker – Fettstoffwechsel, Ausdauerbasis (hier sollte der Großteil liegen)"),
            HrZone(3, "Tempo", bpm(0.70), bpm(0.80) - 1, "Mittel – aerobe Kapazität, „Wohlfühltempo-Plus“"),
            HrZone(4, "Schwelle", bpm(0.80), bpm(0.90) - 1, "Hart – Laktatschwelle, Tempodauerläufe"),
            HrZone(5, "Maximal", bpm(0.90), max, "Sehr hart – VO2max-Intervalle, kurze Belastungen"),
        )
    }

    /** Zonen-Index 0..5 aus der Herzfrequenzreserve. */
    fun zoneIndex(hrr: Double): Int = when {
        hrr < 0.5 -> 0
        hrr < 0.6 -> 1
        hrr < 0.7 -> 2
        hrr < 0.8 -> 3
        hrr < 0.9 -> 4
        else -> 5
    }

    /**
     * VO2max aus einem Lauf: ACSM-Laufgleichung für ebenes Gelände
     * VO2 = 3,5 + 0,2 × v (m/min), hochgerechnet über %HFR ≈ %VO2R.
     */
    fun vo2FromRun(distanceM: Double, durationMin: Double, avgHr: Double, rest: Double, max: Int): Double? {
        if (durationMin < 15 || distanceM <= 0) return null
        val speed = distanceM / durationMin
        val kmh = speed * 0.06
        if (kmh < 6.0 || kmh > 22.0) return null
        val hrr = (avgHr - rest) / (max - rest)
        if (hrr < 0.55 || hrr > 0.95) return null
        val vo2 = 3.5 + 0.2 * speed
        return 3.5 + (vo2 - 3.5) / hrr
    }

    /** Uth-Sørensen-Overgaard-Pedersen (2004). */
    fun vo2FromRestingHr(maxHr: Int, rest: Double): Double = 15.3 * maxHr / rest

    fun analyzeWorkout(
        w: Workout,
        samples: List<HeartRateSample>,
        rest: Double,
        maxHr: Int,
        sex: Sex?,
    ): WorkoutAnalysis {
        val dur = max(w.durationMin, 0.0)
        val s = samples.filter { it.bpm in 30..230 }.sortedBy { it.time }
        val range = (maxHr - rest).coerceAtLeast(20.0)
        var covered = 0.0
        var trimp = 0.0
        var weighted = 0.0
        val zone = DoubleArray(6)
        var moderate = 0.0
        var vigorous = 0.0
        for (k in 0 until s.size - 1) {
            val dt = min(Duration.between(s[k].time, s[k + 1].time).seconds / 60.0, 5.0)
            if (dt <= 0.0) continue
            val hr = (s[k].bpm + s[k + 1].bpm) / 2.0
            val hrr = ((hr - rest) / range).clamp(0.0, 1.0)
            trimp += dt * trimpPerMinute(hrr, sex)
            covered += dt
            weighted += hr * dt
            zone[zoneIndex(hrr)] += dt
            if (hrr >= 0.6) vigorous += dt else if (hrr >= 0.4) moderate += dt
        }
        val coverage = if (dur > 0) covered / dur else 0.0
        val hasHr = s.size >= 2 && coverage >= 0.2
        val avgHr: Double?
        val maxObserved: Int?
        val estimated: Boolean
        if (hasHr) {
            val scale = if (coverage < 0.95) (1.0 / coverage).coerceAtMost(5.0) else 1.0
            trimp *= scale
            for (i in zone.indices) zone[i] *= scale
            moderate *= scale
            vigorous *= scale
            avgHr = weighted / covered
            maxObserved = robustMax(s) ?: s.maxOf { it.bpm }
            estimated = false
        } else {
            val hrr = w.type.typicalHrr
            trimp = dur * trimpPerMinute(hrr, sex)
            for (i in zone.indices) zone[i] = 0.0
            zone[zoneIndex(hrr)] = dur
            moderate = if (hrr in 0.4..0.599) dur else 0.0
            vigorous = if (hrr >= 0.6) dur else 0.0
            avgHr = null
            maxObserved = null
            estimated = true
        }
        val pace = w.distanceMeters?.takeIf { it > 200 && dur > 0 }?.let { dur * 60.0 / (it / 1000.0) }
        val vo2 = if (w.type == SportType.RUNNING && avgHr != null && w.distanceMeters != null) {
            vo2FromRun(w.distanceMeters, dur, avgHr, rest, maxHr)
        } else {
            null
        }
        return WorkoutAnalysis(
            workout = w,
            durationMin = dur,
            avgHr = avgHr,
            maxHr = maxObserved,
            trimp = trimp,
            estimated = estimated,
            zoneMin = zone.toList(),
            moderateMin = moderate,
            vigorousMin = vigorous,
            vo2Estimate = vo2,
            paceSecPerKm = pace,
            focus = focusOf(w.type, zone, dur),
        )
    }

    private fun focusOf(type: SportType, zone: DoubleArray, dur: Double): String {
        if (type == SportType.STRENGTH) return "Kraft"
        if (type == SportType.YOGA) return "Mobilität & Entspannung"
        val total = zone.sum().takeIf { it > 0 } ?: return "Training"
        val easy = (zone[0] + zone[1] + zone[2]) / total
        val hard = (zone[4] + zone[5]) / total
        return when {
            hard >= 0.25 -> "Schwelle / Intervall (hart)"
            zone[3] / total >= 0.35 -> "Tempo (mittel)"
            zone[0] / total >= 0.7 && dur < 45 -> "Regeneration (sehr locker)"
            easy >= 0.7 -> "Grundlage (locker)"
            else -> "Gemischt"
        }
    }
}
