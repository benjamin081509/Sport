package de.vitalcoach.core

import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Regenerations-Modell (Schätzung).
 *
 * Erholungszeit je Training = 18 h × (TRIMP / Fitness-Niveau)^1,1, +12 h bei hohem Anteil (≥ 25 %) in Zone 4–5.
 * Krafttraining mindestens 30 h (Muskelreparatur), Gehen/Yoga höchstens 12 h.
 * Orientierung an typischen Erholungszeiten aus der Literatur: Glykogen-Resynthese ~24 h, Muskelschäden
 * nach intensiver/exzentrischer Belastung 48–72 h. Das Fitness-Niveau (CTL) berücksichtigt, dass trainierte
 * Menschen dieselbe Einheit schneller wegstecken.
 */
object Regeneration {

    fun recoveryHours(w: WorkoutAnalysis, ctlBefore: Double): Double {
        val r = w.trimp / max(ctlBefore, 25.0)
        var h = 18.0 * r.pow(1.1)
        val total = w.zoneMin.sum()
        if (total > 0 && (w.zoneMin[4] + w.zoneMin[5]) / total >= 0.25) h += 12.0
        when (w.workout.type) {
            SportType.STRENGTH -> if (w.durationMin >= 30) h = max(h, 30.0)
            SportType.WALKING, SportType.YOGA -> h = minOf(h, 12.0)
            else -> Unit
        }
        return h.clamp(2.0, 96.0)
    }

    /** Ausstehende Erholung zum Zeitpunkt [t]. */
    fun pending(infos: List<Pair<WorkoutAnalysis, Double>>, t: Instant): List<RecoveryTimeInfo> =
        infos.filter { (w, _) -> !w.workout.end.isAfter(t) && Duration.between(w.workout.end, t).toHours() < 120 }
            .map { (w, h) ->
                val elapsed = Duration.between(w.workout.end, t).seconds / 3600.0
                RecoveryTimeInfo(w, h, (h - elapsed).coerceAtLeast(0.0), w.workout.end.plusSeconds((h * 3600).toLong()))
            }

    /** Regenerationsstand 0–100 % (bezogen auf das Training mit der längsten Rest-Erholung). */
    fun progress(pending: List<RecoveryTimeInfo>): Int {
        val binding = pending.maxByOrNull { it.remainingH } ?: return 100
        if (binding.remainingH <= 0.0) return 100
        return (100.0 * (1.0 - binding.remainingH / binding.hours)).roundToInt().coerceIn(0, 100)
    }

    fun fatigueIndex(atl: Double, ctl: Double): Int = (50.0 * atl / max(ctl, 10.0)).clamp(0.0, 100.0).roundToInt()

    fun fatigueLabel(v: Int): Pair<String, Tone> = when {
        v < 35 -> "gering – du bist frisch" to Tone.GOOD
        v < 60 -> "ausgeglichen" to Tone.GOOD
        v < 75 -> "erhöht" to Tone.WARN
        else -> "hoch – Ermüdung hat sich angesammelt" to Tone.BAD
    }

    fun balanceLabel(b: Double): Pair<String, Tone> = when {
        b < 0.8 -> "Erholung überwiegt" to Tone.OK
        b <= 1.2 -> "ausgeglichen" to Tone.GOOD
        b <= 1.5 -> "mehr Belastung als Erholung" to Tone.WARN
        else -> "deutlich mehr Belastung als Erholung" to Tone.BAD
    }
}
