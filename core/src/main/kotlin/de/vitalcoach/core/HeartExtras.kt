package de.vitalcoach.core

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Zusätzliche Herzfrequenz-Auswertungen:
 *  - Nächtliche Pulsabsenkung („Dipping“): (Puls wach − Puls im Schlaf) / Puls wach.
 *    Eine Absenkung < 10 % gilt als abgeschwächt (Ben-Dov et al. 2007, Arch Intern Med).
 *  - Herzfrequenzerholung (HRR60): Pulsabfall 60 s nach einer Belastungsspitze bzw. nach Trainingsende
 *    (Cole et al. 1999, NEJM: ≤ 12 Schläge nach Belastungstest gilt als auffällig).
 *  - Laktatschwellen-Puls: 95 % des höchsten 20-Minuten-Durchschnitts (Friel-Methode).
 *  - Stress-Index (Schätzung): Pulserhöhung über dem Ruhepuls in Phasen ohne Bewegung.
 */
object HeartExtras {

    /** Niedrigster gleitender Mittelwert und Zeitpunkt (Mitte des Fensters). */
    fun lowestWindow(samples: List<HeartRateSample>, windowMin: Long = 30): Pair<Double, Instant>? {
        val s = samples.filter { it.bpm in 30..200 }.sortedBy { it.time }
        if (s.size < 3) return null
        val window = Duration.ofMinutes(windowMin)
        var best = Double.MAX_VALUE
        var bestTime: Instant? = null
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
            val span = Duration.between(s[i].time, s[j - 1].time).seconds
            if (count >= 3 && span >= window.seconds / 2 && sum / count < best) {
                best = sum / count
                bestTime = s[i].time.plusSeconds(span / 2)
            }
            sum -= s[i].bpm
        }
        return bestTime?.let { best to it }
    }

    /** Zeitgewichteter Mittelwert: erst Mittel je [bucketMin]-Minuten-Fenster, dann Mittel der Fenster. */
    fun timeWeightedMean(samples: List<HeartRateSample>, bucketMin: Long = 10, minBuckets: Int = 6): Double? {
        if (samples.isEmpty()) return null
        val buckets = samples.filter { it.bpm in 30..230 }
            .groupBy { it.time.epochSecond / (bucketMin * 60) }
            .map { (_, v) -> v.map { it.bpm.toDouble() }.average() }
        return if (buckets.size >= minBuckets) buckets.average() else null
    }

    /** Mittelwerte je [bucketMin] Minuten für Diagramme. */
    fun curve(samples: List<HeartRateSample>, bucketMin: Long = 5): List<Pair<Instant, Double>> =
        samples.filter { it.bpm in 30..230 }
            .groupBy { it.time.epochSecond / (bucketMin * 60) }
            .toSortedMap()
            .map { (k, v) -> Instant.ofEpochSecond(k * bucketMin * 60) to v.map { it.bpm.toDouble() }.average() }

    /** Stresswert 0–100 für einen Pulswert in Ruhe (ohne Bewegung). */
    fun stressValue(bpm: Int, rest: Double, maxHr: Int): Double {
        val e = (bpm - rest) / (maxHr - rest).coerceAtLeast(20.0)
        return ((e - 0.05) / 0.25).clamp(0.0, 1.0) * 100.0
    }

    /** Pulsabfall nach Belastung. Nutzt Werte nach Trainingsende, sonst die stärkste Spitze im Training. */
    fun heartRateRecovery(
        samplesWithAfter: List<HeartRateSample>,
        workoutEnd: Instant,
        rest: Double,
        maxHr: Int,
    ): Triple<Int, Int, Boolean>? {
        val s = samplesWithAfter.filter { it.bpm in 40..230 }.sortedBy { it.time }
        if (s.size < 20) return null
        // Dichte prüfen: Median-Abstand ≤ 10 s
        val gaps = s.zipWithNext { a, b -> Duration.between(a.time, b.time).seconds }.sorted()
        if (gaps.isEmpty() || gaps[gaps.size / 2] > 10) return null
        val threshold = rest + 0.75 * (maxHr - rest)
        val endThreshold = rest + 0.6 * (maxHr - rest)
        val times = LongArray(s.size) { s[it].time.epochSecond }

        fun valueNear(t: Instant, tolSec: Long = 10): Int? {
            val target = t.epochSecond
            var lo = 0
            var hi = times.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (times[mid] < target) lo = mid + 1 else hi = mid
            }
            val cands = listOf(lo - 1, lo).filter { it in times.indices }
            val c = cands.minByOrNull { abs(times[it] - target) } ?: return null
            return if (abs(times[c] - target) <= tolSec) s[c].bpm else null
        }

        // 1) Nach Trainingsende (Spitze in den letzten 2 Minuten vor Ende)
        val endSec = workoutEnd.epochSecond
        val endPeak = s.indices.filter { times[it] in (endSec - 120)..endSec }.maxOfOrNull { s[it].bpm }
        val after60 = valueNear(workoutEnd.plusSeconds(60))
        if (endPeak != null && after60 != null && endPeak >= endThreshold) {
            return Triple(endPeak, (endPeak - after60).coerceAtLeast(0), true)
        }
        // 2) Größte Spitze im Training, auf die eine Erholungsphase folgt
        var best: Pair<Int, Int>? = null
        for (i in s.indices) {
            if (times[i] > endSec) break
            val p = s[i]
            if (p.bpm < threshold) continue
            var isPeak = true
            var k = i - 1
            while (k >= 0 && times[i] - times[k] <= 30) {
                if (s[k].bpm > p.bpm) { isPeak = false; break }
                k--
            }
            k = i + 1
            while (isPeak && k < s.size && times[k] - times[i] <= 30) {
                if (s[k].bpm > p.bpm) { isPeak = false; break }
                k++
            }
            if (!isPeak) continue
            val later = valueNear(p.time.plusSeconds(60)) ?: continue
            val drop = p.bpm - later
            if (drop >= 8 && (best == null || p.bpm > best.first)) best = p.bpm to drop
        }
        return best?.let { Triple(it.first, it.second, false) }
    }

    /** Höchster gleitender 20-Minuten-Durchschnitt (nur bei dichter Aufzeichnung). */
    fun best20MinAvg(samples: List<HeartRateSample>): Double? {
        val s = samples.filter { it.bpm in 40..230 }.sortedBy { it.time }
        if (s.size < 60) return null
        val window = Duration.ofMinutes(20)
        var best: Double? = null
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
            val span = Duration.between(s[i].time, s[j - 1].time).seconds
            val count = j - i
            if (span >= window.seconds - 30 && count >= 40) {
                val avg = sum / count
                if (best == null || avg > best) best = avg
            }
            sum -= s[i].bpm
        }
        return best
    }

    /** Alltagsbelastung: TRIMP aus Pulswerten ≥ 35 % HFR außerhalb von Trainings. */
    fun everydayTrimp(samples: List<HeartRateSample>, rest: Double, maxHr: Int, sex: Sex?): Double {
        val s = samples.filter { it.bpm in 30..230 }.sortedBy { it.time }
        var total = 0.0
        for (k in 0 until s.size - 1) {
            val dt = min(Duration.between(s[k].time, s[k + 1].time).seconds / 60.0, 10.0)
            if (dt <= 0) continue
            val hrr = ((s[k].bpm - rest) / (maxHr - rest).coerceAtLeast(20.0)).clamp(0.0, 1.0)
            if (hrr >= 0.35) total += dt * HeartMath.trimpPerMinute(hrr, sex)
        }
        return total
    }

    fun stressLabel(v: Double): Pair<String, Tone> = when {
        v < 25 -> "ruhig" to Tone.GOOD
        v < 50 -> "niedrig" to Tone.OK
        v < 75 -> "mittel" to Tone.WARN
        else -> "hoch" to Tone.BAD
    }

    fun pctOfMax(bpm: Double, maxHr: Int): Int = (bpm / maxHr * 100).roundToInt()
}
