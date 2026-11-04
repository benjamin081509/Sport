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
        item { CoachCard(Coach.sleep(r), title = "Bewertung deines Schlafs") }
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
