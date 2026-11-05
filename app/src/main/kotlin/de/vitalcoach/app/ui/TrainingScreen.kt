package de.vitalcoach.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.WorkoutAnalysis

val ZONE_NAMES = listOf("Z0 sehr leicht", "Z1 Regeneration", "Z2 Grundlage", "Z3 Tempo", "Z4 Schwelle", "Z5 Maximal")

@Composable
fun TrainingScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Training", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.", style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }
        item { RecommendationCard(r, vm) }
        item { CoachCard(Coach.training(r), title = "Bewertung deiner Trainingsbelastung", initiallyExpanded = false) }
        item { LoadCard(r, vm) }
        item { AcwrCard(r) }
        item { DailyLoadCard(r) }
        item { IntensityCard(r) }
        item {
            val p = r.performance
            SectionCard(title = "Leistungsentwicklung", icon = Icons.Filled.Insights, onClick = { vm.open(Route.Metric(MetricKey.PERFORMANCE)) }) {
                val chg = p.efChangePct
                Text(
                    when {
                        chg == null -> "Tippe für VO2max, aerobe Effizienz, Laktatschwelle und Pace-Entwicklung."
                        chg >= 2 -> "Bei gleichem Puls läufst du ${Fmt.num(chg, 1)} % schneller als vor 4 Wochen."
                        chg <= -2 -> "Bei gleichem Puls bist du ${Fmt.num(-chg, 1)} % langsamer als vor 4 Wochen."
                        else -> "Deine aerobe Effizienz ist stabil (${Fmt.signed(chg, 1)} %)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                KeyValue("Belastung 7 / 28 / 90 Tage", "${Fmt.int(p.load7)} / ${Fmt.int(p.load28)} / ${Fmt.int(p.load90)} TRIMP")
            }
        }
        item {
            Text("Letzte Trainings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
        if (r.workouts.isEmpty()) {
            item { Text("Noch keine Trainings aufgezeichnet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(minOf(r.workouts.size, 30)) { i -> WorkoutRow(r.workouts[i], vm) }
    }
}

@Composable
private fun RecommendationCard(r: AnalysisResult, vm: MainViewModel) {
    val rec = r.load.recommendation
    val ready = r.todayMetrics?.readiness
    SectionCard(title = "Empfehlung für heute", icon = Icons.Filled.FitnessCenter, onClick = { vm.open(Route.Metric(MetricKey.READINESS)) }) {
        if (rec == null || ready == null) {
            Text("Sobald Erholungsdaten vorliegen, bekommst du hier eine Trainingsempfehlung.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(ready.value, "Bereitschaft", ready.tone, size = 84.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.title, style = MaterialTheme.typography.titleLarge, color = rec.tone.color())
                Spacer(Modifier.height(4.dp))
                Text(rec.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (rec.targetLoadHigh > 0) {
            Spacer(Modifier.height(12.dp))
            KeyValue("Zielbelastung", "${rec.targetLoadLow}–${rec.targetLoadHigh} TRIMP")
            KeyValue("Entspricht ca.", "${rec.zone2MinLow}–${rec.zone2MinHigh} min in Zone 2")
            r.fitness.zones.firstOrNull { it.number == 2 }?.let { z ->
                KeyValue("Deine Zone 2", "${z.lowBpm}–${z.highBpm} bpm")
                z.paceText?.let { KeyValue("Lauf-Pace in Zone 2", it) }
            }
        }
        Spacer(Modifier.height(10.dp))
        ZonesCompact(r)
    }
}

@Composable
private fun LoadCard(r: AnalysisResult, vm: MainViewModel) {
    val l = r.load
    val days = r.days.takeLast(60)
    SectionCard(title = "Fitness, Ermüdung & Form", icon = Icons.Filled.Timeline, onClick = { vm.open(Route.Metric(MetricKey.LOAD)) }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { BigValue(Fmt.int(l.ctl), "", "Fitness (CTL)", Palette.Blue) }
            Column(Modifier.weight(1f)) { BigValue(Fmt.int(l.atl), "", "Ermüdung (ATL)", Palette.Orange) }
            Column(Modifier.weight(1f)) { BigValue(Fmt.signed(l.tsb), "", "Form (TSB)", if (l.tsb < -20) Palette.Warn else Palette.Good) }
        }
        Spacer(Modifier.height(12.dp))
        LineChart(
            listOf(
                Series(days.map { it.ctl }, Palette.Blue, "Fitness"),
                Series(days.map { it.atl }, Palette.Orange, "Ermüdung"),
            ),
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
        )
        Spacer(Modifier.height(8.dp))
        Legend(listOf("Fitness (42 Tage)" to Palette.Blue, "Ermüdung (7 Tage)" to Palette.Orange))
        Spacer(Modifier.height(8.dp))
        Text(
            "Steigt die blaue Linie, wirst du fitter. Liegt Orange deutlich über Blau, bist du ermüdet – dann helfen lockere Tage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AcwrCard(r: AnalysisResult) {
    val l = r.load
    val acwr = l.acwr
    SectionCard(title = "Belastungssteigerung (ACWR)", icon = Icons.Filled.Bolt) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigValue(acwr?.let { Fmt.num(it, 2) } ?: "–", "", l.status.label, l.status.tone.color())
        }
        Spacer(Modifier.height(10.dp))
        // Skala 0,5 – 2,0
        val pos = acwr?.let { ((it - 0.5) / 1.5).toFloat() }
        ScaleBar(
            listOf(
                "< 0,8" to Palette.Light,
                "0,8–1,3" to Palette.Good,
                "1,3–1,5" to Palette.Warn,
                "> 1,5" to Palette.Bad,
            ),
            position = pos,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            l.status.description + " Das ACWR vergleicht deine Belastung der letzten 7 Tage mit den letzten 4 Wochen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val mono = l.monotony
        if (mono != null) {
            Spacer(Modifier.height(8.dp))
            KeyValue("Monotonie (Foster)", "${Fmt.num(mono, 1)}  (ideal < 1,5)", if (mono > 2.0) Palette.Warn else null)
            l.strain?.let { KeyValue("Strain", Fmt.int(it)) }
        }
        KeyValue("Diese Woche / Vorwoche", "${Fmt.int(l.weekLoad)} / ${Fmt.int(l.prevWeekLoad)} TRIMP")
    }
}

@Composable
private fun DailyLoadCard(r: AnalysisResult) {
    val days = r.days.takeLast(14)
    SectionCard(title = "Tagesbelastung (14 Tage)") {
        BarChart(
            values = days.map { it.load },
            colors = days.map { d ->
                when {
                    d.load >= r.load.ctl * 1.8 -> Palette.Bad
                    d.load >= r.load.ctl * 1.2 -> Palette.Orange
                    else -> Palette.Blue
                }
            },
            labels = days.map { Fmt.weekday(it.date).take(2) },
        )
        Spacer(Modifier.height(8.dp))
        Legend(listOf("normal" to Palette.Blue, "fordernd" to Palette.Orange, "sehr hart" to Palette.Bad))
    }
}

@Composable
private fun IntensityCard(r: AnalysisResult) {
    val l = r.load
    val easy = l.easyPct
    SectionCard(title = "Intensität & Aktivminuten", icon = Icons.Filled.Insights) {
        if (easy != null) {
            Text("Verteilung der letzten 28 Tage", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            ZoneBars(l.zoneMin28, ZONE_NAMES)
            Spacer(Modifier.height(10.dp))
            KeyValue("Locker (Z0–Z2)", Fmt.pct(easy), if (easy >= 0.7) Palette.Good else Palette.Warn)
            KeyValue("Mittel (Z3)", Fmt.pct(l.moderatePct ?: 0.0))
            KeyValue("Hart (Z4–Z5)", Fmt.pct(l.hardPct ?: 0.0))
            Text(
                "Empfehlung für Ausdauer: ca. 80 % locker, 20 % mittel bis hart (polarisiertes Training nach Seiler).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text("WHO-Aktivminuten (7 Tage)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (l.whoMinutes7 / 150.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = if (l.whoMinutes7 >= 150) Palette.Good else Palette.Warn,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text("${Fmt.int(l.whoMinutes7)} von 150 Minuten (intensive Minuten zählen doppelt)", style = MaterialTheme.typography.bodySmall)
    }
}

/** Kompakte Übersicht: Pulsbereich und Lauf-Pace je Zone. */
@Composable
fun ZonesCompact(r: AnalysisResult) {
    val zones = r.fitness.zones
    if (zones.isEmpty()) return
    Text("Deine Zonen mit Lauf-Pace", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    zones.forEach { z ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            ToneDotColor(Palette.Zones[z.number])
            Spacer(Modifier.width(8.dp))
            Text("Z${z.number} ${z.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${z.lowBpm}–${z.highBpm} bpm", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(z.paceText ?: "–", style = MaterialTheme.typography.titleSmall, color = Palette.Zones[z.number])
        }
    }
    r.fitness.zonePaceSource?.let {
        Text("Pace $it · gilt für flaches Gelände", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WorkoutRow(w: WorkoutAnalysis, vm: MainViewModel) {
    SectionCard(onClick = null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { vm.open(Route.WorkoutDetail(w.workout.start.epochSecond)) },
        ) {
            Text(w.workout.type.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.workout.title ?: w.workout.type.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val date = w.workout.start.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                Text(
                    "${Fmt.day(date)} · ${Fmt.duration(w.durationMin)}" + (w.workout.distanceMeters?.let { " · ${Fmt.num(it / 1000, 1)} km" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(w.focus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.int(w.trimp), style = MaterialTheme.typography.titleLarge)
                Text(if (w.estimated) "TRIMP (gesch.)" else "TRIMP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!w.estimated && w.zoneMin.sum() > 0) {
            Spacer(Modifier.height(8.dp))
            StackedBar(w.zoneMin.mapIndexed { i, m -> m to Palette.Zones[i] }, height = 8.dp)
        }
    }
}
