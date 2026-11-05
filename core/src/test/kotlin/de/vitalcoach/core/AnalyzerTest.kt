package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AnalyzerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-04T08:30:00Z")

    @Test
    fun demoDataProducesCompleteAnalysis() {
        val data = DemoData.generate(zone, now)
        val r = Analyzer.analyze(data, DemoData.profile, LocalDate.of(2026, 11, 4))
        val t = r.todayMetrics!!
        assertTrue("Tage: ${r.days.size}", r.days.size in 365..366)
        assertNotNull(t.restingHr)
        assertNotNull(t.hrv)
        assertNotNull(t.sleep)
        assertNotNull(t.recovery)
        assertNotNull(t.readiness)
        assertTrue(t.recovery!!.value in 0..100)
        assertTrue(t.readiness!!.value in 0..100)
        assertEquals(Confidence.HIGH, t.recovery!!.confidence)
        assertNotNull(r.fitness.vo2max)
        assertEquals(Vo2Method.RUNNING, r.fitness.vo2Method)
        assertTrue("VO2max plausibel: ${r.fitness.vo2max}", r.fitness.vo2max!! in 40.0..60.0)
        assertTrue(r.load.ctl > 10)
        assertNotNull(r.load.acwr)
        assertNotNull(r.load.recommendation)
        assertTrue(r.workouts.isNotEmpty())
        assertTrue(r.sleep.lastNight!!.score in 40..100)
        // Erkältungsphase vor ca. 3 Wochen muss erkannt werden
        assertTrue(r.days.any { it.illnessFlag })
        println("Erholung ${t.recovery} | Bereitschaft ${t.readiness} | VO2max ${r.fitness.vo2max} | CTL ${r.load.ctl} ACWR ${r.load.acwr}")
    }

    @Test
    fun emptyDataDoesNotCrash() {
        val r = Analyzer.analyze(HealthData(zone = zone, fetchedAt = now), UserProfile(), LocalDate.of(2026, 11, 4))
        assertEquals(1, r.days.size)
        assertNull(r.todayMetrics!!.recovery)
        assertEquals(LoadStatus.NO_DATA, r.load.status)
        assertNull(r.fitness.vo2max)
    }

    @Test
    fun trimpMatchesBanisterFormula() {
        // 60 min bei 70 % HFR, Mann: 60 × 0,7 × 0,64 × e^(1,92 × 0,7) ≈ 103,1
        assertEquals(103.1, 60 * HeartMath.trimpPerMinute(0.7, Sex.MALE), 0.2)
        // Frau: 60 × 0,7 × 0,86 × e^(1,67 × 0,7) ≈ 116,3
        assertEquals(116.26, 60 * HeartMath.trimpPerMinute(0.7, Sex.FEMALE), 0.2)
    }

    @Test
    fun restingHeartRateUsesLowestThirtyMinuteWindow() {
        val start = Instant.parse("2026-11-03T23:00:00Z")
        val samples = (0 until 96).map { i ->
            val bpm = if (i in 40..52) 50 else 60 // 13 Werte à 5 min = 60 min Tiefphase
            HeartRateSample(start.plusSeconds(i * 300L), bpm)
        }
        assertEquals(50.0, HeartMath.restingFromSamples(samples)!!, 0.01)
    }

    @Test
    fun vo2FromRunMatchesAcsmEquation() {
        // 10 km in 60 min = 166,7 m/min → VO2 = 36,83; HFR 75 % → 3,5 + 33,33/0,75 = 47,9
        val v = HeartMath.vo2FromRun(10_000.0, 60.0, avgHr = 50 + 0.75 * 140, rest = 50.0, max = 190)
        assertEquals(47.9, v!!, 0.1)
        assertNull(HeartMath.vo2FromRun(10_000.0, 10.0, 150.0, 50.0, 190))
    }

    @Test
    fun fitnessCategoriesAndAge() {
        assertEquals("Hervorragend", FitnessMath.CATEGORY_LABELS[FitnessMath.vo2Category(55.0, 25, Sex.MALE)])
        assertEquals("Mittel", FitnessMath.CATEGORY_LABELS[FitnessMath.vo2Category(38.0, 35, Sex.MALE)])
        assertEquals(45, FitnessMath.fitnessAge(47.7, Sex.MALE))
        assertEquals(35, FitnessMath.fitnessAge(40.0, Sex.FEMALE))
        assertEquals(18, FitnessMath.fitnessAge(70.0, Sex.MALE))
    }

    @Test
    fun sleepScoreRewardsGoodNight() {
        val bed = Instant.parse("2026-11-03T22:00:00Z")
        val segs = mutableListOf<SleepSegment>()
        var t = bed
        fun add(stage: SleepStage, min: Long) { segs += SleepSegment(t, t.plusSeconds(min * 60), stage); t = t.plusSeconds(min * 60) }
        repeat(5) { add(SleepStage.LIGHT, 45); add(SleepStage.DEEP, 18); add(SleepStage.REM, 22); add(SleepStage.AWAKE, 3) }
        val night = SleepMath.nights(listOf(SleepSession(bed, t, segs)), zone).single()
        val scored = SleepMath.score(night, 7.0, listOf(night.midpointMin, night.midpointMin, night.midpointMin))
        assertTrue("score ${scored.score}", scored.score >= 85)
        assertEquals(425.0, scored.asleepMin, 0.1)
    }

    @Test
    fun acwrScoreIsMonotonic() {
        assertTrue(Analyzer.acwrScore(1.0) > Analyzer.acwrScore(1.3))
        assertTrue(Analyzer.acwrScore(1.3) > Analyzer.acwrScore(1.8))
    }
}
