package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExtendedTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-04T16:30:00Z")
    private val r by lazy { Analyzer.analyze(DemoData.generate(zone, now), DemoData.profile, LocalDate.of(2026, 11, 4)) }

    @Test
    fun regenerationAndOverload() {
        val g = r.regeneration
        println("Regeneration: index=${g.regenerationIndex} time=${g.timeProgress} remaining=${g.remainingHours} fatigue=${g.fatigueIndex} (${g.fatigueLabel}) balance=${g.balance} ${g.balanceLabel} overload=${g.overloadLabel} ${g.overloadSignals} load7/28/90=${g.load7}/${g.load28}/${g.load90} hrvTrend=${g.hrvTrendPct} rhrDev=${g.rhrDeviation}")
        g.recent.forEach { println("  ${it.workout.workout.title}: ${it.hours} h, rest ${it.remainingH}") }
        assertNotNull(g.regenerationIndex)
        assertNotNull(g.fatigueIndex)
        assertTrue(g.load90 > g.load28 && g.load28 > g.load7)
        assertTrue(g.recent.all { it.hours in 2.0..96.0 })
    }

    @Test
    fun sleepDetails() {
        val s = r.sleep
        println("Schlaf: debt7=${s.debt7H} debt14=${s.debt14H} SRI=${s.sri} need=${s.personalNeedH} (${s.personalNeedNights}) tonight=${s.needTonightMin} ${s.needReasons} bedtime=${s.recommendedBedtimeMin?.let(Fmt::clock)} nightRec=${s.nightRecovery?.value}")
        s.nightRecovery?.components?.forEach { println("   ${it.name}: ${it.score} – ${it.detail}") }
        assertNotNull(s.sri)
        assertTrue(s.sri!! in 40.0..100.0)
        assertNotNull(s.needTonightMin)
        assertNotNull(s.recommendedBedtimeMin)
        assertNotNull(s.nightRecovery)
    }

    @Test
    fun heartStressActivityPerformance() {
        val h = r.heart
        println("Herz: avg=${h.avgHrToday} avg7=${h.avgHr7} sleepAvg=${h.sleepAvgHr} dip=${h.dipPct} pos=${h.lowestHrPos} curve=${h.nightCurve.size} rhrSd=${h.rhrSd14} hrvCv=${h.hrvCv7} hrv7/30/90/180=${h.hrv7}/${h.hrv30}/${h.hrv90}/${h.hrv180} rhr365=${h.rhr365} weeks=${h.rhrWeekly.size} hrr=${h.hrr.take(3)} trend=${h.hrrTrend}")
        val st = r.stress
        println("Stress: today=${st.today} avg7=${st.avg7} ${st.label} rest=${st.restPct} high=${st.highPct} night=${st.nightStress} hours=${st.byHour.map { it?.toInt() }}")
        val a = r.activity
        println("Aktivität: active=${a.activeHoursToday}/${a.inactiveHoursToday} avg7 ${a.activeHours7Avg}/${a.inactiveHours7Avg} movement=${a.movementIndex?.value} regularity=${a.regularity} every=${a.everydayLoad7Avg} share=${a.everydayShare7} kcalShare=${a.activeKcalShare7} gaps=${a.hardSessionGapAvgH}/${a.shortGaps28} cadence=${a.runCadence} byHour=${a.stepsByHour.map { it.toInt() }}")
        val p = r.performance
        println("Leistung: ef=${p.efRecent}/${p.efPrevious} change=${p.efChangePct} paceZ2=${p.paceAtZ2SecKm?.let(Fmt::pace)} lthr=${p.lthr} hardMin=${p.hardMin28} peak=${p.peakHr90} power=${p.cyclingPowerAvg} wkg=${p.cyclingWkg} speed=${p.cyclingSpeedKmh} vo2chg=${p.vo2Change90}")
        assertNotNull(h.dipPct)
        assertTrue(h.hrr.isNotEmpty())
        assertTrue(h.hrr.first().drop60 in 10..45)
        assertNotNull(st.avg7)
        assertTrue(a.hasHourlySteps)
        assertEquals(24, a.stepsByHour.size)
        assertNotNull(a.movementIndex)
        assertNotNull(p.efRecent)
        assertNotNull(p.cyclingPowerAvg)
    }

    @Test
    fun heartHealthForecastInsights() {
        val hh = r.heartHealth
        println("Herzgesundheit: ${hh.score} ${hh.label} (${hh.covered} Bereiche)")
        hh.components.forEach { println("   ${it.name}: ${it.score} – ${it.value}") }
        assertNotNull(hh.score)
        assertEquals(7, hh.covered)
        val f = r.forecast!!
        f.scenarios.forEach { (k, v) -> println("Prognose ${k.label}: ${v.map { "${it.expected} (${it.low}–${it.high})" }}") }
        assertTrue(f.scenarios.getValue(ForecastScenario.REST)[1].expected >= f.scenarios.getValue(ForecastScenario.HARD)[1].expected)
        r.baselines.forEach { println("Normalwert ${it.name}: ${it.current} vs ${it.mean30}±${it.sd30} z=${it.z} ${it.status}") }
        r.correlations.forEach { println("Zusammenhang ${it.title}: ${it.text}") }
        println("Briefing: ${r.briefing.headline}\n  ${r.briefing.changes}\n  ${r.briefing.explanations}\n  ${r.briefing.todo}")
        r.trends.forEach { println("Trend ${it.name}: ${it.summary} (${it.series.size} Wochen)") }
        assertTrue(r.baselines.size >= 10)
        assertTrue(r.correlations.size >= 6)
        assertTrue(r.briefing.headline.isNotBlank())
        assertTrue(r.trends.first().series.size >= 40)
    }

    @Test
    fun snapshotRoundTrip() {
        val snaps = SnapshotCodec.fromResult(r)
        val text = SnapshotCodec.encode(snaps)
        val back = SnapshotCodec.decode(text)
        assertEquals(snaps.size, back.size)
        assertEquals(snaps.last().restingHr!!, back.last().restingHr!!, 0.01)
        val merged = SnapshotCodec.merge(back, listOf(DailySnapshot(back.last().date, restingHr = 50.0)))
        assertEquals(50.0, merged.last().restingHr!!, 0.0)
        assertEquals(back.last().sleepMin!!, merged.last().sleepMin!!, 0.01)
    }

    @Test
    fun srIndexForPerfectlyRegularSleep() {
        val sessions = (1..14).map { d ->
            val s = LocalDate.of(2026, 10, 20).plusDays(d.toLong()).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant()
            SleepSession(s, s.plusSeconds(8 * 3600))
        }
        val sri = SleepMath.sri(sessions, LocalDate.of(2026, 11, 4), ZoneId.of("UTC"), 14)
        assertEquals(100.0, sri!!, 0.5)
    }
}
