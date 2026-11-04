package de.vitalcoach.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CoachTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-04T08:30:00Z")

    private fun all(r: AnalysisResult) = listOf(
        Coach.day(r), Coach.recovery(r), Coach.readiness(r), Coach.sleep(r), Coach.training(r),
        Coach.fitness(r), Coach.restingHr(r), Coach.hrv(r), Coach.activity(r), Coach.vitals(r), Coach.body(r),
    )

    @Test
    fun coachWritesTextsForDemoData() {
        val r = Analyzer.analyze(DemoData.generate(zone, now), DemoData.profile, LocalDate.of(2026, 11, 4))
        val texts = all(r) + r.workouts.take(3).map { Coach.workout(it, r) }
        texts.forEach {
            assertTrue(it.headline.isNotBlank())
            assertTrue(it.text.length > 40)
            assertTrue("Kein NaN in ${it.headline}: ${it.text}", !it.text.contains("NaN") && it.tips.none { t -> t.contains("NaN") })
        }
        texts.forEach { println("### ${it.headline} [${it.tone}]\n${it.text}\n" + it.tips.joinToString("\n") { t -> " • $t" }) }
        r.warnings.forEach { println("!! ${it.title}: ${it.text}") }
    }

    @Test
    fun coachHandlesMissingData() {
        val r = Analyzer.analyze(HealthData(zone = zone, fetchedAt = now), UserProfile(), LocalDate.of(2026, 11, 4))
        all(r).forEach { assertTrue(it.headline.isNotBlank()) }
    }
}
