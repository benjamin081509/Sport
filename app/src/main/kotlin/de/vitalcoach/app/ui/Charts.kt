package de.vitalcoach.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.vitalcoach.core.Fmt
import kotlin.math.max
import kotlin.math.min

data class Series(val values: List<Double?>, val color: Color, val label: String)

/** Liniendiagramm mit optionalem Normalbereich (Band). Lücken (null) werden ausgelassen. */
@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    band: Pair<Double, Double>? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    decimals: Int = 0,
) {
    val all = series.flatMap { it.values.filterNotNull() } + listOfNotNull(band?.first, band?.second)
    if (all.isEmpty()) {
        EmptyChart(height)
        return
    }
    var lo = all.min()
    var hi = all.max()
    if (hi - lo < 1e-6) {
        lo -= 1
        hi += 1
    }
    val pad = (hi - lo) * 0.12
    lo -= pad
    hi += pad
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bandColor = Palette.Good.copy(alpha = 0.12f)
    Column(modifier) {
        Row {
            Column(Modifier.height(height).width(34.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.num(hi - pad, decimals), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.num(lo + pad, decimals), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(height),
            ) {
                val w = size.width
                val h = size.height
                fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
                // Gitter
                for (i in 0..3) {
                    val gy = h * i / 3f
                    drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                band?.let { (a, b) ->
                    val top = y(max(a, b))
                    val bottom = y(min(a, b))
                    drawRect(bandColor, Offset(0f, top), Size(w, bottom - top))
                }
                series.forEach { s ->
                    val n = s.values.size
                    if (n == 0) return@forEach
                    val step = if (n > 1) w / (n - 1) else 0f
                    val path = Path()
                    var started = false
                    var lastPoint: Offset? = null
                    s.values.forEachIndexed { i, v ->
                        if (v == null) {
                            started = false
                        } else {
                            val p = Offset(i * step, y(v))
                            if (!started) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                            started = true
                            lastPoint = p
                        }
                    }
                    drawPath(path, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // einzelne Punkte sichtbar machen, falls keine Linie entsteht
                    s.values.forEachIndexed { i, v ->
                        if (v != null && (i == 0 || s.values[i - 1] == null) && (i == n - 1 || s.values[i + 1] == null)) {
                            drawCircle(s.color, 3.dp.toPx(), Offset(i * step, y(v)))
                        }
                    }
                    lastPoint?.let { drawCircle(s.color, 4.dp.toPx(), it) }
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth().padding(start = 34.dp, top = 4.dp)) {
                Text(startLabel ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(endLabel ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Balkendiagramm mit optionaler Ziellinie. */
@Composable
fun BarChart(
    values: List<Double?>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    goal: Double? = null,
    labels: List<String>? = null,
) {
    val maxV = max((values.filterNotNull().maxOrNull() ?: 0.0), goal ?: 0.0).takeIf { it > 0 } ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceVariant
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.62f
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 3))
                if (v != null && v > 0) {
                    val bh = (v / maxV * size.height).toFloat()
                    drawRoundRect(colors.getOrElse(i) { colors.last() }, Offset(x, size.height - bh), Size(barW, bh), CornerRadius(barW / 3))
                }
            }
            goal?.let {
                val gy = (size.height - it / maxV * size.height).toFloat()
                drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        if (labels != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                labels.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** Horizontaler, gestapelter Balken (z. B. Schlafphasen). */
@Composable
fun StackedBar(parts: List<Pair<Double, Color>>, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val total = parts.sumOf { it.first }.takeIf { it > 0 } ?: 1.0
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        parts.filter { it.first > 0 }.forEach { (v, c) ->
            Box(
                Modifier
                    .weight((v / total).toFloat().coerceAtLeast(0.001f))
                    .height(height)
                    .background(c),
            )
        }
    }
}

/** Farbige Skala mit Markierung (z. B. ACWR oder VO2max-Kategorie). */
@Composable
fun ScaleBar(
    segments: List<Pair<String, Color>>,
    position: Float?,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
) {
    val marker = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                segments.forEach { (_, c) -> Box(Modifier.weight(1f).height(14.dp).background(c)) }
            }
            if (position != null) {
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val x = size.width * position.coerceIn(0.01f, 0.99f)
                    drawLine(marker, Offset(x, -6f), Offset(x, size.height + 6f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            (labels ?: segments.map { it.first }).forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** Minuten pro Herzfrequenzzone als horizontale Balken. */
@Composable
fun ZoneBars(zoneMin: List<Double>, names: List<String>) {
    val maxV = zoneMin.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val total = zoneMin.sum().takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in zoneMin.indices.reversed()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names.getOrElse(i) { "Z$i" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(92.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth((zoneMin[i] / maxV).toFloat().coerceIn(0.01f, 1f))
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Palette.Zones[i]),
                    )
                }
                Text(
                    "${Fmt.int(zoneMin[i])} min · ${Fmt.pct(zoneMin[i] / total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun EmptyChart(height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("Noch keine Daten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
