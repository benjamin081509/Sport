package de.vitalcoach.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun assertTrue(cond: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, cond)

class MeaningTest {

    /** Situation aus den Screenshots: 1 Lauf (107) diese Woche, Vorwoche 23, 28 T = 236, 90 T = 2.160, ACWR 1,02. */
    private fun userLoad() = LoadReport(
        atl = 8.0, ctl = 12.0, tsb = 4.0, acwr = 1.02, monotony = null, strain = null,
        weekLoad = 109.0, prevWeekLoad = 23.0, status = LoadStatus.MAINTAIN,
        easyPct = null, moderatePct = null, hardPct = null, zoneMin28 = List(6) { 0.0 },
        whoMinutes7 = 40.0, workouts7 = 1, recommendation = null, historyDays = 365,
        acuteWeek = 40.0, chronicWeek = 39.0, trainingDays7 = 1,
        weekAvg28 = 236.0 / 4, weekAvgBefore = (2160.0 - 236.0) / 62 * 7,
        daysSinceWorkout = 6, ctl28Ago = 24.0,
    )

    @Test
    fun sessionLevels() {
        assertEquals("leicht", Meaning.sessionLevel(25.0).label)
        assertEquals("moderat", Meaning.sessionLevel(60.0).label)
        assertEquals("fordernd", Meaning.sessionLevel(107.0).label)
        assertEquals("hart", Meaning.sessionLevel(150.0).label)
        assertEquals("sehr hart", Meaning.sessionLevel(260.0).label)
        // 107 Punkte ≈ 75 min lockeres Training (Zone 2, Mann)
        assertEquals(75, Meaning.easyMinutes(107.0, Sex.MALE))
        val s = Meaning.trimpScale(Sex.MALE)
        assertTrue(s.easy30 in 40..45 && s.brisk60 in 100..105 && s.hard60 in 140..145, s.toString())
    }

    @Test
    fun weeksExplainLowerLevelThanSummer() {
        val t = Meaning.weeks(userLoad())
        assertTrue(t.contains("85 % mehr"), t)
        assertTrue(t.contains("deutlich weniger als davor (59 statt 217 pro Woche)"), t)
        assertTrue(t.contains("von 24 auf 12 gesunken"), t)
    }

    @Test
    fun acwrExplainsWeightingAndLowBase() {
        val t = Meaning.acwr(userLoad())
        assertTrue(t.startsWith("Du belastest dich aktuell etwa so viel wie gewohnt."), t)
        assertTrue(t.contains("6 Tage her"), t)
        assertTrue(t.contains("217 pro Woche) wäre ein Belastungssprung"), t)
    }

    @Test
    fun monotonyOnlyWithRegularTraining() {
        val zone = ZoneId.of("Europe/Berlin")
        val r = Analyzer.analyze(DemoData.generate(zone, Instant.parse("2026-11-06T08:30:00Z")), DemoData.profile, LocalDate.of(2026, 11, 6))
        if (r.load.trainingDays7 < 3) assertNull(r.load.monotony)
        assertTrue(r.load.weekAvg28 > 0)
        assertTrue(r.workouts.all { it.recoveryH != null })
        assertTrue(Meaning.form(r.load).startsWith("Fitness = "))
        r.workouts.take(5).forEach { assertTrue(Meaning.session(it, r).contains("Einheit:")) }
    }
}
