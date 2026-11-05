@file:OptIn(ExperimentalMaterial3Api::class)

package de.vitalcoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Explain
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.Score

@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun MetricDetailScreen(key: MetricKey, r: AnalysisResult?, vm: MainViewModel) {
    DetailScaffold(key.title, onBack = { vm.back() }) {
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@DetailScaffold
        }
        val days30 = r.days.takeLast(30)
        val start = days30.firstOrNull()?.let { Fmt.dayShort(it.date) }
        when (key) {
            MetricKey.RECOVERY -> {
                item { ScoreHero(r.todayMetrics?.recovery, "Erholung heute") }
                item {
                    SectionCard(title = "Verlauf (30 Tage)") {
                        LineChart(listOf(Series(days30.map { it.recovery?.value?.toDouble() }, Palette.Good, "Erholung")), startLabel = start, endLabel = "heute")
                    }
                }
                item { CoachCard(Coach.recovery(r)) }
            }
            MetricKey.READINESS -> {
                item { ScoreHero(r.todayMetrics?.readiness, "Trainingsbereitschaft heute") }
                item {
                    SectionCard(title = "Verlauf (30 Tage)") {
                        LineChart(listOf(Series(days30.map { it.readiness?.value?.toDouble() }, Palette.Blue, "Bereitschaft")), startLabel = start, endLabel = "heute")
                    }
                }
                item { CoachCard(Coach.readiness(r)) }
            }
            MetricKey.SLEEP -> {
                val n = r.sleep.lastNight
                if (n != null) {
                    item {
                        SectionCard(title = "Letzte Nacht") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ScoreRing(n.score, "Schlaf-Score", n.scoreTone, size = 88.dp)
                                Spacer(Modifier.width(16.dp))
                                BigValue(Fmt.duration(n.asleepMin), "", "${Fmt.clock(n.bedtimeMin)}–${Fmt.clock(n.wakeMin)} Uhr")
                            }
                            if (n.hasStages) {
                                Spacer(Modifier.height(12.dp))
                                StagesBlock(n)
                            }
                            if (n.components.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                ScoreBreakdown(Score(n.score, n.components, de.vitalcoach.core.Confidence.MEDIUM))
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "Schlafdauer in Stunden (30 Tage)") {
                        LineChart(
                            listOf(Series(days30.map { it.sleep?.asleepMin?.div(60.0) }, Palette.Purple, "Schlaf")),
                            band = r.sleep.goalHours - 0.5 to r.sleep.goalHours + 0.5,
                            startLabel = start, endLabel = "heute", decimals = 1,
                        )
                    }
                }
                item { CoachCard(Coach.sleep(r)) }
            }
            MetricKey.RESTING_HR -> {
                val b = r.todayMetrics?.rhrBaseline
                item {
                    SectionCard(title = "Ruhepuls (30 Tage)") {
                        BigValue(r.todayMetrics?.restingHr?.let { Fmt.int(it) } ?: "–", "bpm", "heute")
                        Spacer(Modifier.height(10.dp))
                        LineChart(
                            listOf(Series(days30.map { it.restingHr }, Palette.Bad, "Ruhepuls")),
                            band = b?.let { (it.mean - it.sd) to (it.mean + it.sd) },
                            startLabel = start, endLabel = "heute",
                        )
                    }
                }
                item { CoachCard(Coach.restingHr(r)) }
            }
            MetricKey.HRV -> {
                val lo = r.fitness.hrvNormalLow
                val hi = r.fitness.hrvNormalHigh
                item {
                    SectionCard(title = "HRV (30 Tage)") {
                        BigValue(r.todayMetrics?.hrv?.let { Fmt.int(it) } ?: "–", "ms", "letzte Nacht")
                        Spacer(Modifier.height(10.dp))
                        LineChart(
                            listOf(Series(days30.map { it.hrv }, Palette.Purple, "HRV")),
                            band = if (lo != null && hi != null) lo to hi else null,
                            startLabel = start, endLabel = "heute",
                        )
                    }
                }
                item { CoachCard(Coach.hrv(r)) }
            }
            MetricKey.LOAD -> {
                val d60 = r.days.takeLast(60)
                item {
                    SectionCard(title = "Form (TSB, 60 Tage)") {
                        LineChart(
                            listOf(Series(d60.map { it.tsb }, Palette.Teal, "Form")),
                            band = -10.0 to 10.0,
                            startLabel = d60.firstOrNull()?.let { Fmt.dayShort(it.date) }, endLabel = "heute",
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Grün = ausgeglichene Form (−10 bis +10). Deutlich negativ = ermüdet (normal in Aufbauphasen), deutlich positiv = frisch (ideal vor Wettkämpfen).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SectionCard(title = "Fitness & Ermüdung (60 Tage)") {
                        LineChart(
                            listOf(Series(d60.map { it.ctl }, Palette.Blue, "Fitness"), Series(d60.map { it.atl }, Palette.Orange, "Ermüdung")),
                            startLabel = d60.firstOrNull()?.let { Fmt.dayShort(it.date) }, endLabel = "heute",
                        )
                        Spacer(Modifier.height(6.dp))
                        Legend(listOf("Fitness (CTL)" to Palette.Blue, "Ermüdung (ATL)" to Palette.Orange))
                    }
                }
                item { CoachCard(Coach.training(r)) }
            }
            MetricKey.VO2MAX -> {
                item { Vo2Card(r, null) }
                item { CoachCard(Coach.fitness(r)) }
            }
            MetricKey.ACTIVITY -> {
                activityItems(r)
                val d14 = r.days.takeLast(14)
                item {
                    SectionCard(title = "Aktive Kalorien (14 Tage)") {
                        BarChart(
                            values = d14.map { it.activeKcal },
                            colors = d14.map { Palette.Orange },
                            labels = d14.map { Fmt.weekday(it.date).take(2) },
                        )
                    }
                }
            }
            MetricKey.VITALS -> {
                item {
                    SectionCard(title = "Sauerstoffsättigung (30 Tage)") {
                        LineChart(listOf(Series(days30.map { it.spo2 }, Palette.Blue, "SpO2")), band = 95.0 to 100.0, startLabel = start, endLabel = "heute", decimals = 1)
                    }
                }
                item {
                    SectionCard(title = "Atemfrequenz (30 Tage)") {
                        val rb = r.todayMetrics?.respBaseline
                        LineChart(
                            listOf(Series(days30.map { it.respRate }, Palette.Teal, "Atmung")),
                            band = rb?.let { (it.mean - it.sd) to (it.mean + it.sd) },
                            startLabel = start, endLabel = "heute", decimals = 1,
                        )
                    }
                }
                item { CoachCard(Coach.vitals(r)) }
            }
            MetricKey.BODY -> {
                item { CoachCard(Coach.body(r)) }
            }
            MetricKey.REGENERATION -> recoveryItems(r, vm)
            MetricKey.HEART_HEALTH -> heartHealthItems(r)
            MetricKey.HEART -> heartItems(r)
            MetricKey.STRESS -> stressItems(r)
            MetricKey.PERFORMANCE -> performanceItems(r)
            MetricKey.FORECAST -> item { ForecastDetail(r) }
            MetricKey.BASELINES -> baselineItems(r)
            MetricKey.CORRELATIONS -> correlationItems(r)
            MetricKey.TRENDS -> trendItems(r)
        }
        item {
            SectionCard(title = "Was ist das?", icon = Icons.Filled.Info) {
                Text(Explain.what(key), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Herkunft des Werts: ", style = MaterialTheme.typography.bodySmall)
                    ProvenanceChip(Explain.provenance(key))
                }
            }
        }
        item {
            SectionCard(title = "So wird es berechnet", icon = Icons.Filled.Science) {
                Text(Explain.how(key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScoreHero(score: Score?, label: String) {
    SectionCard {
        if (score == null) {
            Text("Noch keine Daten – nach der ersten Nacht mit Uhr erscheint hier dein Wert.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score.value, label, score.tone, size = 110.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Bausteine", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        ScoreBreakdown(score)
    }
}

@Composable
fun WorkoutDetailScreen(startEpochSec: Long, r: AnalysisResult?, vm: MainViewModel) {
    val w = r?.workouts?.firstOrNull { it.workout.start.epochSecond == startEpochSec }
    DetailScaffold(w?.workout?.title ?: w?.workout?.type?.label ?: "Training", onBack = { vm.back() }) {
        if (w == null || r == null) {
            item { Text("Training nicht gefunden.") }
            return@DetailScaffold
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.workout.type.emoji, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(w.workout.type.label, style = MaterialTheme.typography.titleLarge)
                        val zone = r.zone
                        val start = w.workout.start.atZone(zone)
                        Text(
                            "${Fmt.day(start.toLocalDate())} · ${String.format(java.util.Locale.GERMANY, "%02d:%02d", start.hour, start.minute)} Uhr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                KeyValue("Dauer", Fmt.duration(w.durationMin))
                w.workout.distanceMeters?.let { KeyValue("Distanz", "${Fmt.num(it / 1000, 2)} km") }
                w.paceSecPerKm?.let {
                    KeyValue(
                        if (w.workout.type == de.vitalcoach.core.SportType.CYCLING) "Ø Geschwindigkeit" else "Ø Tempo",
                        if (w.workout.type == de.vitalcoach.core.SportType.CYCLING) "${Fmt.num(3600.0 / it, 1)} km/h" else Fmt.pace(it),
                    )
                }
                w.avgHr?.let { KeyValue("Ø Puls", "${Fmt.int(it)} bpm") }
                w.maxHr?.let { KeyValue("Max. Puls", "$it bpm") }
                KeyValue("Belastung (TRIMP)", Fmt.int(w.trimp) + if (w.estimated) " (geschätzt)" else "")
                w.workout.activeKcal?.let { KeyValue("Aktive Kalorien", "${Fmt.int(it)} kcal") }
                w.vo2Estimate?.let { KeyValue("VO2max-Schätzung", Fmt.num(it, 1)) }
                KeyValue("Charakter", w.focus)
            }
        }
        if (!w.estimated) {
            item {
                SectionCard(title = "Zeit in Herzfrequenzzonen") {
                    ZoneBars(w.zoneMin, ZONE_NAMES)
                }
            }
        }
        item { CoachCard(Coach.workout(w, r)) }
    }
}

@Composable
fun MethodsScreen(vm: MainViewModel) {
    DetailScaffold("So rechnet VitalCoach", onBack = { vm.back() }) {
        item {
            SectionCard {
                Text(
                    "VitalCoach nutzt ausschließlich veröffentlichte, sportwissenschaftliche Methoden. Alle Werte werden direkt auf deinem Handy berechnet. " +
                        "Wo eine Schätzung nötig ist, wird das angezeigt. Wichtig: Uhren messen optisch am Handgelenk – einzelne Messwerte können abweichen, " +
                        "aussagekräftig sind vor allem Trends und Abweichungen von DEINEN Normalwerten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        MetricKey.entries.forEach { key ->
            item {
                SectionCard(title = key.title) {
                    Text(Explain.how(key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard(title = "Quellen") {
                listOf(
                    "Banister EW (1991). Modeling elite athletic performance. In: Physiological Testing of Elite Athletes.",
                    "Morton RH, Fitz-Clarke JR, Banister EW (1990). Modeling human performance in running. J Appl Physiol.",
                    "Tanaka H, Monahan KD, Seals DR (2001). Age-predicted maximal heart rate revisited. J Am Coll Cardiol.",
                    "Plews DJ et al. (2012). Heart rate variability in elite triathletes. Eur J Appl Physiol.",
                    "Buchheit M (2014). Monitoring training status with HR measures. Front Physiol.",
                    "Gabbett TJ (2016). The training–injury prevention paradox. Br J Sports Med.",
                    "Williams S et al. (2017). Better way to determine the acute:chronic workload ratio? Br J Sports Med.",
                    "Foster C (1998). Monitoring training in athletes with reference to overtraining syndrome. Med Sci Sports Exerc.",
                    "Swain DP, Leutholtz BC (1997). Heart rate reserve is equivalent to %VO2 reserve. Med Sci Sports Exerc.",
                    "Uth N et al. (2004). Estimation of VO2max from the ratio between HRmax and HRrest. Eur J Appl Physiol.",
                    "American College of Sports Medicine (2021). ACSM's Guidelines for Exercise Testing and Prescription, 11th ed.",
                    "Loe H et al. (2013). Aerobic capacity reference data in 3816 healthy men and women 20–90 years. PLoS One.",
                    "Watson NF et al. (2015). Recommended amount of sleep for a healthy adult. Sleep (AASM/SRS).",
                    "Ohayon M et al. (2017). National Sleep Foundation's sleep quality recommendations. Sleep Health.",
                    "Windred DP et al. (2024). Sleep regularity is a stronger predictor of mortality than sleep duration. Sleep.",
                    "Seiler S (2010). What is best practice for training intensity distribution? Int J Sports Physiol Perform.",
                    "Paluch AE et al. (2022). Daily steps and all-cause mortality: a meta-analysis. Lancet Public Health.",
                    "Mishra T et al. (2020). Pre-symptomatic detection of COVID-19 from smartwatch data. Nat Biomed Eng.",
                    "WHO (2020). Guidelines on physical activity and sedentary behaviour.",
                ).forEach { Bullet(it) }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    DetailScaffold("Datenschutz", onBack = onBack) {
        item {
            SectionCard {
                InfoText(
                    "Welche Daten liest VitalCoach?",
                    "Herzfrequenz, HRV, Ruhepuls, Schlaf, Trainings, Schritte, Distanz, Kalorien, Sauerstoffsättigung, Atemfrequenz, VO2max, Gewicht, Größe und Körperfett – " +
                        "ausschließlich lesend aus Health Connect. VitalCoach schreibt oder verändert keine Daten.",
                )
                InfoText(
                    "Wo werden die Daten verarbeitet?",
                    "Alle Berechnungen finden direkt auf deinem Handy statt. Es gibt keinen Server, kein Konto und keine Werbung.",
                )
                InfoText(
                    "KI-Coach (optional)",
                    "Nur wenn du einen eigenen API-Schlüssel hinterlegst und aktiv auf „KI-Analyse“ tippst, wird eine Zusammenfassung deiner Kennzahlen " +
                        "(ohne Namen, ohne Standort, ohne Rohdaten) verschlüsselt an die Anthropic API (Claude) übertragen, um die Antwort zu erzeugen.",
                )
                InfoText(
                    "Berechtigungen widerrufen",
                    "Du kannst den Zugriff jederzeit in Health Connect → App-Berechtigungen → VitalCoach entziehen. Beim Deinstallieren werden alle lokalen Daten gelöscht.",
                )
                InfoText(
                    "Hinweis",
                    "VitalCoach ist kein Medizinprodukt. Die Werte dienen der Information und Trainingssteuerung und ersetzen keine ärztliche Diagnose.",
                )
            }
        }
    }
}
