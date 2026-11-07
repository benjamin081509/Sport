package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun check(cond: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, cond)

class EnergyTest {
    private val wake = Instant.parse("2026-11-06T06:00:00Z")

    private fun samples(fromH: Double, toH: Double, bpm: Int): List<HeartRateSample> {
        val out = ArrayList<HeartRateSample>()
        var t = wake.plusSeconds((fromH * 3600).toLong())
        val end = wake.plusSeconds((toH * 3600).toLong())
        while (t.isBefore(end)) {
            out += HeartRateSample(t, bpm)
            t = t.plusSeconds(300)
        }
        return out
    }

    private fun run(startH: Double, min: Int, trimp: Double) = WorkoutAnalysis(
        workout = Workout(wake.plusSeconds((startH * 3600).toLong()), wake.plusSeconds((startH * 3600).toLong() + min * 60L), SportType.RUNNING),
        durationMin = min.toDouble(), avgHr = 150.0, maxHr = 170, trimp = trimp, estimated = false,
        zoneMin = List(6) { 0.0 }, moderateMin = 0.0, vigorousMin = 0.0, vo2Estimate = null, paceSecPerKm = null, focus = "",
    )

    @Test
    fun awakeTimeAloneDrainsSlowly() {
        val s = Energy.simulate(80.0, wake, wake.plusSeconds(10 * 3600), emptyList(), emptyList(), null, 50.0, 190, Sex.MALE)
        assertEquals(68.0, s.end, 0.01) // 10 h × 1,2
        assertEquals(0.0, s.load, 0.0001)
        check(!s.hadHeartRate)
    }

    @Test
    fun workoutDrainsByTrimp() {
        val s = Energy.simulate(80.0, wake, wake.plusSeconds(10 * 3600), emptyList(), listOf(run(3.0, 53, 107.0)), null, 50.0, 190, Sex.MALE)
        assertEquals(107 * 0.2, s.training, 0.01)
        assertEquals(80.0 - 12.0 - 21.4, s.end, 0.05)
    }

    @Test
    fun stressDrainsAndRelaxationCharges() {
        val steps = emptyMap<Long, Long>() // keine Schritte = Ruhe
        val calm = Energy.simulate(60.0, wake, wake.plusSeconds(4 * 3600), samples(0.0, 4.0, 51), emptyList(), steps, 50.0, 190, Sex.MALE)
        val tense = Energy.simulate(60.0, wake, wake.plusSeconds(4 * 3600), samples(0.0, 4.0, 85), emptyList(), steps, 50.0, 190, Sex.MALE)
        check(calm.charge > 0 && calm.stress < 0.5, "entspannt: $calm")
        check(tense.stress > 5 && tense.charge == 0.0, "gestresst: $tense")
        check(calm.end > tense.end + 8, "calm=${calm.end} tense=${tense.end}")
    }

    @Test
    fun valueStaysWithinBounds() {
        val s = Energy.simulate(10.0, wake, wake.plusSeconds(6 * 3600), emptyList(), listOf(run(1.0, 120, 300.0)), null, 50.0, 190, Sex.MALE)
        check(s.curve.all { it.value in 0.0..100.0 })
        assertEquals(0.0, s.end, 0.0001)
    }

    @Test
    fun trainingLowersReadinessClearly() {
        val end = wake.plusSeconds(9 * 3600 + 45 * 60)
        val now = end.plusSeconds(3600)
        // Richtwerte bei mittlerer Fitness (CTL 40) direkt nach dem Training
        assertEquals(11.7, Energy.trainingDrop(listOf(run(9.0, 45, 40.0)), 40.0, end), 0.2)
        assertEquals(24.3, Energy.trainingDrop(listOf(run(9.0, 45, 100.0)), 40.0, end), 0.2)
        assertEquals(31.6, Energy.trainingDrop(listOf(run(9.0, 45, 150.0)), 40.0, end), 0.2)
        // Wenig Fitness → stärkerer Abzug; der Abzug klingt über den Tag ab
        check(Energy.trainingDrop(listOf(run(9.0, 45, 100.0)), 12.0, end) > 29.0)
        check(Energy.trainingDrop(listOf(run(9.0, 45, 100.0)), 40.0, end.plusSeconds(8 * 3600)) < 20.0)
        assertEquals(0.0, Energy.trainingDrop(emptyList(), 40.0, now), 0.0)

        // Situation aus der Rückmeldung: Bereitschaft 79 am Morgen, 45 min fordernder Lauf um 15 Uhr, eine Stunde später
        val sim = Energy.simulate(79.0, wake, now, emptyList(), listOf(run(9.0, 45, 85.0)), null, 50.0, 190, Sex.MALE)
        val rn = Energy.readinessNow(79, sim, listOf(run(9.0, 45, 85.0)), 12.0, now)!!
        check(rn.value in 45..58, "Bereitschaft nach fordernder Einheit: $rn")
        check(rn.dropTraining > 20, rn.toString())
        assertEquals(0.0, rn.dropDaily, 0.0001)
    }

    @Test
    fun analyzerAndDigestWithDemoData() {
        val zone = ZoneId.of("Europe/Berlin")
        val r = Analyzer.analyze(DemoData.generate(zone, Instant.parse("2026-11-06T19:30:00Z")), DemoData.profile, LocalDate.of(2026, 11, 6))
        val e = r.energy
        assertNotNull(e)
        e!!
        check(e.now in 0..100 && e.morning == r.todayMetrics?.recovery?.value, e.toString())
        check(e.now <= e.morning, "Energie sollte über den Tag sinken: ${e.morning} → ${e.now}")
        check(e.history.size >= 5, "Verlauf: ${e.history}")
        val rn = e.readinessNow
        val rm = e.readinessMorning
        if (rn != null && rm != null) check(rn <= rm)
        check(Meaning.energy(e).startsWith("Morgens ${e.morning}"))

        val m = Digest.morning(r)
        val ev = Digest.evening(r)
        check(m.title.startsWith("☀️ Guten Morgen") && m.lines.size in 3..Digest.MAX_LINES, m.toString())
        check(ev.title.startsWith("🌙 Dein Tag · Energie ${e.now}") && ev.lines.size in 3..Digest.MAX_LINES, ev.toString())
        check(ev.lines.first().startsWith("🔋 Energie ${e.morning} → ${e.now}"), ev.lines.first())
        assertEquals("5:59 h", Digest.hm(359.0))
    }
}
