package de.vitalcoach.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.AiState
import de.vitalcoach.app.HealthConnectRepository
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.Tab
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.HealthWarning
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Provenance
import de.vitalcoach.core.Tone
import de.vitalcoach.core.scoreToneOf
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

val HeroGradient = Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2F6FED), Color(0xFF0FA3A3)))
private val Mint = Color(0xFF86EFAC)
private val Lavender = Color(0xFFC4B5FD)

@Composable
fun TodayScreen(state: UiState, vm: MainViewModel, padding: PaddingValues, onTab: (Tab) -> Unit) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(state, vm) }
        if (r == null) {
            item { NoDataCard(state, vm) }
            return@LazyColumn
        }
        item { HeroCard(r, vm) }
        if (r.warnings.isNotEmpty()) {
            items(r.warnings.size) { i -> WarningRow(r.warnings[i]) }
        }
        item { BriefingCard(r) }
        item { ForecastMini(r, vm) }
        item { Tiles(r, vm, onTab) }
        item { AiCard(state, vm) }
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
                Fmt.day(java.time.LocalDate.now()) + (updated?.let { " · $it Uhr" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.demoMode) Pill("DEMO", Palette.Purple)
        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(horizontal = 10.dp).size(24.dp), strokeWidth = 3.dp)
        } else {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren") }
        }
        IconButton(onClick = { vm.open(Route.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") }
    }
    val progress = state.progress
    if (state.loading && progress != null) {
        Text("Lade: $progress …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.error?.let { Text(it, color = Palette.Bad, style = MaterialTheme.typography.bodySmall) }
}

/** Großer Kopfbereich: drei Ringe für Bereitschaft, Erholung und Schlaf. */
@Composable
private fun HeroCard(r: AnalysisResult, vm: MainViewModel) {
    val t = r.todayMetrics
    val ready = t?.readiness
    val rec = t?.recovery
    val sleep = r.sleep.lastNight
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HeroGradient)
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConcentricRings(
                    listOf(ready?.value to Color.White, rec?.value to Mint, sleep?.score to Lavender),
                    size = 140.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ready?.value ?: "–"}", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("bereit", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroLegend("Bereitschaft", ready?.value, ready?.value?.let { readinessLabel(it) }, Color.White) { vm.open(Route.Metric(MetricKey.READINESS)) }
                    HeroLegend("Erholung", rec?.value, rec?.value?.let { recoveryLabel(it) }, Mint) { vm.open(Route.Metric(MetricKey.RECOVERY)) }
                    HeroLegend("Schlaf", sleep?.score, sleep?.let { Fmt.duration(it.asleepMin) }, Lavender) { vm.open(Route.Metric(MetricKey.SLEEP)) }
                }
            }
            val rcm = r.load.recommendation
            if (rcm != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { vm.open(Route.Metric(MetricKey.READINESS)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rcm.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                        if (rcm.targetLoadHigh > 0) {
                            Text("${rcm.zone2MinLow}–${rcm.zone2MinHigh} min Zone 2 · ${rcm.targetLoadLow}–${rcm.targetLoadHigh} TRIMP", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroLegend(label: String, value: Int?, sub: String?, color: Color, onClick: () -> Unit) {
    Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value?.toString() ?: "–", style = MaterialTheme.typography.titleLarge, color = Color.White)
                if (sub != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(sub, style = MaterialTheme.typography.labelMedium, color = if (value != null) scoreToneOf(value).let { heroToneColor(it) } else Color.White)
                }
            }
        }
    }
}

private fun heroToneColor(t: Tone): Color = when (t) {
    Tone.GOOD -> Color(0xFFBBF7D0)
    Tone.OK -> Color(0xFFD9F99D)
    Tone.WARN -> Color(0xFFFDE68A)
    Tone.BAD -> Color(0xFFFECACA)
    Tone.NEUTRAL -> Color.White
}

fun recoveryLabel(v: Int) = when {
    v >= 75 -> "gut"
    v >= 60 -> "solide"
    v >= 45 -> "mäßig"
    else -> "niedrig"
}

fun readinessLabel(v: Int) = when {
    v >= 75 -> "intensiv"
    v >= 60 -> "moderat"
    v >= 45 -> "locker"
    else -> "Ruhetag"
}

@Composable
private fun WarningRow(w: HealthWarning) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(w.tone.color().copy(alpha = 0.12f))
            .clickable { open = !open }
            .padding(14.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = w.tone.color(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(w.title, style = MaterialTheme.typography.titleSmall, color = w.tone.color(), modifier = Modifier.weight(1f))
            Text(if (open) "weniger" else "mehr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(w.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Tägliche Zusammenfassung – kompakt mit Pfeilen statt langer Texte. */
@Composable
private fun BriefingCard(r: AnalysisResult) {
    val b = r.briefing
    val changes = r.baselines.filter { b -> abs(b.z ?: 0.0) >= 1.0 && b.current != null && b.mean30 != null }
        .sortedByDescending { abs(it.z ?: 0.0) }
        .take(4)
    SectionCard(title = "Tages-Briefing", icon = Icons.Filled.AutoAwesome) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneDot(b.tone, 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(b.headline, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        if (changes.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Palette.Good, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Alle Werte in deinem Normalbereich", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            changes.forEach { c ->
                val cur = c.current ?: 0.0
                val diff = cur - (c.mean30 ?: cur)
                val unit = if (c.unit.isNotEmpty()) " ${c.unit}" else ""
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (diff >= 0) "▲" else "▼", color = c.tone.color(), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Fmt.num(cur, c.decimals)}$unit", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Pill(Fmt.signed(diff, c.decimals), c.tone.color())
                }
            }
        }
        if (b.explanations.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Mögliche Gründe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.explanations.take(2).forEach { Bullet(it) }
        }
        if (b.todo.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Heute", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.todo.take(3).forEach { Bullet(it) }
        }
    }
}

@Composable
private fun ForecastMini(r: AnalysisResult, vm: MainViewModel) {
    val f = r.forecast ?: return
    SectionCard(title = "Bereitschaft · nächste 3 Tage", icon = Icons.Filled.Psychology, onClick = { vm.open(Route.Metric(MetricKey.FORECAST)) }) {
        ForecastChart(f, height = 140.dp)
    }
}

@Composable
private fun AiCard(state: UiState, vm: MainViewModel) {
    var question by rememberSaveable { mutableStateOf("") }
    SectionCard(title = "KI-Coach (Claude)", icon = Icons.Filled.AutoAwesome) {
        if (state.apiKey.isBlank()) {
            Text("Ausführliche KI-Analyse & eigene Fragen – mit eigenem Claude-Schlüssel.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.open(Route.Settings) }) { Text("Einrichten") }
            return@SectionCard
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Frage (optional)") },
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
                Text(if (question.isBlank()) "KI-Analyse starten" else "Frage stellen")
            }
        }
        when (val ai = state.ai) {
            is AiState.Done -> {
                Spacer(Modifier.height(12.dp))
                val time = ai.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
                Text("Analyse vom $time" + (ai.question?.let { " · „$it“" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                MarkdownText(ai.text)
                if (ai.truncated) Text("(Antwort gekürzt)", style = MaterialTheme.typography.labelSmall)
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
private fun Tiles(r: AnalysisResult, vm: MainViewModel, onTab: (Tab) -> Unit) {
    val t = r.todayMetrics
    val f = r.fitness
    val l = r.load
    val g = r.regeneration
    val d14 = r.days.takeLast(14)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Deine Werte", style = MaterialTheme.typography.titleLarge)
        TileRow {
            val rhr = t?.restingHr
            val b = t?.rhrBaseline
            val diff = if (rhr != null && b != null) rhr - b.mean else null
            MetricTile(
                "Ruhepuls", rhr?.let { Fmt.int(it) } ?: "–", "bpm",
                diff?.let { "${Fmt.signed(it, 1)} zu normal" },
                when {
                    diff == null -> Tone.NEUTRAL
                    diff >= 6 -> Tone.BAD
                    diff >= 3 -> Tone.WARN
                    diff <= 0 -> Tone.GOOD
                    else -> Tone.OK
                },
                Icons.Filled.Favorite, Modifier.weight(1f), null, d14.map { it.restingHr },
            ) { vm.open(Route.Metric(MetricKey.RESTING_HR)) }
            val hrv = t?.hrv
            val low = f.hrvNormalLow
            val high = f.hrvNormalHigh
            MetricTile(
                "HRV", hrv?.let { Fmt.int(it) } ?: "–", "ms",
                when {
                    !r.quality.hasHrv -> "nicht verfügbar"
                    hrv != null && low != null && hrv < low -> "unter normal"
                    hrv != null && high != null && hrv > high -> "über normal"
                    hrv != null && low != null -> "normal"
                    else -> null
                },
                when {
                    hrv == null || low == null -> Tone.NEUTRAL
                    hrv < low -> Tone.WARN
                    high != null && hrv > high -> Tone.GOOD
                    else -> Tone.OK
                },
                Icons.Filled.MonitorHeart, Modifier.weight(1f), null, d14.map { it.hrv },
            ) { vm.open(Route.Metric(MetricKey.HRV)) }
        }
        TileRow {
            val idx = g.regenerationIndex
            MetricTile(
                "Regeneration", idx?.let { "$it" } ?: "–", "%",
                if (g.remainingHours >= 1) "noch ${Fmt.int(g.remainingHours)} h" else "erholt",
                scoreToneOf(idx ?: 0).takeIf { idx != null } ?: Tone.NEUTRAL, Icons.Filled.BatteryChargingFull, Modifier.weight(1f), null,
                d14.map { it.regenProgress?.toDouble() },
            ) { onTab(Tab.RECOVERY) }
            val s = r.stress
            val sv = s.today ?: s.avg7
            MetricTile(
                "Stress", sv?.let { Fmt.int(it) } ?: "–", "",
                s.label, s.tone, Icons.Filled.Psychology, Modifier.weight(1f), null, d14.map { it.stressIndex },
            ) { vm.open(Route.Metric(MetricKey.STRESS)) }
        }
        TileRow {
            val steps = r.activity.stepsToday
            MetricTile(
                "Schritte", steps?.let { Fmt.int(it) } ?: "–", "",
                r.activity.steps7Avg?.let { "Ø ${Fmt.int(it)}" },
                if ((steps ?: 0) >= r.activity.stepGoal) Tone.GOOD else Tone.NEUTRAL,
                Icons.AutoMirrored.Filled.DirectionsWalk, Modifier.weight(1f), null, d14.map { it.steps?.toDouble() },
            ) { vm.open(Route.Metric(MetricKey.ACTIVITY)) }
            MetricTile(
                "Aktive kcal", r.activity.activeKcalToday?.let { Fmt.int(it) } ?: "–", "",
                r.activity.activeKcal7Avg?.let { "Ø ${Fmt.int(it)}" },
                Tone.NEUTRAL, Icons.Filled.LocalFireDepartment, Modifier.weight(1f), null, d14.map { it.activeKcal },
            ) { vm.open(Route.Metric(MetricKey.ACTIVITY)) }
        }
        TileRow {
            MetricTile(
                "Belastung", Fmt.int(l.weekLoad), "TRIMP",
                l.status.label, l.status.tone, Icons.Filled.Bolt, Modifier.weight(1f), null, d14.map { it.load },
            ) { vm.open(Route.Metric(MetricKey.LOAD)) }
            MetricTile(
                "Form", Fmt.signed(l.tsb), "",
                "Fitness ${Fmt.int(l.ctl)}",
                when {
                    l.tsb < -25 -> Tone.BAD
                    l.tsb < -10 -> Tone.WARN
                    l.tsb < 5 -> Tone.OK
                    else -> Tone.GOOD
                },
                Icons.Filled.Timeline, Modifier.weight(1f), null, r.days.takeLast(42).map { it.tsb },
            ) { vm.open(Route.Metric(MetricKey.LOAD)) }
        }
        TileRow {
            MetricTile(
                "VO2max", f.vo2max?.let { Fmt.num(it, 1) } ?: "–", "",
                f.vo2CategoryLabel,
                when (f.vo2CategoryIndex) {
                    null -> Tone.NEUTRAL
                    0, 1 -> Tone.BAD
                    2 -> Tone.WARN
                    3 -> Tone.OK
                    else -> Tone.GOOD
                },
                Icons.Filled.Speed, Modifier.weight(1f), Provenance.ESTIMATED, f.vo2History.takeLast(12).map { it.second },
            ) { vm.open(Route.Metric(MetricKey.VO2MAX)) }
            val hh = r.heartHealth
            MetricTile(
                "Herzgesundheit", hh.score?.toString() ?: "–", "",
                hh.label.ifEmpty { null }, hh.tone, Icons.Filled.Favorite, Modifier.weight(1f),
            ) { vm.open(Route.Metric(MetricKey.HEART_HEALTH)) }
        }
        TileRow {
            val spo2 = t?.spo2
            MetricTile(
                "SpO2", spo2?.let { Fmt.num(it, 0) } ?: "–", "%",
                t?.respRate?.let { "Atmung ${Fmt.num(it, 1)}" },
                when {
                    spo2 == null -> Tone.NEUTRAL
                    spo2 < 93 -> Tone.WARN
                    else -> Tone.GOOD
                },
                Icons.Filled.Air, Modifier.weight(1f), null, d14.map { it.spo2 },
            ) { vm.open(Route.Metric(MetricKey.VITALS)) }
            val need = r.sleep.needTonightMin
            val bed = r.sleep.recommendedBedtimeMin
            MetricTile(
                "Schlafbedarf", need?.let { Fmt.duration(it) } ?: "–", "",
                bed?.let { "ins Bett ${Fmt.clock(it)}" },
                Tone.NEUTRAL, Icons.Filled.Bedtime, Modifier.weight(1f), null, d14.map { it.sleep?.asleepMin?.div(60.0) },
            ) { onTab(Tab.SLEEP) }
        }
    }
}

@Composable
private fun NoDataCard(state: UiState, vm: MainViewModel) {
    SectionCard(title = "Noch keine Daten") {
        val text = when {
            state.loading -> "Daten werden geladen …"
            state.availability != HealthConnectRepository.Availability.AVAILABLE -> "Health Connect ist nicht verfügbar oder muss aktualisiert werden."
            !state.hasPermissions -> "Noch kein Zugriff auf deine Gesundheitsdaten."
            else -> "Noch keine Daten gefunden – gibt Samsung Health Daten an Health Connect weiter?"
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { vm.open(Route.Settings) }) { Text("Zu den Einstellungen") }
    }
}

private fun dataNote(r: AnalysisResult): String {
    val q = r.quality
    val parts = mutableListOf("${q.daysWithData} Tage Daten")
    if (!q.hasHrv) parts += "keine HRV"
    if (q.calibrating) parts += "Kalibrierung läuft"
    return parts.joinToString(" · ") + " · Kein Medizinprodukt"
}
