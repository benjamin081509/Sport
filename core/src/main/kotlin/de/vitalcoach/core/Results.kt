package de.vitalcoach.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ampel-Bewertung, die in der Oberfläche als Farbe dargestellt wird. */
enum class Tone { GOOD, OK, WARN, BAD, NEUTRAL }

/** Herkunft eines Wertes – wird in der App bei jeder Kennzahl angezeigt. */
enum class Provenance(val label: String, val description: String) {
    MEASURED("Gemessen", "Originaldaten deiner Uhr (Samsung Health / Health Connect)"),
    CALCULATED("Berechnet", "Aus mehreren Messwerten mathematisch ermittelt"),
    ESTIMATED("Geschätzt", "Schätzung oder Prognose – mit Unsicherheit behaftet"),
}

enum class Confidence(val label: String) {
    HIGH("hoch"),
    MEDIUM("mittel"),
    LOW("gering – noch zu wenige Daten"),
}

/** Ein Baustein eines Scores, z. B. „HRV“ mit 72 Punkten und 40 % Gewicht. */
data class ScoreComponent(val name: String, val score: Int, val weight: Double, val detail: String)

data class Score(val value: Int, val components: List<ScoreComponent>, val confidence: Confidence) {
    val tone: Tone get() = scoreToneOf(value)
}

/** Einheitliche (kritische) Farbstufen für alle 0–100-Werte: ≥ 75 gut · ≥ 60 solide · ≥ 45 mäßig · darunter schlecht. */
fun scoreToneOf(value: Int): Tone = when {
    value >= 75 -> Tone.GOOD
    value >= 60 -> Tone.OK
    value >= 45 -> Tone.WARN
    else -> Tone.BAD
}

enum class RhrSource(val label: String) {
    SLEEP("niedrigster 30-Minuten-Schnitt im Schlaf"),
    DEVICE("Ruhepuls-Messung der Uhr"),
    DAY("niedrigster 30-Minuten-Schnitt des Tages"),
    HISTORY("gespeicherter Wert aus früherer Auswertung"),
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
    /** Schlafphasen im Zeitverlauf (für das Hypnogramm). */
    val stages: List<SleepSegment> = emptyList(),
) {
    val deepPct: Double? get() = if (hasStages && asleepMin > 0) deepMin / asleepMin else null
    val remPct: Double? get() = if (hasStages && asleepMin > 0) remMin / asleepMin else null
    val lightPct: Double? get() = if (hasStages && asleepMin > 0) lightMin / asleepMin else null
    val scoreTone: Tone
        get() = when {
            score >= 85 -> Tone.GOOD
            score >= 70 -> Tone.OK
            score >= 55 -> Tone.WARN
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
    /** true, wenn die Tageslast aus echten Pulsdaten berechnet wurde (nicht geschätzt). */
    val loadFromHr: Boolean = false,
    /** Zeitgewichteter Tagesdurchschnitt der Herzfrequenz. */
    val avgHr: Double? = null,
    val sleepAvgHr: Double? = null,
    /** Nächtliche Pulsabsenkung gegenüber dem Vorabend in Prozent. */
    val hrDipPct: Double? = null,
    /** Lage des niedrigsten Pulses in der Nacht (0 = Einschlafen, 1 = Aufwachen). */
    val lowestHrPos: Double? = null,
    val nightRecovery: Score? = null,
    val stressIndex: Double? = null,
    val activeHours: Int? = null,
    val inactiveHours: Int? = null,
    /** Belastung außerhalb von Trainings (TRIMP aus Alltagspuls ≥ 35 % HFR). */
    val everydayLoad: Double = 0.0,
    /** Regenerationsstand am Morgen (0–100 %) aus dem Erholungszeit-Modell. */
    val regenProgress: Int? = null,
    val lateWorkout: Boolean = false,
    /** Empfohlene Schlafdauer für die kommende Nacht (Minuten). */
    val sleepNeedMin: Double? = null,
    val weight: Double? = null,
    val acuteLoad: Double = 0.0,
    val chronicLoad: Double = 0.0,
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
    /** Geschätzte Erholungszeit in Stunden (Regenerationsmodell). */
    val recoveryH: Double? = null,
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

data class HrZone(
    val number: Int,
    val name: String,
    val lowBpm: Int,
    val highBpm: Int,
    val purpose: String,
    /** Lauftempo am unteren Zonenrand (langsamer), Sekunden pro km. */
    val paceSlowSecKm: Double? = null,
    /** Lauftempo am oberen Zonenrand (schneller), Sekunden pro km. */
    val paceFastSecKm: Double? = null,
) {
    /** z. B. „7:01–6:01 /km“ oder null, wenn keine Pace berechnet werden kann. */
    val paceText: String?
        get() {
            val slow = paceSlowSecKm ?: return null
            val fast = paceFastSecKm ?: return null
            fun p(s: Double) = if (s >= 720) "Gehen" else Fmt.pace(s).removeSuffix(" /km")
            return if (slow >= 720 && fast >= 720) "Gehtempo" else "${p(slow)}–${p(fast)} /km"
        }
}

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
    /** Woher die Pace-Angaben der Zonen stammen (null = keine Pace verfügbar). */
    val zonePaceSource: String? = null,
    /** Laufgeschwindigkeit (m/min) pro Anteil Herzfrequenzreserve – persönlicher Puls-Tempo-Zusammenhang. */
    val speedPerHrr: Double? = null,
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
    DETRAINING("Weniger als gewohnt", Tone.OK, "Deine aktuelle Belastung liegt deutlich unter deinem Gewohnten. Gut zur Erholung – dauerhaft sinkt aber die Fitness."),
    OPTIMAL("Aufbau", Tone.GOOD, "Deine Belastung steigt in einem sicheren Maß – ideal für Fortschritte."),
    MAINTAIN("Wie gewohnt", Tone.GOOD, "Deine Belastung entspricht deinem gewohnten Niveau – sicher, aber ohne Steigerung."),
    HIGH("Starke Steigerung", Tone.WARN, "Deine Belastung ist deutlich höher als gewohnt. Achte auf Erholung."),
    OVERREACHING("Belastungssprung", Tone.BAD, "Deine Belastung ist sprunghaft gestiegen (ACWR > 1,5). Das Verletzungsrisiko ist erhöht."),
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
    /** Kurzfristige (akute) Belastung, gewichtetes Mittel, umgerechnet auf TRIMP pro Woche. */
    val acuteWeek: Double? = null,
    /** Gewohnte (chronische) Belastung, gewichtetes Mittel, umgerechnet auf TRIMP pro Woche. */
    val chronicWeek: Double? = null,
    /** Tage der letzten 7 mit nennenswertem Training (≥ 15 TRIMP). */
    val trainingDays7: Int = 0,
    /** Ø TRIMP pro Woche in den letzten 28 Tagen. */
    val weekAvg28: Double = 0.0,
    /** Ø TRIMP pro Woche in den Tagen 29–90 davor (null, wenn zu wenig Verlauf). */
    val weekAvgBefore: Double? = null,
    /** Tage seit dem letzten Training (≥ 10 min). */
    val daysSinceWorkout: Int? = null,
    /** Fitness-Wert (CTL) vor 28 Tagen – für den Trend. */
    val ctl28Ago: Double? = null,
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
    val debt14H: Double? = null,
    /** Sleep Regularity Index (Phillips et al. 2017), 14 Tage, 0–100. */
    val sri: Double? = null,
    /** Geschätzter persönlicher Schlafbedarf (Stunden). */
    val personalNeedH: Double? = null,
    val personalNeedNights: Int = 0,
    val needTonightMin: Double? = null,
    val needReasons: List<String> = emptyList(),
    val recommendedBedtimeMin: Double? = null,
    val avgInBed7Min: Double? = null,
    val nightRecovery: Score? = null,
    val nightRestingHr: Double? = null,
    val nightHrv: Double? = null,
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
    val activeHoursToday: Int? = null,
    val inactiveHoursToday: Int? = null,
    val inactiveHours7Avg: Double? = null,
    val activeHours7Avg: Double? = null,
    /** Durchschnittliche Schritte je Stunde des Tages (14 Tage). */
    val stepsByHour: List<Double> = emptyList(),
    val movementIndex: Score? = null,
    val regularity: Int? = null,
    val stepsCv14: Double? = null,
    val thisWeek: WeekBalance? = null,
    val lastWeek: WeekBalance? = null,
    val everydayLoadToday: Double = 0.0,
    val everydayLoad7Avg: Double? = null,
    val everydayShare7: Double? = null,
    val activeKcalShare7: Double? = null,
    val hardSessionGapAvgH: Double? = null,
    val shortGaps28: Int = 0,
    val runCadence: Double? = null,
    val hasHourlySteps: Boolean = false,
)

data class WeekBalance(val steps: Long, val activeMinutes: Double, val load: Double, val activeKcal: Double, val workouts: Int)

data class RecoveryTimeInfo(val workout: WorkoutAnalysis, val hours: Double, val remainingH: Double, val readyAt: Instant)

data class RegenerationReport(
    val regenerationIndex: Int?,
    val timeProgress: Int,
    val remainingHours: Double,
    val readyAt: Instant?,
    val binding: RecoveryTimeInfo?,
    val recent: List<RecoveryTimeInfo>,
    val fatigueIndex: Int?,
    val fatigueLabel: String,
    val fatigueTone: Tone,
    val strainRel: Double?,
    val recoveryRel: Double?,
    val balance: Double?,
    val balanceLabel: String,
    val balanceTone: Tone,
    val load7: Double,
    val load28: Double,
    val load90: Double,
    val overloadSignals: List<String>,
    val overloadLevel: Int,
    val overloadLabel: String,
    val overloadTone: Tone,
    val hrvTrendPct: Double?,
    val rhrDeviation: Double?,
    val rhrDeviationZ: Double?,
    /** Alle 8 geprüften Warnsignale (Name, aktiv?) für die Ampel-Anzeige. */
    val overloadChecks: List<Pair<String, Boolean>> = emptyList(),
)

data class HrrResult(val date: LocalDate, val title: String, val peak: Int, val drop60: Int, val afterExercise: Boolean)

data class HeartReport(
    val avgHrToday: Double?,
    val avgHr7: Double?,
    val avgHr30: Double?,
    val sleepAvgHr: Double?,
    val dipPct: Double?,
    val dipAvg7: Double?,
    val lowestHrPos: Double?,
    val nightCurve: List<Pair<Instant, Double>>,
    val rhrSd14: Double?,
    val hrvCv7: Double?,
    val hrv7: Double?,
    val hrv30: Double?,
    val hrv90: Double?,
    val hrv180: Double?,
    val hrvWeekly: List<Pair<LocalDate, Double>>,
    val rhr7: Double?,
    val rhr30: Double?,
    val rhr90: Double?,
    val rhr365: Double?,
    val rhrWeekly: List<Pair<LocalDate, Double>>,
    val hrr: List<HrrResult>,
    val hrrTrend: Double?,
    val trainingHighHrPct28: Double?,
)

data class StressReport(
    val today: Double?,
    val avg7: Double?,
    val avg30: Double?,
    val label: String,
    val tone: Tone,
    val byHour: List<Double?>,
    val restPct: Double?,
    val highPct: Double?,
    val nightStress: Double?,
    val daily: List<Pair<LocalDate, Double?>>,
    val reliable: Boolean,
)

data class PerformanceReport(
    val efRecent: Double?,
    val efPrevious: Double?,
    val efChangePct: Double?,
    val paceAtZ2SecKm: Double?,
    val z2Hr: Int?,
    val runPaces: List<Pair<LocalDate, Double>>,
    val efSeries: List<Pair<LocalDate, Double>>,
    val lthr: Int?,
    val lthrDate: LocalDate?,
    val hardMin28: Double,
    val peakHr90: Int?,
    val hardSessions28: Int,
    val cyclingPowerAvg: Double?,
    val cyclingWkg: Double?,
    val cyclingSpeedKmh: Double?,
    val load7: Double,
    val load28: Double,
    val load90: Double,
    val vo2Change90: Double?,
    val runCount90: Int,
)

data class HeartHealthComponent(
    val name: String,
    val score: Int?,
    val value: String,
    val provenance: Provenance,
    val hint: String,
    val le8: Boolean,
)

data class HeartHealthReport(
    val score: Int?,
    val label: String,
    val tone: Tone,
    val components: List<HeartHealthComponent>,
    val covered: Int,
)

enum class ForecastScenario(val label: String, val loadFactor: Double) {
    REST("Ruhetag", 0.0),
    EASY("Lockeres Training", 0.5),
    NORMAL("Normales Training", 1.0),
    HARD("Hartes Training", 1.8),
}

data class ForecastPoint(val date: LocalDate, val expected: Int, val low: Int, val high: Int)

data class ForecastReport(
    val scenarios: Map<ForecastScenario, List<ForecastPoint>>,
    val personalModel: Boolean,
    val sigma: Double,
    val typicalLoad: Double,
)

enum class BaselineStatus(val label: String) {
    NORMAL("im Normalbereich"),
    SLIGHT_HIGH("leicht erhöht"),
    SLIGHT_LOW("leicht erniedrigt"),
    HIGH("ungewöhnlich hoch"),
    LOW("ungewöhnlich niedrig"),
    NO_DATA("zu wenig Daten"),
}

data class BaselineItem(
    val name: String,
    val unit: String,
    val decimals: Int,
    val current: Double?,
    val mean30: Double?,
    val sd30: Double?,
    val mean90: Double?,
    val z: Double?,
    val status: BaselineStatus,
    val tone: Tone,
    val provenance: Provenance,
)

data class CorrelationInsight(
    val title: String,
    val text: String,
    val r: Double?,
    val n: Int,
    val pValue: Double?,
    val strength: String,
    val significant: Boolean,
    /** Datenpunkte (x, y) für das Streudiagramm. */
    val points: List<Pair<Double, Double>> = emptyList(),
    val xLabel: String = "",
    val yLabel: String = "",
)

data class Briefing(
    val headline: String,
    val changes: List<String>,
    val explanations: List<String>,
    val todo: List<String>,
    val tone: Tone,
)

data class TrendItem(
    val name: String,
    val unit: String,
    val decimals: Int,
    val series: List<Pair<LocalDate, Double>>,
    val last30: Double?,
    val prev30: Double?,
    val last90: Double?,
    val prev90: Double?,
    val first365: Double?,
    val higherIsBetter: Boolean?,
    val summary: String,
    val tone: Tone,
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
    val regeneration: RegenerationReport,
    val heart: HeartReport,
    val stress: StressReport,
    val performance: PerformanceReport,
    val heartHealth: HeartHealthReport = HeartHealthReport(null, "", Tone.NEUTRAL, emptyList(), 0),
    val forecast: ForecastReport? = null,
    val baselines: List<BaselineItem> = emptyList(),
    val correlations: List<CorrelationInsight> = emptyList(),
    val briefing: Briefing = Briefing("", emptyList(), emptyList(), emptyList(), Tone.NEUTRAL),
    val trends: List<TrendItem> = emptyList(),
) {
    val todayMetrics: DayMetrics? get() = days.lastOrNull()
}
