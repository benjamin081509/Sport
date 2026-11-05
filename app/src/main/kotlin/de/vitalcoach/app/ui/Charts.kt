@file:OptIn(ExperimentalTextApi::class)

package de.vitalcoach.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.vitalcoach.core.ForecastReport
import de.vitalcoach.core.ForecastScenario
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.SleepSegment
import de.vitalcoach.core.SleepStage
import de.vitalcoach.core.scoreToneOf
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Series(val values: List<Double?>, val color: Color, val label: String)

/** Weiche Kurve durch die Punkte (ohne Überschwinger). */
private fun smoothPath(p: List<Offset>): Path {
    val path = Path()
    if (p.isEmpty()) return path
    path.moveTo(p[0].x, p[0].y)
    for (i in 1 until p.size) {
        val a = p[i - 1]
        val b = p[i]
        val mx = (a.x + b.x) / 2f
        path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
    return path
}

private fun segments(values: List<Double?>, x: (Int) -> Float, y: (Double) -> Float): List<List<Offset>> {
    val out = mutableListOf<MutableList<Offset>>()
    var cur: MutableList<Offset>? = null
    values.forEachIndexed { i, v ->
        if (v == null) {
            cur = null
        } else {
            val c = cur ?: mutableListOf<Offset>().also { out += it; cur = it }
            c += Offset(x(i), y(v))
        }
    }
    return out
}

/** Liniendiagramm mit weichem Verlauf, Farbfläche und optionalem Normalbereich. */
@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    band: Pair<Double, Double>? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    decimals: Int = 0,
    fill: Boolean = true,
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
    val pad = (hi - lo) * 0.15
    lo -= pad
    hi += pad
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val bandColor = Palette.Good.copy(alpha = 0.13f)
    val surface = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row {
            Column(Modifier.height(height).width(32.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.num(hi - pad, decimals), style = MaterialTheme.typography.labelSmall, color = labelColor)
                Text(Fmt.num(lo + pad, decimals), style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                val w = size.width
                val h = size.height
                fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
                for (i in 0..3) {
                    val gy = h * i / 3f
                    drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                }
                band?.let { (a, b) ->
                    val top = y(max(a, b))
                    val bottom = y(min(a, b))
                    drawRoundRect(bandColor, Offset(0f, top), Size(w, bottom - top), CornerRadius(8f))
                }
                series.forEachIndexed { si, s ->
                    val n = s.values.size
                    if (n == 0) return@forEachIndexed
                    val step = if (n > 1) w / (n - 1) else 0f
                    val segs = segments(s.values, { i -> i * step }, ::y)
                    segs.forEach { seg ->
                        if (seg.size >= 2) {
                            val path = smoothPath(seg)
                            if (fill && si == 0) {
                                val fillPath = Path().apply {
                                    addPath(path)
                                    lineTo(seg.last().x, h)
                                    lineTo(seg.first().x, h)
                                    close()
                                }
                                drawPath(fillPath, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.30f), s.color.copy(alpha = 0f)), startY = 0f, endY = h))
                            }
                            drawPath(path, s.color, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        } else {
                            drawCircle(s.color, 3.dp.toPx(), seg.first())
                        }
                    }
                    segs.lastOrNull()?.lastOrNull()?.let {
                        drawCircle(s.color.copy(alpha = 0.25f), 8.dp.toPx(), it)
                        drawCircle(surface, 5.dp.toPx(), it)
                        drawCircle(s.color, 3.5.dp.toPx(), it)
                    }
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp)) {
                Text(startLabel ?: "", style = MaterialTheme.typography.labelSmall, color = labelColor, modifier = Modifier.weight(1f))
                Text(endLabel ?: "", style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}

/** Kleiner Verlauf ohne Achsen – für Kacheln. */
@Composable
fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val vals = values.filterNotNull()
    if (vals.size < 2) return
    var lo = vals.min()
    var hi = vals.max()
    if (hi - lo < 1e-6) {
        lo -= 1
        hi += 1
    }
    Canvas(modifier.height(height)) {
        val w = size.width
        val h = size.height
        val n = values.size
        val step = if (n > 1) w / (n - 1) else 0f
        val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(i * step, (h - 3f - (it - lo) / (hi - lo) * (h - 6f)).toFloat()) } }
        val path = smoothPath(pts)
        val fillPath = Path().apply {
            addPath(path)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)), startY = 0f, endY = h))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}

/** Balkendiagramm mit abgerundeten Balken und optionaler Ziellinie. */
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
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.62f
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2.5f))
                if (v != null && v > 0) {
                    val bh = (v / maxV * size.height).toFloat().coerceAtLeast(barW / 2)
                    val c = colors.getOrElse(i) { colors.last() }
                    drawRoundRect(
                        Brush.verticalGradient(listOf(c, c.copy(alpha = 0.65f)), startY = size.height - bh, endY = size.height),
                        Offset(x, size.height - bh), Size(barW, bh), CornerRadius(barW / 2.5f),
                    )
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

/** Gestapelte Säulen (z. B. Schlafphasen pro Nacht). */
@Composable
fun StackedBarChart(
    stacks: List<List<Pair<Double, Color>>>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    goal: Double? = null,
    labels: List<String>? = null,
) {
    val maxV = max(stacks.maxOfOrNull { s -> s.sumOf { it.first } } ?: 0.0, goal ?: 0.0).takeIf { it > 0 } ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = stacks.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.62f
            val r = CornerRadius(barW / 2.5f)
            stacks.forEachIndexed { i, stack ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), r)
                val total = stack.sumOf { it.first }
                if (total > 0) {
                    val th = (total / maxV * size.height).toFloat()
                    val clip = Path().apply { addRoundRect(RoundRect(x, size.height - th, x + barW, size.height, r)) }
                    clipPath(clip) {
                        var bottom = size.height
                        stack.forEach { (v, c) ->
                            val sh = (v / maxV * size.height).toFloat()
                            drawRect(c, Offset(x, bottom - sh), Size(barW, sh))
                            bottom -= sh
                        }
                    }
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

/** Horizontaler, gestapelter Balken. */
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

/** Ring-Diagramm mit Inhalt in der Mitte. */
@Composable
fun DonutChart(
    parts: List<Pair<Double, Color>>,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    strokeWidth: Dp = 18.dp,
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val anim by animateFloatAsState(1f, tween(900), label = "donut")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            val total = parts.sumOf { it.first }
            if (total > 0) {
                var start = -90f
                val gap = if (parts.count { it.first > 0 } > 1) 2f else 0f
                parts.filter { it.first > 0 }.forEach { (v, c) ->
                    val sweep = (360f * (v / total).toFloat()) * anim
                    drawArc(c, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        center()
    }
}

/** Halbkreis-Tacho mit farbigen Bereichen und Markierung. */
@Composable
fun SemiGauge(
    fraction: Float?,
    colors: List<Color>,
    valueText: String,
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
) {
    val marker = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val anim by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(900), label = "gauge")
    Box(modifier.width(width).height(width / 2 + 24.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.width(width).height(width / 2 + 24.dp)) {
            val sw = 16.dp.toPx()
            val d = size.width - sw
            val tl = Offset(sw / 2, sw / 2)
            val n = colors.size.coerceAtLeast(1)
            val seg = 180f / n
            colors.forEachIndexed { i, c ->
                drawArc(c, 180f + i * seg + 1f, seg - 2f, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Butt))
            }
            if (fraction != null) {
                val ang = Math.toRadians((180.0 + 180.0 * anim))
                val cx = size.width / 2
                val cy = sw / 2 + d / 2
                val r = d / 2
                val p = Offset((cx + r * cos(ang)).toFloat(), (cy + r * sin(ang)).toFloat())
                drawCircle(surface, 11.dp.toPx(), p)
                drawCircle(marker, 11.dp.toPx(), p, style = Stroke(3.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valueText, style = MaterialTheme.typography.headlineLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Mehrere ineinander liegende Ringe (z. B. Bereitschaft, Erholung, Schlaf). */
@Composable
fun ConcentricRings(rings: List<Pair<Int?, Color>>, size: Dp = 150.dp, track: Color = Color.White.copy(alpha = 0.18f), center: @Composable () -> Unit = {}) {
    val anim by animateFloatAsState(1f, tween(1100), label = "rings")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = this.size.minDimension * 0.085f
            val gap = sw * 0.35f
            rings.forEachIndexed { i, (v, c) ->
                val inset = sw / 2 + i * (sw + gap)
                val d = this.size.minDimension - 2 * inset
                if (d <= 0) return@forEachIndexed
                val tl = Offset(inset, inset)
                drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
                if (v != null) drawArc(c, -90f, 360f * v.coerceIn(0, 100) / 100f * anim, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/** Schlafphasen im Zeitverlauf (Hypnogramm). */
@Composable
fun Hypnogram(stages: List<SleepSegment>, zone: ZoneId, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    if (stages.isEmpty()) {
        EmptyChart(height)
        return
    }
    val start = stages.first().start
    val end = stages.maxOf { it.end }
    val total = Duration.between(start, end).seconds.toFloat().coerceAtLeast(1f)
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    fun level(s: SleepStage) = when (s) {
        SleepStage.AWAKE, SleepStage.OUT_OF_BED -> 0
        SleepStage.REM -> 1
        SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.UNKNOWN -> 2
        SleepStage.DEEP -> 3
    }
    fun color(s: SleepStage) = when (s) {
        SleepStage.AWAKE, SleepStage.OUT_OF_BED -> Palette.Awake
        SleepStage.REM -> Palette.Rem
        SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.UNKNOWN -> Palette.Light
        SleepStage.DEEP -> Palette.Deep
    }
    Column(modifier) {
        Row {
            Column(Modifier.height(height).width(48.dp), verticalArrangement = Arrangement.SpaceAround) {
                listOf("Wach", "REM", "Leicht", "Tief").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Canvas(Modifier.weight(1f).height(height)) {
                val rowH = size.height / 4f
                var prev: Pair<Float, Float>? = null
                stages.forEach { seg ->
                    val x0 = Duration.between(start, seg.start).seconds / total * size.width
                    val x1 = Duration.between(start, seg.end).seconds / total * size.width
                    val lv = level(seg.stage)
                    val cy = rowH * lv + rowH / 2
                    prev?.let { (px, py) -> drawLine(line, Offset(px, py), Offset(x0, cy), strokeWidth = 1.5f) }
                    drawRoundRect(color(seg.stage), Offset(x0, cy - rowH * 0.32f), Size((x1 - x0).coerceAtLeast(2f), rowH * 0.64f), CornerRadius(6f))
                    prev = x1 to cy
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp)) {
            val f = { t: java.time.Instant -> t.atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) } }
            Text(f(start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(f(end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Schlaffenster pro Nacht: senkrechter Balken von Einschlafen bis Aufwachen. */
@Composable
fun SleepWindowChart(
    nights: List<Triple<String, Pair<Double, Double>?, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val known = nights.mapNotNull { it.second }
    if (known.isEmpty()) {
        EmptyChart(height)
        return
    }
    val top = (known.minOf { it.first } - 30).let { (it / 60).toInt() * 60.0 }
    val bottom = (known.maxOf { it.second } + 30).let { ((it / 60).toInt() + 1) * 60.0 }
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Column(modifier) {
        Row {
            Column(Modifier.height(height).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.clock(top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.clock((top + bottom) / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.clock(bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                fun y(v: Double) = ((v - top) / (bottom - top) * size.height).toFloat()
                for (i in 0..2) {
                    val gy = size.height * i / 2f
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                }
                val n = nights.size.coerceAtLeast(1)
                val slot = size.width / n
                val bw = slot * 0.5f
                nights.forEachIndexed { i, (_, win, c) ->
                    val x = i * slot + (slot - bw) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                    if (win != null) {
                        val y0 = y(win.first)
                        val y1 = y(win.second)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(c.copy(alpha = 0.7f), c), startY = y0, endY = y1),
                            Offset(x, y0), Size(bw, (y1 - y0).coerceAtLeast(4f)), CornerRadius(bw / 2),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) {
            nights.forEach {
                Text(it.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Kalender-Heatmap (Wochen × Wochentage), Farbe nach Score. */
@Composable
fun CalendarHeatmap(values: List<Pair<LocalDate, Int?>>, weeks: Int = 12, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val map = values.toMap()
    val last = values.maxOf { it.first }
    val firstMonday = last.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier) {
        Row {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Mo", "", "Mi", "", "Fr", "", "So").forEach {
                    Box(Modifier.height(16.dp).width(22.dp), contentAlignment = Alignment.CenterStart) {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (w in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 0 until 7) {
                            val date = firstMonday.plusDays((w * 7 + d).toLong())
                            val v = map[date]
                            val c = when {
                                date.isAfter(last) -> Color.Transparent
                                v == null -> empty
                                else -> scoreToneOf(v).color()
                            }
                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(c))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Legend(listOf("gut" to Palette.Good, "solide" to Palette.Ok, "mäßig" to Palette.Warn, "schlecht" to Palette.Bad))
    }
}

/** Netzdiagramm (z. B. Bereiche der Herzgesundheit). */
@Composable
fun RadarChart(axes: List<Pair<String, Int?>>, color: Color, modifier: Modifier = Modifier, size: Dp = 260.dp) {
    if (axes.size < 3) return
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2 - 44.dp.toPx()
        val n = axes.size
        fun point(i: Int, f: Float): Offset {
            val a = -Math.PI / 2 + 2 * Math.PI * i / n
            return Offset((c.x + r * f * cos(a)).toFloat(), (c.y + r * f * sin(a)).toFloat())
        }
        for (lv in 1..4) {
            val p = Path()
            for (i in 0 until n) {
                val pt = point(i, lv / 4f)
                if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
            }
            p.close()
            drawPath(p, grid, style = Stroke(1f))
        }
        for (i in 0 until n) drawLine(grid, c, point(i, 1f), strokeWidth = 1f)
        val data = Path()
        axes.forEachIndexed { i, (_, v) ->
            val pt = point(i, ((v ?: 0) / 100f).coerceIn(0.02f, 1f))
            if (i == 0) data.moveTo(pt.x, pt.y) else data.lineTo(pt.x, pt.y)
        }
        data.close()
        drawPath(data, color.copy(alpha = 0.25f))
        drawPath(data, color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        axes.forEachIndexed { i, (label, v) ->
            if (v != null) drawCircle(color, 3.5.dp.toPx(), point(i, (v / 100f).coerceIn(0.02f, 1f)))
            val layout = tm.measure(AnnotatedString(label), style = labelStyle)
            val lp = point(i, 1.22f)
            drawText(layout, topLeft = Offset(lp.x - layout.size.width / 2f, lp.y - layout.size.height / 2f))
        }
    }
}

/** Streudiagramm mit Trendlinie (für Zusammenhänge). */
@Composable
fun ScatterChart(
    points: List<Pair<Double, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    xLabel: String = "",
    yLabel: String = "",
) {
    if (points.size < 3) {
        EmptyChart(height)
        return
    }
    val xs = points.map { it.first }
    val ys = points.map { it.second }
    val x0 = xs.min()
    val x1 = xs.max().let { if (it - x0 < 1e-9) x0 + 1 else it }
    val y0 = ys.min()
    val y1 = ys.max().let { if (it - y0 < 1e-9) y0 + 1 else it }
    val mx = xs.average()
    val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    val slope = if (sxx > 0) xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx else 0.0
    val icpt = my - slope * mx
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val trend = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Row {
            Column(Modifier.height(height).width(36.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.num(y1, if (y1 - y0 < 10) 1 else 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.num(y0, if (y1 - y0 < 10) 1 else 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                fun px(x: Double) = ((x - x0) / (x1 - x0) * (size.width - 12f) + 6f).toFloat()
                fun py(y: Double) = (size.height - 6f - (y - y0) / (y1 - y0) * (size.height - 12f)).toFloat()
                drawRoundRect(grid, Offset.Zero, size, CornerRadius(12f), style = Stroke(1f))
                points.forEach { (x, y) -> drawCircle(color.copy(alpha = 0.55f), 4.dp.toPx(), Offset(px(x), py(y))) }
                drawLine(
                    trend, Offset(px(x0), py(icpt + slope * x0)), Offset(px(x1), py(icpt + slope * x1)),
                    strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp)) {
            Text(Fmt.num(x0, if (x1 - x0 < 10) 1 else 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(xLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(Fmt.num(x1, if (x1 - x0 < 10) 1 else 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (yLabel.isNotEmpty()) Text("↑ $yLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Normalbereich als Band, aktueller Wert als Punkt. */
@Composable
fun BulletRange(value: Double?, mean: Double?, sd: Double?, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val center = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val h = size.height
        drawRoundRect(track, Offset(0f, h / 2 - 4.dp.toPx()), Size(size.width, 8.dp.toPx()), CornerRadius(8f))
        if (mean == null || sd == null || sd <= 0) return@Canvas
        var lo = mean - 3 * sd
        var hi = mean + 3 * sd
        if (value != null) {
            lo = min(lo, value - 0.3 * sd)
            hi = max(hi, value + 0.3 * sd)
        }
        fun x(v: Double) = ((v - lo) / (hi - lo) * size.width).toFloat()
        drawRoundRect(Palette.Good.copy(alpha = 0.18f), Offset(x(mean - 2 * sd), h / 2 - 4.dp.toPx()), Size(x(mean + 2 * sd) - x(mean - 2 * sd), 8.dp.toPx()), CornerRadius(8f))
        drawRoundRect(Palette.Good.copy(alpha = 0.45f), Offset(x(mean - sd), h / 2 - 4.dp.toPx()), Size(x(mean + sd) - x(mean - sd), 8.dp.toPx()), CornerRadius(8f))
        drawLine(center, Offset(x(mean), h / 2 - 6.dp.toPx()), Offset(x(mean), h / 2 + 6.dp.toPx()), strokeWidth = 2f)
        if (value != null) {
            drawCircle(surface, 8.dp.toPx(), Offset(x(value), h / 2))
            drawCircle(color, 6.dp.toPx(), Offset(x(value), h / 2))
        }
    }
}

fun ForecastScenario.color(): Color = when (this) {
    ForecastScenario.REST -> Palette.Good
    ForecastScenario.EASY -> Palette.Teal
    ForecastScenario.NORMAL -> Palette.Blue
    ForecastScenario.HARD -> Palette.Orange
}

/** Prognose: je Tag und Szenario ein Bereichsbalken mit erwartetem Wert. */
@Composable
fun ForecastChart(
    f: ForecastReport,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    scenarios: List<ForecastScenario> = listOf(ForecastScenario.REST, ForecastScenario.NORMAL, ForecastScenario.HARD),
) {
    val tm = rememberTextMeasurer()
    val numStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val dates = f.scenarios.values.firstOrNull()?.map { it.date }.orEmpty()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            fun y(v: Int) = size.height - v / 100f * size.height
            listOf(45, 60, 75).forEach { t ->
                drawLine(grid, Offset(0f, y(t)), Offset(size.width, y(t)), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            val groups = dates.size.coerceAtLeast(1)
            val gw = size.width / groups
            val bw = gw / (scenarios.size + 1.5f)
            for (g in 0 until groups) {
                scenarios.forEachIndexed { si, sc ->
                    val p = f.scenarios[sc]?.getOrNull(g) ?: return@forEachIndexed
                    val x = g * gw + (gw - bw * scenarios.size) / 2 + si * bw + bw / 2
                    val c = sc.color()
                    drawLine(c.copy(alpha = 0.35f), Offset(x, y(p.high)), Offset(x, y(p.low)), strokeWidth = bw * 0.55f, cap = StrokeCap.Round)
                    drawCircle(c, bw * 0.32f, Offset(x, y(p.expected)))
                    val layout = tm.measure(AnnotatedString("${p.expected}"), style = numStyle)
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y(p.high) - layout.size.height - 2f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
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
        Spacer(Modifier.height(6.dp))
        Legend(scenarios.map { it.label to it.color() })
    }
}

/** Farbige Skala mit Markierung. */
@Composable
fun ScaleBar(
    segments: List<Pair<String, Color>>,
    position: Float?,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
) {
    val marker = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                segments.forEach { (_, c) -> Box(Modifier.weight(1f).height(12.dp).background(c)) }
            }
            if (position != null) {
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val x = size.width * position.coerceIn(0.02f, 0.98f)
                    drawCircle(surface, 9.dp.toPx(), Offset(x, size.height / 2))
                    drawCircle(marker, 9.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in zoneMin.indices.reversed()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names.getOrElse(i) { "Z$i" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(92.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                    Box(
                        Modifier
                            .fillMaxWidth((zoneMin[i] / maxV).toFloat().coerceIn(0.02f, 1f))
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Palette.Zones[i]),
                    )
                }
                Text(
                    "${Fmt.int(zoneMin[i])}′ · ${Fmt.pct(zoneMin[i] / total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp),
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Noch keine Daten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
