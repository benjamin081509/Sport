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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import de.vitalcoach.core.FitnessMath
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.MetricKey

@Composable
fun BodyScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Körper & Fitness", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@LazyColumn
        }
        item { Vo2Card(r, vm) }
        item { CoachCard(Coach.fitness(r), title = "Bewertung deines Fitnesslevels", initiallyExpanded = false) }
        item { RestingHrCard(r, vm) }
        item { HrvCard(r, vm) }
        item { ZonesCard(r) }
        item { ActivityCard(r, vm) }
        item { VitalsCard(r, vm) }
        item { BodyCompCard(r, vm) }
    }
}

@Composable
fun Vo2Card(r: AnalysisResult, vm: MainViewModel?) {
    val f = r.fitness
    val vo2 = f.vo2max
    SectionCard(title = "VO2max & Fitnessalter", icon = Icons.Filled.Speed, onClick = vm?.let { { it.open(Route.Metric(MetricKey.VO2MAX)) } }) {
        if (vo2 == null) {
            Text("Noch nicht genug Daten. Trage die Uhr ein paar Nächte und zeichne Läufe auf.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BigValue(Fmt.num(vo2, 1), "ml/kg/min", f.vo2Method?.label)
            }
            val fa = f.fitnessAge
            if (fa != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("$fa", style = MaterialTheme.typography.headlineLarge, color = Palette.Teal)
                    Text("Fitnessalter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    f.age?.let { Text("tatsächlich $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        val idx = f.vo2CategoryIndex
        if (idx != null) {
            Spacer(Modifier.height(12.dp))
            ScaleBar(
                FitnessMath.CATEGORY_LABELS.mapIndexed { i, l ->
                    l to listOf(Palette.Bad, Palette.Orange, Palette.Warn, Palette.Ok, Palette.Good, Palette.Teal)[i]
                },
                position = (idx + 0.5f) / 6f,
                labels = listOf("sehr\nschwach", "schwach", "mittel", "gut", "sehr gut", "hervor-\nragend"),
            )
        }
        if (f.vo2History.size >= 2) {
            Spacer(Modifier.height(12.dp))
            Text("Verlauf", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            LineChart(
                listOf(Series(f.vo2History.map { it.second }, Palette.Teal, "VO2max")),
                height = 100.dp,
                startLabel = Fmt.dayShort(f.vo2History.first().first),
                endLabel = Fmt.dayShort(f.vo2History.last().first),
                decimals = 1,
            )
        }
    }
}

@Composable
private fun RestingHrCard(r: AnalysisResult, vm: MainViewModel) {
    val f = r.fitness
    val days = r.days.takeLast(60)
    val base = r.todayMetrics?.rhrBaseline
    SectionCard(title = "Ruhepuls (60 Tage)", icon = Icons.Filled.Favorite, onClick = { vm.open(Route.Metric(MetricKey.RESTING_HR)) }) {
        Row {
            Column(Modifier.weight(1f)) { BigValue(f.restingHrNow?.let { Fmt.int(it) } ?: "–", "bpm", "Ø letzte 7 Tage") }
            val trend = f.restingHrTrend30d
            if (trend != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.signed(trend, 1), style = MaterialTheme.typography.headlineMedium, color = if (trend <= 0) Palette.Good else Palette.Warn)
                    Text("bpm pro Monat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LineChart(
            listOf(Series(days.map { it.restingHr }, Palette.Bad, "Ruhepuls")),
            band = base?.let { (it.mean - it.sd) to (it.mean + it.sd) },
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
        )
        Spacer(Modifier.height(6.dp))
        Text("Grüner Bereich = dein persönlicher Normalbereich (30 Tage).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HrvCard(r: AnalysisResult, vm: MainViewModel) {
    val f = r.fitness
    val days = r.days.takeLast(60)
    SectionCard(title = "HRV (60 Tage)", icon = Icons.Filled.MonitorHeart, onClick = { vm.open(Route.Metric(MetricKey.HRV)) }) {
        if (!r.quality.hasHrv) {
            Text(
                "Samsung Health stellt über Health Connect aktuell keine HRV-Werte bereit. Die Erholung wird deshalb aus Ruhepuls, Schlaf und Atemfrequenz berechnet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        BigValue(f.hrvNow?.let { Fmt.int(it) } ?: "–", "ms", "Ø letzte 7 Tage (RMSSD)")
        Spacer(Modifier.height(10.dp))
        val lo = f.hrvNormalLow
        val hi = f.hrvNormalHigh
        LineChart(
            listOf(Series(days.map { it.hrv }, Palette.Purple, "HRV")),
            band = if (lo != null && hi != null) lo to hi else null,
            startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
            endLabel = "heute",
        )
        if (lo != null && hi != null) {
            Spacer(Modifier.height(6.dp))
            Text("Dein Normalbereich: ${Fmt.int(lo)}–${Fmt.int(hi)} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ZonesCard(r: AnalysisResult) {
    val f = r.fitness
    SectionCard(title = "Deine Herzfrequenzzonen") {
        Text(
            "Maximalpuls ${f.hrMax} bpm (${f.hrMaxSource.label}). Zonen nach Karvonen (Herzfrequenzreserve).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        f.zones.reversed().forEach { z ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                ToneDotColor(Palette.Zones[z.number])
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Zone ${z.number} · ${z.name}", style = MaterialTheme.typography.titleSmall)
                    Text(z.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${z.lowBpm}–${z.highBpm}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ActivityCard(r: AnalysisResult, vm: MainViewModel) {
    val a = r.activity
    val days = r.days.takeLast(14)
    SectionCard(title = "Schritte (14 Tage)", icon = Icons.AutoMirrored.Filled.DirectionsWalk, onClick = { vm.open(Route.Metric(MetricKey.ACTIVITY)) }) {
        BigValue(a.steps7Avg?.let { Fmt.int(it) } ?: "–", "Schritte", "Ø pro Tag · Ziel an ${a.daysGoalReached7}/7 Tagen erreicht")
        Spacer(Modifier.height(10.dp))
        BarChart(
            values = days.map { it.steps?.toDouble() },
            colors = days.map { if ((it.steps ?: 0) >= a.stepGoal) Palette.Good else Palette.Blue },
            goal = a.stepGoal.toDouble(),
            labels = days.map { Fmt.weekday(it.date).take(2) },
        )
    }
}

@Composable
private fun VitalsCard(r: AnalysisResult, vm: MainViewModel) {
    val f = r.fitness
    val days = r.days.takeLast(30)
    SectionCard(title = "Sauerstoff & Atmung im Schlaf", icon = Icons.Filled.Air, onClick = { vm.open(Route.Metric(MetricKey.VITALS)) }) {
        Row {
            Column(Modifier.weight(1f)) { BigValue(f.spo2Avg7?.let { Fmt.num(it, 1) } ?: "–", "%", "SpO2 Ø 7 Tage") }
            Column(Modifier.weight(1f)) { BigValue(f.respAvg7?.let { Fmt.num(it, 1) } ?: "–", "/min", "Atemfrequenz Ø 7 Tage") }
        }
        if (days.any { it.respRate != null }) {
            Spacer(Modifier.height(10.dp))
            LineChart(
                listOf(Series(days.map { it.respRate }, Palette.Teal, "Atemfrequenz")),
                height = 100.dp,
                decimals = 1,
                startLabel = days.firstOrNull()?.let { Fmt.dayShort(it.date) },
                endLabel = "heute",
            )
        }
    }
}

@Composable
private fun BodyCompCard(r: AnalysisResult, vm: MainViewModel) {
    val f = r.fitness
    SectionCard(title = "Körperzusammensetzung", icon = Icons.Filled.Person, onClick = { vm.open(Route.Metric(MetricKey.BODY)) }) {
        f.weightKg?.let { KeyValue("Gewicht", "${Fmt.num(it, 1)} kg") }
        f.heightCm?.let { KeyValue("Größe", "${Fmt.int(it)} cm") }
        f.bmi?.let { KeyValue("BMI", "${Fmt.num(it, 1)} · ${FitnessMath.bmiLabel(it)}") }
        f.bodyFatPct?.let { KeyValue("Körperfett", "${Fmt.num(it, 1)} %" + (f.bodyFatLabel?.let { l -> " · $l" } ?: "")) }
        if (f.weightKg == null) Text("Trage dein Gewicht unter „Mehr → Profil“ ein oder miss es mit der Uhr.", style = MaterialTheme.typography.bodyMedium)
    }
}
