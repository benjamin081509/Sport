package de.vitalcoach.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.BaselineStatus
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.HeartExtras
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Tone
import de.vitalcoach.core.baselines
import de.vitalcoach.core.dailyActivity
import de.vitalcoach.core.heart
import de.vitalcoach.core.heartHealth
import de.vitalcoach.core.performance
import de.vitalcoach.core.scoreToneOf
import de.vitalcoach.core.stress

private data class Hub(val title: String, val value: String?, val sub: String, val icon: ImageVector, val tone: Tone, val onClick: () -> Unit)

@Composable
fun AnalysesScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Analysen", style = MaterialTheme.typography.headlineMedium) }
        if (r != null) {
            val unusual = r.baselines.count { it.status == BaselineStatus.HIGH || it.status == BaselineStatus.LOW }
            val sig = r.correlations.count { it.significant }
            val hubs = listOf(
                Hub("Herzgesundheit", r.heartHealth.score?.toString(), r.heartHealth.label.ifEmpty { "AHA-Modell" }, Icons.Filled.Favorite, r.heartHealth.tone) { vm.open(Route.Metric(MetricKey.HEART_HEALTH)) },
                Hub("Herz & Kreislauf", r.heart.avgHr7?.let { Fmt.int(it) }, "Ø Puls · HRR · Trends", Icons.Filled.MonitorHeart, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.HEART)) },
                Hub("Stress", (r.stress.avg7 ?: r.stress.today)?.let { Fmt.int(it) }, r.stress.label, Icons.Filled.Psychology, r.stress.tone) { vm.open(Route.Metric(MetricKey.STRESS)) },
                Hub("Fitness & Leistung", r.fitness.vo2max?.let { Fmt.num(it, 1) }, "VO2max · Effizienz", Icons.Filled.Speed, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.PERFORMANCE)) },
                Hub("Bewegung", r.activity.movementIndex?.value?.toString(), "Alltag · Sitzzeit", Icons.AutoMirrored.Filled.DirectionsWalk, scoreToneOf(r.activity.movementIndex?.value ?: 0).takeIf { r.activity.movementIndex != null } ?: Tone.NEUTRAL) {
                    vm.open(Route.Metric(MetricKey.ACTIVITY))
                },
                Hub("Langzeit-Trends", null, "bis 1 Jahr", Icons.Filled.Timeline, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.TRENDS)) },
                Hub("Normalwerte", if (unusual > 0) "$unusual" else "✓", if (unusual > 0) "Abweichungen" else "alles normal", Icons.Filled.Rule, if (unusual > 0) Tone.WARN else Tone.GOOD) {
                    vm.open(Route.Metric(MetricKey.BASELINES))
                },
                Hub("Zusammenhänge", if (sig > 0) "$sig" else null, "Muster in deinen Daten", Icons.Filled.Insights, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.CORRELATIONS)) },
                Hub("Körper", r.fitness.bmi?.let { Fmt.num(it, 1) }, "Gewicht · BMI · Fett", Icons.Filled.Person, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.BODY)) },
                Hub("Belastung", Fmt.int(r.load.ctl), "Fitness · Form", Icons.Filled.BarChart, r.load.status.tone) { vm.open(Route.Metric(MetricKey.LOAD)) },
            )
            hubs.chunked(2).forEach { row ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { h -> HubTile(h, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { HubItem("Einstellungen", null, "Profil · Datenquelle · KI", Icons.Filled.Settings, Tone.NEUTRAL) { vm.open(Route.Settings) } }
        item { HubItem("So rechnet VitalCoach", null, "Formeln & Quellen", Icons.Filled.Science, Tone.NEUTRAL) { vm.open(Route.Methods) } }
        item { HubItem("Datenschutz", null, "Daten bleiben auf dem Handy", Icons.Filled.Lock, Tone.NEUTRAL) { vm.open(Route.Privacy) } }
        item { InfoCard("Woher kommen die Werte?", "Jeder Wert ist als gemessen, berechnet oder geschätzt gekennzeichnet.") { Spacer(Modifier.height(8.dp)); ProvenanceLegend() } }
    }
}

@Composable
private fun HubTile(h: Hub, modifier: Modifier) {
    Card(
        modifier = modifier.clickable(onClick = h.onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(h.tone.color().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(h.icon, contentDescription = null, tint = h.tone.color(), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                if (h.value != null) Text(h.value, style = MaterialTheme.typography.titleLarge, color = h.tone.color())
            }
            Spacer(Modifier.height(10.dp))
            Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Herzgesundheit
fun LazyListScope.heartHealthItems(r: AnalysisResult) {
    val h = r.heartHealth
    item {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(h.score, "", h.tone, size = 110.dp, caption = "von 100")
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.label, style = MaterialTheme.typography.titleLarge, color = h.tone.color())
                    Text("${h.covered}/${h.components.size} Bereiche erfasst", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item {
        SectionCard(title = "Profil deiner Herzgesundheit") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                RadarChart(h.components.map { it.name.replace("Herz-Kreislauf-Fitness", "Fitness") to it.score }, h.tone.color().takeIf { h.tone != Tone.NEUTRAL } ?: Palette.Blue)
            }
        }
    }
    item {
        SectionCard(title = "Bereiche") {
            h.components.forEach { c ->
                val sc = c.score
                LabeledBar(c.name, (sc ?: 0) / 100f, sc?.toString() ?: "fehlt", scoreToneOf(sc ?: 0).takeIf { sc != null }?.color() ?: Palette.Neutral)
                Text(c.value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    item { CoachCard(Coach.heartHealth(r), title = "Coach · Herzgesundheit") }
}

// ---------------------------------------------------------------- Herz & Kreislauf
fun LazyListScope.heartItems(r: AnalysisResult) {
    val h = r.heart
    val zone = r.zone
    item {
        SectionCard(title = "Puls im Alltag", icon = Icons.Filled.MonitorHeart) {
            StatRow(
                listOf(
                    Triple(h.avgHrToday?.let { Fmt.int(it) } ?: "–", "Ø heute", null),
                    Triple(h.avgHr7?.let { Fmt.int(it) } ?: "–", "Ø 7 Tage", null),
                    Triple(h.avgHr30?.let { Fmt.int(it) } ?: "–", "Ø 30 Tage", null),
                ),
            )
            val d30 = r.days.takeLast(30)
            Spacer(Modifier.height(10.dp))
            LineChart(listOf(Series(d30.map { it.avgHr }, Palette.Orange, "Ø Puls")), height = 110.dp, startLabel = d30.firstOrNull()?.let { Fmt.dayShort(it.date) }, endLabel = "heute", xLabels = dayTips(d30.map { it.date }), unit = "bpm Ø")
        }
    }
    item {
        SectionCard(title = "Herz in der Nacht", icon = Icons.Filled.Favorite) {
            val dip = h.dipPct
            StatRow(
                listOf(
                    Triple(h.sleepAvgHr?.let { Fmt.int(it) } ?: "–", "Ø im Schlaf", null),
                    Triple(dip?.let { Fmt.int(it) + " %" } ?: "–", "Absenkung", dip?.let { if (it >= 10) Palette.Good else Palette.Warn }),
                    Triple(h.lowestHrPos?.let { Fmt.int(it * 100) + " %" } ?: "–", "Tiefpunkt", h.lowestHrPos?.let { if (it <= 0.7) Palette.Good else Palette.Warn }),
                ),
            )
            if (h.nightCurve.size >= 3) {
                Spacer(Modifier.height(10.dp))
                LineChart(
                    listOf(Series(h.nightCurve.map { it.second }, Palette.Bad, "Puls")),
                    height = 120.dp,
                    xLabels = h.nightCurve.map { timeTip(it.first, zone) },
                    unit = "bpm",
                    startLabel = h.nightCurve.first().first.atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
                    endLabel = h.nightCurve.last().first.atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
                )
            }
        }
    }
    item {
        SectionCard(title = "HRV · 6 Monate", icon = Icons.Filled.Insights) {
            if (!r.quality.hasHrv) {
                Text("Keine HRV über Health Connect.", style = MaterialTheme.typography.bodyMedium)
                return@SectionCard
            }
            StatRow(
                listOf(
                    Triple(h.hrv7?.let { Fmt.int(it) } ?: "–", "7 T", null),
                    Triple(h.hrv30?.let { Fmt.int(it) } ?: "–", "30 T", null),
                    Triple(h.hrv90?.let { Fmt.int(it) } ?: "–", "90 T", null),
                    Triple(h.hrv180?.let { Fmt.int(it) } ?: "–", "180 T", null),
                ),
            )
            if (h.hrvWeekly.size >= 3) {
                Spacer(Modifier.height(10.dp))
                LineChart(listOf(Series(h.hrvWeekly.map { it.second }, Palette.Purple, "HRV")), height = 120.dp, startLabel = Fmt.dayShort(h.hrvWeekly.first().first), endLabel = Fmt.dayShort(h.hrvWeekly.last().first), xLabels = h.hrvWeekly.map { weekTip(it.first) }, unit = "ms (Wochen-Ø)")
            }
        }
    }
    item {
        SectionCard(title = "Ruhepuls · 1 Jahr", icon = Icons.Filled.Favorite) {
            StatRow(
                listOf(
                    Triple(h.rhr7?.let { Fmt.num(it, 1) } ?: "–", "7 T", null),
                    Triple(h.rhr90?.let { Fmt.num(it, 1) } ?: "–", "90 T", null),
                    Triple(h.rhr365?.let { Fmt.num(it, 1) } ?: "–", "vor 1 J.", null),
                    Triple(h.rhrSd14?.let { "±" + Fmt.num(it, 1) } ?: "–", "Stabilität", h.rhrSd14?.let { if (it > 3) Palette.Warn else Palette.Good }),
                ),
            )
            if (h.rhrWeekly.size >= 3) {
                Spacer(Modifier.height(10.dp))
                LineChart(listOf(Series(h.rhrWeekly.map { it.second }, Palette.Bad, "Ruhepuls")), height = 120.dp, startLabel = Fmt.dayShort(h.rhrWeekly.first().first), endLabel = Fmt.dayShort(h.rhrWeekly.last().first), xLabels = h.rhrWeekly.map { weekTip(it.first) }, decimals = 1, unit = "bpm (Wochen-Ø)")
            }
        }
    }
    item {
        SectionCard(title = "Herzfrequenzerholung (60 s)", icon = Icons.Filled.Speed) {
            if (h.hrr.isEmpty()) {
                Text("Noch keine Messung – Trainings auf der Uhr starten.", style = MaterialTheme.typography.bodyMedium)
                return@SectionCard
            }
            val list = h.hrr.take(8).reversed()
            BarChart(
                values = list.map { it.drop60.toDouble() },
                colors = list.map { if (it.drop60 >= 25) Palette.Good else if (it.drop60 > 12) Palette.Ok else Palette.Bad },
                labels = list.map { Fmt.dayShort(it.date) },
                goal = 12.0,
                height = 120.dp,
                tipLabels = list.map { dayTip(it.date) + " · " + it.title },
                name = "Abfall in 60 s",
                unit = "Schläge",
                goalName = "auffällig unter",
            )
            Spacer(Modifier.height(6.dp))
            Text("Schläge Abfall in 60 s · Linie = 12 (darunter auffällig)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item { ZonesCard(r) }
    item { CoachCard(Coach.heart(r), title = "Coach · Herz") }
}

// ---------------------------------------------------------------- Stress
fun LazyListScope.stressItems(r: AnalysisResult) {
    val s = r.stress
    item {
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val v = s.avg7 ?: s.today
                SemiGauge(v?.let { (it / 100).toFloat() }, listOf(Palette.Good, Palette.Ok, Palette.Warn, Palette.Bad), v?.let { Fmt.int(it) } ?: "–", "Stress-Index · ${s.label}")
            }
            Spacer(Modifier.height(8.dp))
            StatRow(
                listOf(
                    Triple(s.today?.let { Fmt.int(it) } ?: "–", "heute", null),
                    Triple(s.avg30?.let { Fmt.int(it) } ?: "–", "Ø 30 T", null),
                    Triple(s.nightStress?.let { Fmt.int(it) } ?: "–", "nachts", null),
                ),
            )
        }
    }
    item {
        SectionCard(title = "Über den Tag") {
            BarChart(
                values = s.byHour,
                colors = s.byHour.map { v -> v?.let { HeartExtras.stressLabel(it).second.color() } ?: Palette.Neutral },
                labels = (0 until 24).map { if (it % 3 == 0) "$it" else "" },
                height = 120.dp,
                tipLabels = (0 until 24).map { "$it–${it + 1} Uhr · Ø 14 Tage" },
                format = { "${Fmt.int(it)} · ${HeartExtras.stressLabel(it).first}" },
                name = "Stress",
            )
            val rest = s.restPct
            val high = s.highPct
            if (rest != null && high != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        listOf(rest to Palette.Good, (1 - rest - high).coerceAtLeast(0.0) to Palette.Ok, high to Palette.Warn),
                        size = 96.dp, strokeWidth = 14.dp,
                        names = listOf("entspannt", "normal", "angespannt"), format = { Fmt.pct(it) },
                    ) {
                        Text(Fmt.pct(rest), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Legend(listOf("entspannt" to Palette.Good))
                        Spacer(Modifier.height(4.dp))
                        Legend(listOf("normal" to Palette.Ok))
                        Spacer(Modifier.height(4.dp))
                        Legend(listOf("angespannt" to Palette.Warn))
                    }
                }
            }
        }
    }
    item {
        SectionCard(title = "30 Tage") {
            LineChart(
                listOf(Series(s.daily.map { it.second }, Palette.Orange, "Stress")), height = 120.dp,
                startLabel = s.daily.firstOrNull()?.let { Fmt.dayShort(it.first) }, endLabel = "heute",
                xLabels = dayTips(s.daily.map { it.first }), format = { "${Fmt.int(it)} · ${HeartExtras.stressLabel(it).first}" },
            )
        }
    }
    item { CoachCard(Coach.stress(r), title = "Coach · Stress") }
}

// ---------------------------------------------------------------- Fitness & Leistung
fun LazyListScope.performanceItems(r: AnalysisResult) {
    val p = r.performance
    item { Vo2Card(r, null) }
    item {
        SectionCard(title = "Aerobe Effizienz", icon = Icons.Filled.Insights) {
            val chg = p.efChangePct
            StatRow(
                listOf(
                    Triple(chg?.let { Fmt.signed(it, 1) + " %" } ?: "–", "vs. vor 4 Wo.", chg?.let { if (it >= 0) Palette.Good else Palette.Warn }),
                    Triple(p.paceAtZ2SecKm?.let { Fmt.pace(it).removeSuffix(" /km") } ?: "–", "Pace Zone 2", Palette.Zones[2]),
                    Triple(p.lthr?.let { "$it" } ?: "–", "Schwelle bpm", null),
                ),
            )
            if (p.efSeries.size >= 3) {
                Spacer(Modifier.height(10.dp))
                LineChart(
                    listOf(Series(p.efSeries.map { it.second * 1000 }, Palette.Teal, "Effizienz")), height = 110.dp,
                    startLabel = Fmt.dayShort(p.efSeries.first().first), endLabel = Fmt.dayShort(p.efSeries.last().first),
                    xLabels = dayTips(p.efSeries.map { it.first }), format = { "Effizienz ${Fmt.num(it, 1)}" },
                )
                Text("steigend = schneller bei gleichem Puls", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (p.runPaces.size >= 3) {
        item {
            SectionCard(title = "Pace · 90 Tage", icon = Icons.Filled.Timeline) {
                LineChart(
                    listOf(Series(p.runPaces.map { it.second / 60.0 }, Palette.Blue, "Pace")), height = 110.dp,
                    startLabel = Fmt.dayShort(p.runPaces.first().first), endLabel = Fmt.dayShort(p.runPaces.last().first), decimals = 1,
                    xLabels = dayTips(p.runPaces.map { it.first }), format = { Fmt.pace(it * 60.0) },
                )
                Text("min/km – tiefer = schneller · ${p.runCount90} Läufe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item {
        SectionCard(title = "Intensität & Leistung", icon = Icons.Filled.Speed) {
            StatRow(
                listOf(
                    Triple(Fmt.int(p.hardMin28), "Min. Z4–5", null),
                    Triple("${p.hardSessions28}", "harte Einh.", null),
                    Triple(p.peakHr90?.let { "$it" } ?: "–", "Spitzenpuls", null),
                ),
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                listOf(
                    Triple(p.cyclingPowerAvg?.let { Fmt.int(it) + " W" } ?: "–", "Rad Ø Watt", null),
                    Triple(p.cyclingWkg?.let { Fmt.num(it, 1) } ?: "–", "W/kg", null),
                    Triple(p.cyclingSpeedKmh?.let { Fmt.num(it, 1) } ?: "–", "Rad km/h", null),
                ),
            )
        }
    }
    item { ZonesCard(r) }
    item { CoachCard(Coach.performance(r), title = "Coach · Leistung") }
}

// ---------------------------------------------------------------- Bewegung & Alltag
fun LazyListScope.activityItems(r: AnalysisResult) {
    val a = r.activity
    item {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val m = a.movementIndex
                ScoreRing(m?.value, "", scoreToneOf(m?.value ?: 0).takeIf { m != null } ?: Tone.NEUTRAL, size = 100.dp, caption = "Bewegung")
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    BigValue(a.stepsToday?.let { Fmt.int(it) } ?: "–", "Schritte", "heute · Ø ${a.steps7Avg?.let { Fmt.int(it) } ?: "–"}")
                }
            }
            a.movementIndex?.let {
                Spacer(Modifier.height(10.dp))
                ScoreBreakdown(it)
            }
        }
    }
    item {
        val days = r.days.takeLast(14)
        SectionCard(title = "Schritte · 14 Tage", icon = Icons.AutoMirrored.Filled.DirectionsWalk) {
            BarChart(
                values = days.map { it.steps?.toDouble() },
                colors = days.map { if ((it.steps ?: 0) >= a.stepGoal) Palette.Good else Palette.Blue },
                goal = a.stepGoal.toDouble(),
                labels = days.map { Fmt.weekday(it.date).take(2) },
                tipLabels = dayTips(days.map { it.date }),
                name = "Schritte",
            )
        }
    }
    if (a.stepsByHour.isNotEmpty()) {
        item {
            SectionCard(title = "Aktivität über den Tag") {
                BarChart(
                    values = a.stepsByHour,
                    colors = a.stepsByHour.map { if (it >= 250) Palette.Good else Palette.Neutral },
                    labels = (0 until 24).map { if (it % 3 == 0) "$it" else "" },
                    height = 110.dp,
                    tipLabels = (0 until 24).map { "$it–${it + 1} Uhr · Ø 14 Tage" },
                    name = "Schritte",
                )
                val act = a.activeHours7Avg
                val ina = a.inactiveHours7Avg
                if (act != null && ina != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            listOf(act to Palette.Good, ina to Palette.Bad), size = 96.dp, strokeWidth = 14.dp,
                            names = listOf("aktiv", "inaktiv"), format = { "Ø " + Fmt.num(it, 1) + " h" },
                        ) {
                            Text(Fmt.num(ina, 1) + " h", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            KeyValue("Aktiv", "Ø ${Fmt.num(act, 1)} h", Palette.Good)
                            KeyValue("Inaktiv (≈ Sitzzeit)", "Ø ${Fmt.num(ina, 1)} h", if (ina >= 8) Palette.Bad else Palette.Warn)
                        }
                    }
                }
            }
        }
    }
    item {
        SectionCard(title = "Wochenbilanz") {
            val tw = a.thisWeek
            val lw = a.lastWeek
            if (tw == null) {
                Text("Noch keine Daten.")
                return@SectionCard
            }
            fun pct(x: Double, y: Double?): Float = if (y == null || y <= 0) 1f else (x / max2(x, y)).toFloat()
            listOf(
                Triple("Schritte", tw.steps.toDouble(), lw?.steps?.toDouble()),
                Triple("Aktivminuten", tw.activeMinutes, lw?.activeMinutes),
                Triple("Trainingsbelastung", tw.load, lw?.load),
                Triple("Aktive kcal", tw.activeKcal, lw?.activeKcal),
            ).forEach { (label, now, prev) ->
                val up = prev == null || now >= prev
                LabeledBar(
                    label, pct(now, prev),
                    Fmt.int(now) + (prev?.let { "  (" + Fmt.signed((now - it) / it.coerceAtLeast(1.0) * 100) + " %)" } ?: ""),
                    if (up) Palette.Good else Palette.Warn,
                )
            }
        }
    }
    item {
        SectionCard(title = "Alltag & Rhythmus") {
            StatRow(
                listOf(
                    Triple(a.everydayLoad7Avg?.let { Fmt.int(it) } ?: "–", "Alltags-TRIMP", null),
                    Triple(a.regularity?.let { "$it" } ?: "–", "Regelmäßigk.", a.regularity?.let { scoreToneOf(it).color() }),
                    Triple("${a.daysGoalReached7}/7", "Ziel erreicht", if (a.daysGoalReached7 >= 5) Palette.Good else Palette.Warn),
                ),
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                listOf(
                    Triple(a.hardSessionGapAvgH?.let { Fmt.int(it) + " h" } ?: "–", "Pause hart↔hart", a.hardSessionGapAvgH?.let { if (it < 48) Palette.Warn else Palette.Good }),
                    Triple(a.runCadence?.let { Fmt.int(it) } ?: "–", "Laufkadenz", null),
                    Triple(a.activeKcalShare7?.let { Fmt.pct(it) } ?: "–", "aktive kcal", null),
                ),
            )
        }
    }
    item { CoachCard(Coach.dailyActivity(r), title = "Coach · Bewegung") }
}

private fun max2(a: Double, b: Double) = if (a > b) a else b

// ---------------------------------------------------------------- Langzeit-Trends
fun LazyListScope.trendItems(r: AnalysisResult) {
    r.trends.chunked(2).forEach { pair ->
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { t ->
                    SectionCard(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val l30 = t.last30
                        val p30 = t.prev30
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(l30?.let { Fmt.num(it, t.decimals) } ?: "–", style = MaterialTheme.typography.headlineSmall)
                            if (t.unit.isNotEmpty()) Text(" ${t.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (l30 != null && p30 != null) {
                            val d = l30 - p30
                            Text((if (d >= 0) "▲ " else "▼ ") + Fmt.signed(d, t.decimals), style = MaterialTheme.typography.labelMedium, color = t.tone.color())
                        }
                        if (t.series.size >= 3) {
                            Spacer(Modifier.height(6.dp))
                            Sparkline(
                                t.series.map { it.second }, t.tone.color().takeIf { t.tone != Tone.NEUTRAL } ?: Palette.Blue, Modifier.fillMaxWidth(), height = 40.dp,
                                xLabels = t.series.map { weekTip(it.first) }, format = { Fmt.num(it, t.decimals) + if (t.unit.isNotEmpty()) " ${t.unit}" else "" },
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    item { Text("Ø der letzten 30 Tage · Pfeil = Veränderung zu den 30 Tagen davor · Verlauf = Wochenmittel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

// ---------------------------------------------------------------- Normalwerte
fun LazyListScope.baselineItems(r: AnalysisResult) {
    item {
        SectionCard(title = "Heute vs. dein Normalbereich") {
            r.baselines.forEach { b ->
                val unit = if (b.unit.isNotEmpty()) " ${b.unit}" else ""
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(b.current?.let { Fmt.num(it, b.decimals) + unit } ?: "–", style = MaterialTheme.typography.titleSmall, color = b.tone.color())
                    }
                    BulletRange(b.current, b.mean30, b.sd30, b.tone.color())
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Dunkles Band = normal (±1 SD), hell = ±2 SD, Strich = Ø 30 Tage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item { CoachCard(Coach.baselines(r), title = "Coach · Normalwerte") }
}

// ---------------------------------------------------------------- Zusammenhänge
fun LazyListScope.correlationItems(r: AnalysisResult) {
    val sorted = r.correlations.sortedByDescending { it.significant }
    sorted.forEach { c ->
        item {
            SectionCard(title = c.title, icon = Icons.Filled.Insights) {
                if (c.significant) Pill("Muster erkannt", Palette.Teal) else Pill("kein klares Muster", Palette.Neutral)
                if (c.points.size >= 3) {
                    Spacer(Modifier.height(10.dp))
                    ScatterChart(c.points, if (c.significant) Palette.Teal else Palette.Neutral, xLabel = c.xLabel, yLabel = c.yLabel)
                }
                Spacer(Modifier.height(8.dp))
                Text(c.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item { Text("Statistische Muster in deinen Daten – kein Beweis für Ursachen, keine Diagnose.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Kurzes Datum für Workouts. */
fun shortDate(r: AnalysisResult, i: java.time.Instant): String = Fmt.dayShort(i.atZone(r.zone).toLocalDate())
