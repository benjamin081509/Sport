package de.vitalcoach.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Schlafauswertung. Referenzwerte:
 *  - Schlafdauer Erwachsene: 7–9 h (AASM/SRS Consensus 2015, National Sleep Foundation)
 *  - Schlafeffizienz ≥ 85 % gilt als gut (Ohayon et al. 2017, NSF Sleep Quality Recommendations)
 *  - Tiefschlaf typ. 13–23 %, REM typ. 20–25 % der Schlafzeit (Ohayon et al. 2004)
 *  - Regelmäßigkeit: Schwankung der Schlafmitte – Regelmäßigkeit ist ein eigenständiger
 *    Gesundheitsfaktor (Windred et al. 2024, Sleep)
 */
object SleepMath {

    private const val MERGE_GAP_MIN = 90L

    /** Verbindet unterbrochene Schlafphasen (Lücke ≤ 90 min) zu einer Nacht. */
    fun mergeSessions(sessions: List<SleepSession>): List<SleepSession> {
        val sorted = sessions.filter { it.end.isAfter(it.start) }.sortedBy { it.start }
        val out = mutableListOf<SleepSession>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && !s.start.isAfter(last.end.plus(Duration.ofMinutes(MERGE_GAP_MIN)))) {
                val gap = if (s.start.isAfter(last.end)) listOf(SleepSegment(last.end, s.start, SleepStage.AWAKE)) else emptyList()
                out[out.size - 1] = SleepSession(
                    start = last.start,
                    end = if (s.end.isAfter(last.end)) s.end else last.end,
                    segments = last.segments + gap + s.segments,
                )
            } else {
                out += s
            }
        }
        return out
    }

    /** Minuten ab 12:00 Uhr mittags (Bereich 720..2160), damit Zeiten um Mitternacht vergleichbar sind. */
    fun clockMinutes(t: Instant, zone: ZoneId): Double {
        val lt = t.atZone(zone).toLocalTime()
        val m = lt.hour * 60 + lt.minute + lt.second / 60.0
        return if (m < 720) m + 1440 else m
    }

    fun buildNight(main: SleepSession, napMin: Double, zone: ZoneId): SleepNight {
        val inBed = Duration.between(main.start, main.end).seconds / 60.0
        var deep = 0.0
        var rem = 0.0
        var light = 0.0
        var generic = 0.0
        var awake = 0.0
        for (seg in main.segments) {
            val st = if (seg.start.isBefore(main.start)) main.start else seg.start
            val en = if (seg.end.isAfter(main.end)) main.end else seg.end
            if (!en.isAfter(st)) continue
            val m = Duration.between(st, en).seconds / 60.0
            when (seg.stage) {
                SleepStage.DEEP -> deep += m
                SleepStage.REM -> rem += m
                SleepStage.LIGHT -> light += m
                SleepStage.SLEEPING -> generic += m
                SleepStage.AWAKE, SleepStage.OUT_OF_BED -> awake += m
                SleepStage.UNKNOWN -> Unit
            }
        }
        val known = deep + rem + light + generic + awake
        val segmentsUsable = inBed > 0 && known / inBed >= 0.5
        val asleep: Double
        val efficiency: Double?
        val hasStages: Boolean
        if (segmentsUsable) {
            asleep = deep + rem + light + generic
            efficiency = if (asleep + awake > 0) asleep / (asleep + awake) else null
            hasStages = deep + rem + light > 0
        } else {
            asleep = inBed
            efficiency = null
            hasStages = false
        }
        val mid = main.start.plusSeconds(Duration.between(main.start, main.end).seconds / 2)
        return SleepNight(
            date = main.end.atZone(zone).toLocalDate(),
            start = main.start,
            end = main.end,
            inBedMin = inBed,
            asleepMin = asleep,
            awakeMin = if (segmentsUsable) awake else 0.0,
            deepMin = deep,
            remMin = rem,
            lightMin = light,
            hasStages = hasStages,
            efficiency = efficiency,
            napMin = napMin,
            midpointMin = clockMinutes(mid, zone),
            bedtimeMin = clockMinutes(main.start, zone),
            wakeMin = clockMinutes(main.end, zone),
        )
    }

    /** Ordnet jede Nacht dem Aufwachdatum zu; die längste Phase (≥ 2 h) ist der Hauptschlaf. */
    fun nights(sessions: List<SleepSession>, zone: ZoneId): List<SleepNight> {
        val merged = mergeSessions(sessions)
        return merged.groupBy { it.end.atZone(zone).toLocalDate() }
            .mapNotNull { (_, list) ->
                val main = list.maxByOrNull { Duration.between(it.start, it.end).seconds } ?: return@mapNotNull null
                if (Duration.between(main.start, main.end).toMinutes() < 120) return@mapNotNull null
                val naps = list.filter { it !== main }.sumOf { Duration.between(it.start, it.end).seconds / 60.0 }
                buildNight(main, naps, zone)
            }
            .sortedBy { it.date }
    }

    /**
     * Schlaf-Score 0–100:
     *  Dauer 50 % · Effizienz 15 % · Tiefschlaf 12,5 % · REM 12,5 % · Regelmäßigkeit 10 %.
     *  Ohne Schlafphasen: Dauer 75 % · Regelmäßigkeit 25 %.
     */
    fun score(night: SleepNight, goalHours: Double, previousMidpoints: List<Double>): SleepNight {
        val comps = mutableListOf<ScoreComponent>()
        val asleepH = night.asleepMin / 60.0
        val goal = goalHours.clamp(5.0, 11.0)
        val dur = (100.0 * ((asleepH - 4.0) / (goal - 4.0)).clamp(0.0, 1.0)).roundToInt()
        comps += ScoreComponent("Dauer", dur, 0.50, "${Fmt.duration(night.asleepMin)} geschlafen (Ziel ${Fmt.hours(goal)})")
        night.efficiency?.let { eff ->
            val s = (100.0 * ((eff - 0.65) / (0.92 - 0.65)).clamp(0.0, 1.0)).roundToInt()
            comps += ScoreComponent("Effizienz", s, 0.15, "${Fmt.pct(eff)} der Bettzeit geschlafen (gut: ≥ 85 %)")
        }
        if (night.hasStages) {
            val deep = night.deepPct ?: 0.0
            val rem = night.remPct ?: 0.0
            comps += ScoreComponent(
                "Tiefschlaf",
                (100.0 * (deep / 0.15).clamp(0.0, 1.0)).roundToInt(),
                0.125,
                "${Fmt.duration(night.deepMin)} (${Fmt.pct(deep)}, typisch 13–23 %)",
            )
            comps += ScoreComponent(
                "REM-Schlaf",
                (100.0 * (rem / 0.20).clamp(0.0, 1.0)).roundToInt(),
                0.125,
                "${Fmt.duration(night.remMin)} (${Fmt.pct(rem)}, typisch 20–25 %)",
            )
        }
        val ref = Stats.median(previousMidpoints.takeLast(14))
        if (ref != null && previousMidpoints.size >= 3) {
            val dev = abs(night.midpointMin - ref)
            val s = (100.0 * (1.0 - (dev - 30.0) / 90.0).clamp(0.0, 1.0)).roundToInt()
            comps += ScoreComponent(
                "Regelmäßigkeit",
                s,
                if (night.hasStages) 0.10 else 0.25,
                "Schlafmitte ${dev.roundToInt()} min von deinem üblichen Rhythmus entfernt",
            )
        }
        if (!night.hasStages) {
            val i = comps.indexOfFirst { it.name == "Dauer" }
            comps[i] = comps[i].copy(weight = 0.75)
        }
        val wsum = comps.sumOf { it.weight }
        val total = if (wsum > 0) comps.sumOf { it.score * it.weight } / wsum else 0.0
        return night.copy(score = total.roundToInt().coerceIn(0, 100), components = comps)
    }

    /**
     * Sleep Regularity Index (Phillips et al. 2017, Sci Rep): Wahrscheinlichkeit, 24 Stunden später im selben
     * Zustand (wach/schlafend) zu sein, skaliert auf −100…100. Berechnet in 5-Minuten-Epochen über [days] Tage.
     * Nur Tage mit aufgezeichnetem Schlaf werden verglichen.
     */
    fun sri(sessions: List<SleepSession>, endDate: java.time.LocalDate, zone: ZoneId, days: Int = 14): Double? {
        val epochSec = 300L
        val perDay = (86400 / epochSec).toInt()
        val startT = endDate.minusDays(days.toLong()).atTime(12, 0).atZone(zone).toInstant()
        val total = days * perDay
        val asleep = BooleanArray(total)
        val dayHasSleep = BooleanArray(days)
        fun mark(a: Instant, b: Instant) {
            val i0 = (Duration.between(startT, a).seconds / epochSec).toInt().coerceIn(0, total)
            val i1 = (Duration.between(startT, b).seconds / epochSec).toInt().coerceIn(0, total)
            for (i in i0 until i1) asleep[i] = true
        }
        for (ses in sessions) {
            if (!ses.end.isAfter(startT)) continue
            val sleepSegs = ses.segments.filter { it.stage in setOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM, SleepStage.SLEEPING) }
            if (sleepSegs.isNotEmpty()) sleepSegs.forEach { mark(it.start, it.end) } else mark(ses.start, ses.end)
            val mid = ses.start.plusSeconds(Duration.between(ses.start, ses.end).seconds / 2)
            val d = (Duration.between(startT, mid).seconds / 86400).toInt()
            if (d in 0 until days && Duration.between(ses.start, ses.end).toMinutes() >= 120) dayHasSleep[d] = true
        }
        var matches = 0L
        var count = 0L
        for (d in 0 until days - 1) {
            if (!dayHasSleep[d] || !dayHasSleep[d + 1]) continue
            for (e in 0 until perDay) {
                val i = d * perDay + e
                if (asleep[i] == asleep[i + perDay]) matches++
                count++
            }
        }
        if (count < 5L * perDay) return null
        return (-100.0 + 200.0 * matches / count).clamp(-100.0, 100.0)
    }

    /**
     * Persönlicher Schlafbedarf: mittlere Schlafdauer in den Nächten, nach denen dein Nervensystem
     * besonders gut erholt war (HRV/Ruhepuls mindestens 0,5 Standardabweichungen besser als normal).
     */
    fun personalNeed(pairs: List<Pair<Double, Double>>): Pair<Double, Int>? {
        if (pairs.size < 21) return null
        val good = pairs.filter { it.second >= 0.5 }.map { it.first / 60.0 }
        if (good.size < 5) return null
        val med = Stats.median(good) ?: return null
        return med.clamp(6.5, 9.5) to good.size
    }
}
