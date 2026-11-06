package de.vitalcoach.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.EnergyDay
import de.vitalcoach.core.EnergyPoint
import de.vitalcoach.core.EnergyReport
import de.vitalcoach.core.Energy
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.Meaning
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** Farbe eines Energiewerts (gleiche Stufen wie das Etikett). */
fun energyColor(v: Int): Color = Energy.label(v).second.color()

/** Energie über den Tag: Akku, Tagesverlauf, Verbrauch und die letzten 7 Tage. */
@Composable
fun EnergyCard(r: AnalysisResult, onClick: (() -> Unit)? = null) {
    val e = r.energy ?: return
    val color = energyColor(e.now)
    SectionCard(title = "Energie", icon = Icons.Filled.BatteryChargingFull, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryGauge(e.now, color, width = 58.dp, height = 100.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${e.now}", style = MaterialTheme.typography.displaySmall, color = color, fontWeight = FontWeight.SemiBold)
                    Text(" / 100", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(e.label, style = MaterialTheme.typography.titleMedium, color = color)
                val diff = e.now - e.morning
                Text(
                    "morgens ${e.morning} · " + (if (diff > 0) "+$diff" else if (diff < 0) "−${-diff}" else "±0"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val rn = e.readinessNow
                val rm = e.readinessMorning
                if (rn != null && rm != null) {
                    Text(
                        "Bereitschaft jetzt $rn (früh $rm)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        EnergyDayChart(e, r.zone, color)
        Spacer(Modifier.height(12.dp))
        DrainBars(e)
        if (e.history.size >= 3) {
            Spacer(Modifier.height(14.dp))
            Text("Letzte Tage · morgens → abends", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            EnergyWeekChart(e.history)
        }
        Spacer(Modifier.height(10.dp))
        MeaningBox(Meaning.energy(e), color)
    }
}

/** Senkrechter Akku mit Füllstand. */
@Composable
fun BatteryGauge(value: Int, color: Color, width: Dp, height: Dp) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val anim by animateFloatAsState(value.coerceIn(0, 100) / 100f, tween(900), label = "battery")
    Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width, height)) {
            val capH = size.height * 0.07f
            val capW = size.width * 0.42f
            val stroke = 3.dp.toPx()
            drawRoundRect(outline, Offset((size.width - capW) / 2, 0f), Size(capW, capH + stroke), CornerRadius(4.dp.toPx()))
            val top = capH
            val bodyH = size.height - capH
            drawRoundRect(track, Offset(0f, top), Size(size.width, bodyH), CornerRadius(12.dp.toPx()))
            val pad = stroke + 3.dp.toPx()
            val innerH = bodyH - 2 * pad
            val fillH = innerH * anim
            if (fillH > 0f) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.75f), color)),
                    Offset(pad, top + pad + innerH - fillH),
                    Size(size.width - 2 * pad, fillH),
                    CornerRadius(7.dp.toPx()),
                )
            }
            drawRoundRect(outline, Offset(stroke / 2, top + stroke / 2), Size(size.width - stroke, bodyH - stroke), CornerRadius(12.dp.toPx()), style = Stroke(stroke))
        }
    }
}

/** Tagesverlauf der Energie seit dem Aufwachen auf einer festen 0–100-Skala bis zum Abend. */
@Composable
private fun EnergyDayChart(e: EnergyReport, zone: ZoneId, color: Color, height: Dp = 130.dp) {
    val pts: List<EnergyPoint> = e.curve
    if (pts.size < 2) return
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val surface = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val start = pts.first().time
    val last = pts.last().time
    val axisEnd = maxOf(last, start.plusSeconds(16 * 3600))
    val span = Duration.between(start, axisEnd).seconds.toFloat().coerceAtLeast(1f)
    Column {
        Row {
            Column(Modifier.height(height).width(28.dp)) {
                Text("100", style = MaterialTheme.typography.labelSmall, color = labelColor)
                Spacer(Modifier.weight(1f))
                Text("50", style = MaterialTheme.typography.labelSmall, color = labelColor)
                Spacer(Modifier.weight(1f))
                Text("0", style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                val w = size.width
                val h = size.height
                fun x(t: java.time.Instant) = Duration.between(start, t).seconds / span * w
                fun y(v: Double) = (h - v / 100.0 * h).toFloat()
                for (i in 0..4) {
                    val gy = h * i / 4f
                    drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                }
                val p = pts.map { Offset(x(it.time), y(it.value)) }
                val line = Path().apply {
                    moveTo(p[0].x, p[0].y)
                    for (i in 1 until p.size) lineTo(p[i].x, p[i].y)
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(p.last().x, h)
                    lineTo(p.first().x, h)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)), startY = 0f, endY = h))
                drawPath(line, color, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = p.last()
                drawCircle(color.copy(alpha = 0.25f), 8.dp.toPx(), end)
                drawCircle(surface, 5.dp.toPx(), end)
                drawCircle(color, 3.5.dp.toPx(), end)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 4.dp)) {
            Text("Aufwachen ${start.atZone(zone).format(HM)}", style = MaterialTheme.typography.labelSmall, color = labelColor, modifier = Modifier.weight(1f))
            Text("jetzt ${last.atZone(zone).format(HM)}", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/** Woher der Verbrauch kommt – als Balken. */
@Composable
private fun DrainBars(e: EnergyReport) {
    val rows = listOf(
        Triple("Training", e.drainTraining, Palette.Orange),
        Triple("Wachzeit", e.drainAwake, Palette.Neutral),
        Triple("Alltag & Bewegung", e.drainEveryday, Palette.Teal),
        Triple("Stress", e.drainStress, Palette.Purple),
    ).filter { it.second >= 1.0 }.sortedByDescending { it.second }
    val max = (rows.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(e.charge).coerceAtLeast(1.0)
    Column {
        rows.forEach { (label, v, c) -> LabeledBar(label, (v / max).toFloat(), "−${v.roundToInt()}", c) }
        if (e.charge >= 1.0) LabeledBar("Aufgeladen in Ruhephasen", (e.charge / max).toFloat(), "+${e.charge.roundToInt()}", Palette.Good)
    }
}

/** Letzte Tage: Balken vom Abendwert (unten) bis zum Morgenwert (oben). */
@Composable
private fun EnergyWeekChart(days: List<EnergyDay>, height: Dp = 110.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = days.size
            val slot = size.width / n
            val bw = slot * 0.42f
            val h = size.height
            days.forEachIndexed { i, d ->
                val cx = slot * i + slot / 2
                drawRoundRect(track, Offset(cx - bw / 2, 0f), Size(bw, h), CornerRadius(bw / 2))
                val hi = maxOf(d.morning, d.evening)
                val lo = minOf(d.morning, d.evening)
                val top = h - hi / 100f * h
                val bottom = h - lo / 100f * h
                drawRoundRect(
                    Brush.verticalGradient(listOf(energyColor(d.morning), energyColor(d.evening)), startY = top, endY = bottom.coerceAtLeast(top + 1f)),
                    Offset(cx - bw / 2, top),
                    Size(bw, (bottom - top).coerceAtLeast(bw)),
                    CornerRadius(bw / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEachIndexed { i, d ->
                Text(
                    (if (i == days.lastIndex) "heute" else Fmt.weekday(d.date).take(2)) + "\n${d.morning}→${d.evening}",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
            }
        }
    }
}
