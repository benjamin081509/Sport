package de.vitalcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.SleepNight

@Composable
fun SleepScreen(state: UiState, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Schlaf", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@LazyColumn
        }
        item { LastNightCard(r) }
        item { SleepNeedCard(r) }
        item { CoachCard(Coach.sleep(r), title = "Bewertung deines Schlafs") }
        item { NightRecoveryCard(r) }
        item { SleepQualityCard(r) }
        item { SleepHistoryCard(r) }
        item { SleepStatsCard(r) }
    }
}

@Composable
private fun LastNightCard(r: AnalysisResult) {
    val n = r.sleep.lastNight
    SectionCard(title = "Letzte Nacht", icon = Icons.Filled.Bedtime) {
        if (n == null) {
            Text("Für die letzte Nacht liegen keine Schlafdaten vor. Trage die Uhr beim Schlafen und öffne danach Samsung Health, damit synchronisiert wird.")
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(n.score, "Schlaf-Score", n.scoreTone, size = 96.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BigValue(Fmt.duration(n.asleepMin), "", "geschlafen")
                Spacer(Modifier.height(4.dp))
                Text("${Fmt.clock(n.bedtimeMin)} – ${Fmt.clock(n.wakeMin)} Uhr", style = MaterialTheme.typography.bodyMedium)
                val eff = n.efficiency
                if (eff != null) Text("Effizienz ${Fmt.pct(eff)}", style = MaterialTheme.typography.bodyMedium)
                if (n.napMin > 0) Text("+ ${Fmt.duration(n.napMin)} Nickerchen", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (n.hasStages) {
            Spacer(Modifier.height(14.dp))
            StagesBlock(n)
        }
        if (n.components.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("So setzt sich der Score zusammen", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            ScoreBreakdown(de.vitalcoach.core.Score(n.score, n.components, de.vitalcoach.core.Confidence.MEDIUM))
        }
    }
}

@Composable
fun StagesBlock(n: SleepNight) {
    StackedBar(
        listOf(
            n.deepMin to Palette.Deep,
            n.lightMin to Palette.Light,
            n.remMin to Palette.Rem,
            n.awakeMin to Palette.Awake,
        ),
    )
    Spacer(Modifier.height(10.dp))
    val asleep = n.asleepMin.coerceAtLeast(1.0)
    StageRow("Tiefschlaf", n.deepMin, n.deepMin / asleep, "13–23 %", Palette.Deep)
    StageRow("Leichtschlaf", n.lightMin, n.lightMin / asleep, "45–55 %", Palette.Light)
    StageRow("REM-Schlaf", n.remMin, n.remMin / asleep, "20–25 %", Palette.Rem)
    StageRow("Wach", n.awakeMin, null, "< 10 % der Bettzeit", Palette.Awake)
}

@Composable
private fun StageRow(name: String, min: Double, pct: Double?, ref: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ToneDotColor(color)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Fmt.duration(min) + (pct?.let { " · ${Fmt.pct(it)}" } ?: ""), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        Text("($ref)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToneDotColor(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SleepHistoryCard(r: AnalysisResult) {
    val days = r.days.takeLast(14)
    SectionCard(title = "Schlafdauer (14 Nächte)", icon = Icons.Filled.Insights) {
        BarChart(
            values = days.map { it.sleep?.asleepMin?.div(60.0) },
            colors = days.map { (it.sleep?.scoreTone ?: de.vitalcoach.core.Tone.NEUTRAL).color() },
            goal = r.sleep.goalHours,
            labels = days.map { Fmt.weekday(it.date).take(2) },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Gestrichelte Linie = dein Schlafziel (${Fmt.hours(r.sleep.goalHours)}). Farbe = Schlaf-Score.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("Schlaf-Score (30 Nächte)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        val d30 = r.days.takeLast(30)
        LineChart(
            listOf(Series(d30.map { it.sleep?.score?.toDouble() }, Palette.Purple, "Score")),
            height = 110.dp,
            startLabel = d30.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
        )
    }
}

@Composable
private fun SleepNeedCard(r: AnalysisResult) {
    val s = r.sleep
    SectionCard(title = "Schlafbedarf heute Nacht", icon = Icons.Filled.Bedtime) {
        val need = s.needTonightMin
        if (need == null) {
            Text("Wird berechnet, sobald Schlafdaten vorliegen.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { BigValue(Fmt.duration(need), "", "empfohlene Schlafdauer") }
            val bed = s.recommendedBedtimeMin
            if (bed != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.clock(bed), style = MaterialTheme.typography.headlineMedium, color = Palette.Purple)
                    Text("Uhr ins Bett", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        KeyValue("Basis (dein Schlafziel)", Fmt.hours(s.goalHours))
        s.needReasons.forEach { Bullet(it) }
        if (s.needReasons.isEmpty()) Text("Keine Zuschläge – Belastung und Schlafkonto sind ausgeglichen.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        val pn = s.personalNeedH
        if (pn != null) {
            KeyValue("Dein persönlicher Schlafbedarf (geschätzt)", Fmt.hours(pn))
            Text(
                "Median deiner Schlafdauer in den ${s.personalNeedNights} Nächten, nach denen HRV und Ruhepuls am besten waren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Persönlicher Schlafbedarf: wird nach ca. 3 Wochen mit Schlaf- und HRV/Ruhepuls-Daten geschätzt.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        de.vitalcoach.app.ui.ProvenanceChip(de.vitalcoach.core.Provenance.ESTIMATED)
    }
}

@Composable
private fun NightRecoveryCard(r: AnalysisResult) {
    val s = r.sleep
    val h = r.heart
    SectionCard(title = "Erholung während der Nacht") {
        val nr = s.nightRecovery
        if (nr != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(nr.value, "Nächtliche Erholung", nr.tone, size = 88.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    s.nightRestingHr?.let { KeyValue("Nächtlicher Ruhepuls", "${Fmt.int(it)} bpm") }
                    s.nightHrv?.let { KeyValue("Nächtliche HRV", "${Fmt.int(it)} ms") }
                    h.dipPct?.let { KeyValue("Pulsabsenkung", "${Fmt.int(it)} %") }
                }
            }
            Spacer(Modifier.height(10.dp))
            ScoreBreakdown(nr)
        } else {
            Text("Für die nächtliche Erholung braucht es Pulswerte während des Schlafs.", style = MaterialTheme.typography.bodyMedium)
        }
        if (h.nightCurve.size >= 3) {
            Spacer(Modifier.height(10.dp))
            Text("Puls in der letzten Nacht", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            LineChart(
                listOf(Series(h.nightCurve.map { it.second }, Palette.Bad, "Puls")),
                height = 120.dp,
                startLabel = h.nightCurve.first().first.atZone(r.zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
                endLabel = h.nightCurve.last().first.atZone(r.zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
            )
        }
    }
}

@Composable
private fun SleepQualityCard(r: AnalysisResult) {
    val s = r.sleep
    val n = s.lastNight
    SectionCard(title = "Schlafqualität im Detail") {
        if (n != null) {
            KeyValue("Tatsächlicher Schlaf / Bettzeit", "${Fmt.duration(n.asleepMin)} / ${Fmt.duration(n.inBedMin)}")
            val eff = n.efficiency
            if (eff != null) KeyValue("Schlafeffizienz", Fmt.pct(eff), if (eff >= 0.85) Palette.Good else Palette.Warn)
            n.deepPct?.let { KeyValue("Tiefschlafanteil", Fmt.pct(it) + (s.avgDeepPct?.let { a -> " (Ø ${Fmt.pct(a)})" } ?: "")) }
            n.remPct?.let { KeyValue("REM-Anteil", Fmt.pct(it) + (s.avgRemPct?.let { a -> " (Ø ${Fmt.pct(a)})" } ?: "")) }
        }
        val sri = s.sri
        if (sri != null) {
            Spacer(Modifier.height(8.dp))
            Text("Schlaf-Konsistenz-Score (SRI)", style = MaterialTheme.typography.titleSmall)
            LabeledBar(
                if (sri >= 80) "regelmäßig" else if (sri >= 70) "mittel" else "unregelmäßig",
                (sri / 100).toFloat(), Fmt.int(sri), if (sri >= 80) Palette.Good else if (sri >= 70) Palette.Warn else Palette.Bad,
            )
            Text(
                "Wahrscheinlichkeit, zur gleichen Uhrzeit an aufeinanderfolgenden Tagen im selben Zustand (wach/schlafend) zu sein (Phillips et al. 2017). 100 = jeden Tag exakt gleich.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        val d7 = s.debt7H
        if (d7 != null) KeyValue("Schlafdefizit 7 Tage", "${Fmt.num(d7, 1)} h", if (d7 >= 3) Palette.Warn else Palette.Good)
        val d14 = s.debt14H
        if (d14 != null) KeyValue("Schlafschuld 14 Tage", "${Fmt.num(d14, 1)} h", if (d14 >= 6) Palette.Warn else Palette.Good)
        s.regularitySdMin?.let { KeyValue("Schwankung der Schlafmitte", "± ${Fmt.int(it)} min") }
    }
}

@Composable
private fun SleepStatsCard(r: AnalysisResult) {
    val s = r.sleep
    SectionCard(title = "Deine Durchschnittswerte") {
        s.avgAsleep7Min?.let { KeyValue("Ø Schlaf (7 Nächte)", Fmt.duration(it)) }
        s.avgAsleep30Min?.let { KeyValue("Ø Schlaf (30 Nächte)", Fmt.duration(it)) }
        val debt = s.debt7H
        if (debt != null) KeyValue("Schlafschuld (7 Nächte)", "${Fmt.num(debt, 1)} h", if (debt >= 3) Palette.Warn else Palette.Good)
        val reg = s.regularitySdMin
        if (reg != null) KeyValue("Regelmäßigkeit", "± ${Fmt.int(reg)} min", if (reg > 60) Palette.Warn else Palette.Good)
        s.avgBedtimeMin?.let { KeyValue("Ø Zubettgehen", Fmt.clock(it) + " Uhr") }
        s.avgWakeMin?.let { KeyValue("Ø Aufstehen", Fmt.clock(it) + " Uhr") }
        s.avgDeepPct?.let { KeyValue("Ø Tiefschlaf", Fmt.pct(it)) }
        s.avgRemPct?.let { KeyValue("Ø REM-Schlaf", Fmt.pct(it)) }
        s.avgEfficiency?.let { KeyValue("Ø Effizienz", Fmt.pct(it)) }
        KeyValue("Erfasste Nächte (30 Tage)", "${s.nights}")
    }
}
