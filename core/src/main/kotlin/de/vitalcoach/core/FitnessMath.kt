package de.vitalcoach.core

import kotlin.math.roundToInt

/**
 * Fitness-Einordnung.
 *  - VO2max-Kategorien: Cooper Institute Normwerte (veröffentlicht in Heyward, „Advanced Fitness
 *    Assessment and Exercise Prescription“).
 *  - Fitnessalter: Altersmittelwerte der HUNT-3-Fitnessstudie (Loe et al. 2013, PLoS One,
 *    3.816 gesunde Erwachsene). Fitnessalter = Alter, in dem dein VO2max dem Durchschnitt entspricht.
 *  - BMI: WHO-Klassifikation. Körperfett: ACE-Kategorien.
 */
object FitnessMath {

    val CATEGORY_LABELS = listOf("Sehr schwach", "Schwach", "Mittel", "Gut", "Sehr gut", "Hervorragend")

    // Untergrenzen der Kategorien „Schwach“ … „Hervorragend“ (ml/kg/min) je Altersgruppe.
    private val MEN = listOf(
        20 to listOf(33.0, 36.5, 42.5, 46.5, 52.5),
        30 to listOf(31.5, 35.5, 41.0, 45.0, 49.5),
        40 to listOf(30.2, 33.6, 39.0, 43.8, 48.1),
        50 to listOf(26.1, 31.0, 35.8, 41.0, 45.4),
        60 to listOf(20.5, 26.1, 32.3, 36.5, 44.3),
    )
    private val WOMEN = listOf(
        20 to listOf(23.6, 29.0, 33.0, 37.0, 41.1),
        30 to listOf(22.8, 27.0, 31.5, 35.7, 40.1),
        40 to listOf(21.0, 24.5, 29.0, 32.9, 37.0),
        50 to listOf(20.2, 22.8, 27.0, 31.5, 35.8),
        60 to listOf(17.5, 20.2, 24.5, 30.3, 31.5),
    )

    fun vo2Category(vo2: Double, age: Int, sex: Sex?): Int {
        fun idx(table: List<Pair<Int, List<Double>>>): Int {
            val row = table.last { age >= it.first || it == table.first() }.second
            return row.count { vo2 >= it }
        }
        return when (sex) {
            Sex.MALE -> idx(MEN)
            Sex.FEMALE -> idx(WOMEN)
            null -> ((idx(MEN) + idx(WOMEN)) / 2.0).roundToInt()
        }
    }

    // HUNT-3 Mittelwerte (Altersmitte → VO2max).
    private val HUNT_MEN = listOf(25.0 to 54.4, 35.0 to 49.0, 45.0 to 47.7, 55.0 to 42.6, 65.0 to 39.2, 75.0 to 34.5, 85.0 to 30.4)
    private val HUNT_WOMEN = listOf(25.0 to 43.0, 35.0 to 40.0, 45.0 to 38.4, 55.0 to 34.4, 65.0 to 31.1, 75.0 to 27.6, 85.0 to 23.6)

    fun fitnessAge(vo2: Double, sex: Sex?): Int {
        fun ageFor(table: List<Pair<Double, Double>>): Double {
            if (vo2 >= table.first().second) {
                val (a0, v0) = table[0]
                val (a1, v1) = table[1]
                val slope = (a1 - a0) / (v1 - v0)
                return a0 + (vo2 - v0) * slope
            }
            for (i in 0 until table.size - 1) {
                val (a0, v0) = table[i]
                val (a1, v1) = table[i + 1]
                if (vo2 <= v0 && vo2 >= v1) return a0 + (v0 - vo2) / (v0 - v1) * (a1 - a0)
            }
            val (a0, v0) = table[table.size - 2]
            val (a1, v1) = table.last()
            val slope = (a1 - a0) / (v1 - v0)
            return a1 + (vo2 - v1) * slope
        }
        val age = when (sex) {
            Sex.MALE -> ageFor(HUNT_MEN)
            Sex.FEMALE -> ageFor(HUNT_WOMEN)
            null -> (ageFor(HUNT_MEN) + ageFor(HUNT_WOMEN)) / 2.0
        }
        return age.roundToInt().coerceIn(18, 90)
    }

    /** Durchschnittlicher VO2max der Altersgruppe (HUNT-3) – für Vergleiche im Text. */
    fun averageVo2(age: Int, sex: Sex?): Double {
        fun at(table: List<Pair<Double, Double>>): Double {
            val a = age.toDouble().clamp(25.0, 85.0)
            for (i in 0 until table.size - 1) {
                val (a0, v0) = table[i]
                val (a1, v1) = table[i + 1]
                if (a in a0..a1) return v0 + (a - a0) / (a1 - a0) * (v1 - v0)
            }
            return table.last().second
        }
        return when (sex) {
            Sex.MALE -> at(HUNT_MEN)
            Sex.FEMALE -> at(HUNT_WOMEN)
            null -> (at(HUNT_MEN) + at(HUNT_WOMEN)) / 2
        }
    }

    fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    fun bmiLabel(bmi: Double): String = when {
        bmi < 18.5 -> "Untergewicht"
        bmi < 25.0 -> "Normalgewicht"
        bmi < 30.0 -> "Übergewicht (Präadipositas)"
        bmi < 35.0 -> "Adipositas Grad I"
        else -> "Adipositas Grad II+"
    }

    fun bodyFatLabel(pct: Double, sex: Sex?): String? {
        val t = when (sex) {
            Sex.MALE -> listOf(6.0, 14.0, 18.0, 25.0)
            Sex.FEMALE -> listOf(14.0, 21.0, 25.0, 32.0)
            null -> return null
        }
        return when {
            pct < t[0] -> "Sehr niedrig (essenzielles Fett)"
            pct < t[1] -> "Athletisch"
            pct < t[2] -> "Fit"
            pct < t[3] -> "Durchschnitt"
            else -> "Erhöht"
        }
    }
}
