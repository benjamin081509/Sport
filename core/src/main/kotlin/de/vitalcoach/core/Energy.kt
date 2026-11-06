package de.vitalcoach.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EnergyPoint(val time: Instant, val value: Double)

/** Energie eines Tages: Wert beim Aufwachen und am Abend (bzw. jetzt). */
data class EnergyDay(val date: LocalDate, val morning: Int, val evening: Int)

data class EnergyReport(
    /** Aktueller Energiewert 0–100. */
    val now: Int,
    /** Wert beim Aufwachen (= Erholungswert des Morgens). */
    val morning: Int,
    val wakeTime: Instant,
    /** Tagesverlauf in 15-Minuten-Schritten seit dem Aufwachen. */
    val curve: List<EnergyPoint>,
    /** Verbrauch (positiv) durch Wachzeit, Stress in Ruhe, Alltagsbewegung und Training; Aufladung in Ruhephasen. */
    val drainAwake: Double,
    val drainStress: Double,
    val drainEveryday: Double,
    val drainTraining: Double,
    val charge: Double,
    val readinessMorning: Int?,
    /** Trainingsbereitschaft jetzt: Morgenwert minus Belastung durch Training, Stress und Alltag seit dem Aufwachen. */
    val readinessNow: Int?,
    /** Letzte Tage (inkl. heute) für den Verlauf. */
    val history: List<EnergyDay>,
    val label: String,
    val tone: Tone,
    /** true, wenn tagsüber Pulsdaten vorlagen (sonst nur Wachzeit und Trainings berücksichtigt). */
    val hasDayHeartRate: Boolean,
)

/**
 * Energie-Modell („wie voll ist dein Akku?“), angelehnt an das Prinzip von Garmin Body Battery / Firstbeat:
 * Morgens startet der Wert bei deiner Erholung (Schlaf, Ruhepuls, HRV, Rest-Erholung). Über den Tag
 * verbrauchen Wachzeit, Stress (erhöhter Puls in Ruhe), Alltagsbewegung und Training Energie; tief
 * entspannte Ruhephasen laden etwas auf. Eine Schätzung – keine medizinische Messung.
 *
 * Verbrauch: Wachzeit 1,2 Punkte/h · Stress bis 3,5 Punkte/h (proportional zum Stresswert) ·
 * Bewegung/Training 0,2 Punkte je TRIMP · Aufladen bis 2 Punkte/h bei Stresswert < 10.
 */
object Energy {
    const val AWAKE_PER_H = 1.2
    const val STRESS_MAX_PER_H = 3.5
    const val REST_CHARGE_PER_H = 2.0
    const val PER_TRIMP = 0.2
    /** Anteil der Belastung (ohne reine Wachzeit), um den die Trainingsbereitschaft über den Tag sinkt. */
    const val READINESS_FACTOR = 0.5
    private const val SLOT_SEC = 15L * 60

    data class Sim(
        val curve: List<EnergyPoint>,
        val awake: Double,
        val stress: Double,
        val everyday: Double,
        val training: Double,
        val charge: Double,
        val hadHeartRate: Boolean,
    ) {
        val end: Double get() = curve.last().value
        /** Belastung ohne reine Wachzeit – senkt die Trainingsbereitschaft. */
        val load: Double get() = max(0.0, training + everyday + stress - charge)
    }

    /**
     * Simuliert den Energieverlauf von [from] (Aufwachen) bis [to] (jetzt bzw. Schlafenszeit).
     * [hourlySteps] = Schritte je Stunde (Schlüssel: Epoch-Stunde) oder null, wenn für den Tag keine Stundenwerte vorliegen.
     */
    fun simulate(
        start: Double,
        from: Instant,
        to: Instant,
        samples: List<HeartRateSample>,
        workouts: List<WorkoutAnalysis>,
        hourlySteps: Map<Long, Long>?,
        rest: Double,
        maxHr: Int,
        sex: Sex?,
    ): Sim {
        val v0 = start.clamp(0.0, 100.0)
        if (!to.isAfter(from)) return Sim(listOf(EnergyPoint(from, v0)), 0.0, 0.0, 0.0, 0.0, 0.0, false)
        val n = ceil(Duration.between(from, to).seconds / SLOT_SEC.toDouble()).toInt().coerceAtLeast(1)
        val delta = DoubleArray(n)
        fun slotStart(i: Int): Instant = from.plusSeconds(i * SLOT_SEC)
        fun slotEnd(i: Int): Instant = minOf(from.plusSeconds((i + 1) * SLOT_SEC), to)
        fun slotOf(t: Instant): Int = ((t.epochSecond - from.epochSecond) / SLOT_SEC).toInt().coerceIn(0, n - 1)

        // 1) Grundverbrauch durch Wachzeit
        var awake = 0.0
        for (i in 0 until n) {
            val d = AWAKE_PER_H * Duration.between(slotStart(i), slotEnd(i)).seconds / 3600.0
            delta[i] -= d
            awake += d
        }

        // 2) Trainings: Verbrauch proportional zu den Trainingspunkten, verteilt über die Trainingszeit
        var training = 0.0
        for (w in workouts) {
            val ws = w.workout.start
            val we = w.workout.end
            val total = Duration.between(ws, we).seconds.toDouble()
            if (total <= 0) continue
            for (i in 0 until n) {
                val ov = overlapSec(slotStart(i), slotEnd(i), ws, we)
                if (ov > 0) {
                    val d = PER_TRIMP * w.trimp * ov / total
                    delta[i] -= d
                    training += d
                }
            }
        }

        // 3) Puls außerhalb von Trainings (inkl. 15 min danach): Stress in Ruhe, Alltagsbewegung, entspannte Ruhe
        val windows = workouts.map { it.workout.start to it.workout.end.plusSeconds(15 * 60) }
        val s = samples.asSequence()
            .filter { it.bpm in 30..230 && !it.time.isBefore(from) && it.time.isBefore(to) }
            .filter { x -> windows.none { (a, b) -> !x.time.isBefore(a) && !x.time.isAfter(b) } }
            .sortedBy { it.time }
            .toList()
        var stress = 0.0
        var everyday = 0.0
        var charge = 0.0
        for (k in s.indices) {
            val cur = s[k]
            val next = if (k + 1 < s.size) s[k + 1].time else to
            val dtH = min(Duration.between(cur.time, next).seconds / 3600.0, 10.0 / 60.0)
            if (dtH <= 0) continue
            val hrr = ((cur.bpm - rest) / (maxHr - rest).coerceAtLeast(20.0)).clamp(0.0, 1.0)
            val calm = if (hourlySteps != null) (hourlySteps[cur.time.epochSecond / 3600] ?: 0L) < 100L else hrr < 0.4
            val i = slotOf(cur.time)
            if (calm) {
                val sv = HeartExtras.stressValue(cur.bpm, rest, maxHr)
                val d = sv / 100.0 * STRESS_MAX_PER_H * dtH
                delta[i] -= d
                stress += d
                if (sv < 10) {
                    val c = REST_CHARGE_PER_H * (10 - sv) / 10.0 * dtH
                    delta[i] += c
                    charge += c
                }
            } else if (hrr >= 0.35) {
                val d = PER_TRIMP * dtH * 60.0 * HeartMath.trimpPerMinute(hrr, sex)
                delta[i] -= d
                everyday += d
            }
        }

        val curve = ArrayList<EnergyPoint>(n + 1)
        var v = v0
        curve += EnergyPoint(from, v)
        for (i in 0 until n) {
            v = (v + delta[i]).clamp(0.0, 100.0)
            curve += EnergyPoint(slotEnd(i), v)
        }
        return Sim(curve, awake, stress, everyday, training, charge, s.isNotEmpty())
    }

    fun label(v: Int): Pair<String, Tone> = when {
        v >= 70 -> "voll geladen" to Tone.GOOD
        v >= 50 -> "gut geladen" to Tone.OK
        v >= 30 -> "halb leer" to Tone.WARN
        else -> "fast leer" to Tone.BAD
    }

    fun readinessNow(morning: Int?, sim: Sim): Int? =
        morning?.let { (it - READINESS_FACTOR * sim.load).roundToInt().coerceIn(0, 100) }

    private fun overlapSec(a0: Instant, a1: Instant, b0: Instant, b1: Instant): Long {
        val s = maxOf(a0, b0)
        val e = minOf(a1, b1)
        return if (e.isAfter(s)) Duration.between(s, e).seconds else 0L
    }
}
