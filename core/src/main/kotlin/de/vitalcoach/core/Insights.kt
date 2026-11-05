package de.vitalcoach.core

import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Persönliche Auswertungen:
 *  - Normalwerte-Analyse (Personal Baseline Analyzer)
 *  - Zusammenhangs-Analyse (Correlation Analyzer) – Pearson-Korrelation mit Signifikanztest (Fisher-z)
 *  - Tages-Briefing (Daily Health Intelligence)
 *  - Langzeit-Trends
 * Alle Aussagen sind statistische Beobachtungen in DEINEN Daten – keine medizinischen Diagnosen.
 */
object Insights {

    private data class Metric(
        val name: String,
        val unit: String,
        val decimals: Int,
        val minSd: Double,
        val higherIsBetter: Boolean?,
        val provenance: Provenance,
        val value: (DayMetrics) -> Double?,
    )

    private val METRICS = listOf(
        Metric("Ruhepuls", "bpm", 1, 1.5, false, Provenance.CALCULATED) { it.restingHr },
        Metric("HRV", "ms", 0, 2.0, true, Provenance.MEASURED) { it.hrv },
        Metric("Atemfrequenz", "/min", 1, 0.4, null, Provenance.MEASURED) { it.respRate },
        Metric("Sauerstoffsättigung", "%", 1, 0.5, true, Provenance.MEASURED) { it.spo2 },
        Metric("Schlafdauer", "h", 1, 0.3, true, Provenance.MEASURED) { d -> d.sleep?.asleepMin?.div(60.0) },
        Metric("Schlaf-Score", "", 0, 4.0, true, Provenance.CALCULATED) { d -> d.sleep?.score?.toDouble() },
        Metric("Tiefschlaf", "%", 0, 2.0, true, Provenance.MEASURED) { d -> d.sleep?.deepPct?.times(100) },
        Metric("REM-Schlaf", "%", 0, 2.0, true, Provenance.MEASURED) { d -> d.sleep?.remPct?.times(100) },
        Metric("Schlafeffizienz", "%", 0, 1.5, true, Provenance.CALCULATED) { d -> d.sleep?.efficiency?.times(100) },
        Metric("Ø Herzfrequenz (24 h)", "bpm", 0, 1.5, false, Provenance.CALCULATED) { it.avgHr },
        Metric("Nächtliche Pulsabsenkung", "%", 0, 2.0, true, Provenance.CALCULATED) { it.hrDipPct },
        Metric("Stress-Index", "", 0, 4.0, false, Provenance.ESTIMATED) { it.stressIndex },
        Metric("Schritte", "", 0, 800.0, true, Provenance.MEASURED) { d -> d.steps?.toDouble() },
        Metric("Aktive Kalorien", "kcal", 0, 60.0, true, Provenance.MEASURED) { it.activeKcal },
        Metric("Inaktive Stunden", "h", 0, 1.0, false, Provenance.CALCULATED) { d -> d.inactiveHours?.toDouble() },
        Metric("Erholung", "", 0, 5.0, true, Provenance.CALCULATED) { d -> d.recovery?.value?.toDouble() },
    )

    fun baselines(r: AnalysisResult): List<BaselineItem> {
        val days = r.days
        if (days.isEmpty()) return emptyList()
        val lastIdx = days.size - 1
        // Aktivitätswerte von heute sind unvollständig → gestern verwenden
        val activityNames = setOf("Schritte", "Aktive Kalorien", "Inaktive Stunden", "Ø Herzfrequenz (24 h)", "Stress-Index")
        return METRICS.map { m ->
            val idx = if (m.name in activityNames && lastIdx > 0) lastIdx - 1 else lastIdx
            val current = m.value(days[idx]) ?: if (idx > 0) m.value(days[idx - 1]) else null
            val hist30 = days.subList(maxOf(0, idx - 30), idx).mapNotNull(m.value)
            val hist90 = days.subList(maxOf(0, idx - 90), idx).mapNotNull(m.value)
            val mean = Stats.mean(hist30)
            val sd = Stats.sd(hist30)?.let { max(it, m.minSd) }
            val z = if (current != null && mean != null && sd != null && hist30.size >= 5) (current - mean) / sd else null
            val status = when {
                z == null -> BaselineStatus.NO_DATA
                z >= 2 -> BaselineStatus.HIGH
                z >= 1 -> BaselineStatus.SLIGHT_HIGH
                z <= -2 -> BaselineStatus.LOW
                z <= -1 -> BaselineStatus.SLIGHT_LOW
                else -> BaselineStatus.NORMAL
            }
            val tone = when {
                z == null -> Tone.NEUTRAL
                abs(z) < 1 -> Tone.GOOD
                m.higherIsBetter == null -> if (abs(z) >= 2) Tone.WARN else Tone.OK
                (z > 0) == m.higherIsBetter -> Tone.GOOD
                abs(z) >= 2 -> Tone.BAD
                else -> Tone.WARN
            }
            BaselineItem(m.name, m.unit, m.decimals, current, mean, sd, Stats.mean(hist90), z, status, tone, m.provenance)
        }
    }

    // ------------------------------------------------------------------ Zusammenhänge

    private fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size < 3) return null
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in xs.indices) {
            sxy += (xs[i] - mx) * (ys[i] - my)
            sxx += (xs[i] - mx) * (xs[i] - mx)
            syy += (ys[i] - my) * (ys[i] - my)
        }
        if (sxx <= 0 || syy <= 0) return null
        return sxy / sqrt(sxx * syy)
    }

    /** Normalverteilung, Abramowitz & Stegun 7.1.26. */
    private fun phi(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * abs(x) / sqrt(2.0))
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x / 2.0)
        return if (x >= 0) 0.5 * (1 + y) else 0.5 * (1 - y)
    }

    private fun pValue(r: Double, n: Int): Double {
        if (n < 4) return 1.0
        val z = 0.5 * ln((1 + r.clamp(-0.999, 0.999)) / (1 - r.clamp(-0.999, 0.999))) * sqrt((n - 3).toDouble())
        return 2 * (1 - phi(abs(z)))
    }

    private fun strength(r: Double) = when {
        abs(r) < 0.1 -> "kein"
        abs(r) < 0.3 -> "schwacher"
        abs(r) < 0.5 -> "mittlerer"
        else -> "starker"
    }

    private fun pairCorrelation(
        title: String,
        pairs: List<Pair<Double, Double>>,
        xLabel: String,
        yLabel: String,
        yUnit: String,
        yDecimals: Int,
        relative: Boolean,
    ): CorrelationInsight {
        val n = pairs.size
        if (n < 14) {
            return CorrelationInsight(title, "Noch zu wenige Daten (${n} von mind. 14 Tagen).", null, n, null, "", false)
        }
        val r = pearson(pairs.map { it.first }, pairs.map { it.second }) ?: return CorrelationInsight(title, "Keine Schwankung in den Daten – kein Zusammenhang berechenbar.", null, n, null, "", false)
        val p = pValue(r, n)
        val sorted = pairs.sortedBy { it.first }
        val k = maxOf(3, n / 3)
        val low = sorted.take(k)
        val high = sorted.takeLast(k)
        val yLow = low.map { it.second }.average()
        val yHigh = high.map { it.second }.average()
        val diffText = if (relative && yLow != 0.0) {
            "${Fmt.signed((yHigh / yLow - 1) * 100)} %"
        } else {
            "${Fmt.signed(yHigh - yLow, yDecimals)}${if (yUnit.isNotEmpty()) " $yUnit" else ""}"
        }
        val significant = p < 0.1 && abs(r) >= 0.2
        val pts = pairs.takeLast(120)
        val text = if (significant) {
            "Bei dir besteht ein ${strength(r)} Zusammenhang (r = ${Fmt.num(r, 2)}, $n Tage): An Tagen mit hohem Wert für „$xLabel“ " +
                "war „$yLabel“ im Schnitt $diffText gegenüber Tagen mit niedrigem Wert."
        } else {
            "In deinen Daten ist bisher kein klarer Zusammenhang erkennbar (r = ${Fmt.num(r, 2)}, $n Tage). Unterschied hohe vs. niedrige Tage: $diffText."
        }
        return CorrelationInsight(title, text, r, n, p, strength(r), significant, pts, xLabel, yLabel)
    }

    fun correlations(r: AnalysisResult): List<CorrelationInsight> {
        val d = r.days
        val out = mutableListOf<CorrelationInsight>()
        fun next(f: (DayMetrics) -> Double?, g: (DayMetrics) -> Double?): List<Pair<Double, Double>> =
            (0 until d.size - 1).mapNotNull { i -> val x = f(d[i]); val y = g(d[i + 1]); if (x != null && y != null) x to y else null }
        fun same(f: (DayMetrics) -> Double?, g: (DayMetrics) -> Double?): List<Pair<Double, Double>> =
            d.mapNotNull { day -> val x = f(day); val y = g(day); if (x != null && y != null) x to y else null }
        val lastDays = d.takeLast(120)
        fun lastNext(f: (DayMetrics) -> Double?, g: (DayMetrics) -> Double?) =
            (0 until lastDays.size - 1).mapNotNull { i -> val x = f(lastDays[i]); val y = g(lastDays[i + 1]); if (x != null && y != null) x to y else null }

        out += pairCorrelation(
            "Schlafdauer → HRV", same({ it.sleep?.asleepMin?.div(60.0) }, { it.hrv }).takeLast(120),
            "Schlafdauer", "nächtliche HRV", "ms", 0, true,
        )
        out += pairCorrelation(
            "Trainingsbelastung → HRV der Folgenacht", lastNext({ it.load.takeIf { l -> l > 0 } }, { it.hrv }),
            "Trainingsbelastung", "HRV in der Nacht danach", "ms", 0, true,
        )
        out += pairCorrelation(
            "Trainingsbelastung → Ruhepuls am Folgetag", lastNext({ it.load.takeIf { l -> l > 0 } }, { it.restingHr }),
            "Trainingsbelastung", "Ruhepuls am nächsten Morgen", "bpm", 1, false,
        )
        out += pairCorrelation(
            "Stress → Schlafqualität", lastNext({ it.stressIndex }, { it.sleep?.score?.toDouble() }),
            "Stress-Index am Tag", "Schlaf-Score der Nacht danach", "Punkte", 0, false,
        )
        out += pairCorrelation(
            "Schritte → Schlafqualität", lastNext({ it.steps?.toDouble() }, { it.sleep?.score?.toDouble() }),
            "Schritte", "Schlaf-Score der Nacht danach", "Punkte", 0, false,
        )
        out += pairCorrelation(
            "Inaktive Zeit → Ruhepuls", lastNext({ it.inactiveHours?.toDouble() }, { it.restingHr }),
            "inaktive Stunden", "Ruhepuls am nächsten Morgen", "bpm", 1, false,
        )

        // Späte Trainings → Schlaf
        val late = (0 until lastDays.size - 1).filter { lastDays[it].lateWorkout }.mapNotNull { lastDays[it + 1].sleep?.score?.toDouble() }
        val notLate = (0 until lastDays.size - 1).filter { !lastDays[it].lateWorkout && lastDays[it].workoutCount > 0 }
            .mapNotNull { lastDays[it + 1].sleep?.score?.toDouble() }
        out += if (late.size >= 4 && notLate.size >= 4) {
            val diff = late.average() - notLate.average()
            CorrelationInsight(
                "Spätes Training → Schlaf",
                "Nach Trainings, die nach 20 Uhr endeten, lag dein Schlaf-Score im Schnitt bei ${Fmt.int(late.average())} – " +
                    "nach früheren Trainings bei ${Fmt.int(notLate.average())} (${Fmt.signed(diff)} Punkte; ${late.size} vs. ${notLate.size} Tage).",
                null, late.size + notLate.size, null, "", abs(diff) >= 5,
            )
        } else {
            CorrelationInsight("Spätes Training → Schlaf", "Noch zu wenige späte Trainings (nach 20 Uhr) für einen Vergleich.", null, late.size, null, "", false)
        }

        // Hohe Belastung + hoher Stress → HRV
        val idxs = (0 until lastDays.size - 1)
        val loadsT = lastDays.map { it.load }.filter { it > 0 }.sorted()
        val stressT = lastDays.mapNotNull { it.stressIndex }.sorted()
        if (loadsT.size >= 9 && stressT.size >= 9) {
            val lCut = loadsT[loadsT.size * 2 / 3]
            val sCut = stressT[stressT.size * 2 / 3]
            val both = idxs.filter { lastDays[it].load >= lCut && (lastDays[it].stressIndex ?: -1.0) >= sCut }.mapNotNull { lastDays[it + 1].hrv }
            val other = idxs.filter { !(lastDays[it].load >= lCut && (lastDays[it].stressIndex ?: -1.0) >= sCut) }.mapNotNull { lastDays[it + 1].hrv }
            out += if (both.size >= 3 && other.size >= 10) {
                val pct = (both.average() / other.average() - 1) * 100
                CorrelationInsight(
                    "Belastung + Stress → HRV",
                    "An Tagen mit hoher Trainingsbelastung UND hohem Stress lag deine nächtliche HRV danach im Schnitt ${Fmt.signed(pct)} % " +
                        "gegenüber anderen Tagen (${both.size} vs. ${other.size} Tage).",
                    null, both.size + other.size, null, "", abs(pct) >= 5,
                )
            } else {
                CorrelationInsight("Belastung + Stress → HRV", "Noch zu wenige Tage mit gleichzeitig hoher Belastung und hohem Stress (${both.size}).", null, both.size, null, "", false)
            }
        }
        return out
    }

    // ------------------------------------------------------------------ Tages-Briefing

    fun briefing(r: AnalysisResult): Briefing {
        val t = r.todayMetrics ?: return Briefing("Noch keine Daten", emptyList(), emptyList(), emptyList(), Tone.NEUTRAL)
        val y = r.days.getOrNull(r.days.size - 2)
        val rec = t.recovery
        val ready = t.readiness
        val headline = when {
            t.illnessFlag -> "Dein Körper zeigt Warnzeichen – heute lieber Ruhe."
            rec == null -> "Noch nicht genug Daten für eine Tagesbewertung."
            ready != null && ready.value >= 75 -> "Gut erholt – heute ist ein guter Tag für eine fordernde Einheit."
            ready != null && ready.value >= 60 -> "Solide erholt – moderates Training passt heute."
            ready != null && ready.value >= 45 -> "Nur teilweise erholt – heute eher locker."
            else -> "Wenig erholt – gönn deinem Körper heute Ruhe."
        }
        val changes = r.baselines.filter { it.z != null && abs(it.z) >= 1.0 && it.current != null && it.mean30 != null }
            .sortedByDescending { abs(it.z!!) }
            .take(4)
            .map { b ->
                val diff = b.current!! - b.mean30!!
                val unit = if (b.unit.isNotEmpty()) " ${b.unit}" else ""
                "${b.name}: ${Fmt.num(b.current, b.decimals)}$unit – ${Fmt.signed(diff, b.decimals)}$unit zu deinem Normalwert (${b.status.label})"
            }
        val negative = r.baselines.any { (it.tone == Tone.WARN || it.tone == Tone.BAD) && it.name in setOf("Ruhepuls", "HRV", "Schlaf-Score", "Erholung") }
        val explanations = mutableListOf<String>()
        if (y != null) {
            if (y.lateWorkout) explanations += "Gestern endete ein Training erst nach 20 Uhr – späte Belastung kann Puls und HRV in der Nacht beeinflussen."
            if (y.load >= 1.5 * max(y.ctl, 30.0)) explanations += "Gestern hattest du eine sehr fordernde Einheit (${Fmt.int(y.load)} TRIMP)."
            y.stressIndex?.let { if (it >= 50) explanations += "Dein Stress-Index war gestern erhöht (${Fmt.int(it)})." }
            y.inactiveHours?.let { if (it >= 10) explanations += "Gestern warst du $it Wachstunden kaum in Bewegung." }
        }
        t.sleep?.let { n ->
            val goalMin = r.profile.sleepGoalHours * 60
            if (n.asleepMin < goalMin - 45) explanations += "Letzte Nacht hast du nur ${Fmt.duration(n.asleepMin)} geschlafen (Ziel ${Fmt.hours(r.profile.sleepGoalHours)})."
            val usual = r.sleep.avgBedtimeMin
            if (usual != null && n.bedtimeMin - usual >= 60) explanations += "Du bist ${Fmt.int(n.bedtimeMin - usual)} min später als üblich ins Bett gegangen."
        }
        val resp = t.respRate
        val rb = t.respBaseline
        if (resp != null && rb != null && resp - rb.mean >= 1.0) explanations += "Deine Atemfrequenz im Schlaf war erhöht – mögliches Zeichen für einen beginnenden Infekt."
        if (!negative && explanations.isEmpty() && rec != null && rec.value >= 67) {
            explanations += "Deine Werte liegen im grünen Bereich – Schlaf und Belastung waren gut ausbalanciert."
        }
        val todo = mutableListOf<String>()
        r.load.recommendation?.let { rc ->
            todo += if (rc.targetLoadHigh > 0) "${rc.title}: ca. ${rc.zone2MinLow}–${rc.zone2MinHigh} min in Zone 2 bzw. ${rc.targetLoadLow}–${rc.targetLoadHigh} TRIMP." else rc.title + "."
        }
        val reg = r.regeneration
        if (reg.remainingHours >= 4) todo += "Noch ca. ${Fmt.int(reg.remainingHours)} h Regeneration nach deinem letzten Training."
        r.sleep.needTonightMin?.let { need ->
            val bed = r.sleep.recommendedBedtimeMin
            todo += "Schlafbedarf heute Nacht: ${Fmt.duration(need)}" + (bed?.let { " – am besten gegen ${Fmt.clock(it)} Uhr ins Bett." } ?: ".")
        }
        r.activity.inactiveHours7Avg?.let { if (it >= 8) todo += "Steh öfter auf: Ziel ist mindestens 250 Schritte in jeder Wachstunde." }
        return Briefing(headline, changes, explanations, todo, ready?.tone ?: rec?.tone ?: Tone.NEUTRAL)
    }

    // ------------------------------------------------------------------ Langzeit-Trends

    fun trends(r: AnalysisResult): List<TrendItem> {
        val days = r.days
        fun item(
            name: String,
            unit: String,
            decimals: Int,
            higherIsBetter: Boolean?,
            sumWeekly: Boolean = false,
            minPerWeek: Int = 2,
            f: (DayMetrics) -> Double?,
        ): TrendItem {
            val weekly = days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                .toSortedMap()
                .mapNotNull { (wk, list) ->
                    val v = list.mapNotNull(f)
                    when {
                        sumWeekly && list.size >= 4 -> wk to v.sum()
                        !sumWeekly && v.size >= minPerWeek -> wk to v.average()
                        else -> null
                    }
                }
                .takeLast(52)
            fun avg(fromAgo: Int, toAgo: Int): Double? {
                val vals = days.filter { ChronoUnit.DAYS.between(it.date, r.today) in fromAgo.toLong()..toAgo.toLong() }.mapNotNull(f)
                return if (vals.size >= 5) vals.average() else null
            }
            val l30 = avg(0, 29)
            val p30 = avg(30, 59)
            val l90 = avg(0, 89)
            val p90 = avg(90, 179)
            val y1 = avg(335, 365)
            val u = if (unit.isNotEmpty()) " $unit" else ""
            val parts = mutableListOf<String>()
            if (l30 != null) parts += "Ø 30 Tage: ${Fmt.num(l30, decimals)}$u" + (p30?.let { " (davor ${Fmt.num(it, decimals)}, ${Fmt.signed(l30 - it, decimals)})" } ?: "")
            if (l90 != null && p90 != null) parts += "90 Tage: ${Fmt.signed(l90 - p90, decimals)}$u gegenüber dem Vorquartal"
            if (l30 != null && y1 != null) parts += "vor einem Jahr: ${Fmt.num(y1, decimals)}$u"
            val change = if (l30 != null && p30 != null) l30 - p30 else null
            val tone = when {
                change == null || higherIsBetter == null -> Tone.NEUTRAL
                abs(change) < 1e-9 -> Tone.NEUTRAL
                (change > 0) == higherIsBetter -> Tone.GOOD
                else -> Tone.WARN
            }
            return TrendItem(name, unit, decimals, weekly, l30, p30, l90, p90, y1, higherIsBetter, parts.joinToString(" · ").ifEmpty { "Noch zu wenige Daten." }, tone)
        }
        return listOf(
            item("Ruhepuls", "bpm", 1, false) { it.restingHr },
            item("HRV", "ms", 0, true) { it.hrv },
            item("Persönlicher Erholungswert", "", 0, true) { d -> d.recovery?.value?.toDouble() },
            item("Schlafdauer", "h", 1, true) { d -> d.sleep?.asleepMin?.div(60.0) },
            item("Schlaf-Score", "", 0, true) { d -> d.sleep?.score?.toDouble() },
            item("Fitness (CTL)", "", 0, true) { it.ctl },
            item("Trainingsbelastung pro Woche", "TRIMP", 0, null, sumWeekly = true) { it.load },
            item("Schritte", "", 0, true) { d -> d.steps?.toDouble() },
            item("Stress-Index", "", 0, false) { it.stressIndex },
            item("Gewicht", "kg", 1, null, minPerWeek = 1) { it.weight },
        )
    }
}
