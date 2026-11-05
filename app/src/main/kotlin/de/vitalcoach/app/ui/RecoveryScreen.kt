package de.vitalcoach.app.ui

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Provenance
import de.vitalcoach.core.Tone
import de.vitalcoach.core.forecast
import de.vitalcoach.core.regeneration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecoveryScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Erholung & Bereitschaft", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@LazyColumn
        }
        recoveryItems(r, vm)
    }
}

/** Alle Erholungs-Karten – wird auch auf der Detailseite „Regeneration“ verwendet. */
fun LazyListScope.recoveryItems(r: AnalysisResult, vm: MainViewModel) {
    item { PersonalRecoveryCard(r, vm) }
    item { RegenerationCard(r) }
    item { OverloadCard(r) }
    item { CoachCard(Coach.regeneration(r), title = "Bewertung deiner Regeneration") }
    item { HrvTrendCard(r, vm) }
    item { RhrDeviationCard(r, vm) }
    item { ForecastCard(r, vm) }
}

@Composable
private fun PersonalRecoveryCard(r: AnalysisResult, vm: MainViewModel) {
    val t = r.todayMetrics
    val rec = t?.recovery
    val ready = t?.readiness
    SectionCard(title = "Persönlicher Erholungswert", icon = Icons.Filled.BatteryChargingFull, onClick = { vm.open(Route.Metric(MetricKey.RECOVERY)) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(rec?.value, "Erholung", rec?.tone ?: Tone.NEUTRAL, size = 92.dp, caption = rec?.let { recoveryLabel(it.value) })
            Spacer(Modifier.width(12.dp))
            ScoreRing(
                ready?.value, "Bereitschaft", ready?.tone ?: Tone.NEUTRAL, size = 92.dp,
                caption = ready?.let { readinessLabel(it.value) },
                onClick = { vm.open(Route.Metric(MetricKey.READINESS)) },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ProvenanceChip(Provenance.CALCULATED)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kombiniert HRV, Ruhepuls, Schlafqualität, Atemfrequenz und deine Trainingsbelastung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rec != null) {
            Spacer(Modifier.height(12.dp))
            ScoreBreakdown(rec)
        }
    }
}

@Composable
fun RegenerationCard(r: AnalysisResult) {
    val g = r.regeneration
    SectionCard(title = "Regeneration nach Training", icon = Icons.Filled.ShowChart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val idx = g.regenerationIndex
            ScoreRing(idx, "Regenerationsindex", scoreTone(idx), size = 92.dp, caption = idx?.let { "$it %" })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (g.remainingHours >= 1) {
                    BigValue(Fmt.int(g.remainingHours), "h", "bis zur vollen Erholung")
                    val ready = g.readyAt
                    if (ready != null) {
                        val f = DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.GERMANY)
                        Text("voraussichtlich ${ready.atZone(ZoneId.systemDefault()).format(f)} Uhr", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    BigValue("0", "h", "keine offene Erholung")
                }
                Spacer(Modifier.height(4.dp))
                ProvenanceChip(Provenance.ESTIMATED)
            }
        }
        if (g.recent.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Erholungsdauer deiner letzten Trainings", style = MaterialTheme.typography.titleSmall)
            g.recent.take(5).forEach { info ->
                val w = info.workout.workout
                val done = 1.0 - info.remainingH / info.hours
                LabeledBar(
                    "${w.type.emoji} ${w.title ?: w.type.label} · ${Fmt.dayShort(w.start.atZone(ZoneId.systemDefault()).toLocalDate())}",
                    done.toFloat(),
                    if (info.remainingH > 0) "noch ${Fmt.int(info.remainingH)} h von ${Fmt.int(info.hours)} h" else "erholt (${Fmt.int(info.hours)} h)",
                    if (info.remainingH > 0) Palette.Warn else Palette.Good,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val fat = g.fatigueIndex
        Text("Ermüdungsindex (Fatigue Index)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        ScaleBar(
            listOf("frisch" to Palette.Light, "ausgeglichen" to Palette.Good, "erhöht" to Palette.Warn, "hoch" to Palette.Bad),
            position = fat?.let { (it / 100f) },
        )
        Text("${fat ?: "–"} – ${g.fatigueLabel}", style = MaterialTheme.typography.bodyMedium, color = g.fatigueTone.color())
        Spacer(Modifier.height(10.dp))
        KeyValue("Belastungs-/Erholungsverhältnis", g.balance?.let { "${Fmt.num(it, 2)} · ${g.balanceLabel}" } ?: "–", g.balanceTone.color())
        KeyValue("Trainingsbelastung 7 Tage", "${Fmt.int(g.load7)} TRIMP")
        KeyValue("Trainingsbelastung 28 Tage", "${Fmt.int(g.load28)} TRIMP")
        KeyValue("Trainingsbelastung 90 Tage", "${Fmt.int(g.load90)} TRIMP")
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
        Spacer(Modifier.height(8.dp))
        if (g.overloadSignals.isEmpty()) {
            Text("Keines der 8 geprüften Warnsignale ist aktiv.", style = MaterialTheme.typography.bodyMedium)
        } else {
            g.overloadSignals.forEach { Bullet(it) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Geprüft: Belastungssprung (ACWR), Monotonie, HRV-Trend, Ruhepuls-Trend, Erholung, Schlafdefizit, Ermüdung, Bereitschafts-Trend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HrvTrendCard(r: AnalysisResult, vm: MainViewModel) {
    val g = r.regeneration
    val f = r.fitness
    val days = r.days.takeLast(60)
    SectionCard(title = "HRV-Trend", icon = Icons.Filled.MonitorHeart, onClick = { vm.open(Route.Metric(MetricKey.HRV)) }) {
        if (!r.quality.hasHrv) {
            Text("Samsung Health stellt aktuell keine HRV-Werte über Health Connect bereit.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row {
            Column(Modifier.weight(1f)) { BigValue(f.hrvNow?.let { Fmt.int(it) } ?: "–", "ms", "Ø 7 Tage") }
            val tr = g.hrvTrendPct
            Column(horizontalAlignment = Alignment.End) {
                Text(tr?.let { Fmt.signed(it) + " %" } ?: "–", style = MaterialTheme.typography.headlineMedium, color = if ((tr ?: 0.0) >= -5) Palette.Good else Palette.Warn)
                Text("vs. 30-Tage-Schnitt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        val lo = f.hrvNormalLow
        val hi = f.hrvNormalHigh
        LineChart(
            listOf(Series(days.map { it.hrv }, Palette.Purple, "HRV")),
            band = if (lo != null && hi != null) lo to hi else null,
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
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
        Row {
            Column(Modifier.weight(1f)) { BigValue(t?.restingHr?.let { Fmt.int(it) } ?: "–", "bpm", "heute") }
            Column(horizontalAlignment = Alignment.End) {
                Text(dev?.let { Fmt.signed(it, 1) } ?: "–", style = MaterialTheme.typography.headlineMedium, color = if ((dev ?: 0.0) >= 3) Palette.Warn else Palette.Good)
                Text("bpm zu deinem Normalwert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        g.rhrDeviationZ?.let {
            Text("Das entspricht ${Fmt.signed(it, 1)} Standardabweichungen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        val b = t?.rhrBaseline
        LineChart(
            listOf(Series(days.map { it.restingHr }, Palette.Bad, "Ruhepuls")),
            band = b?.let { (it.mean - it.sd) to (it.mean + it.sd) },
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
            decimals = 0,
        )
    }
}

@Composable
fun ForecastCard(r: AnalysisResult, vm: MainViewModel?) {
    val f = r.forecast
    SectionCard(title = "Prognose: Bereitschaft in 1–3 Tagen", icon = Icons.Filled.Psychology, onClick = vm?.let { { it.open(Route.Metric(MetricKey.FORECAST)) } }) {
        if (f == null) {
            Text("Die Prognose erscheint, sobald ein Erholungswert berechnet wurde.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        ForecastTable(f)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProvenanceChip(Provenance.ESTIMATED)
            Spacer(Modifier.width(8.dp))
            Text(
                "Zahl = erwarteter Wert, darunter der 80-%-Bereich. " + if (f.personalModel) "An deine Daten angepasst." else "Noch Standardmodell.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ForecastDetail(r: AnalysisResult) {
    Column {
        ForecastCard(r, null)
        Spacer(Modifier.height(12.dp))
        CoachCard(Coach.forecast(r))
    }
}
