package de.vitalcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
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
import de.vitalcoach.core.ForecastScenario
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.Meaning
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Tone
import de.vitalcoach.core.forecast
import de.vitalcoach.core.regeneration
import de.vitalcoach.core.scoreToneOf
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ToneScale = listOf(Palette.Bad, Palette.Warn, Palette.Ok, Palette.Good)

@Composable
fun RecoveryScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Erholung", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@LazyColumn
        }
        recoveryItems(r, vm)
    }
}

/** Alle Erholungs-Karten – auch für die Detailseite „Regeneration“. */
fun LazyListScope.recoveryItems(r: AnalysisResult, vm: MainViewModel) {
    item { PersonalRecoveryCard(r, vm) }
    item { RecoveryHeatmapCard(r) }
    item { RegenerationCard(r) }
    item { FatigueBalanceCard(r) }
    item { OverloadCard(r) }
    item { HrvTrendCard(r, vm) }
    item { RhrDeviationCard(r, vm) }
    item { ForecastCard(r, vm) }
    item { CoachCard(Coach.regeneration(r), title = "Coach · Regeneration") }
}

@Composable
private fun PersonalRecoveryCard(r: AnalysisResult, vm: MainViewModel) {
    val t = r.todayMetrics
    val rec = t?.recovery
    val ready = t?.readiness
    SectionCard(title = "Persönlicher Erholungswert", icon = Icons.Filled.BatteryChargingFull, onClick = { vm.open(Route.Metric(MetricKey.RECOVERY)) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SemiGauge(
                rec?.value?.let { it / 100f }, ToneScale,
                rec?.value?.toString() ?: "–", rec?.value?.let { recoveryLabel(it) } ?: "Erholung",
                width = 190.dp,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(ready?.value, "Bereitschaft", ready?.tone ?: Tone.NEUTRAL, size = 84.dp, caption = ready?.value?.let { readinessLabel(it) }) {
                    vm.open(Route.Metric(MetricKey.READINESS))
                }
            }
        }
        if (rec != null) {
            Spacer(Modifier.height(10.dp))
            ScoreBreakdown(rec)
        }
    }
}

@Composable
private fun RecoveryHeatmapCard(r: AnalysisResult) {
    val vals = r.days.takeLast(84).map { it.date to it.recovery?.value }
    if (vals.none { it.second != null }) return
    SectionCard(title = "Erholung · 12 Wochen", icon = Icons.Filled.CalendarMonth) {
        CalendarHeatmap(vals, name = "Erholung")
    }
}

@Composable
fun RegenerationCard(r: AnalysisResult) {
    val g = r.regeneration
    SectionCard(title = "Regeneration nach Training", icon = Icons.Filled.Timeline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val idx = g.regenerationIndex
            ScoreRing(idx, "", scoreToneOf(idx ?: 0).takeIf { idx != null } ?: Tone.NEUTRAL, size = 96.dp, caption = "%")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (g.remainingHours >= 1) {
                    BigValue(Fmt.int(g.remainingHours), "h", "bis voll erholt")
                    val ready = g.readyAt
                    if (ready != null) {
                        Text(
                            ready.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.GERMANY)) + " Uhr",
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.Warn,
                        )
                    }
                } else {
                    BigValue("✓", "", "keine offene Erholung", Palette.Good)
                }
            }
        }
        if (g.recent.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            g.recent.take(5).forEach { info ->
                val w = info.workout.workout
                val done = (1.0 - info.remainingH / info.hours).toFloat()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.type.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${w.title ?: w.type.label} · ${Fmt.dayShort(w.start.atZone(ZoneId.systemDefault()).toLocalDate())}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(
                                Modifier.fillMaxWidth(done.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (info.remainingH > 0) Palette.Warn else Palette.Good),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (info.remainingH > 0) "${Fmt.int(info.remainingH)} h" else "✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (info.remainingH > 0) Palette.Warn else Palette.Good,
                        modifier = Modifier.width(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FatigueBalanceCard(r: AnalysisResult) {
    val g = r.regeneration
    SectionCard(title = "Ermüdung & Balance", icon = Icons.Filled.Speed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val fat = g.fatigueIndex
                SemiGauge(
                    fat?.let { it / 100f }, listOf(Palette.Light, Palette.Good, Palette.Warn, Palette.Bad),
                    fat?.toString() ?: "–", "Ermüdung", width = 150.dp,
                )
                Text(g.fatigueLabel.substringBefore(" –"), style = MaterialTheme.typography.labelMedium, color = g.fatigueTone.color())
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bal = g.balance
                SemiGauge(
                    bal?.let { ((it - 0.5) / 1.5).toFloat() }, listOf(Palette.Light, Palette.Good, Palette.Warn, Palette.Bad),
                    bal?.let { Fmt.num(it, 2) } ?: "–", "Belastung : Erholung", width = 150.dp,
                )
                Text(g.balanceLabel, style = MaterialTheme.typography.labelMedium, color = g.balanceTone.color())
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Trainingspunkte (TRIMP)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        WeekCompareBars(r.load)
        Spacer(Modifier.height(8.dp))
        MeaningBox(Meaning.weeks(r.load))
    }
}

@Composable
fun OverloadCard(r: AnalysisResult) {
    val g = r.regeneration
    SectionCard(title = "Überlastungsindikator", icon = Icons.Filled.Warning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneDot(g.overloadTone, 14.dp)
            Spacer(Modifier.width(10.dp))
            Text(g.overloadLabel, style = MaterialTheme.typography.titleMedium, color = g.overloadTone.color())
        }
        Spacer(Modifier.height(10.dp))
        val checks = g.overloadChecks
        checks.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, active) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background((if (active) Palette.Bad else Palette.Good).copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (active) Palette.Bad else Palette.Good))
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HrvTrendCard(r: AnalysisResult, vm: MainViewModel) {
    val g = r.regeneration
    val f = r.fitness
    val days = r.days.takeLast(60)
    SectionCard(title = "HRV-Trend", icon = Icons.Filled.MonitorHeart, onClick = { vm.open(Route.Metric(MetricKey.HRV)) }) {
        if (!r.quality.hasHrv) {
            Text("Keine HRV über Health Connect verfügbar.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val tr = g.hrvTrendPct
        StatRow(
            listOf(
                Triple(f.hrvNow?.let { Fmt.int(it) } ?: "–", "ms Ø 7 T", null),
                Triple(f.hrvBaseline?.let { Fmt.int(it) } ?: "–", "ms Ø 30 T", null),
                Triple(tr?.let { Fmt.signed(it) + " %" } ?: "–", "Trend", if ((tr ?: 0.0) >= -5) Palette.Good else Palette.Warn),
            ),
        )
        Spacer(Modifier.height(10.dp))
        val lo = f.hrvNormalLow
        val hi = f.hrvNormalHigh
        LineChart(
            listOf(Series(days.map { it.hrv }, Palette.Purple, "HRV")),
            band = if (lo != null && hi != null) lo to hi else null,
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
            xLabels = dayTips(days.map { it.date }),
            unit = "ms",
        )
    }
}

@Composable
private fun RhrDeviationCard(r: AnalysisResult, vm: MainViewModel) {
    val g = r.regeneration
    val t = r.todayMetrics
    val days = r.days.takeLast(30)
    SectionCard(title = "Ruhepuls-Abweichung", icon = Icons.Filled.Favorite, onClick = { vm.open(Route.Metric(MetricKey.RESTING_HR)) }) {
        val dev = g.rhrDeviation
        StatRow(
            listOf(
                Triple(t?.restingHr?.let { Fmt.int(it) } ?: "–", "bpm heute", null),
                Triple(dev?.let { Fmt.signed(it, 1) } ?: "–", "zu normal", if ((dev ?: 0.0) >= 3) Palette.Warn else Palette.Good),
                Triple(g.rhrDeviationZ?.let { Fmt.signed(it, 1) } ?: "–", "Std.-Abw.", null),
            ),
        )
        Spacer(Modifier.height(10.dp))
        val b = t?.rhrBaseline
        LineChart(
            listOf(Series(days.map { it.restingHr }, Palette.Bad, "Ruhepuls")),
            band = b?.let { (it.mean - it.sd) to (it.mean + it.sd) },
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
            xLabels = dayTips(days.map { it.date }),
            format = { v -> Fmt.num(v, 1) + " bpm" + (b?.let { " (" + Fmt.signed(v - it.mean, 1) + " zu normal)" } ?: "") },
        )
    }
}

@Composable
fun ForecastCard(r: AnalysisResult, vm: MainViewModel?) {
    val f = r.forecast
    SectionCard(title = "Prognose · 1–3 Tage", icon = Icons.Filled.Psychology, onClick = vm?.let { { it.open(Route.Metric(MetricKey.FORECAST)) } }) {
        if (f == null) {
            Text("Erscheint, sobald ein Erholungswert vorliegt.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        ForecastChart(f, scenarios = ForecastScenario.entries)
        Spacer(Modifier.height(4.dp))
        Text(
            "Punkt = erwartet, Balken = 80-%-Bereich" + if (f.personalModel) " · an dich angepasst" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ForecastDetail(r: AnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ForecastCard(r, null)
        CoachCard(Coach.forecast(r), title = "Coach · Prognose")
    }
}
