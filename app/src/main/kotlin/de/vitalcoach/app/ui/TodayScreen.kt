package de.vitalcoach.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.AiState
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.Tab
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Tone
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(state: UiState, vm: MainViewModel, padding: PaddingValues, onTab: (Tab) -> Unit) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state, vm) }
        if (r == null) {
            item { NoDataCard(state, onTab) }
            return@LazyColumn
        }
        items(r.warnings.size) { i ->
            val w = r.warnings[i]
            Card(
                colors = CardDefaults.cardColors(containerColor = w.tone.color().copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = w.tone.color())
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(w.title, style = MaterialTheme.typography.titleSmall, color = w.tone.color())
                        Spacer(Modifier.height(2.dp))
                        Text(w.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { RingsCard(r, vm) }
        item { CoachCard(Coach.day(r), title = "Dein Tagesfazit") }
        item { AiCard(state, vm, onTab) }
        item { Tiles(r, vm) }
        item {
            Text(
                dataNote(r),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Header(state: UiState, vm: MainViewModel) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Hallo"
        else -> "Guten Abend"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium)
            val updated = state.lastRefresh?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm"))
            Text(
                Fmt.day(java.time.LocalDate.now()) + (updated?.let { " · aktualisiert $it Uhr" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.demoMode) {
                Spacer(Modifier.height(4.dp))
                Pill("DEMO-DATEN", Palette.Purple)
            }
        }
        if (state.loading) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren") }
        }
    }
    state.error?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = Palette.Bad, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RingsCard(r: AnalysisResult, vm: MainViewModel) {
    val t = r.todayMetrics
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val rec = t?.recovery
            ScoreRing(
                rec?.value, "Erholung", rec?.tone ?: Tone.NEUTRAL,
                caption = rec?.let { recoveryLabel(it.value) },
                onClick = { vm.open(Route.Metric(MetricKey.RECOVERY)) },
            )
            val ready = t?.readiness
            ScoreRing(
                ready?.value, "Bereitschaft", ready?.tone ?: Tone.NEUTRAL,
                caption = ready?.let { readinessLabel(it.value) },
                onClick = { vm.open(Route.Metric(MetricKey.READINESS)) },
            )
            val n = r.sleep.lastNight
            ScoreRing(
                n?.score, "Schlaf", n?.scoreTone ?: Tone.NEUTRAL,
                caption = n?.let { Fmt.duration(it.asleepMin) },
                onClick = { vm.open(Route.Metric(MetricKey.SLEEP)) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tippe auf einen Wert für Details und Erklärungen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun recoveryLabel(v: Int) = when {
    v >= 67 -> "gut"
    v >= 50 -> "solide"
    v >= 34 -> "mäßig"
    else -> "niedrig"
}

fun readinessLabel(v: Int) = when {
    v >= 67 -> "intensiv"
    v >= 50 -> "moderat"
    v >= 34 -> "locker"
    else -> "Ruhetag"
}

@Composable
private fun AiCard(state: UiState, vm: MainViewModel, onTab: (Tab) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    SectionCard(title = "KI-Coach (Claude)", icon = Icons.Filled.AutoAwesome) {
        if (state.apiKey.isBlank()) {
            Text(
                "Lass dir deine Daten von einer echten KI ausführlich analysieren und stelle eigene Fragen („Warum ist meine HRV gesunken?“). " +
                    "Dafür brauchst du einen eigenen Claude-API-Schlüssel.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { onTab(Tab.MORE) }) { Text("KI-Coach einrichten") }
            return@SectionCard
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Eigene Frage (optional)") },
            placeholder = { Text("z. B. Soll ich heute Intervalle laufen?") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Spacer(Modifier.height(10.dp))
        val loading = state.ai is AiState.Loading
        Button(onClick = { vm.runAi(question.ifBlank { null }) }, enabled = !loading && state.result != null) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Analysiere …")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (question.isBlank()) "Ausführliche KI-Analyse" else "Frage stellen")
            }
        }
        when (val ai = state.ai) {
            is AiState.Done -> {
                Spacer(Modifier.height(12.dp))
                val time = ai.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
                Text(
                    "Analyse vom $time" + (ai.question?.let { " · Frage: „$it“" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                MarkdownText(ai.text)
                if (ai.truncated) Text("(Antwort wurde gekürzt)", style = MaterialTheme.typography.labelSmall)
            }
            is AiState.Error -> {
                Spacer(Modifier.height(10.dp))
                Text(ai.message, color = Palette.Bad, style = MaterialTheme.typography.bodyMedium)
            }
            else -> Unit
        }
    }
}

@Composable
private fun Tiles(r: AnalysisResult, vm: MainViewModel) {
    val t = r.todayMetrics
    val f = r.fitness
    val l = r.load
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow {
            val rhr = t?.restingHr
            val b = t?.rhrBaseline
            val diff = if (rhr != null && b != null) rhr - b.mean else null
            MetricTile(
                "Ruhepuls", rhr?.let { Fmt.int(it) } ?: "–", "bpm",
                diff?.let { "${Fmt.signed(it, 1)} zu normal" } ?: "Normalwert wird ermittelt",
                when {
                    diff == null -> Tone.NEUTRAL
                    diff >= 4 -> Tone.WARN
                    diff <= 1 -> Tone.GOOD
                    else -> Tone.OK
                },
                Icons.Filled.Favorite, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.RESTING_HR)) }
            val hrv = t?.hrv
            val low = f.hrvNormalLow
            val high = f.hrvNormalHigh
            MetricTile(
                "HRV", hrv?.let { Fmt.int(it) } ?: "–", "ms",
                when {
                    !r.quality.hasHrv -> "nicht verfügbar"
                    hrv != null && low != null && hrv < low -> "unter Normalbereich"
                    hrv != null && high != null && hrv > high -> "über Normalbereich"
                    hrv != null && low != null -> "im Normalbereich"
                    else -> "Normalbereich wird ermittelt"
                },
                when {
                    hrv == null || low == null -> Tone.NEUTRAL
                    hrv < low -> Tone.WARN
                    else -> Tone.GOOD
                },
                Icons.Filled.MonitorHeart, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.HRV)) }
        }
        TileRow {
            val steps = r.activity.stepsToday
            MetricTile(
                "Schritte", steps?.let { Fmt.int(it) } ?: "–", "",
                r.activity.steps7Avg?.let { "Ø 7 Tage ${Fmt.int(it)}" },
                if ((steps ?: 0) >= r.activity.stepGoal) Tone.GOOD else Tone.NEUTRAL,
                Icons.AutoMirrored.Filled.DirectionsWalk, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.ACTIVITY)) }
            MetricTile(
                "Aktive kcal", r.activity.activeKcalToday?.let { Fmt.int(it) } ?: "–", "kcal",
                r.activity.activeKcal7Avg?.let { "Ø 7 Tage ${Fmt.int(it)}" },
                Tone.NEUTRAL, Icons.Filled.LocalFireDepartment, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.ACTIVITY)) }
        }
        TileRow {
            MetricTile(
                "Belastung 7 Tage", Fmt.int(l.weekLoad), "TRIMP",
                l.status.label, l.status.tone, Icons.Filled.Bolt, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.LOAD)) }
            MetricTile(
                "Form", Fmt.signed(l.tsb), "",
                "Fitness ${Fmt.int(l.ctl)} · Ermüdung ${Fmt.int(l.atl)}",
                when {
                    l.tsb < -25 -> Tone.WARN
                    l.tsb < -10 -> Tone.OK
                    else -> Tone.GOOD
                },
                Icons.Filled.Timeline, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.LOAD)) }
        }
        TileRow {
            MetricTile(
                "VO2max", f.vo2max?.let { Fmt.num(it, 1) } ?: "–", "",
                f.vo2CategoryLabel ?: f.vo2Method?.label,
                when (f.vo2CategoryIndex) {
                    null -> Tone.NEUTRAL
                    0, 1 -> Tone.WARN
                    2 -> Tone.OK
                    else -> Tone.GOOD
                },
                Icons.Filled.Speed, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.VO2MAX)) }
            val spo2 = t?.spo2
            MetricTile(
                "SpO2 / Atmung",
                spo2?.let { Fmt.num(it, 0) } ?: "–", "%",
                t?.respRate?.let { "Atmung ${Fmt.num(it, 1)} /min" } ?: "Atmung –",
                when {
                    spo2 == null -> Tone.NEUTRAL
                    spo2 < 93 -> Tone.WARN
                    else -> Tone.GOOD
                },
                Icons.Filled.Air, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.VITALS)) }
        }
    }
}

@Composable
private fun NoDataCard(state: UiState, onTab: (Tab) -> Unit) {
    SectionCard(title = "Noch keine Daten") {
        val text = when {
            state.loading -> "Daten werden aus Health Connect geladen …"
            state.availability != de.vitalcoach.app.HealthConnectRepository.Availability.AVAILABLE ->
                "Health Connect ist auf diesem Gerät nicht verfügbar oder muss aktualisiert werden."
            !state.hasPermissions -> "VitalCoach hat noch keinen Zugriff auf deine Gesundheitsdaten. Erlaube den Zugriff unter „Mehr → Datenquelle“."
            else -> "Es wurden noch keine Daten gefunden. Prüfe, ob Samsung Health Daten an Health Connect weitergibt."
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { onTab(Tab.MORE) }) { Text("Zu den Einstellungen") }
    }
}

private fun dataNote(r: AnalysisResult): String {
    val q = r.quality
    val parts = mutableListOf("Datenbasis: ${q.daysWithData} Tage")
    if (!q.hasHrv) parts += "keine HRV über Health Connect"
    if (!q.hasSleepStages && q.hasSleep) parts += "keine Schlafphasen"
    if (q.calibrating) parts += "Normalwerte werden noch kalibriert"
    return parts.joinToString(" · ") + ". VitalCoach ist kein Medizinprodukt."
}
