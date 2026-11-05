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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.material3.HorizontalDivider
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
import de.vitalcoach.core.BaselineStatus
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Provenance
import de.vitalcoach.core.Tone
import de.vitalcoach.core.baselines
import de.vitalcoach.core.dailyActivity
import de.vitalcoach.core.heart
import de.vitalcoach.core.heartHealth
import de.vitalcoach.core.performance
import de.vitalcoach.core.stress

@Composable
fun AnalysesScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Analysen", style = MaterialTheme.typography.headlineMedium)
            Text("Alle Bereiche im Detail – tippe auf einen Eintrag.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r != null) {
            val hh = r.heartHealth
            item { HubItem("Herzgesundheit", hh.score?.toString(), hh.label.ifEmpty { "Life's Essential 8" }, Icons.Filled.Favorite, hh.tone) { vm.open(Route.Metric(MetricKey.HEART_HEALTH)) } }
            item {
                HubItem(
                    "Herz & Kreislauf", r.heart.avgHr7?.let { Fmt.int(it) },
                    "Ø Puls, nächtliche Absenkung, HRV- & Ruhepuls-Trend, Pulserholung", Icons.Filled.MonitorHeart, Tone.NEUTRAL,
                ) { vm.open(Route.Metric(MetricKey.HEART)) }
            }
            item {
                val s = r.stress
                HubItem("Stress & Nervensystem", (s.avg7 ?: s.today)?.let { Fmt.int(it) }, "Stress-Index: ${s.label}", Icons.Filled.Psychology, s.tone) { vm.open(Route.Metric(MetricKey.STRESS)) }
            }
            item {
                HubItem(
                    "Fitness & Leistung", r.fitness.vo2max?.let { Fmt.num(it, 1) },
                    "VO2max, Fitnessalter, Effizienz, Laktatschwelle", Icons.Filled.Speed,
                    scoreTone(r.fitness.vo2CategoryIndex?.let { 20 + it * 16 }),
                ) { vm.open(Route.Metric(MetricKey.PERFORMANCE)) }
            }
            item {
                val m = r.activity.movementIndex
                HubItem("Bewegung & Alltag", m?.value?.toString(), "Bewegungsindex, inaktive Zeit, Tagesverteilung", Icons.AutoMirrored.Filled.DirectionsWalk, scoreTone(m?.value)) {
                    vm.open(Route.Metric(MetricKey.ACTIVITY))
                }
            }
            item { HubItem("Langzeit-Trends", null, "Entwicklung über 30 Tage bis 1 Jahr", Icons.Filled.Timeline, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.TRENDS)) } }
            item {
                val unusual = r.baselines.count { it.status == BaselineStatus.HIGH || it.status == BaselineStatus.LOW }
                HubItem(
                    "Persönliche Normalwerte", null,
                    if (unusual > 0) "$unusual ungewöhnliche Abweichung(en)" else "Alle Werte im Normalbereich",
                    Icons.Filled.Rule, if (unusual > 0) Tone.WARN else Tone.GOOD,
                ) { vm.open(Route.Metric(MetricKey.BASELINES)) }
            }
            item {
                val sig = r.correlations.count { it.significant }
                HubItem("Zusammenhänge", null, if (sig > 0) "$sig Muster in deinen Daten erkannt" else "Muster zwischen Schlaf, Training & Stress", Icons.Filled.Insights, Tone.NEUTRAL) {
                    vm.open(Route.Metric(MetricKey.CORRELATIONS))
                }
            }
            item {
                HubItem("Körperwerte", r.fitness.bmi?.let { Fmt.num(it, 1) }, "Gewicht, BMI, Körperfett", Icons.Filled.Person, Tone.NEUTRAL) { vm.open(Route.Metric(MetricKey.BODY)) }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
        item { HubItem("Einstellungen", null, "Profil, Datenquelle, KI-Coach", Icons.Filled.Settings, Tone.NEUTRAL) { vm.open(Route.Settings) } }
        item { HubItem("So rechnet VitalCoach", null, "Formeln und Quellen", Icons.Filled.Science, Tone.NEUTRAL) { vm.open(Route.Methods) } }
        item { HubItem("Datenschutz", null, "Deine Daten bleiben auf dem Handy", Icons.Filled.Lock, Tone.NEUTRAL) { vm.open(Route.Privacy) } }
        item {
            SectionCard(title = "Woher kommen die Werte?") { ProvenanceLegend() }
        }
    }
}

// ---------------------------------------------------------------- Herzgesundheit
fun LazyListScope.heartHealthItems(r: AnalysisResult) {
    val h = r.heartHealth
    item {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(h.score, "Herzgesundheit", h.tone, size = 110.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.label, style = MaterialTheme.typography.titleLarge, color = h.tone.color())
                    Text("${h.covered} von ${h.components.size} Bereichen erfasst", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("≥ 80 hoch · 50–79 mittel · < 50 niedrig (AHA)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item {
        SectionCard(title = "Bereiche") {
            h.components.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    ProvenanceChip(c.provenance)
                    Spacer(Modifier.width(8.dp))
                    Text(c.score?.toString() ?: "–", style = MaterialTheme.typography.titleMedium, color = scoreTone(c.score).color())
                }
                Text(c.value, style = MaterialTheme.typography.bodyMedium)
                if (c.score != null) {
                    Spacer(Modifier.height(4.dp))
                    LabeledBar("", c.score!! / 100f, "", scoreTone(c.score).color())
                }
                Text(c.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!c.le8) Text("Zusatzwert deiner Uhr (nicht Teil von Life's Essential 8)", style = MaterialTheme.typography.labelSmall, color = Palette.Teal)
            }
        }
    }
    item { CoachCard(Coach.heartHealth(r)) }
}

// ---------------------------------------------------------------- Herz & Kreislauf
fun LazyListScope.heartItems(r: AnalysisResult) {
    val h = r.heart
    val zone = r.zone
    item {
        SectionCard(title = "Herzfrequenz im Alltag") {
            Row {
                Column(Modifier.weight(1f)) { BigValue(h.avgHrToday?.let { Fmt.int(it) } ?: "–", "bpm", "Ø heute (24 h)") }
                Column(Modifier.weight(1f)) { BigValue(h.avgHr7?.let { Fmt.int(it) } ?: "–", "bpm", "Ø 7 Tage") }
            }
            h.avgHr30?.let { KeyValue("Ø 30 Tage", "${Fmt.int(it)} bpm") }
            h.trainingHighHrPct28?.let { KeyValue("Pulsbelastung im Training (Zeit in Zone 4–5)", Fmt.pct(it)) }
            Spacer(Modifier.height(4.dp))
            ProvenanceChip(Provenance.CALCULATED)
        }
    }
    item {
        SectionCard(title = "Herz in der Nacht") {
            if (h.nightCurve.size >= 3) {
                LineChart(
                    listOf(Series(h.nightCurve.map { it.second }, Palette.Bad, "Puls")),
                    height = 130.dp,
                    startLabel = h.nightCurve.first().first.atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
                    endLabel = h.nightCurve.last().first.atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
                )
                Spacer(Modifier.height(8.dp))
            }
            h.sleepAvgHr?.let { KeyValue("Ø Puls im Schlaf", "${Fmt.int(it)} bpm") }
            r.todayMetrics?.restingHr?.let { KeyValue("Nächtlicher Ruhepuls", "${Fmt.int(it)} bpm") }
            r.todayMetrics?.hrv?.let { KeyValue("Nächtliche HRV", "${Fmt.int(it)} ms") }
            val dip = h.dipPct
            if (dip != null) KeyValue("Nächtliche Pulsabsenkung", "${Fmt.int(dip)} %", if (dip >= 10) Palette.Good else Palette.Warn)
            h.dipAvg7?.let { KeyValue("Ø Absenkung 7 Nächte", "${Fmt.int(it)} %") }
            h.lowestHrPos?.let { KeyValue("Tiefster Puls nach", "${Fmt.int(it * 100)} % der Nacht", if (it <= 0.7) Palette.Good else Palette.Warn) }
            Text(
                "Gesund ist eine Absenkung von 10–20 % gegenüber dem Vorabend und ein Tiefpunkt in der ersten Nachthälfte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        SectionCard(title = "HRV-Entwicklung") {
            if (!r.quality.hasHrv) {
                Text("Keine HRV-Werte über Health Connect verfügbar.", style = MaterialTheme.typography.bodyMedium)
                return@SectionCard
            }
            Row {
                listOf("7 T" to h.hrv7, "30 T" to h.hrv30, "90 T" to h.hrv90, "180 T" to h.hrv180).forEach { (l, v) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v?.let { Fmt.int(it) } ?: "–", style = MaterialTheme.typography.titleLarge)
                        Text("Ø $l", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (h.hrvWeekly.size >= 3) {
                Spacer(Modifier.height(8.dp))
                LineChart(
                    listOf(Series(h.hrvWeekly.map { it.second }, Palette.Purple, "HRV")),
                    height = 120.dp,
                    startLabel = Fmt.dayShort(h.hrvWeekly.first().first),
                    endLabel = Fmt.dayShort(h.hrvWeekly.last().first),
                )
                Text("Wochenmittel (bis 6 Monate)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            h.hrvCv7?.let { KeyValue("HRV-Schwankung (CV, 7 Tage)", "${Fmt.num(it, 1)} %", if (it > 10) Palette.Warn else null) }
        }
    }
    item {
        SectionCard(title = "Ruhepuls-Entwicklung") {
            Row {
                listOf("7 T" to h.rhr7, "30 T" to h.rhr30, "90 T" to h.rhr90, "vor 1 J." to h.rhr365).forEach { (l, v) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v?.let { Fmt.num(it, 1) } ?: "–", style = MaterialTheme.typography.titleLarge)
                        Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (h.rhrWeekly.size >= 3) {
                Spacer(Modifier.height(8.dp))
                LineChart(
                    listOf(Series(h.rhrWeekly.map { it.second }, Palette.Bad, "Ruhepuls")),
                    height = 120.dp,
                    startLabel = Fmt.dayShort(h.rhrWeekly.first().first),
                    endLabel = Fmt.dayShort(h.rhrWeekly.last().first),
                    decimals = 0,
                )
                Text("Wochenmittel (bis 1 Jahr)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            h.rhrSd14?.let { KeyValue("Stabilität (Schwankung 14 Tage)", "± ${Fmt.num(it, 1)} bpm", if (it > 3) Palette.Warn else Palette.Good) }
        }
    }
    item {
        SectionCard(title = "Herzfrequenzerholung (HRR)") {
            if (h.hrr.isEmpty()) {
                Text(
                    "Noch keine Messung. Die Pulserholung wird aus Trainings mit dichter Pulsaufzeichnung berechnet (Training auf der Uhr starten).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@SectionCard
            }
            val first = h.hrr.first()
            BigValue("${first.drop60}", "Schläge / 60 s", "${first.title}, ${Fmt.dayShort(first.date)} – ${if (first.afterExercise) "nach Trainingsende" else "nach Belastungsspitze"}")
            h.hrrTrend?.let { KeyValue("Trend (letzte 3 vs. 3 davor)", Fmt.signed(it) + " Schläge", if (it >= 0) Palette.Good else Palette.Warn) }
            Spacer(Modifier.height(6.dp))
            h.hrr.take(6).forEach { KeyValue("${Fmt.dayShort(it.date)} ${it.title}", "${it.drop60} (von ${it.peak} bpm)") }
            Text(
                "Je schneller der Puls nach Belastung sinkt, desto besser. Werte ≤ 12 nach Belastungsende gelten als auffällig (Cole et al. 1999). " +
                    "Lockeres Auslaufen senkt den Wert gegenüber Stehenbleiben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item { ZonesCard(r) }
    item { CoachCard(Coach.heart(r)) }
}

// ---------------------------------------------------------------- Stress
fun LazyListScope.stressItems(r: AnalysisResult) {
    val s = r.stress
    item {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val v = s.avg7 ?: s.today
                ScoreRing(v?.toInt(), "Stress-Index", s.tone, size = 100.dp, caption = s.label)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    s.today?.let { KeyValue("Heute", Fmt.int(it)) }
                    s.avg7?.let { KeyValue("Ø 7 Tage", Fmt.int(it)) }
                    s.avg30?.let { KeyValue("Ø 30 Tage", Fmt.int(it)) }
                    s.nightStress?.let { KeyValue("Nachts", Fmt.int(it)) }
                    ProvenanceChip(Provenance.ESTIMATED)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("0–25 ruhig · 26–50 niedrig · 51–75 mittel · 76–100 hoch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        SectionCard(title = "Stress über den Tag (14 Tage)") {
            BarChart(
                values = s.byHour.map { it ?: 0.0 },
                colors = s.byHour.map { v -> v?.let { de.vitalcoach.core.HeartExtras.stressLabel(it).second.color() } ?: Palette.Neutral },
                labels = (0 until 24).map { if (it % 3 == 0) "$it" else "" },
                height = 110.dp,
            )
            Text(
                "Nur Phasen ohne Bewegung werden bewertet. Leere Stunden: keine ruhigen Messungen (Schlaf oder Bewegung).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            s.restPct?.let { LabeledBar("Ruhephasen (entspannt)", it.toFloat(), Fmt.pct(it), Palette.Good) }
            s.highPct?.let { LabeledBar("Belastungsphasen (angespannt)", it.toFloat(), Fmt.pct(it), Palette.Warn) }
        }
    }
    item {
        SectionCard(title = "Verlauf (30 Tage)") {
            LineChart(
                listOf(Series(s.daily.map { it.second }, Palette.Orange, "Stress")),
                height = 110.dp,
                startLabel = s.daily.firstOrNull()?.let { Fmt.dayShort(it.first) },
                endLabel = "heute",
            )
        }
    }
    item { CoachCard(Coach.stress(r)) }
}

// ---------------------------------------------------------------- Fitness & Leistung
fun LazyListScope.performanceItems(r: AnalysisResult) {
    val p = r.performance
    item { Vo2Card(r, null) }
    item {
        SectionCard(title = "Aerobe Effizienz (Grundlagenausdauer)") {
            val chg = p.efChangePct
            if (p.efRecent == null) {
                Text("Für diese Auswertung braucht es lockere Läufe mit GPS (mind. 20 min).", style = MaterialTheme.typography.bodyMedium)
                return@SectionCard
            }
            Row {
                Column(Modifier.weight(1f)) {
                    BigValue(chg?.let { Fmt.signed(it, 1) } ?: "–", "%", "Tempo bei gleichem Puls vs. vor 4 Wochen", if ((chg ?: 0.0) >= 0) Palette.Good else Palette.Warn)
                }
            }
            p.paceAtZ2SecKm?.let { KeyValue("Geschätztes Tempo bei ${p.z2Hr} bpm", Fmt.pace(it)) }
            if (p.efSeries.size >= 3) {
                Spacer(Modifier.height(8.dp))
                LineChart(
                    listOf(Series(p.efSeries.map { it.second * 1000 }, Palette.Teal, "Effizienz")),
                    height = 110.dp,
                    startLabel = Fmt.dayShort(p.efSeries.first().first),
                    endLabel = Fmt.dayShort(p.efSeries.last().first),
                )
                Text("Efficiency Factor × 1000 (m/min pro Herzschlag) – steigend = fitter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (p.runPaces.size >= 3) {
        item {
            SectionCard(title = "Pace-Entwicklung beim Laufen (90 Tage)") {
                LineChart(
                    listOf(Series(p.runPaces.map { it.second / 60.0 }, Palette.Blue, "Pace")),
                    height = 110.dp,
                    startLabel = Fmt.dayShort(p.runPaces.first().first),
                    endLabel = Fmt.dayShort(p.runPaces.last().first),
                    decimals = 1,
                )
                Text("Minuten pro km – niedriger = schneller (${p.runCount90} Läufe)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item {
        SectionCard(title = "Schwelle & hohe Intensität") {
            val lthr = p.lthr
            if (lthr != null) {
                KeyValue("Geschätzte Laktatschwelle", "≥ $lthr bpm")
                p.lthrDate?.let { Text("aus der härtesten 20-Minuten-Belastung am ${Fmt.dayShort(it)} (Friel-Methode)", style = MaterialTheme.typography.bodySmall) }
            } else {
                Text("Laktatschwelle: noch keine 20-minütige harte Belastung gefunden.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            KeyValue("Minuten in Zone 4–5 (28 Tage)", Fmt.int(p.hardMin28))
            KeyValue("Harte Einheiten (28 Tage)", "${p.hardSessions28}")
            p.peakHr90?.let { KeyValue("Höchster Puls (90 Tage)", "$it bpm") }
            Text(
                "Die anaerobe Leistungsfähigkeit lässt sich ohne Leistungs- oder Laktatmessung nicht seriös bestimmen – die Werte oben zeigen deinen hochintensiven Trainingsreiz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ProvenanceChip(Provenance.ESTIMATED)
        }
    }
    item {
        SectionCard(title = "Radfahren & Belastung") {
            val power = p.cyclingPowerAvg
            if (power != null) KeyValue("Ø Leistung (90 Tage)", "${Fmt.int(power)} W" + (p.cyclingWkg?.let { " · ${Fmt.num(it, 1)} W/kg" } ?: ""))
            else Text("Keine Leistungsdaten (Wattmessung) vorhanden.", style = MaterialTheme.typography.bodySmall)
            p.cyclingSpeedKmh?.let { KeyValue("Ø Geschwindigkeit", "${Fmt.num(it, 1)} km/h") }
            KeyValue("Belastung 7 / 28 / 90 Tage", "${Fmt.int(p.load7)} / ${Fmt.int(p.load28)} / ${Fmt.int(p.load90)} TRIMP")
            r.load.monotony?.let { KeyValue("Trainingsmonotonie", Fmt.num(it, 1)) }
            KeyValue("Fitness (CTL)", Fmt.int(r.load.ctl))
        }
    }
    item { CoachCard(Coach.performance(r)) }
}

// ---------------------------------------------------------------- Bewegung & Alltag
fun LazyListScope.activityItems(r: AnalysisResult) {
    val a = r.activity
    item {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val m = a.movementIndex
                ScoreRing(m?.value, "Bewegungsindex", scoreTone(m?.value), size = 100.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    BigValue(a.stepsToday?.let { Fmt.int(it) } ?: "–", "Schritte", "heute · Ø ${a.steps7Avg?.let { Fmt.int(it) } ?: "–"}")
                    ProvenanceChip(Provenance.MEASURED)
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
        SectionCard(title = "Schritte (14 Tage)") {
            BarChart(
                values = days.map { it.steps?.toDouble() },
                colors = days.map { if ((it.steps ?: 0) >= a.stepGoal) Palette.Good else Palette.Blue },
                goal = a.stepGoal.toDouble(),
                labels = days.map { Fmt.weekday(it.date).take(2) },
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
                )
                Text("Ø Schritte je Stunde (14 Tage). Grün = aktive Stunde (≥ 250 Schritte).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                a.inactiveHours7Avg?.let { KeyValue("Inaktive Zeit (Näherung Sitzzeit)", "Ø ${Fmt.num(it, 1)} h/Tag", if (it >= 8) Palette.Warn else null) }
                a.activeHours7Avg?.let { KeyValue("Aktive Stunden", "Ø ${Fmt.num(it, 1)} h/Tag") }
                val act = a.activeHours7Avg
                val ina = a.inactiveHours7Avg
                if (act != null && ina != null && ina > 0) KeyValue("Verhältnis aktiv : inaktiv", "1 : ${Fmt.num(ina / act.coerceAtLeast(0.1), 1)}")
                a.activeHoursToday?.let { KeyValue("Heute aktiv / inaktiv", "$it h / ${a.inactiveHoursToday ?: 0} h") }
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
            Row(Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1.3f))
                Text("7 Tage", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Vorwoche", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            }
            fun row(label: String, a1: String, b1: String) = Triple(label, a1, b1)
            listOf(
                row("Schritte", Fmt.int(tw.steps), lw?.let { Fmt.int(it.steps) } ?: "–"),
                row("Aktivminuten", Fmt.int(tw.activeMinutes), lw?.let { Fmt.int(it.activeMinutes) } ?: "–"),
                row("Trainingsbelastung", Fmt.int(tw.load), lw?.let { Fmt.int(it.load) } ?: "–"),
                row("Aktive kcal", Fmt.int(tw.activeKcal), lw?.let { Fmt.int(it.activeKcal) } ?: "–"),
                row("Trainings", "${tw.workouts}", lw?.let { "${it.workouts}" } ?: "–"),
            ).forEach { (l, x, y) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(l, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
                    Text(x, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(y, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    item {
        SectionCard(title = "Alltagsbelastung & Regelmäßigkeit") {
            a.everydayLoad7Avg?.let { KeyValue("Alltagsbelastungsindex (außerhalb Training)", "Ø ${Fmt.int(it)} TRIMP/Tag") }
            a.everydayShare7?.let { KeyValue("Anteil an der Gesamtbelastung", Fmt.pct(it)) }
            a.activeKcalShare7?.let { KeyValue("Aktive kcal am Gesamtverbrauch", Fmt.pct(it)) }
            a.regularity?.let { KeyValue("Regelmäßigkeit der Bewegung", "$it / 100") }
            KeyValue("Ziel erreicht (7 Tage)", "${a.daysGoalReached7} von 7 Tagen")
            a.hardSessionGapAvgH?.let { KeyValue("Ø Pause zwischen harten Einheiten", "${Fmt.int(it)} h", if (it < 48) Palette.Warn else Palette.Good) }
            a.runCadence?.let { KeyValue("Laufkadenz", "${Fmt.int(it)} Schritte/min") }
            Spacer(Modifier.height(4.dp))
            ProvenanceChip(Provenance.CALCULATED)
        }
    }
    item { CoachCard(Coach.dailyActivity(r)) }
    item { CoachCard(Coach.activity(r), title = "Schritte & Kalorien", initiallyExpanded = false) }
}

// ---------------------------------------------------------------- Langzeit-Trends
fun LazyListScope.trendItems(r: AnalysisResult) {
    r.trends.forEach { t ->
        item {
            SectionCard(title = t.name) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneDot(t.tone)
                    Spacer(Modifier.width(8.dp))
                    Text(t.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                if (t.series.size >= 3) {
                    Spacer(Modifier.height(8.dp))
                    LineChart(
                        listOf(Series(t.series.map { it.second }, Palette.Blue, t.name)),
                        height = 100.dp,
                        startLabel = Fmt.dayShort(t.series.first().first),
                        endLabel = Fmt.dayShort(t.series.last().first),
                        decimals = t.decimals,
                    )
                }
            }
        }
    }
    item {
        Text(
            "Die App speichert Tageswerte lokal, damit Trends über bis zu ein Jahr möglich sind. Je länger du VitalCoach nutzt, desto aussagekräftiger werden sie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Normalwerte
fun LazyListScope.baselineItems(r: AnalysisResult) {
    item { CoachCard(Coach.baselines(r)) }
    item {
        SectionCard(title = "Heute im Vergleich zu deinem Normalbereich") {
            r.baselines.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                val unit = if (b.unit.isNotEmpty()) " ${b.unit}" else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                        val mean = b.mean30
                        val sd = b.sd30
                        Text(
                            if (mean != null && sd != null) "Normal: ${Fmt.num(mean - sd, b.decimals)}–${Fmt.num(mean + sd, b.decimals)}$unit" + (b.mean90?.let { " · Ø 90 T: ${Fmt.num(it, b.decimals)}" } ?: "")
                            else "Normalbereich wird ermittelt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(b.current?.let { Fmt.num(it, b.decimals) + unit } ?: "–", style = MaterialTheme.typography.titleMedium)
                        Text(b.status.label, style = MaterialTheme.typography.labelSmall, color = b.tone.color())
                    }
                }
                Spacer(Modifier.height(2.dp))
                ProvenanceChip(b.provenance)
            }
        }
    }
}

// ---------------------------------------------------------------- Zusammenhänge
fun LazyListScope.correlationItems(r: AnalysisResult) {
    item {
        Text(
            "Statistische Muster in DEINEN Daten. Ein Zusammenhang ist kein Beweis für eine Ursache und keine medizinische Diagnose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val sorted = r.correlations.sortedByDescending { it.significant }
    sorted.forEach { c ->
        item {
            SectionCard(title = c.title, icon = Icons.Filled.Insights) {
                if (c.significant) Pill("Muster erkannt", Palette.Teal) else Pill("kein klares Muster", Palette.Neutral)
                Spacer(Modifier.height(6.dp))
                Text(c.text, style = MaterialTheme.typography.bodyMedium)
                val rr = c.r
                val pp = c.pValue
                if (rr != null && pp != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("r = ${Fmt.num(rr, 2)} · p = ${Fmt.num(pp, 3)} · n = ${c.n}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Kurzes Datum für Workouts. */
fun shortDate(r: AnalysisResult, i: java.time.Instant): String = Fmt.dayShort(i.atZone(r.zone).toLocalDate())
