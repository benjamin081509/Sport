package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Kurze Nächte müssen klar schlecht bewertet werden – auch wenn alle anderen Werte normal sind. */
class StrictScoringTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun night(asleepMin: Long): SleepSession {
        val bed = Instant.parse("2026-11-04T23:30:00Z")
        val segs = mutableListOf<SleepSegment>()
        var t = bed
        fun add(stage: SleepStage, min: Long) {
            segs += SleepSegment(t, t.plusSeconds(min * 60), stage)
            t = t.plusSeconds(min * 60)
        }
        // ca. 18 % Tief, 22 % REM, Rest leicht, kaum wach
        val cycles = asleepMin / 90
        repeat(cycles.toInt()) { add(SleepStage.LIGHT, 54); add(SleepStage.DEEP, 16); add(SleepStage.REM, 20); add(SleepStage.AWAKE, 2) }
        val rest = asleepMin - cycles * 90
        if (rest > 0) add(SleepStage.LIGHT, rest)
        return SleepSession(bed, t, segs)
    }

    @Test
    fun sixHourNightIsRatedPoor() {
        val n = SleepMath.nights(listOf(night(359)), zone).single()
        val scored = SleepMath.score(n, 8.0, List(10) { n.midpointMin })
        println("5 h 59 min → Schlaf-Score ${scored.score}; " + scored.components.joinToString { "${it.name} ${it.score}" })
        assertTrue("Schlaf-Score ${scored.score}", scored.score <= 50)
        assertEquals(Tone.BAD, scored.scoreTone)

        val rhrB = Baseline(55.0, 2.5, 30)
        val hrvB = Baseline(kotlin.math.ln(50.0), 0.15, 30)
        val respB = Baseline(14.0, 0.5, 30)
        val rec = Analyzer.recoveryScore(55.0, rhrB, 50.0, hrvB, scored, 14.0, respB, 100)!!
        println("Erholung bei normalen Werten + 5 h 59 min Schlaf: ${rec.value}")
        assertTrue("Erholung ${rec.value}", rec.value <= 55)
        assertTrue(rec.tone == Tone.WARN || rec.tone == Tone.BAD)
    }

    @Test
    fun normalDayIsSolidNotGreat() {
        val n = SleepMath.nights(listOf(night(450)), zone).single() // 7,5 h
        val scored = SleepMath.score(n, 8.0, List(10) { n.midpointMin })
        val rec = Analyzer.recoveryScore(
            55.0, Baseline(55.0, 2.5, 30), 50.0, Baseline(kotlin.math.ln(50.0), 0.15, 30), scored, 14.0, Baseline(14.0, 0.5, 30), 100,
        )!!
        println("Normaltag mit 7,5 h Schlaf: Schlaf ${scored.score}, Erholung ${rec.value}")
        assertTrue("Schlaf ${scored.score}", scored.score in 75..97)
        assertTrue("Erholung ${rec.value}", rec.value in 60..74)
    }

    @Test
    fun shortNightLimitsReadinessInFullAnalysis() {
        val now = Instant.parse("2026-11-05T09:00:00Z")
        val demo = DemoData.generate(zone, now)
        val lastNightEnd = demo.sleep.maxOf { it.end }
        val short = demo.sleep.map { s ->
            if (s.end == lastNightEnd) SleepSession(s.start, s.start.plusSeconds(359 * 60), s.segments.filter { it.start.isBefore(s.start.plusSeconds(359 * 60)) }) else s
        }
        val r = Analyzer.analyze(demo.copy(sleep = short), DemoData.profile, LocalDate.of(2026, 11, 5))
        val t = r.todayMetrics!!
        println("Demo mit kurzer Nacht: Schlaf ${t.sleep?.score} (${t.sleep?.asleepMin}), Erholung ${t.recovery?.value}, Bereitschaft ${t.readiness?.value}, Empfehlung ${r.load.recommendation?.title}")
        assertTrue(t.readiness!!.value <= 59)
        assertTrue(t.recovery!!.value <= 55)
        assertTrue(r.load.recommendation!!.title != "Bereit für intensives Training")
    }
}
