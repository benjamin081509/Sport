package de.vitalcoach.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Art der Einheit im Wochenplan. */
enum class SessionKind(val label: String, val emoji: String, val short: String) {
    EASY("Locker", "🟢", "Z2"),
    QUALITY("Intervalle", "🔥", "Int"),
    LONG("Langer Lauf", "🛣️", "Lang"),
    REST("Pause", "🛌", "Pause"),
}

/** Eine konkrete Einheit mit Lauf- und Hometrainer-Variante. */
data class SessionPlan(
    val date: LocalDate,
    val kind: SessionKind,
    /** Kurztitel, z. B. „Intervalle · 5 × 3 min Zone 4“. */
    val title: String,
    /** Anleitung für den Lauf. */
    val run: String,
    /** Gleichwertige Einheit auf dem Hometrainer. */
    val bike: String,
    val runMinutes: Int,
    val bikeMinutes: Int,
    /** Ungefähre Streckenlänge für die Strecken-Vorschläge. */
    val targetKm: Double,
    /** Wozu die Einheit gut ist. */
    val why: String,
    /** Hinweis, wenn die Einheit an die Tagesform angepasst wurde. */
    val note: String? = null,
    /** Ursprünglich geplante Art (vor Anpassung). */
    val plannedKind: SessionKind = kind,
)

data class CoachWeek(val weekNo: Int, val deload: Boolean, val phase: String, val sessions: List<SessionPlan>)

/**
 * Wochenstruktur für Läuferinnen und Läufer (Freizeitsport), orientiert an gängiger Trainingswissenschaft:
 * - ~80 % locker / ~20 % intensiv (Seiler 2010; Stöggl & Sperlich 2014),
 * - eine Intervall-Einheit pro Woche, möglichst nach einem Ruhetag und nicht direkt vor dem langen Lauf,
 * - langer Lauf am Wochenende, behutsam steigend (+5 min/Woche, max. 90 min),
 * - Intervall-Progression: Fahrtspiel → 5 × 3 min → 4 × 4 min (Helgerud et al. 2007),
 * - jede 4. Woche Entlastung (−20 %, statt Intervallen nur kurze Steigerungen).
 * Die Tagesform hat Vorrang: bei niedriger Bereitschaft wird aus Intervallen ein lockerer Lauf.
 */
object Coaching {
    /** Hometrainer braucht für einen ähnlichen Ausdauerreiz etwa 30 % mehr Zeit. */
    const val BIKE_FACTOR = 1.3
    /** Auf dem Rad liegt der Puls bei gleicher Anstrengung meist einige Schläge niedriger (u. a. Millet et al. 2009). */
    const val BIKE_HR_OFFSET = 7
    const val DEFAULT_PACE_SEC = 390.0

    /** Rolle je Lauftag: langer Lauf am Wochenende, Intervalle mit größtmöglichem Abstand dazu, Rest locker. */
    fun roles(days: Set<DayOfWeek>): Map<DayOfWeek, SessionKind> {
        val sorted = days.sortedBy { it.value }
        if (sorted.isEmpty()) return emptyMap()
        if (sorted.size == 1) return mapOf(sorted.first() to SessionKind.EASY)
        val long = sorted.lastOrNull { it == DayOfWeek.SUNDAY } ?: sorted.lastOrNull { it == DayOfWeek.SATURDAY } ?: sorted.last()
        val others = sorted - long
        fun dist(a: DayOfWeek, b: DayOfWeek): Int = abs(a.value - b.value).let { min(it, 7 - it) }
        fun restBefore(d: DayOfWeek): Boolean = d.minus(1) !in days
        val quality = others.maxWith(compareBy<DayOfWeek>({ dist(it, long) }, { if (restBefore(it)) 1 else 0 }, { -it.value }))
        return sorted.associateWith {
            when (it) {
                long -> SessionKind.LONG
                quality -> SessionKind.QUALITY
                else -> SessionKind.EASY
            }
        }
    }

    /** Trainingswoche seit Planstart (1, 2, 3 …). */
    fun weekNumber(plan: RunPlan, date: LocalDate): Int {
        val start = plan.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mon = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (ChronoUnit.WEEKS.between(start, mon).toInt() + 1).coerceAtLeast(1)
    }

    fun isDeload(weekNo: Int): Boolean = weekNo % 4 == 0

    /** Stufe der Intervalle: 1 = Fahrtspiel, 2 = 5 × 3 min, 3 = 4 × 4 min – nach Trainingswochen und Fitness. */
    fun qualityStage(weekNo: Int, ctl: Double): Int {
        val byWeeks = when {
            weekNo <= 2 -> 1
            weekNo <= 5 -> 2
            else -> 3
        }
        val byFitness = when {
            ctl < 15 -> 1
            ctl < 30 -> 2
            else -> 3
        }
        return min(byWeeks, byFitness)
    }

    fun phaseLabel(weekNo: Int, ctl: Double): String = when {
        isDeload(weekNo) -> "Woche $weekNo · Entlastungswoche"
        qualityStage(weekNo, ctl) == 1 -> "Woche $weekNo · Einstieg"
        qualityStage(weekNo, ctl) == 2 -> "Woche $weekNo · Aufbau"
        else -> "Woche $weekNo · Leistung"
    }

    /** Geplante Einheit für einen Tag (ohne Anpassung an die Tagesform). */
    fun session(plan: RunPlan, r: AnalysisResult, date: LocalDate, kind: SessionKind): SessionPlan {
        val weekNo = weekNumber(plan, date)
        val deload = isDeload(weekNo)
        val ctl = r.load.ctl
        val z = Zones.of(r)
        return when (kind) {
            SessionKind.EASY -> easy(date, z, round5((30 + 2.5 * (weekNo - 1)).coerceAtMost(45.0) * if (deload) 0.8 else 1.0), strides = false)
            SessionKind.LONG -> {
                val base = if (ctl < 15) 40.0 else 45.0
                val m = round5((base + 5 * (weekNo - 1)).coerceAtMost(90.0) * if (deload) 0.8 else 1.0)
                SessionPlan(
                    date, kind, "Langer Lauf · $m min",
                    "$m min ruhig in Zone 2 · ${z.z2Text}. Gleichmäßig, du solltest dich unterhalten können.",
                    bike(bikeMinutes(m), "in Zone 2 · ${z.bikeZ2Text}, Trittfrequenz 85–95"),
                    m, bikeMinutes(m), km(m, z.easyPaceSec),
                    "Der lange Lauf baut Ausdauer, Fettstoffwechsel und Durchhaltevermögen auf.",
                )
            }
            SessionKind.QUALITY -> if (deload) {
                easy(date, z, 30, strides = true).copy(
                    plannedKind = SessionKind.QUALITY,
                    note = "Entlastungswoche: statt Intervallen nur kurze Steigerungen – so verarbeitet dein Körper das Training der letzten Wochen.",
                )
            } else {
                quality(date, z, qualityStage(weekNo, ctl))
            }
            SessionKind.REST -> rest(date, "Ruhetag.")
        }
    }

    /** Einheiten der laufenden Woche für alle Plantage. */
    fun week(plan: RunPlan, r: AnalysisResult): CoachWeek {
        val monday = r.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val roles = roles(plan.days)
        val sessions = (0 until 7).map { monday.plusDays(it.toLong()) }
            .mapNotNull { d -> roles[d.dayOfWeek]?.let { k -> if (d == r.today) today(plan, r) ?: session(plan, r, d, k) else session(plan, r, d, k) } }
        val weekNo = weekNumber(plan, r.today)
        return CoachWeek(weekNo, isDeload(weekNo), phaseLabel(weekNo, r.load.ctl), sessions)
    }

    /** Heutige Einheit, angepasst an die aktuelle Bereitschaft – null, wenn heute kein Lauftag ist. */
    fun today(plan: RunPlan, r: AnalysisResult): SessionPlan? {
        val kind = roles(plan.days)[r.today.dayOfWeek] ?: return null
        val planned = session(plan, r, r.today, kind)
        val t = r.todayMetrics
        val ready = r.energy?.readinessNow ?: t?.readiness?.value
        val z = Zones.of(r)
        return when {
            t?.illnessFlag == true -> rest(r.today, "Deine Werte deuten auf eine beginnende Erkrankung hin – heute Pause, viel trinken, früh schlafen.")
                .copy(plannedKind = kind, note = "Pause wegen Krankheitszeichen")
            ready == null -> planned
            ready < 45 -> easy(r.today, z, 20, strides = false).copy(
                plannedKind = kind,
                note = "Angepasst: Bereitschaft $ready – heute nur ganz locker oder zügig spazieren. Hauptsache dranbleiben.",
            )
            planned.kind == SessionKind.QUALITY && ready < 60 -> easy(r.today, z, 30, strides = false).copy(
                plannedKind = kind,
                note = "Angepasst: Bereitschaft $ready – Intervalle heute zu viel. Lieber locker; die Intervalle kommen nächste Woche.",
            )
            planned.kind == SessionKind.LONG && ready < 60 -> easy(r.today, z, min(planned.runMinutes, 40), strides = false).copy(
                plannedKind = kind,
                note = "Angepasst: Bereitschaft $ready – langer Lauf heute kürzer.",
            )
            planned.kind == SessionKind.EASY && ready >= 75 && planned.plannedKind == SessionKind.EASY ->
                easy(r.today, z, planned.runMinutes, strides = true)
            else -> planned
        }
    }

    // ---------------------------------------------------------------- Bausteine

    private fun easy(date: LocalDate, z: Zones, minutes: Int, strides: Boolean): SessionPlan = SessionPlan(
        date, SessionKind.EASY,
        "Locker · $minutes min Zone 2" + if (strides) " + Steigerungen" else "",
        "$minutes min locker in Zone 2 · ${z.z2Text}." + if (strides) " Zum Schluss 4–6 × 20 s zügig steigern, dazwischen locker gehen." else "",
        bike(bikeMinutes(minutes), "in Zone 2 · ${z.bikeZ2Text}" + if (strides) ", zum Schluss 4 × 20 s höhere Trittfrequenz" else ""),
        minutes, bikeMinutes(minutes), km(minutes, z.easyPaceSec),
        "Lockere Läufe stärken Herz, Grundlagenausdauer und Sehnen – die Basis für alles andere.",
    )

    private fun quality(date: LocalDate, z: Zones, stage: Int): SessionPlan {
        val bz4Low = z.z4Low - BIKE_HR_OFFSET
        val q = when (stage) {
            1 -> Quality(
                "Fahrtspiel · 6 × 1 min zügig",
                "6 × 1 min zügig (Zone 4 · ${z.z4Text}), dazwischen je 2 min locker",
                "6 × 1 min kräftig treten (Zone 4 · ${z.bikeZ4Text}), dazwischen je 2 min locker kurbeln",
                18,
                "Kurze, zügige Abschnitte gewöhnen Körper und Beine wieder an Tempo – ohne Überlastung.",
            )
            2 -> Quality(
                "Intervalle · 5 × 3 min Zone 4",
                "5 × 3 min in Zone 4 (${z.z4Text}), dazwischen je 2 min locker traben",
                "5 × 3 min in Zone 4 (${z.bikeZ4Text}), dazwischen je 2 min locker kurbeln",
                25,
                "Intervalle sind der stärkste Reiz für deine VO2max – du wirst schneller bei gleichem Puls.",
            )
            else -> Quality(
                "Intervalle · 4 × 4 min",
                "4 × 4 min in Zone 4–5 (Puls ab ${z.z4Low}), dazwischen je 3 min locker traben",
                "4 × 4 min in Zone 4–5 (Puls ab $bz4Low), dazwischen je 3 min locker kurbeln",
                28,
                "Das 4×4-Protokoll steigert die VO2max besonders wirksam (Helgerud et al. 2007).",
            )
        }
        val total = 10 + q.mainMinutes + 10
        return SessionPlan(
            date, SessionKind.QUALITY, q.title,
            "10 min einlaufen · ${q.run} · 10 min auslaufen.",
            "Hometrainer: 10 min einrollen · ${q.bike} · 10 min ausrollen.",
            total, total, km(total, z.easyPaceSec * 0.95), q.why,
        )
    }

    private fun rest(date: LocalDate, text: String) = SessionPlan(
        date, SessionKind.REST, "Heute Pause", text, "Kein Training – Erholung hat heute Vorrang.", 0, 0, 0.0,
        "Erholung ist Teil des Trainings: Erst in der Pause wird der Körper stärker.",
    )

    private fun bike(minutes: Int, how: String) = "Hometrainer: $minutes min $how."

    fun bikeMinutes(runMinutes: Int): Int = round5((runMinutes * BIKE_FACTOR).coerceAtMost(120.0))

    private fun km(minutes: Int, paceSec: Double): Double = ((minutes * 60.0 / paceSec) * 10).roundToInt() / 10.0

    private fun round5(x: Double): Int = ((x / 5.0).roundToInt() * 5).coerceAtLeast(5)

    private data class Quality(val title: String, val run: String, val bike: String, val mainMinutes: Int, val why: String)

    /** Pulszonen (Lauf und Rad) und Pace als fertige Texte. */
    class Zones private constructor(
        val z2Text: String,
        val z4Text: String,
        val z4Low: Int,
        val bikeZ2Text: String,
        val bikeZ4Text: String,
        val easyPaceSec: Double,
    ) {
        companion object {
            fun of(r: AnalysisResult): Zones {
                val z2 = r.fitness.zones.firstOrNull { it.number == 2 }
                val z4 = r.fitness.zones.firstOrNull { it.number == 4 }
                val pace = z2?.paceText
                val z2Text = (z2?.let { "Puls ${it.lowBpm}–${it.highBpm}" } ?: "gut unterhalten können") + (pace?.let { " · Pace $it" } ?: "")
                val z4Text = z4?.let { "Puls ${it.lowBpm}–${it.highBpm}" } ?: "kurzatmig, aber kontrolliert"
                val bikeZ2 = z2?.let { "Puls ${it.lowBpm - BIKE_HR_OFFSET}–${it.highBpm - BIKE_HR_OFFSET}" } ?: "gut unterhalten können"
                val bikeZ4 = z4?.let { "Puls ${it.lowBpm - BIKE_HR_OFFSET}–${it.highBpm - BIKE_HR_OFFSET}" } ?: "kurzatmig, aber kontrolliert"
                val slow = z2?.paceSlowSecKm
                val fast = z2?.paceFastSecKm
                val easyPace = if (slow != null && fast != null) (slow + fast) / 2.0 else DEFAULT_PACE_SEC
                return Zones(z2Text, z4Text, z4?.lowBpm ?: 0, bikeZ2, bikeZ4, easyPace)
            }
        }
    }
}
