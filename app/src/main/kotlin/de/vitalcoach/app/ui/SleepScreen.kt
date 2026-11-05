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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.UiState
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Coach
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.Score
import de.vitalcoach.core.SleepNight
import de.vitalcoach.core.Tone

@Composable
fun SleepScreen(state: UiState, padding: PaddingValues) {
    val r = state.result
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Schlaf", style = MaterialTheme.typography.headlineMedium) }
        if (r == null) {
            item { Text("Noch keine Daten vorhanden.") }
            return@LazyColumn
        }
        item { LastNightHero(r) }
        item { HypnogramCard(r) }
        item { PhasesCard(r) }
        item { SleepNeedCard(r) }
        item { NightsCard(r) }
        item { SleepWindowCard(r) }
        item { ConsistencyCard(r) }
        item { NightRecoveryCard(r) }
        item { CoachCard(Coach.sleep(r), title = "Coach · Schlaf") }
    }
}

@Composable
private fun LastNightHero(r: AnalysisResult) {
    val n = r.sleep.lastNight
    SectionCard {
        if (n == null) {
            Text("Keine Schlafdaten für letzte Nacht. Trage die Uhr nachts und öffne danach Samsung Health.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(n.score, "", n.scoreTone, size = 110.dp, caption = "Score")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Fmt.duration(n.asleepMin), style = MaterialTheme.typography.headlineLarge, color = durationTone(n.asleepMin).color())
                Text("geschlafen · Ziel ${Fmt.hours(r.sleep.goalHours)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("${Fmt.clock(n.bedtimeMin)} → ${Fmt.clock(n.wakeMin)}", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        val eff = n.efficiency
        StatRow(
            listOf(
                Triple(eff?.let { Fmt.pct(it) } ?: "–", "Effizienz", eff?.let { if (it >= 0.9) Palette.Good else if (it >= 0.85) Palette.Ok else Palette.Warn }),
                Triple(Fmt.duration(n.inBedMin), "im Bett", null),
                Triple(Fmt.duration(n.awakeMin), "wach", if (n.awakeMin > 45) Palette.Warn else null),
            ),
        )
        if (n.components.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ScoreBreakdown(Score(n.score, n.components, de.vitalcoach.core.Confidence.MEDIUM))
        }
    }
}

fun durationTone(min: Double): Tone = when {
    min >= 420 -> Tone.GOOD
    min >= 390 -> Tone.OK
    min >= 360 -> Tone.WARN
    else -> Tone.BAD
}

@Composable
private fun HypnogramCard(r: AnalysisResult) {
    val n = r.sleep.lastNight ?: return
    if (!n.hasStages || n.stages.isEmpty()) return
    SectionCard(title = "Schlafverlauf", icon = Icons.Filled.Timeline) {
        Hypnogram(n.stages, r.zone)
    }
}

@Composable
private fun PhasesCard(r: AnalysisResult) {
    val n = r.sleep.lastNight ?: return
    if (!n.hasStages) return
    SectionCard(title = "Schlafphasen", icon = Icons.Filled.DonutLarge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                listOf(n.deepMin to Palette.Deep, n.lightMin to Palette.Light, n.remMin to Palette.Rem, n.awakeMin to Palette.Awake),
                size = 130.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.duration(n.asleepMin), style = MaterialTheme.typography.titleMedium)
                    Text("Schlaf", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val asleep = n.asleepMin.coerceAtLeast(1.0)
                PhaseRow("Tief", n.deepMin, n.deepMin / asleep, Palette.Deep, n.deepMin / asleep >= 0.15)
                PhaseRow("REM", n.remMin, n.remMin / asleep, Palette.Rem, n.remMin / asleep >= 0.20)
                PhaseRow("Leicht", n.lightMin, n.lightMin / asleep, Palette.Light, null)
                PhaseRow("Wach", n.awakeMin, null, Palette.Awake, n.awakeMin <= 30)
            }
        }
    }
}

@Composable
private fun PhaseRow(name: String, min: Double, pct: Double?, color: Color, ok: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Fmt.duration(min) + (pct?.let { " · ${Fmt.pct(it)}" } ?: ""), style = MaterialTheme.typography.titleSmall)
        if (ok != null) {
            Spacer(Modifier.width(6.dp))
            Text(if (ok) "✓" else "!", color = if (ok) Palette.Good else Palette.Warn, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SleepNeedCard(r: AnalysisResult) {
    val s = r.sleep
    val need = s.needTonightMin ?: return
    SectionCard(title = "Heute Nacht", icon = Icons.Filled.Bedtime) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.duration(need), style = MaterialTheme.typography.headlineLarge, color = Palette.Purple)
                Text("Schlafbedarf", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val bed = s.recommendedBedtimeMin
            if (bed != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.clock(bed), style = MaterialTheme.typography.headlineLarge)
                    Text("ins Bett", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (s.needReasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            s.needReasons.forEach { Pill(it, Palette.Purple) ; Spacer(Modifier.height(4.dp)) }
        }
        val pn = s.personalNeedH
        if (pn != null) {
            Spacer(Modifier.height(6.dp))
            KeyValue("Dein persönlicher Bedarf (geschätzt)", Fmt.hours(pn))
        }
    }
}

@Composable
private fun NightsCard(r: AnalysisResult) {
    val days = r.days.takeLast(14)
    SectionCard(title = "14 Nächte", icon = Icons.Filled.Insights) {
        StackedBarChart(
            days.map { d ->
                val n = d.sleep
                if (n == null) {
                    emptyList<Pair<Double, Color>>()
                } else if (n.hasStages) {
                    listOf(n.deepMin / 60 to Palette.Deep, n.lightMin / 60 to Palette.Light, n.remMin / 60 to Palette.Rem)
                } else {
                    listOf(n.asleepMin / 60 to Palette.Light)
                }
            },
            goal = r.sleep.goalHours,
            labels = days.map { Fmt.weekday(it.date).take(2) },
            height = 160.dp,
        )
        Spacer(Modifier.height(8.dp))
        Legend(listOf("Tief" to Palette.Deep, "Leicht" to Palette.Light, "REM" to Palette.Rem))
        Spacer(Modifier.height(10.dp))
        StatRow(
            listOf(
                Triple(r.sleep.avgAsleep7Min?.let { Fmt.duration(it) } ?: "–", "Ø 7 Nächte", r.sleep.avgAsleep7Min?.let { durationTone(it).color() }),
                Triple(r.sleep.debt7H?.let { Fmt.num(it, 1) + " h" } ?: "–", "Defizit 7 T", r.sleep.debt7H?.let { if (it >= 3) Palette.Bad else if (it >= 1.5) Palette.Warn else Palette.Good }),
                Triple(r.sleep.debt14H?.let { Fmt.num(it, 1) + " h" } ?: "–", "Schuld 14 T", r.sleep.debt14H?.let { if (it >= 6) Palette.Bad else if (it >= 3) Palette.Warn else Palette.Good }),
            ),
        )
    }
}

@Composable
private fun SleepWindowCard(r: AnalysisResult) {
    val days = r.days.takeLast(14)
    SectionCard(title = "Schlafenszeiten", icon = Icons.Filled.Schedule) {
        SleepWindowChart(
            days.map { d ->
                val n = d.sleep
                Triple(Fmt.weekday(d.date).take(2), n?.let { it.bedtimeMin to it.wakeMin }, (n?.scoreTone ?: Tone.NEUTRAL).color())
            },
        )
        Spacer(Modifier.height(8.dp))
        StatRow(
            listOf(
                Triple(r.sleep.avgBedtimeMin?.let { Fmt.clock(it) } ?: "–", "Ø ins Bett", null),
                Triple(r.sleep.avgWakeMin?.let { Fmt.clock(it) } ?: "–", "Ø aufstehen", null),
                Triple(r.sleep.regularitySdMin?.let { "±" + Fmt.int(it) + "′" } ?: "–", "Schwankung", r.sleep.regularitySdMin?.let { if (it > 60) Palette.Bad else if (it > 30) Palette.Warn else Palette.Good }),
            ),
        )
    }
}

@Composable
private fun ConsistencyCard(r: AnalysisResult) {
    val sri = r.sleep.sri ?: return
    SectionCard(title = "Schlaf-Konsistenz (SRI)") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            SemiGauge(
                (sri / 100).toFloat(), listOf(Palette.Bad, Palette.Warn, Palette.Ok, Palette.Good),
                Fmt.int(sri), if (sri >= 85) "sehr regelmäßig" else if (sri >= 75) "regelmäßig" else if (sri >= 65) "mittel" else "unregelmäßig",
            )
        }
    }
}

@Composable
private fun NightRecoveryCard(r: AnalysisResult) {
    val s = r.sleep
    val h = r.heart
    val nr = s.nightRecovery
    if (nr == null && h.nightCurve.size < 3) return
    SectionCard(title = "Erholung in der Nacht", icon = Icons.Filled.Favorite) {
        if (nr != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(nr.value, "", nr.tone, size = 84.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    StatRow(
                        listOf(
                            Triple(s.nightRestingHr?.let { Fmt.int(it) } ?: "–", "Ruhepuls", null),
                            Triple(s.nightHrv?.let { Fmt.int(it) } ?: "–", "HRV ms", null),
                            Triple(h.dipPct?.let { Fmt.int(it) + " %" } ?: "–", "Absenkung", h.dipPct?.let { if (it >= 10) Palette.Good else Palette.Warn }),
                        ),
                    )
                }
            }
        }
        if (h.nightCurve.size >= 3) {
            Spacer(Modifier.height(10.dp))
            LineChart(
                listOf(Series(h.nightCurve.map { it.second }, Palette.Bad, "Puls")),
                height = 120.dp,
                startLabel = h.nightCurve.first().first.atZone(r.zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
                endLabel = h.nightCurve.last().first.atZone(r.zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) },
            )
        }
    }
}

/** Für Detailseiten: Schlafphasen-Balken einer Nacht. */
@Composable
fun StagesBlock(n: SleepNight) {
    StackedBar(
        listOf(n.deepMin to Palette.Deep, n.lightMin to Palette.Light, n.remMin to Palette.Rem, n.awakeMin to Palette.Awake),
        height = 18.dp,
    )
    Spacer(Modifier.height(6.dp))
    Legend(listOf("Tief" to Palette.Deep, "Leicht" to Palette.Light, "REM" to Palette.Rem, "Wach" to Palette.Awake))
}

@Composable
fun ToneDotColor(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}
