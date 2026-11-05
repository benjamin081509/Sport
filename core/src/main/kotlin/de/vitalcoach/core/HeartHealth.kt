package de.vitalcoach.core

import kotlin.math.roundToInt

/**
 * Herzgesundheit, angelehnt an „Life's Essential 8“ der American Heart Association
 * (Lloyd-Jones et al. 2022, Circulation). Bewertet werden die Bereiche, für die Daten vorliegen:
 * Bewegung, Schlaf, BMI, Blutdruck und Nikotin (offizielle AHA-Punkteskalen), ergänzt um die von der
 * Uhr messbaren Werte Ruhepuls und kardiorespiratorische Fitness (VO2max).
 * Die Samsung-„Gefäßbelastung“ ist für andere Apps nicht abrufbar und daher nicht enthalten.
 */
object HeartHealth {

    /** AHA-Punkte für Bewegung (Minuten moderat + 2 × intensiv pro Woche). */
    fun activityPoints(min: Double): Int = when {
        min >= 150 -> 100
        min >= 120 -> 90
        min >= 90 -> 80
        min >= 60 -> 60
        min >= 30 -> 40
        min >= 1 -> 20
        else -> 0
    }

    /** AHA-Punkte für die durchschnittliche Schlafdauer. */
    fun sleepPoints(h: Double): Int = when {
        h >= 7 && h < 9 -> 100
        h >= 9 && h < 10 -> 90
        h >= 6 && h < 7 -> 70
        (h >= 5 && h < 6) || h >= 10 -> 40
        h >= 4 && h < 5 -> 20
        else -> 0
    }

    fun bmiPoints(bmi: Double): Int = when {
        bmi < 25 -> 100
        bmi < 30 -> 70
        bmi < 35 -> 30
        bmi < 40 -> 15
        else -> 0
    }

    fun bpPoints(sys: Double, dia: Double): Int = when {
        sys >= 160 || dia >= 100 -> 0
        sys >= 140 || dia >= 90 -> 25
        sys >= 130 || dia >= 80 -> 50
        sys >= 120 -> 75
        else -> 100
    }

    /** Ruhepuls: höheres Risiko je +10 bpm (Zhang et al. 2016, CMAJ Meta-Analyse). */
    fun restingHrPoints(rhr: Double): Int = when {
        rhr < 60 -> 100
        rhr < 70 -> 85
        rhr < 80 -> 60
        rhr < 90 -> 35
        else -> 10
    }

    /** Fitness: jede Stufe höhere VO2max senkt das Risiko deutlich (Kodama et al. 2009, JAMA). */
    fun fitnessPoints(categoryIndex: Int): Int = listOf(10, 30, 50, 70, 85, 100)[categoryIndex.coerceIn(0, 5)]

    fun build(r: AnalysisResult, bp: Pair<Double, Double>?, bpFromDevice: Boolean): HeartHealthReport {
        val c = mutableListOf<HeartHealthComponent>()
        val who = r.load.whoMinutes7
        c += HeartHealthComponent(
            "Bewegung", activityPoints(who), "${Fmt.int(who)} min moderate/intensive Aktivität in 7 Tagen",
            Provenance.CALCULATED, "Ziel: ≥ 150 min pro Woche (intensive Minuten zählen doppelt).", true,
        )
        val sleep = r.sleep.avgAsleep7Min
        c += if (sleep != null) {
            HeartHealthComponent(
                "Schlaf", sleepPoints(sleep / 60.0), "Ø ${Fmt.duration(sleep)} pro Nacht (7 Tage)",
                Provenance.MEASURED, "Optimal: 7–9 Stunden.", true,
            )
        } else {
            HeartHealthComponent("Schlaf", null, "keine Schlafdaten", Provenance.MEASURED, "Trage die Uhr nachts.", true)
        }
        val bmi = r.fitness.bmi
        c += if (bmi != null) {
            HeartHealthComponent("BMI", bmiPoints(bmi), "BMI ${Fmt.num(bmi, 1)}", Provenance.CALCULATED, "Optimal: unter 25.", true)
        } else {
            HeartHealthComponent("BMI", null, "Größe/Gewicht fehlen", Provenance.CALCULATED, "Trage Größe und Gewicht im Profil ein.", true)
        }
        c += if (bp != null) {
            HeartHealthComponent(
                "Blutdruck", bpPoints(bp.first, bp.second), "${bp.first.roundToInt()}/${bp.second.roundToInt()} mmHg",
                if (bpFromDevice) Provenance.MEASURED else Provenance.MEASURED,
                if (bpFromDevice) "Messung aus Health Connect. Optimal: unter 120/80." else "Deine Angabe im Profil. Optimal: unter 120/80.",
                true,
            )
        } else {
            HeartHealthComponent(
                "Blutdruck", null, "kein Wert", Provenance.MEASURED,
                "Miss deinen Blutdruck (z. B. mit der Uhr über Samsung Health Monitor oder einem Oberarmgerät) und trage ihn im Profil ein.", true,
            )
        }
        val nic = r.profile.nicotine
        c += if (nic != null) {
            HeartHealthComponent("Nikotin", nic.le8Points, nic.label, Provenance.MEASURED, "Rauchfrei = 100 Punkte.", true)
        } else {
            HeartHealthComponent("Nikotin", null, "keine Angabe", Provenance.MEASURED, "Trage deinen Rauchstatus im Profil ein.", true)
        }
        val rhr = r.fitness.restingHrNow
        c += if (rhr != null) {
            HeartHealthComponent(
                "Ruhepuls", restingHrPoints(rhr), "${Fmt.int(rhr)} bpm (Ø 7 Tage)", Provenance.CALCULATED,
                "Ein niedriger Ruhepuls spricht für ein effizientes Herz.", false,
            )
        } else {
            HeartHealthComponent("Ruhepuls", null, "keine Daten", Provenance.CALCULATED, "Trage die Uhr nachts.", false)
        }
        val cat = r.fitness.vo2CategoryIndex
        val vo2 = r.fitness.vo2max
        c += if (cat != null && vo2 != null) {
            HeartHealthComponent(
                "Herz-Kreislauf-Fitness", fitnessPoints(cat), "VO2max ${Fmt.num(vo2, 1)} (${FitnessMath.CATEGORY_LABELS[cat]})",
                Provenance.ESTIMATED, "Die VO2max ist einer der stärksten Prädiktoren für Herzgesundheit.", false,
            )
        } else {
            HeartHealthComponent(
                "Herz-Kreislauf-Fitness", null, "keine VO2max", Provenance.ESTIMATED,
                "Trage Geburtsjahr ein und zeichne Läufe auf.", false,
            )
        }
        val scored = c.mapNotNull { it.score }
        val score = if (scored.size >= 3) scored.average().roundToInt() else null
        val (label, tone) = when {
            score == null -> "Noch zu wenige Bereiche erfasst" to Tone.NEUTRAL
            score >= 80 -> "Hohe Herzgesundheit" to Tone.GOOD
            score >= 50 -> "Mittlere Herzgesundheit" to Tone.OK
            else -> "Niedrige Herzgesundheit" to Tone.BAD
        }
        return HeartHealthReport(score, label, tone, c, scored.size)
    }
}
