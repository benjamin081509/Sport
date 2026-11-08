package de.vitalcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import de.vitalcoach.app.outdoor.Locator
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.Tab
import de.vitalcoach.app.UiState
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.Coaching
import de.vitalcoach.core.Motivation
import de.vitalcoach.core.SessionKind
import de.vitalcoach.core.SessionPlan
import de.vitalcoach.core.PlanWeek
import de.vitalcoach.core.RunPlan
import java.time.DayOfWeek
import java.time.LocalDate

private val RunGradient = Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFEA580C), Color(0xFFDB2777)))
private val DONE_GRADIENT = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0FA3A3)))
private val WEEK = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)
private val SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

// ---------------------------------------------------------------- Heute-Seite

/** Auffälliges Banner an Lauftagen (bzw. Lob, wenn erledigt) – oder ein Hinweis, einen Plan anzulegen. */
@Composable
fun RunTodayBanner(state: UiState, vm: MainViewModel, onTab: (Tab) -> Unit) {
    val r = state.result ?: return
    val plan = state.runPlan
    if (plan == null) {
        SectionCard(onClick = { vm.open(Route.PlanEditor) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Palette.Orange, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Öfter laufen?", style = MaterialTheme.typography.titleMedium)
                    Text("Feste Lauftage + Erinnerung im richtigen Moment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Plan erstellen", style = MaterialTheme.typography.labelLarge, color = Palette.Orange)
            }
        }
        return
    }
    val rep = remember(plan, r) { Motivation.report(plan, r) }
    if (!rep.todayPlanned) return
    val done = rep.todayDone
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (done) DONE_GRADIENT else RunGradient)
            .clickable { onTab(Tab.TRAINING) }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) Icons.Filled.Check else Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (done) {
                    Text("Lauftag geschafft – stark!", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(Motivation.weekLine(rep).removePrefix("🎯 "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                } else {
                    val s = rep.suggestion
                    Text(
                        if (s.restAdvised) "Lauftag – heute lieber Pause" else "Heute ist Lauftag · ${Motivation.clockFromMidnight(plan.reminderMinutes)}",
                        style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold,
                    )
                    Text(s.title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(Motivation.weekLine(rep).removePrefix("🎯 "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            if (!done && !rep.suggestion.restAdvised) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable { vm.open(Route.RunRoutes) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("🗺️ Strecken", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Training-Seite

/** Wochenplan mit Häkchen, Serie, Gewohnheits-Fortschritt und Vorschlag für heute. */
@Composable
fun PlanCard(state: UiState, vm: MainViewModel) {
    val r = state.result ?: return
    val plan = state.runPlan
    if (plan == null) {
        SectionCard(title = "Öfter laufen", icon = Icons.AutoMirrored.Filled.DirectionsRun) {
            Text(
                "Lege feste Lauftage fest und bekomme zur passenden Zeit eine Erinnerung mit Vorschlag für deine Tagesform. " +
                    "Feste Termine und ein Wenn-Dann-Plan erhöhen nachweislich, wie oft man wirklich trainiert.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.open(Route.PlanEditor) }, modifier = Modifier.fillMaxWidth()) { Text("Laufplan erstellen") }
        }
        return
    }
    val rep = remember(plan, r) { Motivation.report(plan, r) }
    val week = remember(plan, r) { Coaching.week(plan, r) }
    val roles = remember(plan) { Coaching.roles(plan.days) }
    val todaySession = remember(plan, r) { Coaching.today(plan, r) }
    SectionCard(title = "Mein Laufplan", icon = Icons.AutoMirrored.Filled.DirectionsRun, onClick = { vm.open(Route.PlanEditor) }) {
        Text(week.phase, style = MaterialTheme.typography.labelLarge, color = if (week.deload) Palette.Teal else Palette.Orange)
        Spacer(Modifier.height(8.dp))
        WeekStrip(rep.week, roles)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${rep.week.done}", style = MaterialTheme.typography.displaySmall, color = if (rep.week.reached) Palette.Good else Palette.Orange)
                    Text(" / ${rep.week.goal}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(if (rep.week.reached) "Wochenziel erreicht ✓" else "Einheiten diese Woche", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rep.streakWeeks >= 1) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("🔥 ${rep.streakWeeks}", style = MaterialTheme.typography.headlineSmall)
                    Text(if (rep.streakWeeks == 1) "Woche geschafft" else "Wochen in Folge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LabeledBar(
            "Gewohnheit im Aufbau",
            rep.sessionsSinceStart.toFloat() / rep.habitTarget,
            "${rep.sessionsSinceStart} / ${rep.habitTarget} Einheiten",
            Palette.Teal,
        )
        Spacer(Modifier.height(10.dp))
        val hist = rep.history
        BarChart(
            values = hist.map { it.done.toDouble() },
            colors = hist.mapIndexed { i, w -> if (w.reached) Palette.Good else if (i == hist.lastIndex) Palette.Orange else Palette.Blue },
            goal = plan.goal.toDouble(),
            labels = hist.map { w -> "${w.monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" },
            height = 90.dp,
            tipLabels = hist.map { weekTip(it.monday) },
            name = "Einheiten",
            goalName = "Wochenziel",
        )
        Spacer(Modifier.height(10.dp))
        when {
            rep.todayPlanned && rep.todayDone -> MeaningBox("Heute erledigt – stark! Jede Einheit macht den nächsten Lauf leichter.", Palette.Good)
            rep.todayPlanned && todaySession != null -> TodaySessionBox(todaySession) { vm.open(Route.RunRoutes) }
            else -> rep.nextPlanned?.let { d ->
                Text(
                    "Nächster Lauftag: ${dayTip(d).substringAfter(" · ").ifEmpty { dayTip(d) }} · Erinnerung ${Motivation.clockFromMidnight(plan.reminderMinutes)} Uhr",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Diese Woche", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        week.sessions.forEach { ses ->
            val done = rep.week.days.firstOrNull { it.date == ses.date }?.done == true
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Text(
                    SHORT[ses.date.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(30.dp),
                    color = if (ses.date == r.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(ses.kind.emoji, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(ses.title + if (done) "  ✓" else "", style = MaterialTheme.typography.bodyMedium, color = if (done) Palette.Good else MaterialTheme.colorScheme.onSurface)
                    if (ses.kind != SessionKind.REST) {
                        Text(
                            "≈ ${Fmt.num(ses.targetKm, 1)} km · oder ${ses.bikeMinutes} min Hometrainer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MeaningBox("💭 ${plan.ifThen}", Palette.Purple)
    }
}

/** Heutige Einheit: Lauf, Hometrainer-Alternative, Zweck und Strecken-Knopf. */
@Composable
private fun TodaySessionBox(s: SessionPlan, onRoutes: () -> Unit) {
    val color = when (s.kind) {
        SessionKind.QUALITY -> Palette.Bad
        SessionKind.LONG -> Palette.Blue
        SessionKind.EASY -> Palette.Good
        SessionKind.REST -> Palette.Neutral
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text("Heute: ${s.kind.emoji} ${s.title}", style = MaterialTheme.typography.titleMedium, color = color)
        s.note?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
        }
        Spacer(Modifier.height(6.dp))
        Text("🏃 ${s.run}", style = MaterialTheme.typography.bodyMedium)
        if (s.kind != SessionKind.REST) {
            Spacer(Modifier.height(4.dp))
            Text("🚴 ${s.bike}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("💪 ${s.why}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRoutes, modifier = Modifier.fillMaxWidth()) { Text("🗺️ Strecken für heute (≈ ${Fmt.num(s.targetKm, 1)} km)") }
        }
    }
}

/** Mo–So mit Häkchen (erledigt), Läufer-Symbol (geplant), Strich (verpasst). */
@Composable
fun WeekStrip(week: PlanWeek, roles: Map<DayOfWeek, SessionKind> = emptyMap()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.days.forEachIndexed { i, d ->
            val (bg, border) = when {
                d.done && d.planned -> Palette.Good to Palette.Good
                d.done -> Palette.Teal to Palette.Teal
                d.missed -> Palette.Bad.copy(alpha = 0.12f) to Palette.Bad.copy(alpha = 0.5f)
                d.planned -> Palette.Orange.copy(alpha = 0.12f) to Palette.Orange
                else -> MaterialTheme.colorScheme.surfaceVariant to Color.Transparent
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    SHORT[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (d.isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .border(if (d.isToday) 2.5.dp else 1.5.dp, if (d.isToday && border == Color.Transparent) MaterialTheme.colorScheme.primary else border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        d.done -> Icon(Icons.Filled.Check, contentDescription = "erledigt", tint = Color.White, modifier = Modifier.size(20.dp))
                        d.missed -> Text("–", color = Palette.Bad, style = MaterialTheme.typography.titleMedium)
                        d.planned -> roles[d.date.dayOfWeek]?.let { Text(it.emoji, style = MaterialTheme.typography.titleSmall) }
                            ?: Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "geplant", tint = Palette.Orange, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Plan bearbeiten

@Composable
fun RunPlanScreen(state: UiState, vm: MainViewModel, actions: AppActions) {
    val existing = state.runPlan
    var days by remember { mutableStateOf(existing?.days ?: setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)) }
    var minutes by remember { mutableStateOf(existing?.reminderMinutes ?: (17 * 60 + 30)) }
    val existingIdx = existing?.let { Motivation.TEMPLATES.indexOf(it.ifThen) } ?: 0
    var choice by remember { mutableStateOf(existingIdx) }
    var custom by remember { mutableStateOf(if (existingIdx < 0) existing?.ifThen.orEmpty() else "") }
    var followUp by remember { mutableStateOf(existing?.followUp ?: true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locationOk by remember { mutableStateOf(Locator.hasPermission(context)) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationOk = Locator.hasPermission(context)
        if (locationOk) scope.launch { Locator.current(context) }
    }

    DetailScaffold("Mein Laufplan", onBack = { vm.back() }) {
        item {
            SectionCard(title = "An welchen Tagen läufst du?", icon = Icons.Filled.CalendarMonth) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WEEK.forEachIndexed { i, d ->
                        val on = d in days
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (on) Palette.Orange else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(SHORT[i], style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Wochenziel: ${days.size} ${if (days.size == 1) "Einheit" else "Einheiten"}. Tipp: 2 Tage unter der Woche + 1 am Wochenende, mit einem Ruhetag dazwischen. " +
                        "Es zählt jedes Training ab 15 Minuten – auch an anderen Tagen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (days.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val preview = Coaching.roles(days).entries.sortedBy { it.key.value }
                        .joinToString(" · ") { (d, k) -> "${SHORT[d.value - 1]} ${k.emoji} ${k.label}" }
                    Text("Deine Woche: $preview", style = MaterialTheme.typography.bodyMedium)
                }
                if (Motivation.hasBackToBack(days)) {
                    Spacer(Modifier.height(6.dp))
                    Text("Hinweis: Zwei Lauftage direkt hintereinander – plane den zweiten eher locker.", style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                }
            }
        }
        item {
            SectionCard(title = "Erinnerung", icon = Icons.Filled.Notifications) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Uhrzeit an Lauftagen", style = MaterialTheme.typography.titleSmall)
                        Text("Am besten kurz bevor du heimkommst oder Feierabend hast", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m -> minutes = h * 60 + m }, minutes / 60, minutes % 60, true).show()
                    }) { Text(Motivation.clockFromMidnight(minutes), style = MaterialTheme.typography.titleLarge) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nachhaken nach 2 Stunden", style = MaterialTheme.typography.titleSmall)
                        Text("Nur falls bis dahin noch kein Training aufgezeichnet ist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = followUp, onCheckedChange = { followUp = it })
                }
                if (!state.notificationsAllowed) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = actions.requestNotifications, modifier = Modifier.fillMaxWidth()) { Text("Benachrichtigungen erlauben") }
                }
                Spacer(Modifier.height(8.dp))
                if (locationOk) {
                    Text(
                        "🌦️ Wetter-Check aktiv: Bei Regen, Schnee, Glätte, Sturm oder starkem Frost schlägt die Erinnerung den Hometrainer vor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(
                        onClick = { locationLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Standort für Wetter-Check erlauben") }
                    Text(
                        "Dann schlägt die Erinnerung bei schlechtem Wetter automatisch den Hometrainer vor. Der Standort bleibt auf deinem Handy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionCard(title = "Dein Wenn-Dann-Plan", icon = Icons.Filled.Psychology) {
                Text(
                    "Lege vorher fest, WAS du in welchem Moment tust. So entscheidest du nicht erst auf dem Sofa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Motivation.TEMPLATES.forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { choice = i }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == i, onClick = { choice = i })
                        Text(t, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { choice = -1 }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice < 0, onClick = { choice = -1 })
                    Text("Eigener Plan", style = MaterialTheme.typography.bodyMedium)
                }
                if (choice < 0) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Wenn …, dann …") },
                    )
                }
            }
        }
        item {
            InfoCard(
                "Warum das funktioniert",
                "• Wenn-Dann-Pläne: Wer genau festlegt, wann und wo er handelt, setzt Vorsätze deutlich häufiger um (Gollwitzer & Sheeran 2006).\n" +
                    "• Gewohnheit: Feste Tage und Uhrzeiten machen Laufen nach im Mittel rund 2 Monaten zur Routine – ein verpasster Tag wirft dich kaum zurück (Lally et al. 2010).\n" +
                    "• Erinnerung im richtigen Moment, mit einem Vorschlag passend zu deiner Tagesform.\n" +
                    "• Hürden senken: Laufsachen morgens bereitlegen, nach der Arbeit nicht erst hinsetzen.\n" +
                    "• Belohnung koppeln: Lieblings-Podcast oder Hörbuch nur beim Laufen.\n" +
                    "• Klein anfangen: 10 Minuten zählen – meist läufst du weiter.\n\n" +
                    "Wochenstruktur: ~80 % locker, eine Intervall-Einheit (möglichst nach einem Ruhetag, nicht direkt vor dem langen Lauf), " +
                    "langer Lauf am Wochenende. Intervalle steigern sich von Fahrtspiel über 5 × 3 min bis 4 × 4 min (Helgerud et al. 2007), " +
                    "der lange Lauf wächst um 5 min pro Woche. Jede 4. Woche ist eine Entlastungswoche. Bei niedriger Bereitschaft wird " +
                    "die Einheit automatisch leichter. Hometrainer: ca. 30 % längere Dauer, Puls ca. 7 Schläge niedriger als beim Laufen.",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val text = if (choice >= 0) Motivation.TEMPLATES[choice] else custom.trim().ifEmpty { Motivation.TEMPLATES.first() }
                        vm.saveRunPlan(
                            RunPlan(
                                days = days,
                                reminderMinutes = minutes,
                                ifThen = text,
                                startDate = existing?.startDate ?: LocalDate.now(),
                                followUp = followUp,
                            ),
                        )
                        if (!state.notificationsAllowed) actions.requestNotifications()
                        vm.back()
                    },
                    enabled = days.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (existing == null) "Plan starten" else "Plan speichern") }
                if (existing != null) {
                    OutlinedButton(onClick = { vm.testRunReminder() }, modifier = Modifier.fillMaxWidth(), enabled = state.notificationsAllowed) {
                        Text("Erinnerung jetzt testen")
                    }
                    TextButton(onClick = { vm.saveRunPlan(null); vm.back() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Plan löschen", color = Palette.Bad)
                    }
                }
                Text(
                    "Gestartet: ${existing?.startDate?.let { Fmt.day(it) } ?: "heute"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
