package de.vitalcoach.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ampel-Bewertung, die in der Oberfläche als Farbe dargestellt wird. */
enum class Tone { GOOD, OK, WARN, BAD, NEUTRAL }

enum class Confidence(val label: String) {
    HIGH("hoch"),
    MEDIUM("mittel"),
    LOW("gering – noch zu wenige Daten"),
}

/** Ein Baustein eines Scores, z. B. „HRV“ mit 72 Punkten und 40 % Gewicht. */
data class ScoreComponent(val name: String, val score: Int, val weight: Double, val detail: String)

data class Score(val value: Int, val components: List<ScoreComponent>, val confidence: Confidence) {
    val tone: Tone
        get() = when {
            value >= 67 -> Tone.GOOD
            value >= 50 -> Tone.OK
            value >= 34 -> Tone.WARN
            else -> Tone.BAD
        }
}

enum class RhrSource(val label: String) {
    SLEEP("niedrigster 30-Minuten-Schnitt im Schlaf"),
    DEVICE("Ruhepuls-Messung der Uhr"),
    DAY("niedrigster 30-Minuten-Schnitt des Tages"),
}

data class SleepNight(
    /** Datum des Aufwachens („Nacht zum …“). */
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val inBedMin: Double,
    val asleepMin: Double,
    val awakeMin: Double,
    val deepMin: Double,
    val remMin: Double,
    val lightMin: Double,
    val hasStages: Boolean,
    val efficiency: Double?,
    val napMin: Double,
    /** Mitte des Schlafs in Minuten ab 12:00 Uhr des Vortags (z. B. 03:30 Uhr = 930). */
    val midpointMin: Double,
    val bedtimeMin: Double,
    val wakeMin: Double,
    val score: Int = 0,
    val components: List<ScoreComponent> = emptyList(),
) {
    val deepPct: Double? get() = if (hasStages && asleepMin > 0) deepMin / asleepMin else null
    val remPct: Double? get() = if (hasStages && asleepMin > 0) remMin / asleepMin else null
    val lightPct: Double? get() = if (hasStages && asleepMin > 0) lightMin / asleepMin else null
    val scoreTone: Tone
        get() = when {
            score >= 80 -> Tone.GOOD
            score >= 65 -> Tone.OK
            score >= 50 -> Tone.WARN
            else -> Tone.BAD
        }
}

data class DayMetrics(
    val date: LocalDate,
    val sleep: SleepNight?,
    val restingHr: Double?,
    val restingHrSource: RhrSource?,
    val hrv: Double?,
    val respRate: Double?,
    val spo2: Double?,
    val spo2Min: Double?,
    val steps: Long?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val distanceM: Double?,
    val load: Double,
    val moderateMin: Double,
    val vigorousMin: Double,
    val workoutCount: Int,
    /** Akute Belastung (Ermüdung, 7-Tage-Schnitt, Tagesende). */
    val atl: Double,
    /** Chronische Belastung (Fitness, 42-Tage-Schnitt, Tagesende). */
    val ctl: Double,
    /** Form = Fitness – Ermüdung des Vortags. */
    val tsb: Double,
    val acwr: Double?,
    val rhrBaseline: Baseline?,
    /** Referenz für ln(RMSSD). */
    val lnHrvBaseline: Baseline?,
    val respBaseline: Baseline?,
    val recovery: Score?,
    val readiness: Score?,
    val sleepDebtH: Double?,
    val illnessFlag: Boolean,
)

data class WorkoutAnalysis(
    val workout: Workout,
    val durationMin: Double,
    val avgHr: Double?,
    val maxHr: Int?,
    val trimp: Double,
    /** true = keine Herzfrequenzdaten, Belastung wurde aus Sportart und Dauer geschätzt. */
    val estimated: Boolean,
    /** Minuten in Zone 0 (<50 % HFR) bis Zone 5 (≥90 % HFR). */
    val zoneMin: List<Double>,
    val moderateMin: Double,
    val vigorousMin: Double,
    val vo2Estimate: Double?,
    val paceSecPerKm: Double?,
    val focus: String,
)

enum class Vo2Method(val label: String, val detail: String) {
    DEVICE(
        "Messung der Uhr",
        "Von Samsung Health beim Laufen gemessener VO2max-Wert.",
    ),
    RUNNING(
        "aus deinen Läufen berechnet",
        "ACSM-Laufgleichung (VO2 = 3,5 + 0,2 × Geschwindigkeit in m/min) kombiniert mit deiner Herzfrequenzreserve " +
            "(%HFR ≈ %VO2-Reserve, Swain & Leutholtz 1997). Median der letzten Läufe. Genauigkeit ca. ±10 %.",
    ),
    RESTING_HR(
        "aus Ruhe- und Maximalpuls geschätzt",
        "Uth-Sørensen-Formel (VO2max = 15,3 × HFmax / HFRuhe, Uth et al. 2004). Grobe Schätzung (±15 %) – " +
            "genauer wird es, sobald du Läufe mit Distanz und Puls aufzeichnest.",
    ),
}

enum class HrMaxSource(val label: String) {
    USER("von dir eingetragen"),
    MEASURED("in deinen Trainings gemessen"),
    FORMULA("Tanaka-Formel (208 – 0,7 × Alter)"),
    DEFAULT("Standardwert – bitte Alter eintragen"),
}

data class HrZone(val number: Int, val name: String, val lowBpm: Int, val highBpm: Int, val purpose: String)

data class FitnessReport(
    val vo2max: Double?,
    val vo2Method: Vo2Method?,
    /** 0 = sehr schwach … 5 = hervorragend. */
    val vo2CategoryIndex: Int?,
    val vo2CategoryLabel: String?,
    val fitnessAge: Int?,
    val age: Int?,
    val vo2History: List<Pair<LocalDate, Double>>,
    val hrMax: Int,
    val hrMaxSource: HrMaxSource,
    val restingHrNow: Double?,
    val restingHrBaseline: Double?,
    /** Veränderung des Ruhepulses in Schlägen pro 30 Tage (negativ = Verbesserung). */
    val restingHrTrend30d: Double?,
    val hrvNow: Double?,
    val hrvBaseline: Double?,
    val hrvNormalLow: Double?,
    val hrvNormalHigh: Double?,
    val zones: List<HrZone>,
    val weightKg: Double?,
    val heightCm: Double?,
    val bmi: Double?,
    val bodyFatPct: Double?,
    val bodyFatLabel: String?,
    val spo2Avg7: Double?,
    val respAvg7: Double?,
    val respBaseline: Double?,
)

enum class LoadStatus(val label: String, val tone: Tone, val description: String) {
    NO_DATA("Noch zu wenige Daten", Tone.NEUTRAL, "Für eine Belastungsbewertung braucht es mindestens 3 Wochen Trainingsdaten."),
    DETRAINING("Entlastung", Tone.OK, "Deine aktuelle Belastung liegt deutlich unter deinem Gewohnten. Gut zur Erholung – dauerhaft sinkt aber die Fitness."),
    OPTIMAL("Produktiv", Tone.GOOD, "Deine Belastung steigt kontrolliert – ideal für Fortschritte."),
    MAINTAIN("Erhaltend", Tone.GOOD, "Deine Belastung entspricht deinem gewohnten Niveau."),
    HIGH("Erhöht", Tone.WARN, "Deine Belastung ist deutlich höher als gewohnt. Achte auf Erholung."),
    OVERREACHING("Überlastet", Tone.BAD, "Deine Belastung ist sprunghaft gestiegen (ACWR > 1,5). Das Verletzungsrisiko ist erhöht."),
}

data class TrainingRecommendation(
    val title: String,
    val text: String,
    val tone: Tone,
    val targetLoadLow: Int,
    val targetLoadHigh: Int,
    val zone2MinLow: Int,
    val zone2MinHigh: Int,
)

data class LoadReport(
    val atl: Double,
    val ctl: Double,
    val tsb: Double,
    val acwr: Double?,
    val monotony: Double?,
    val strain: Double?,
    val weekLoad: Double,
    val prevWeekLoad: Double,
    val status: LoadStatus,
    /** Anteil Minuten der letzten 28 Tage: locker (Zone 0–2), mittel (Zone 3), hart (Zone 4–5). */
    val easyPct: Double?,
    val moderatePct: Double?,
    val hardPct: Double?,
    val zoneMin28: List<Double>,
    /** WHO-Aktivminuten der letzten 7 Tage (mittel + 2 × intensiv). */
    val whoMinutes7: Double,
    val workouts7: Int,
    val recommendation: TrainingRecommendation?,
    val historyDays: Int,
)

data class SleepReport(
    val lastNight: SleepNight?,
    val avgAsleep7Min: Double?,
    val avgAsleep30Min: Double?,
    val debt7H: Double?,
    /** Standardabweichung der Schlafmitte in Minuten (14 Nächte) – je kleiner, desto regelmäßiger. */
    val regularitySdMin: Double?,
    val avgBedtimeMin: Double?,
    val avgWakeMin: Double?,
    val avgDeepPct: Double?,
    val avgRemPct: Double?,
    val avgEfficiency: Double?,
    val avgScore7: Double?,
    val nights: Int,
    val goalHours: Double,
)

data class ActivityReport(
    val stepsToday: Long?,
    val stepsYesterday: Long?,
    val steps7Avg: Double?,
    val stepGoal: Int,
    val daysGoalReached7: Int,
    val activeKcalToday: Double?,
    val activeKcal7Avg: Double?,
    val totalKcal7Avg: Double?,
    val whoMinutes7: Double,
)

data class HealthWarning(val title: String, val text: String, val tone: Tone)

data class DataQuality(
    val daysWithData: Int,
    val hasHeartRate: Boolean,
    val hasHrv: Boolean,
    val hasSleep: Boolean,
    val hasSleepStages: Boolean,
    val hasWorkouts: Boolean,
    val hasSteps: Boolean,
    val hasSpo2: Boolean,
    val hasRespiratoryRate: Boolean,
    val hasVo2Device: Boolean,
    val baselineDays: Int,
) {
    val calibrating: Boolean get() = baselineDays < 7
}

data class AnalysisResult(
    val today: LocalDate,
    val profile: UserProfile,
    val days: List<DayMetrics>,
    val workouts: List<WorkoutAnalysis>,
    val fitness: FitnessReport,
    val load: LoadReport,
    val sleep: SleepReport,
    val activity: ActivityReport,
    val warnings: List<HealthWarning>,
    val quality: DataQuality,
    val isDemo: Boolean,
    val generatedAt: Instant,
    val zone: ZoneId,
) {
    val todayMetrics: DayMetrics? get() = days.lastOrNull()
}
