package de.vitalcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.vitalcoach.core.Fmt
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Eine Zeile in der Werte-Blase: Name, Wert und Farbpunkt. */
data class TipRow(val name: String, val value: String, val color: Color? = null)

/** Inhalt der Werte-Blase eines Diagramms. */
data class Tip(val title: String, val rows: List<TipRow>)

/** Ausgewählter Index eines Diagramms; wird bei neuen Daten zurückgesetzt. Kurzes Vibrieren beim Wechsel. */
@Composable
fun rememberChartSelection(key: Any?): MutableState<Int?> {
    val s = remember(key) { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(s.value) { if (s.value != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    return s
}

/**
 * Antippen wählt einen Punkt aus (nochmal antippen = ausblenden), seitliches Wischen fährt die Werte ab.
 * Senkrechtes Scrollen der Seite bleibt möglich. [indexAt] rechnet die Fingerposition in einen Index um.
 */
fun Modifier.chartTouch(key: Any?, selected: MutableState<Int?>, indexAt: (Offset, IntSize) -> Int?): Modifier = this
    .pointerInput(key) {
        detectTapGestures { p ->
            val i = indexAt(p, size)
            selected.value = if (i == null || i == selected.value) null else i
        }
    }
    .pointerInput(key) {
        detectHorizontalDragGestures(
            onDragStart = { p -> indexAt(p, size)?.let { selected.value = it } },
        ) { change, _ ->
            change.consume()
            indexAt(change.position, size)?.let { selected.value = it }
        }
    }

/** Index bei gleichmäßig verteilten Punkten (Liniendiagramm: erster Punkt links, letzter rechts). */
fun pointIndex(x: Float, width: Int, n: Int): Int =
    if (n <= 1) 0 else (x / width.coerceAtLeast(1) * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** Index bei Säulen (jede Säule hat ein gleich breites Feld). */
fun slotIndex(x: Float, width: Int, n: Int): Int =
    (x / width.coerceAtLeast(1) * n).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))

/** Nächster Index mit vorhandenem Wert. */
fun nearestWithValue(i: Int, has: (Int) -> Boolean, n: Int): Int? {
    if (n == 0) return null
    for (d in 0 until n) {
        if (i - d >= 0 && has(i - d)) return i - d
        if (i + d < n && has(i + d)) return i + d
    }
    return null
}

/** Werte-Blase, waagerecht über der Auswahl ausgerichtet ([xFraction] 0–1) und am Rand begrenzt. */
@Composable
fun ChartTip(tip: Tip, xFraction: Float, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.inverseSurface
    val fg = MaterialTheme.colorScheme.inverseOnSurface
    Layout(
        content = {
            Column(
                Modifier
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (tip.title.isNotEmpty()) Text(tip.title, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
                tip.rows.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.color != null) {
                            androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(CircleShape).background(r.color))
                            Spacer(Modifier.width(6.dp))
                        }
                        if (r.name.isNotEmpty()) {
                            Text(r.name, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.85f))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(r.value, style = MaterialTheme.typography.titleSmall, color = fg)
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        // Höhe nicht begrenzen: bei sehr flachen Diagrammen darf die Blase nach unten überstehen.
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        val w = constraints.maxWidth
        val x = (xFraction * w - p.width / 2f).roundToInt().coerceIn(0, max(0, w - p.width))
        layout(w, min(p.height, constraints.maxHeight)) { p.place(x, 0) }
    }
}

// ---------------------------------------------------------------- Beschriftungen

/** „Heute · Fr, 7.11.“, „Gestern · Do, 6.11.“, „vor 3 Tagen · Di, 4.11.“ oder „Mo, 3.11.“ */
fun dayTip(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val base = Fmt.weekday(d).take(2) + ", " + Fmt.dayShort(d) + if (d.year != today.year) d.year.toString() else ""
    return when (val diff = ChronoUnit.DAYS.between(d, today)) {
        0L -> "Heute · $base"
        1L -> "Gestern · $base"
        in 2L..6L -> "vor $diff Tagen · $base"
        else -> base
    }
}

fun dayTips(dates: List<LocalDate>): List<String> {
    val today = LocalDate.now()
    return dates.map { dayTip(it, today) }
}

/** „Letzte Nacht · zum Fr, 7.11.“ – Schlaf wird dem Tag des Aufwachens zugeordnet. */
fun nightTip(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val base = "zum " + Fmt.weekday(d).take(2) + ", " + Fmt.dayShort(d)
    return when (ChronoUnit.DAYS.between(d, today)) {
        0L -> "Letzte Nacht · $base"
        1L -> "Vorletzte Nacht · $base"
        else -> "Nacht $base"
    }
}

/** „KW 45 · 3.11.–9.11.“ (+ „diese Woche“ / „letzte Woche“). */
fun weekTip(anyDay: LocalDate, today: LocalDate = LocalDate.now()): String {
    val mon = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisMon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rel = when (ChronoUnit.WEEKS.between(mon, thisMon)) {
        0L -> " · diese Woche"
        1L -> " · letzte Woche"
        else -> ""
    }
    return "KW ${mon.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${Fmt.dayShort(mon)}–${Fmt.dayShort(mon.plusDays(6))}$rel"
}

/** „14:30 Uhr · vor 1 h 10 min“ (bei anderen Tagen mit Datum). */
fun timeTip(t: Instant, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
    val z = t.atZone(zone)
    val hm = String.format(java.util.Locale.GERMANY, "%02d:%02d Uhr", z.hour, z.minute)
    val min = Duration.between(t, now).toMinutes()
    val rel = when {
        min < 0 -> null
        min < 2 -> "jetzt"
        min < 60 -> "vor $min min"
        min < 24 * 60 -> "vor ${min / 60} h" + if (min % 60 >= 5) " ${min % 60} min" else ""
        else -> null
    }
    val today = LocalDate.now(zone)
    val prefix = if (z.toLocalDate() != today) dayTip(z.toLocalDate(), today).substringBefore(" · ") + ", " else ""
    return prefix + hm + (rel?.let { " · $it" } ?: "")
}

/** Zahl mit Einheit, z. B. „52 bpm“, „7,5 h“. */
fun withUnit(v: Double, decimals: Int, unit: String): String = Fmt.num(v, decimals) + if (unit.isNotEmpty()) " $unit" else ""
