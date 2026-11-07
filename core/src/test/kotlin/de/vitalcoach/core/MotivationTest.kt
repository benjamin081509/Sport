package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private fun ok(cond: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, cond)

class MotivationTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val r = Analyzer.analyze(DemoData.generate(zone, Instant.parse("2026-11-06T16:30:00Z")), DemoData.profile, LocalDate.of(2026, 11, 6))
    private val plan = RunPlan(
        days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
        reminderMinutes = 17 * 60 + 30,
        ifThen = Motivation.TEMPLATES.first(),
        startDate = LocalDate.of(2026, 10, 1),
    )

    @Test
    fun weekStatusMatchesWorkouts() {
        val rep = Motivation.report(plan, r)
        val monday = r.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(monday, rep.week.monday)
        assertEquals(7, rep.week.days.size)
        assertEquals(3, rep.week.goal)
        val expectedDays = r.workouts.filter { Motivation.counts(it) }
            .map { it.workout.start.atZone(zone).toLocalDate() }
            .filter { !it.isBefore(monday) && !it.isAfter(r.today) }
            .toSet()
        assertEquals(expectedDays.size, rep.week.done)
        assertEquals(8, rep.history.size)
        ok(rep.week.days.single { it.isToday }.date == r.today)
        ok(rep.week.days.filter { it.isFuture }.all { !it.done && !it.missed })
        // Freitag ist kein Plantag → nächster offener Plantag ist Samstag
        assertEquals(LocalDate.of(2026, 11, 7), rep.nextPlanned)
        ok(!rep.todayPlanned)
        assertEquals(27, rep.habitTarget)
        ok(rep.sessionsSinceStart > 0)
    }

    @Test
    fun streakCountsConsecutiveWeeks() {
        val rep = Motivation.report(plan, r)
        var expected = if (rep.week.reached) 1 else 0
        for (w in rep.history.dropLast(1).reversed()) if (w.reached) expected++ else break
        assertEquals(expected, rep.streakWeeks)
    }

    @Test
    fun walkingAndShortSessionsDoNotCount() {
        fun w(type: SportType, min: Long) = WorkoutAnalysis(
            Workout(Instant.EPOCH, Instant.EPOCH.plusSeconds(min * 60), type), min.toDouble(), null, null, 10.0, true,
            List(6) { 0.0 }, 0.0, 0.0, null, null, "",
        )
        ok(Motivation.counts(w(SportType.RUNNING, 20)))
        ok(Motivation.counts(w(SportType.CYCLING, 30)))
        ok(!Motivation.counts(w(SportType.RUNNING, 8)))
        ok(!Motivation.counts(w(SportType.WALKING, 60)))
    }

    @Test
    fun reminderTextsAreConcrete() {
        val c = Motivation.reminder(plan, r)
        ok(c.title.contains("Lauftag"), c.title)
        ok(c.lines.any { it.contains(plan.ifThen) } || c.title.contains("Pause"), c.toString())
        ok(c.lines.any { it.startsWith("🎯 Diese Woche") }, c.toString())
        ok(c.lines.any { it.startsWith("💡 ") }, c.toString())
        val f = Motivation.followUp(plan, r)
        ok(f.lines.first().contains("Wochenziel"), f.toString())
        ok(f.lines.last().contains("Nächster Lauftag"), f.toString())
        // Tipp des Tages ist stabil und wechselt täglich
        assertEquals(Motivation.tipOfDay(r.today), Motivation.tipOfDay(r.today))
        ok(Motivation.tipOfDay(r.today) != Motivation.tipOfDay(r.today.plusDays(1)))
    }

    @Test
    fun digestsMentionPlan() {
        val tuesdayPlan = plan.copy(days = setOf(r.today.dayOfWeek))
        val morning = Motivation.digestLine(tuesdayPlan, r, morning = true)
        val rep = Motivation.report(tuesdayPlan, r)
        if (!rep.todayDone && !rep.suggestion.restAdvised) ok(morning!!.startsWith("🎒 Heute Lauftag (17:30)"), morning.toString())
        val evening = Digest.evening(r, plan)
        ok(evening.lines.any { it.startsWith("🎯 Woche ") }, evening.toString())
        ok(evening.lines.size <= Digest.MAX_LINES)
        ok(Motivation.hasBackToBack(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)))
        ok(!Motivation.hasBackToBack(plan.days))
        ok(Motivation.hasBackToBack(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)))
    }
}
