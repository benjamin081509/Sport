package de.vitalcoach.core

import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kurze Klartext-Einordnungen („Was heißt das für dich?“) für Kennzahlen, die ohne Vorwissen schwer zu deuten
 * sind: Trainingspunkte (TRIMP), Wochenvergleiche, ACWR und Fitness/Ermüdung/Form.
 * Bewusst kurz – wenige Sätze mit deinen eigenen Zahlen.
 */
object Meaning {

    /** Einordnung einer Einheit nach Trainingspunkten. [step]: 0 = leicht … 4 = sehr hart. */
    data class SessionLevel(val label: String, val adjective: String, val step: Int)

    /** Richtwerte der TRIMP-Skala, passend zur Banister-Formel für dein Geschlecht. */
    data class TrimpScale(val easy30: Int, val brisk60: Int, val hard60: Int)

    /** „Locker“ = Mitte von Zone 2 (65 % der Herzfrequenzreserve). */
    private const val EASY_HRR = 0.65

    fun trimpScale(sex: Sex?) = TrimpScale(
        easy30 = round5(30 * HeartMath.trimpPerMinute(EASY_HRR, sex)),
        brisk60 = round5(60 * HeartMath.trimpPerMinute(0.70, sex)),
        hard60 = round5(60 * HeartMath.trimpPerMinute(0.80, sex)),
    )

    fun trimpLegend(sex: Sex?): String {
        val s = trimpScale(sex)
        return "TRIMP = Trainingspunkte aus Dauer × Puls. 30 min locker ≈ ${s.easy30} · 1 h zügig ≈ ${s.brisk60} · 1 h hart ≈ ${s.hard60}"
    }

    fun sessionLevel(trimp: Double): SessionLevel = when {
        trimp < 40 -> SessionLevel("leicht", "Leichte", 0)
        trimp < 80 -> SessionLevel("moderat", "Moderate", 1)
        trimp < 130 -> SessionLevel("fordernd", "Fordernde", 2)
        trimp < 200 -> SessionLevel("hart", "Harte", 3)
        else -> SessionLevel("sehr hart", "Sehr harte", 4)
    }

    /** So viele Minuten lockeres Training (Zone 2) ergeben dieselben Punkte. */
    fun easyMinutes(trimp: Double, sex: Sex?): Int = round5(trimp / HeartMath.trimpPerMinute(EASY_HRR, sex))

    /** Einordnung einer einzelnen Einheit – auch im Vergleich zu deinen üblichen Einheiten derselben Sportart. */
    fun session(w: WorkoutAnalysis, r: AnalysisResult): String {
        val lvl = sessionLevel(w.trimp)
        val sb = StringBuilder()
        sb.append("${lvl.adjective} Einheit: ${Fmt.int(w.trimp)} Punkte entsprechen ≈ ${easyMinutes(w.trimp, r.profile.sex)} min lockerem Training (Zone 2).")
        val same = r.workouts.filter {
            it.workout.type == w.workout.type && it.durationMin >= 10 && it.workout.start != w.workout.start &&
                Duration.between(it.workout.start, r.generatedAt).toDays() <= 90
        }
        val med = if (same.size >= 3) Stats.median(same.map { it.trimp }) else null
        if (med != null && med > 0) {
            val rel = w.trimp / med
            val typical = "sonst ≈ ${Fmt.int(med)}"
            sb.append(' ').append(
                when {
                    rel >= 1.3 -> "Deutlich mehr als deine übliche Einheit dieser Art ($typical)."
                    rel >= 1.1 -> "Etwas mehr als üblich ($typical)."
                    rel > 0.9 -> "Typisch für dich ($typical)."
                    rel > 0.7 -> "Etwas weniger als üblich ($typical)."
                    else -> "Deutlich weniger als deine übliche Einheit dieser Art ($typical)."
                },
            )
        }
        return sb.toString()
    }

    /** Diese Woche vs. letzte 4 Wochen vs. die 2 Monate davor. */
    fun weeks(l: LoadReport): String {
        val now = l.weekLoad
        val recent = l.weekAvg28
        val parts = mutableListOf<String>()
        parts += when {
            recent < 5 && now < 5 -> "Zuletzt kaum Training."
            recent < 5 -> "Diese Woche ${Fmt.int(now)} Punkte – davor kaum Training."
            else -> {
                val pct = ((now / recent - 1) * 100).roundToInt()
                when {
                    abs(pct) < 10 -> "Diese Woche ungefähr so viel wie im Schnitt der letzten 4 Wochen."
                    pct > 0 -> "Diese Woche $pct % mehr als im Schnitt der letzten 4 Wochen."
                    else -> "Diese Woche ${-pct} % weniger als im Schnitt der letzten 4 Wochen."
                }
            }
        }
        val before = l.weekAvgBefore
        if (before != null && before >= 10) {
            val rel = recent / before
            val nums = "(${Fmt.int(recent)} statt ${Fmt.int(before)} pro Woche)"
            parts += when {
                rel < 0.6 -> "Seit etwa einem Monat trainierst du deutlich weniger als davor $nums." + ctlTrend(l)
                rel < 0.85 -> "Insgesamt etwas weniger als vor 1–3 Monaten $nums."
                rel <= 1.2 -> "Über 3 Monate betrachtet trainierst du gleichmäßig."
                else -> "Seit etwa einem Monat trainierst du deutlich mehr als davor $nums – achte auf Erholung."
            }
        }
        return parts.joinToString(" ")
    }

    private fun ctlTrend(l: LoadReport): String {
        val ago = l.ctl28Ago ?: return ""
        return if (ago >= 5 && l.ctl < ago * 0.9) " Dein Fitness-Wert ist dadurch von ${Fmt.int(ago)} auf ${Fmt.int(l.ctl)} gesunken." else ""
    }

    /** ACWR in Klartext: aktuelle vs. gewohnte Belastung und was das fürs Verletzungsrisiko heißt. */
    fun acwr(l: LoadReport): String {
        val a = l.acwr ?: return "Für diesen Vergleich braucht die App mindestens 3 Wochen Daten."
        val parts = mutableListOf<String>()
        val pct = ((a - 1.0) * 100).roundToInt()
        parts += when {
            abs(pct) < 10 -> "Du belastest dich aktuell etwa so viel wie gewohnt."
            pct > 0 -> "Du belastest dich aktuell $pct % mehr als gewohnt."
            else -> "Du belastest dich aktuell ${-pct} % weniger als gewohnt."
        }
        parts += when (l.status) {
            LoadStatus.DETRAINING -> "Gut zur Erholung – länger als 2 Wochen kostet es aber Fitness."
            LoadStatus.MAINTAIN -> "Sicherer Bereich: hält deine Fitness, baut aber kaum neue auf."
            LoadStatus.OPTIMAL -> "Idealer Bereich: Fortschritt bei geringem Verletzungsrisiko."
            LoadStatus.HIGH -> "Ab 1,3 steigt das Verletzungsrisiko – plane lockere Tage ein."
            LoadStatus.OVERREACHING -> "Über 1,5 ist das Verletzungsrisiko deutlich erhöht – tritt ein paar Tage kürzer."
            LoadStatus.NO_DATA -> ""
        }
        val acute = l.acuteWeek
        val ds = l.daysSinceWorkout
        if (acute != null && l.weekLoad >= 20 && l.weekLoad > acute * 1.5 && ds != null && ds >= 3) {
            parts += "Frische Trainings zählen stärker – dein letztes ist $ds Tage her, daher liegt „aktuell“ unter der 7-Tage-Summe (${Fmt.int(l.weekLoad)})."
        }
        val before = l.weekAvgBefore
        if (before != null && before >= 10 && l.weekAvg28 < 0.6 * before) {
            parts += "Achtung: „Gewohnt“ ist nach den ruhigen Wochen niedrig – steigere schrittweise. Ein Sprung zurück auf früher (≈ ${Fmt.int(before)} pro Woche) wäre ein Belastungssprung."
        }
        l.monotony?.let { if (it > 1.5) parts += "Dein Training ist sehr gleichförmig (Monotonie ${Fmt.num(it, 1)}) – wechsle harte und lockere Tage stärker ab." }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Fitness (CTL), Ermüdung (ATL) und Form (TSB) in Klartext. */
    fun form(l: LoadReport): String {
        val parts = mutableListOf<String>()
        parts += "Fitness = Ø Trainingspunkte pro Tag der letzten ~6 Wochen, Ermüdung = der letzten ~7 Tage."
        val ago = l.ctl28Ago
        if (ago != null && ago >= 5) {
            val rel = l.ctl / ago
            parts += when {
                rel < 0.85 -> "Deine Fitness ist in 4 Wochen von ${Fmt.int(ago)} auf ${Fmt.int(l.ctl)} gesunken."
                rel > 1.15 -> "Deine Fitness ist in 4 Wochen von ${Fmt.int(ago)} auf ${Fmt.int(l.ctl)} gestiegen."
                else -> "Deine Fitness ist in den letzten 4 Wochen stabil."
            }
        }
        val f = "Form ${Fmt.signed(l.tsb)}"
        parts += when {
            l.tsb > 5 -> "$f: Du bist ausgeruht – gut vor einem Wettkampf, auf Dauer aber zu wenig Trainingsreiz."
            l.tsb >= -10 -> "$f: ausgeglichen."
            l.tsb >= -30 -> "$f: Du bist vom Training ermüdet – im Aufbau normal."
            else -> "$f: stark ermüdet – plane Erholung ein."
        }
        return parts.joinToString(" ")
    }

    private fun round5(x: Double): Int = ((x / 5.0).roundToInt() * 5).coerceAtLeast(5)
}
