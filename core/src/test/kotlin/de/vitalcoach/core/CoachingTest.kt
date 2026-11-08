package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun ok(cond: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, cond)

class CoachingTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun result(day: String) = Analyzer.analyze(
        DemoData.generate(zone, Instant.parse("${day}T16:00:00Z")), DemoData.profile, LocalDate.parse(day),
    )

    private fun plan(vararg d: DayOfWeek, start: LocalDate = LocalDate.of(2026, 9, 1)) =
        RunPlan(d.toSet(), 17 * 60 + 30, Motivation.TEMPLATES.first(), start)

    @Test
    fun rolesPlaceLongOnWeekendAndIntervalsFarAway() {
        assertEquals(
            mapOf(TUESDAY to SessionKind.QUALITY, THURSDAY to SessionKind.EASY, SATURDAY to SessionKind.LONG),
            Coaching.roles(setOf(TUESDAY, THURSDAY, SATURDAY)),
        )
        // Beispiel aus der Anfrage: Mi, Sa, So → langer Lauf Sonntag, Intervalle Mittwoch (nicht direkt davor)
        assertEquals(
            mapOf(WEDNESDAY to SessionKind.QUALITY, SATURDAY to SessionKind.EASY, SUNDAY to SessionKind.LONG),
            Coaching.roles(setOf(WEDNESDAY, SATURDAY, SUNDAY)),
        )
        assertEquals(mapOf(MONDAY to SessionKind.EASY), Coaching.roles(setOf(MONDAY)))
        assertEquals(mapOf(WEDNESDAY to SessionKind.QUALITY, SUNDAY to SessionKind.LONG), Coaching.roles(setOf(WEDNESDAY, SUNDAY)))
        val four = Coaching.roles(setOf(MONDAY, WEDNESDAY, THURSDAY, SATURDAY))
        assertEquals(1, four.values.count { it == SessionKind.QUALITY })
        assertEquals(SessionKind.LONG, four[SATURDAY])
    }

    @Test
    fun progressionAndDeload() {
        assertEquals(1, Coaching.qualityStage(1, 50.0))
        assertEquals(2, Coaching.qualityStage(4, 50.0))
        assertEquals(3, Coaching.qualityStage(7, 50.0))
        assertEquals(1, Coaching.qualityStage(7, 10.0)) // wenig Fitness → behutsam
        ok(Coaching.isDeload(4) && Coaching.isDeload(8) && !Coaching.isDeload(5))
        val start = LocalDate.of(2026, 11, 2) // Montag
        assertEquals(1, Coaching.weekNumber(plan(TUESDAY, start = start), LocalDate.of(2026, 11, 8)))
        assertEquals(2, Coaching.weekNumber(plan(TUESDAY, start = start), LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun sessionsHaveRunAndBikeVariants() {
        val r = result("2026-11-05") // Donnerstag
        val p = plan(TUESDAY, THURSDAY, SATURDAY)
        val week = Coaching.week(p, r)
        assertEquals(3, week.sessions.size)
        week.sessions.forEach { s ->
            ok(s.run.isNotBlank() && s.bike.startsWith("Hometrainer"), s.toString())
            if (s.kind != SessionKind.REST) ok(s.bikeMinutes >= s.runMinutes && s.targetKm > 1.0, s.toString())
            ok(!s.bike.contains("kurbeln kurbeln"), s.bike)
            if (s.kind == SessionKind.EASY || s.kind == SessionKind.LONG) ok(s.bike.contains("${s.bikeMinutes} min"), s.bike)
        }
        val z2 = r.fitness.zones.first { it.number == 2 }
        val easy = Coaching.session(p, r, LocalDate.of(2026, 11, 5), SessionKind.EASY)
        ok(easy.run.contains("Puls ${z2.lowBpm}–${z2.highBpm}"), easy.run)
        ok(easy.bike.contains("Puls ${z2.lowBpm - Coaching.BIKE_HR_OFFSET}–${z2.highBpm - Coaching.BIKE_HR_OFFSET}"), easy.bike)
        val long = Coaching.session(p, r, LocalDate.of(2026, 11, 7), SessionKind.LONG)
        ok(long.runMinutes in 40..90 && long.runMinutes > easy.runMinutes, long.toString())
        val q = Coaching.session(p, r, LocalDate.of(2026, 11, 3), SessionKind.QUALITY)
        ok(q.kind == SessionKind.QUALITY || q.note?.contains("Entlastungswoche") == true, q.toString())
    }

    @Test
    fun todayAdaptsToReadiness() {
        val r = result("2026-11-05")
        val p = plan(THURSDAY)
        val s = Coaching.today(p, r)!!
        val ready = r.energy?.readinessNow ?: r.todayMetrics?.readiness?.value
        if (ready != null && ready < 45) ok(s.runMinutes <= 20 && s.note != null, s.toString())
        assertEquals(null, Coaching.today(plan(MONDAY), r))
        // Erinnerung nennt Einheit, Hometrainer-Alternative und Wochenstand
        val c = Motivation.reminder(p, r)
        ok(c.lines.any { it.startsWith("🚴 ") }, c.toString())
        ok(c.lines.any { it.startsWith("🎯 Diese Woche") }, c.toString())
        // Bei schlechtem Wetter: Hometrainer zuerst
        val bad = WeatherVerdict(true, "18:00: Regen, 4 °C, Regen 90 %", "starker Regen", dark = true)
        val ci = Motivation.reminder(p, r, bad)
        if (s.kind != SessionKind.REST) {
            ok(ci.title.startsWith("🚴 Heute Hometrainer"), ci.title)
            ok(ci.lines[1].startsWith("🚴 Hometrainer:"), ci.lines.toString())
        }
    }
}
