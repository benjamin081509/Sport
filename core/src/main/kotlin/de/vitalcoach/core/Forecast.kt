package de.vitalcoach.core

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Prognose der Trainingsbereitschaft für die nächsten 1–3 Tage (Readiness Forecast).
 *
 * Erholung: autoregressives Modell, das an DEINE Daten angepasst wird:
 *   Erholung(morgen) − Ø = φ · (Erholung(heute) − Ø) + β · relative Belastung(heute) + Zufall
 * (φ = wie schnell du zu deinem Normalwert zurückkehrst, β = wie stark Training deine Erholung drückt).
 * Belastungswerte (Fitness, Ermüdung, ACWR) werden mit dem Banister-Modell für das gewählte Szenario
 * fortgeschrieben. Der Bereich zeigt das 80-%-Intervall (wird mit jedem Tag breiter).
 */
object Forecast {

    fun build(days: List<DayMetrics>): ForecastReport? {
        val n = days.size
        val t = days.lastOrNull() ?: return null
        val rec0 = t.recovery?.value?.toDouble() ?: return null
        val recs = days.map { it.recovery?.value?.toDouble() }
        val mu = Stats.mean(recs.takeLast(30).filterNotNull()) ?: 65.0

        // Modell an persönliche Daten anpassen
        var sxx = 0.0
        var sxz = 0.0
        var szz = 0.0
        var sxy = 0.0
        var szy = 0.0
        val rows = mutableListOf<Triple<Double, Double, Double>>()
        for (i in 2 until n) {
            val y = recs[i] ?: continue
            val x = recs[i - 1] ?: continue
            val z = days[i - 1].load / max(days[i - 2].ctl, 20.0)
            rows += Triple(x - mu, z, y - mu)
        }
        rows.forEach { (x, z, y) ->
            sxx += x * x; sxz += x * z; szz += z * z; sxy += x * y; szy += z * y
        }
        var phi = 0.4
        var beta = -8.0
        var personal = false
        val det = sxx * szz - sxz * sxz
        if (rows.size >= 21 && det > 1e-6) {
            phi = ((szz * sxy - sxz * szy) / det).clamp(0.0, 0.8)
            beta = ((sxx * szy - sxz * sxy) / det).clamp(-25.0, 0.0)
            personal = true
        }
        val resid = rows.map { (x, z, y) -> y - phi * x - beta * z }
        val sigma = if (resid.size >= 10) sqrt(resid.sumOf { it * it } / (resid.size - 2)).clamp(5.0, 20.0) else 10.0

        val trainingDays = days.takeLast(28).map { it.load }.filter { it > 0 }
        val typical = Stats.median(trainingDays) ?: max(t.ctl * 1.4, 50.0)

        val debtComp = t.readiness?.components?.firstOrNull { it.name == "Schlafschuld" }?.score
        val scenarios = ForecastScenario.entries.associateWith { sc ->
            var atl = t.atl
            var ctl = t.ctl
            var acute = t.acuteLoad
            var chronic = t.chronicLoad
            var r = rec0
            var variance = 0.0
            val pts = mutableListOf<ForecastPoint>()
            for (k in 1..3) {
                val load = if (k == 1) max(t.load, sc.loadFactor * typical) else sc.loadFactor * typical
                val ctlPrev = ctl
                atl += Analyzer.K_ATL * (load - atl)
                ctl += Analyzer.K_CTL * (load - ctl)
                acute = Analyzer.L_ACUTE * load + (1 - Analyzer.L_ACUTE) * acute
                chronic = Analyzer.L_CHRONIC * load + (1 - Analyzer.L_CHRONIC) * chronic
                r = (mu + phi * (r - mu) + beta * load / max(ctlPrev, 20.0)).clamp(0.0, 100.0)
                variance = variance * phi * phi + sigma * sigma
                var wsum = 0.5
                var value = 0.5 * r
                if (n >= 21 && chronic > 1.0) {
                    value += 0.2 * Analyzer.acwrScore(acute / chronic)
                    wsum += 0.2
                }
                if (n >= 14) {
                    value += 0.2 * (80.0 + 100.0 * (ctl - atl) / max(ctl, 15.0)).clamp(10.0, 100.0)
                    wsum += 0.2
                }
                if (debtComp != null) {
                    value += 0.1 * debtComp
                    wsum += 0.1
                }
                val expected = value / wsum
                val band = 1.28 * (0.5 / wsum) * sqrt(variance)
                pts += ForecastPoint(
                    t.date.plusDays(k.toLong()),
                    expected.roundToInt().coerceIn(0, 100),
                    (expected - band).roundToInt().coerceIn(0, 100),
                    (expected + band).roundToInt().coerceIn(0, 100),
                )
            }
            pts.toList()
        }
        return ForecastReport(scenarios, personal, sigma, typical)
    }
}
