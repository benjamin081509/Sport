package de.vitalcoach.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Biologisches Geschlecht – wird nur für wissenschaftliche Formeln (TRIMP, VO2max-Normen) genutzt. */
enum class Sex { MALE, FEMALE }

/** Persönliche Angaben, die der Nutzer in der App einträgt. */
data class UserProfile(
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    /** Falls bekannt (z. B. aus Laktat-/Leistungstest): überschreibt die berechnete maximale Herzfrequenz. */
    val maxHrOverride: Int? = null,
    val sleepGoalHours: Double = 8.0,
    val stepGoal: Int = 8000,
) {
    fun age(today: LocalDate): Int? = birthYear?.let { (today.year - it).coerceIn(10, 100) }
}

data class HeartRateSample(val time: Instant, val bpm: Int)

/** Herzfrequenzvariabilität als RMSSD in Millisekunden. */
data class HrvSample(val time: Instant, val rmssdMs: Double)

data class TimedValue(val time: Instant, val value: Double)

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, UNKNOWN }

data class SleepSegment(val start: Instant, val end: Instant, val stage: SleepStage)

data class SleepSession(
    val start: Instant,
    val end: Instant,
    val segments: List<SleepSegment> = emptyList(),
)

/**
 * Sportarten. [typicalHrr] ist die typische Intensität (Anteil der Herzfrequenzreserve), die nur dann
 * verwendet wird, wenn für ein Training keine Herzfrequenzdaten vorliegen.
 */
enum class SportType(val label: String, val emoji: String, val typicalHrr: Double, val endurance: Boolean) {
    RUNNING("Laufen", "🏃", 0.70, true),
    WALKING("Gehen", "🚶", 0.40, false),
    HIKING("Wandern", "🥾", 0.50, true),
    CYCLING("Radfahren", "🚴", 0.62, true),
    SWIMMING("Schwimmen", "🏊", 0.65, true),
    ROWING("Rudern", "🚣", 0.65, true),
    ELLIPTICAL("Crosstrainer", "⛷️", 0.60, true),
    STRENGTH("Krafttraining", "🏋️", 0.50, false),
    HIIT("HIIT / Intervalle", "🔥", 0.75, false),
    YOGA("Yoga / Mobility", "🧘", 0.30, false),
    TEAM_SPORT("Teamsport", "⚽", 0.65, false),
    RACKET("Rückschlagsport", "🎾", 0.60, false),
    DANCE("Tanzen", "💃", 0.50, false),
    OTHER("Training", "💪", 0.50, false),
}

data class Workout(
    val start: Instant,
    val end: Instant,
    val type: SportType,
    val title: String? = null,
    val distanceMeters: Double? = null,
    val activeKcal: Double? = null,
) {
    val durationMin: Double get() = Duration.between(start, end).seconds / 60.0
}

data class DailyActivity(
    val date: LocalDate,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
)

/** Alle Rohdaten, die aus Health Connect (bzw. den Demo-Daten) kommen. */
data class HealthData(
    val heartRate: List<HeartRateSample> = emptyList(),
    val hrv: List<HrvSample> = emptyList(),
    val restingHr: List<TimedValue> = emptyList(),
    val sleep: List<SleepSession> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val daily: List<DailyActivity> = emptyList(),
    val spo2: List<TimedValue> = emptyList(),
    val respiratoryRate: List<TimedValue> = emptyList(),
    val vo2max: List<TimedValue> = emptyList(),
    val weight: List<TimedValue> = emptyList(),
    val height: List<TimedValue> = emptyList(),
    val bodyFat: List<TimedValue> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val fetchedAt: Instant = Instant.now(),
    val isDemo: Boolean = false,
)
