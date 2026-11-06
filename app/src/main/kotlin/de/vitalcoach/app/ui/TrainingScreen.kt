package de.vitalcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.Meaning
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.WorkoutAnalysis
import de.vitalcoach.core.scoreToneOf
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

val ZONE_NAMES = listOf("Z0 sehr leicht", "Z1 Regeneration", "Z2 Grundlage", "Z3 Tempo", "Z4 Schwelle", "Z5 Maximal")

@Composable
fun TrainingScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Training", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@LazyColumn
        }
        item { RecommendationCard(r, vm) }
        item { LoadCard(r, vm) }
        item { AcwrCard(r) }
        item { WeeklyLoadCard(r) }
        item { IntensityCard(r) }
        item { PerformanceLinkCard(r, vm) }
        item { CoachCard(Coach.training(r), title = "Coach · Training") }
        item { Text("Letzte Trainings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp)) }
        // Sehr kurze Aktivitäten (oft automatisch erkannte Mini-Radfahrten o. Ä.) ausblenden – sie zählen trotzdem zur Belastung.
        val shown = r.workouts.filter { it.durationMin >= MIN_LIST_MIN }.take(30)
        val oldestShown = shown.lastOrNull()?.workout?.start
        val hidden = r.workouts.count { it.durationMin < MIN_LIST_MIN && (oldestShown == null || !it.workout.start.isBefore(oldestShown)) }
        if (r.workouts.isEmpty()) {
            item { Text("Noch keine Trainings aufgezeichnet.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item { MeaningBox(Meaning.trimpLegend(r.profile.sex)) }
        }
        items(shown.size) { i -> WorkoutRow(shown[i], vm) }
        if (hidden > 0) {
            item {
                Text(
                    "$hidden kurze Aktivitäten unter ${MIN_LIST_MIN.toInt()} min ausgeblendet (z. B. automatisch erkannte Mini-Radfahrten) – sie zählen trotzdem zur Belastung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MIN_LIST_MIN = 10.0

@Composable
private fun RecommendationCard(r: AnalysisResult, vm: MainViewModel) {
    val rec = r.load.recommendation
    val ready = r.todayMetrics?.readiness
    SectionCard(title = "Heute", icon = Icons.Filled.FitnessCenter, onClick = { vm.open(Route.Metric(MetricKey.READINESS)) }) {
        if (rec == null || ready == null) {
            Text("Empfehlung erscheint, sobald Erholungsdaten vorliegen.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val rv = r.energy?.readinessNow ?: ready.value
            ScoreRing(rv, "", scoreToneOf(rv), size = 88.dp, caption = readinessLabel(rv))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.title, style = MaterialTheme.typography.titleLarge, color = rec.tone.color())
                if (ready.value - rv >= 3) {
                    Text("Bereitschaft jetzt $rv (heute früh ${ready.value})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (rec.targetLoadHigh > 0) {
            Spacer(Modifier.height(12.dp))
            val z2 = r.fitness.zones.firstOrNull { it.number == 2 }
            StatRow(
                listOf(
                    Triple("${rec.targetLoadLow}–${rec.targetLoadHigh}", "TRIMP Ziel", null),
                    Triple("${rec.zone2MinLow}–${rec.zone2MinHigh}′", "Zone 2", null),
                    Triple(z2?.paceText?.removeSuffix(" /km") ?: "–", "Pace Z2 /km", Palette.Zones[2]),
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        ZonesCompact(r)
    }
}

@Composable
private fun LoadCard(r: AnalysisResult, vm: MainViewModel) {
    val l = r.load
    val days = r.days.takeLast(90)
    SectionCard(title = "Fitness & Ermüdung", icon = Icons.Filled.Timeline, onClick = { vm.open(Route.Metric(MetricKey.LOAD)) }) {
        StatRow(
            listOf(
                Triple(Fmt.int(l.ctl), "Fitness", Palette.Blue),
                Triple(Fmt.int(l.atl), "Ermüdung", Palette.Orange),
                Triple(Fmt.signed(l.tsb), "Form", if (l.tsb < -20) Palette.Bad else if (l.tsb < -10) Palette.Warn else Palette.Good),
            ),
        )
        Spacer(Modifier.height(12.dp))
        LineChart(
            listOf(Series(days.map { it.ctl }, Palette.Blue, "Fitness"), Series(days.map { it.atl }, Palette.Orange, "Ermüdung")),
            height = 170.dp,
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
        )
        Spacer(Modifier.height(6.dp))
        Legend(listOf("Fitness (42 T)" to Palette.Blue, "Ermüdung (7 T)" to Palette.Orange))
        Spacer(Modifier.height(10.dp))
        MeaningBox(Meaning.form(l))
    }
}

@Composable
private fun AcwrCard(r: AnalysisResult) {
    val l = r.load
    val acwr = l.acwr
    SectionCard(title = "Belastungssteigerung (ACWR)", icon = Icons.Filled.Speed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(acwr?.let { Fmt.num(it, 2) } ?: "–", style = MaterialTheme.typography.displaySmall, color = l.status.tone.color())
            Spacer(Modifier.width(14.dp))
            Column {
                Text(l.status.label, style = MaterialTheme.typography.titleMedium, color = l.status.tone.color())
                Text("ACWR = aktuell ÷ gewohnt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        ScaleBar(
            listOf(
                "< 0,8\nweniger" to Palette.Light,
                "0,8–1,1\ngewohnt" to Palette.Ok,
                "1,1–1,3\nAufbau" to Palette.Good,
                "1,3–1,5\nstark" to Palette.Warn,
                "> 1,5\nSprung" to Palette.Bad,
            ),
            acwr?.let { acwrPosition(it) },
        )
        Spacer(Modifier.height(12.dp))
        StatRow(
            listOf(
                Triple(l.acuteWeek?.let { Fmt.int(it) } ?: "–", "aktuell / Woche", Palette.Teal),
                Triple(l.chronicWeek?.let { Fmt.int(it) } ?: "–", "gewohnt / Woche", Palette.Blue),
                Triple("${l.trainingDays7}/7", "Trainingstage", null),
            ),
        )
        Spacer(Modifier.height(10.dp))
        MeaningBox(Meaning.acwr(l), l.status.tone.color())
    }
}

/** Position auf der 5-teiligen ACWR-Skala (gleich breite Abschnitte mit den Grenzen 0,8 / 1,1 / 1,3 / 1,5). */
private fun acwrPosition(a: Double): Float {
    val bounds = listOf(0.4, 0.8, 1.1, 1.3, 1.5, 1.9)
    val v = a.coerceIn(bounds.first(), bounds.last())
    val i = (0 until 5).firstOrNull { v <= bounds[it + 1] } ?: 4
    val within = (v - bounds[i]) / (bounds[i + 1] - bounds[i])
    return ((i + within) / 5.0).toFloat()
}

@Composable
private fun WeeklyLoadCard(r: AnalysisResult) {
    val weeks = r.days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap().entries.toList().takeLast(12)
    if (weeks.isEmpty()) return
    val loads = weeks.map { e -> e.value.sumOf { it.load } }
    val avg = loads.dropLast(1).takeIf { it.isNotEmpty() }?.average()
    SectionCard(title = "Wochenbelastung · 12 Wochen", icon = Icons.Filled.BarChart) {
        BarChart(
            values = loads,
            colors = loads.mapIndexed { i, v ->
                when {
                    i == loads.lastIndex -> Palette.Teal
                    avg != null && v > avg * 1.3 -> Palette.Orange
                    else -> Palette.Blue
                }
            },
            goal = avg,
            labels = weeks.map { "${it.key.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" },
            height = 140.dp,
        )
        Spacer(Modifier.height(6.dp))
        Legend(listOf("normal" to Palette.Blue, "> 30 % über Ø" to Palette.Orange, "aktuelle Woche" to Palette.Teal))
    }
}

@Composable
private fun IntensityCard(r: AnalysisResult) {
    val l = r.load
    val easy = l.easyPct
    SectionCard(title = "Intensität · 28 Tage", icon = Icons.Filled.DonutLarge) {
        if (easy == null) {
            Text("Noch keine Trainings mit Puls.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(l.zoneMin28.mapIndexed { i, m -> m to Palette.Zones[i] }, size = 130.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.pct(easy), style = MaterialTheme.typography.titleLarge, color = if (easy >= 0.75) Palette.Good else Palette.Warn)
                    Text("locker", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValue("Locker Z0–2", Fmt.pct(easy), if (easy >= 0.75) Palette.Good else Palette.Warn)
                KeyValue("Mittel Z3", Fmt.pct(l.moderatePct ?: 0.0))
                KeyValue("Hart Z4–5", Fmt.pct(l.hardPct ?: 0.0))
                Text("Ziel ≈ 80 / 20", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        val who = l.whoMinutes7
        LabeledBar("WHO-Aktivminuten (7 Tage)", (who / 150.0).toFloat(), "${Fmt.int(who)} / 150", if (who >= 150) Palette.Good else Palette.Warn)
    }
}

@Composable
private fun PerformanceLinkCard(r: AnalysisResult, vm: MainViewModel) {
    val p = r.performance
    SectionCard(title = "Leistungsentwicklung", icon = Icons.Filled.Insights, onClick = { vm.open(Route.Metric(MetricKey.PERFORMANCE)) }) {
        val chg = p.efChangePct
        StatRow(
            listOf(
                Triple(chg?.let { Fmt.signed(it, 1) + " %" } ?: "–", "Tempo bei gleichem Puls", chg?.let { if (it >= 0) Palette.Good else Palette.Warn }),
                Triple(r.fitness.vo2max?.let { Fmt.num(it, 1) } ?: "–", "VO2max", null),
                Triple(p.lthr?.let { "$it" } ?: "–", "Schwelle bpm", null),
            ),
        )
        if (p.efSeries.size >= 3) {
            Spacer(Modifier.height(8.dp))
            Sparkline(p.efSeries.map { it.second }, Palette.Teal, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

/** Kompakte Übersicht: Pulsbereich und Lauf-Pace je Zone. */
@Composable
fun ZonesCompact(r: AnalysisResult) {
    val zones = r.fitness.zones
    if (zones.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        zones.reversed().forEach { z ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Z${z.number}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Zones[z.number],
                    modifier = Modifier.width(28.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.Zones[z.number].copy(alpha = 0.14f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(z.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${z.lowBpm}–${z.highBpm}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(z.paceText ?: "–", style = MaterialTheme.typography.labelLarge, color = Palette.Zones[z.number], modifier = Modifier.width(96.dp), maxLines = 1)
            }
        }
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
            }
            Column(horizontalAlignment = Alignment.End) {
                val lvl = Meaning.sessionLevel(w.trimp)
                Text(Fmt.int(w.trimp), style = MaterialTheme.typography.titleLarge, color = intensityColor(lvl.step))
                Text(lvl.label, style = MaterialTheme.typography.labelMedium, color = intensityColor(lvl.step))
            }
        }
        if (!w.estimated && w.zoneMin.sum() > 0) {
            Spacer(Modifier.height(8.dp))
            StackedBar(w.zoneMin.mapIndexed { i, m -> m to Palette.Zones[i] }, height = 8.dp)
        }
    }
}
