package de.vitalcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.vitalcoach.core.ForecastReport
import de.vitalcoach.core.ForecastScenario
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.LoadReport
import de.vitalcoach.core.Provenance
import de.vitalcoach.core.Tone

fun Provenance.color(): Color = when (this) {
    Provenance.MEASURED -> Palette.Blue
    Provenance.CALCULATED -> Palette.Teal
    Provenance.ESTIMATED -> Palette.Purple
}

/** Kleines Etikett „Gemessen / Berechnet / Geschätzt“. */
@Composable
fun ProvenanceChip(p: Provenance) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(p.color().copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(p.label, color = p.color(), style = MaterialTheme.typography.labelSmall)
    }
}

/** Legende zur Datenherkunft. */
@Composable
fun ProvenanceLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Provenance.entries.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProvenanceChip(p)
                Spacer(Modifier.width(8.dp))
                Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun scoreTone(v: Int?): Tone = when {
    v == null -> Tone.NEUTRAL
    v >= 67 -> Tone.GOOD
    v >= 50 -> Tone.OK
    v >= 34 -> Tone.WARN
    else -> Tone.BAD
}

/** Eintrag in der Analysen-Übersicht. */
@Composable
fun HubItem(title: String, value: String?, sub: String, icon: ImageVector, tone: Tone, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tone.color().copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.color())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = tone.color())
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fortschrittsbalken mit Beschriftung. */
@Composable
fun LabeledBar(label: String, fraction: Float, valueText: String, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/** Tabelle der Bereitschafts-Prognose (Szenarien × nächste 3 Tage). */
@Composable
fun ForecastTable(f: ForecastReport, scenarios: List<ForecastScenario> = ForecastScenario.entries) {
    val dates = f.scenarios.values.firstOrNull()?.map { it.date }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Wenn du …", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.4f))
            dates.forEach {
                Text(
                    Fmt.weekday(it).take(2) + " " + Fmt.dayShort(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        scenarios.forEach { sc ->
            val pts = f.scenarios[sc].orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(sc.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                pts.forEach { p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${p.expected}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scoreTone(p.expected).color())
                        Text("${p.low}–${p.high}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** „Das heißt für dich“: kurze Klartext-Einordnung direkt unter einer Kennzahl. */
@Composable
fun MeaningBox(text: String, color: Color = Palette.Blue) {
    if (text.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Farbe je Intensitätsstufe einer Einheit (0 = leicht … 4 = sehr hart), passend zu den Pulszonen-Farben. */
fun intensityColor(step: Int): Color = Palette.Zones[(step + 1).coerceIn(1, 5)]

/** Wochenvergleich als Balken: diese Woche, Ø der letzten 4 Wochen und Ø der 2 Monate davor. */
@Composable
fun WeekCompareBars(l: LoadReport) {
    val rows = listOfNotNull(
        Triple("Diese Woche", l.weekLoad, Palette.Teal),
        Triple("Ø pro Woche · letzte 4 Wochen", l.weekAvg28, Palette.Blue),
        l.weekAvgBefore?.let { Triple("Ø pro Woche · 1–3 Monate davor", it, Palette.Neutral) },
    )
    val max = rows.maxOf { it.second }.coerceAtLeast(1.0)
    Column {
        rows.forEach { (label, v, c) -> LabeledBar(label, (v / max).toFloat(), Fmt.int(v), c) }
    }
}
