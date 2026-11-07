package de.vitalcoach.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Persönlicher Laufplan: feste Tage, Erinnerungszeit und ein Wenn-Dann-Plan.
 * Wenn-Dann-Pläne („Implementation Intentions“) erhöhen die Umsetzung von Vorsätzen deutlich
 * (Gollwitzer & Sheeran 2006, Metaanalyse, d ≈ 0,65); feste Tage und Uhrzeiten machen Sport zur Gewohnheit
 * (Lally et al. 2010: im Mittel ~66 Tage, einzelne verpasste Tage schaden kaum).
 */
data class RunPlan(
    val days: Set<DayOfWeek>,
    /** Erinnerungszeit in Minuten nach Mitternacht. */
    val reminderMinutes: Int,
    val ifThen: String,
    val startDate: LocalDate,
    /** Nachhaken 2 h nach der Erinnerung, falls noch kein Training. */
    val followUp: Boolean = true,
) {
    val goal: Int get() = days.size
}

/** Ein Tag im Wochenplan. */
data class PlanDay(
    val date: LocalDate,
    val planned: Boolean,
    val done: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val sessions: List<WorkoutAnalysis>,
) {
    val missed: Boolean get() = planned && !done && !isFuture && !isToday
}

data class PlanWeek(val monday: LocalDate, val days: List<PlanDay>, val done: Int, val goal: Int) {
    val reached: Boolean get() = goal > 0 && done >= goal
}

/** Vorschlag für die heutige Einheit – abhängig von der aktuellen Trainingsbereitschaft. */
data class SessionSuggestion(val title: String, val detail: String, val tone: Tone, val restAdvised: Boolean = false)

data class PlanReport(
    val week: PlanWeek,
    /** Letzte Wochen inkl. der aktuellen (älteste zuerst). */
    val history: List<PlanWeek>,
    /** Wochen in Folge mit erreichtem Wochenziel (die laufende Woche zählt, sobald erreicht). */
    val streakWeeks: Int,
    val sessionsSinceStart: Int,
    /** Einheiten bis zur Gewohnheit (Wochenziel × ~9 Wochen ≈ 66 Tage). */
    val habitTarget: Int,
    val todayPlanned: Boolean,
    val todayDone: Boolean,
    /** Nächster geplanter, noch offener Tag (heute eingeschlossen). */
    val nextPlanned: LocalDate?,
    val suggestion: SessionSuggestion,
)

object Motivation {
    /** Als Einheit zählt jedes Training ab 15 Minuten (Gehen ausgenommen). */
    const val MIN_SESSION_MIN = 15.0
    const val HABIT_WEEKS = 9
    const val FOLLOW_UP_MIN = 120

    val TEMPLATES = listOf(
        "Wenn ich nach der Arbeit heimkomme, ziehe ich sofort die Laufsachen an – bevor ich mich hinsetze.",
        "Wenn ich Feierabend habe, laufe ich direkt von der Arbeit los – die Laufsachen habe ich dabei.",
        "Wenn morgens mein Wecker klingelt, ziehe ich die bereitgelegten Laufsachen an und laufe vor der Arbeit.",
        "Wenn ich Mittagspause habe, laufe ich 20–30 Minuten.",
    )

    /** Kurze, belegte Tipps gegen den inneren Schweinehund. */
    val TIPS = listOf(
        "Schon 10 Minuten zählen – meist läufst du dann von selbst weiter.",
        "Nicht erst hinsetzen: direkt umziehen, dann ist der schwerste Teil geschafft.",
        "Leg die Laufsachen schon morgens bereit – je weniger Hürden, desto eher läufst du.",
        "Gönn dir deinen Lieblings-Podcast nur beim Laufen – so freust du dich darauf.",
        "Ein verpasster Tag schadet der Gewohnheit kaum – wichtig ist nur der nächste Termin.",
        "Nach dem Laufen fühlst du dich fast immer besser als vorher – Bewegung senkt Stress spürbar.",
        "Regelmäßige lockere Läufe verbessern deine Ausdauer schon nach wenigen Wochen messbar.",
        "Verabrede dich mit jemandem – gemeinsam sagt man seltener ab.",
    )

    fun counts(w: WorkoutAnalysis): Boolean = w.durationMin >= MIN_SESSION_MIN && w.workout.type != SportType.WALKING

    fun tipOfDay(date: LocalDate): String = TIPS[Math.floorMod(date.toEpochDay(), TIPS.size.toLong()).toInt()]

    fun report(plan: RunPlan, r: AnalysisResult): PlanReport {
        val today = r.today
        val byDate = r.workouts.filter { counts(it) }.groupBy { it.workout.start.atZone(r.zone).toLocalDate() }
        fun week(monday: LocalDate): PlanWeek {
            val days = (0 until 7).map { i ->
                val d = monday.plusDays(i.toLong())
                val s = byDate[d].orEmpty()
                PlanDay(d, d.dayOfWeek in plan.days, s.isNotEmpty(), d == today, d.isAfter(today), s)
            }
            return PlanWeek(monday, days, days.count { it.done }, plan.goal)
        }
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val history = (7 downTo 0).map { week(thisMonday.minusWeeks(it.toLong())) }
        val current = history.last()
        var streak = if (current.reached) 1 else 0
        for (w in history.dropLast(1).reversed()) {
            if (w.reached) streak++ else break
        }
        val sinceStart = byDate.keys.count { !it.isBefore(plan.startDate) && !it.isAfter(today) }
        val todayDay = current.days.first { it.isToday }
        val next = (0L..7L).map { today.plusDays(it) }.firstOrNull { d ->
            d.dayOfWeek in plan.days && (d != today || !todayDay.done)
        }
        return PlanReport(
            week = current,
            history = history,
            streakWeeks = streak,
            sessionsSinceStart = sinceStart,
            habitTarget = (plan.goal * HABIT_WEEKS).coerceAtLeast(1),
            todayPlanned = todayDay.planned,
            todayDone = todayDay.done,
            nextPlanned = next,
            suggestion = suggestion(r),
        )
    }

    fun suggestion(r: AnalysisResult): SessionSuggestion {
        val t = r.todayMetrics
        val ready = r.energy?.readinessNow ?: t?.readiness?.value
        val pace = r.fitness.zones.firstOrNull { it.number == 2 }?.paceText?.let { " · Pace $it" } ?: ""
        return when {
            t?.illnessFlag == true -> SessionSuggestion(
                "Heute lieber Pause",
                "Deine Werte deuten auf eine beginnende Erkrankung hin – Lauf verschieben, viel trinken, früh schlafen.",
                Tone.BAD, restAdvised = true,
            )
            ready == null -> SessionSuggestion("30 min locker", "Zone 2$pace – im Zweifel lieber etwas langsamer.", Tone.OK)
            ready >= 75 -> SessionSuggestion("35–50 min locker", "Zone 2$pace. Wenn du dich gut fühlst: zum Schluss 4 × 20 s zügig.", Tone.GOOD)
            ready >= 60 -> SessionSuggestion("30–40 min locker", "Zone 2$pace.", Tone.GOOD)
            ready >= 45 -> SessionSuggestion("20–30 min ganz locker", "Zone 1–2, langsamer als gedacht – auch das bringt dich weiter.", Tone.OK)
            else -> SessionSuggestion(
                "15–20 min ganz locker",
                "Dein Körper ist heute müde (Bereitschaft $ready). Ganz locker laufen oder zügig spazieren – Hauptsache dranbleiben.",
                Tone.WARN,
            )
        }
    }

    /** Erinnerung zur geplanten Zeit. */
    fun reminder(plan: RunPlan, r: AnalysisResult): DigestContent {
        val rep = report(plan, r)
        val s = rep.suggestion
        val title = if (s.restAdvised) "🏃 Lauftag – heute lieber Pause" else "🏃 Heute ist Lauftag!"
        val lines = mutableListOf<String>()
        lines += "⏱️ ${s.title}: ${s.detail}"
        if (!s.restAdvised) lines += "💭 ${plan.ifThen}"
        lines += weekLine(rep)
        fitnessLine(r)?.let { lines += it }
        lines += "💡 ${tipOfDay(r.today)}"
        return DigestContent(title, if (s.restAdvised) s.detail else "${s.title} – jetzt umziehen und los!", lines, s.tone)
    }

    /** Nachhaken, wenn 2 h nach der Erinnerung noch kein Training da ist. */
    fun followUp(plan: RunPlan, r: AnalysisResult): DigestContent {
        val rep = report(plan, r)
        val lines = mutableListOf<String>()
        lines += "Ein kurzer, lockerer Lauf zählt voll für dein Wochenziel (${rep.week.done}/${rep.week.goal})."
        lines += "💭 ${plan.ifThen}"
        val after = rep.nextPlanned?.takeIf { it != r.today } ?: nextPlannedAfter(plan, r.today)
        lines += "Klappt es heute nicht? Kein Problem – ein verpasster Tag schadet der Gewohnheit kaum." +
            (after?.let { " Nächster Lauftag: ${Fmt.weekday(it).take(2)}, ${Fmt.dayShort(it)}" } ?: "")
        return DigestContent("⏰ Noch Zeit für 15 Minuten?", "Auch ein kurzer Lauf zählt – Schuhe an und raus.", lines, Tone.OK)
    }

    /** Erinnerung ohne aktuelle Daten (z. B. ohne Hintergrund-Zugriff). */
    fun genericReminder(plan: RunPlan, today: LocalDate): DigestContent = DigestContent(
        "🏃 Heute ist Lauftag!",
        "Schuhe an und raus – schon 20 lockere Minuten zählen.",
        listOf("💭 ${plan.ifThen}", "💡 ${tipOfDay(today)}"),
        Tone.OK,
    )

    /** Zeile für die Morgen- bzw. Abend-Zusammenfassung. */
    fun digestLine(plan: RunPlan, r: AnalysisResult, morning: Boolean): String? {
        val rep = report(plan, r)
        val time = clockFromMidnight(plan.reminderMinutes)
        return if (morning) {
            when {
                rep.todayPlanned && !rep.todayDone && !rep.suggestion.restAdvised -> "🎒 Heute Lauftag ($time) – Laufsachen schon jetzt bereitlegen"
                rep.todayPlanned && rep.suggestion.restAdvised -> "🛌 Heute geplanter Lauf – besser verschieben"
                else -> null
            }
        } else {
            val next = nextPlannedAfter(plan, r.today)
            "🎯 Woche ${rep.week.done}/${rep.week.goal}" +
                (if (rep.todayDone) " · heute geschafft ✓" else "") +
                (if (rep.streakWeeks >= 2) " · 🔥 ${rep.streakWeeks} Wochen in Folge" else "") +
                (next?.let { " · nächster Lauf ${Fmt.weekday(it).take(2)}" } ?: "")
        }
    }

    fun weekLine(rep: PlanReport): String =
        "🎯 Diese Woche ${rep.week.done}/${rep.week.goal}" + if (rep.streakWeeks >= 1) " · 🔥 ${rep.streakWeeks} ${if (rep.streakWeeks == 1) "Woche" else "Wochen"} in Folge" else ""

    /** Persönliche Motivation aus dem Fitness-Verlauf. */
    fun fitnessLine(r: AnalysisResult): String? {
        val l = r.load
        val ago = l.ctl28Ago ?: return null
        return when {
            ago >= 5 && l.ctl < ago * 0.85 -> "📈 Dein Fitness-Wert ist von ${Fmt.int(ago)} auf ${Fmt.int(l.ctl)} gesunken – jeder Lauf bringt ihn wieder nach oben."
            ago >= 5 && l.ctl > ago * 1.1 -> "📈 Dein Fitness-Wert steigt: ${Fmt.int(ago)} → ${Fmt.int(l.ctl)} in 4 Wochen. Weiter so!"
            else -> null
        }
    }

    fun nextPlannedAfter(plan: RunPlan, date: LocalDate): LocalDate? =
        (1L..7L).map { date.plusDays(it) }.firstOrNull { it.dayOfWeek in plan.days }

    fun clockFromMidnight(minutes: Int): String = String.format("%02d:%02d", minutes / 60 % 24, minutes % 60)

    /** Zwei geplante Tage direkt hintereinander? (Hinweis: besser mit Ruhetag dazwischen.) */
    fun hasBackToBack(days: Set<DayOfWeek>): Boolean = days.any { it.plus(1) in days }
}
