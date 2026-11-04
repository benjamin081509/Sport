package de.vitalcoach.core

import de.vitalcoach.core.ai.AiAnswer
import de.vitalcoach.core.ai.AiReport
import de.vitalcoach.core.ai.ClaudeCoach
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AiReportTest {
    @Test
    fun reportContainsKeyMetrics() {
        val zone = ZoneId.of("Europe/Berlin")
        val r = Analyzer.analyze(DemoData.generate(zone, Instant.parse("2026-11-04T08:30:00Z")), DemoData.profile, LocalDate.of(2026, 11, 4))
        val text = AiReport.build(r)
        listOf("Erholung", "Trainingsbereitschaft", "VO2max", "Ruhepuls", "TRIMP", "Demo-Daten").forEach {
            assertTrue("fehlt: $it", text.contains(it))
        }
        assertTrue(!text.contains("NaN"))
        println(text)
    }

    @Test
    fun missingKeyGivesFriendlyError() {
        val a = ClaudeCoach("").analyze("x", null)
        assertTrue(a is AiAnswer.Failure)
    }
}
