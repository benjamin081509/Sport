package de.vitalcoach.app

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.vitalcoach.core.BloodPressure
import de.vitalcoach.core.DailyActivity
import de.vitalcoach.core.HourlySteps
import de.vitalcoach.core.HealthData
import de.vitalcoach.core.HeartRateSample
import de.vitalcoach.core.HrvSample
import de.vitalcoach.core.SleepSegment
import de.vitalcoach.core.SleepSession
import de.vitalcoach.core.SleepStage
import de.vitalcoach.core.SportType
import de.vitalcoach.core.TimedValue
import de.vitalcoach.core.Workout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Liest alle relevanten Daten aus Health Connect, wohin Samsung Health die Daten der Galaxy Watch schreibt. */
class HealthConnectRepository(private val context: Context) {

    enum class Availability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
        else -> Availability.NOT_INSTALLED
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
    )

    /** Wichtigste Berechtigungen – ohne diese ist keine sinnvolle Auswertung möglich. */
    val corePermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    suspend fun permissionsToRequest(): Set<String> {
        val all = readPermissions.toMutableSet()
        if (historyFeatureAvailable()) all += HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        return all
    }

    private suspend fun historyFeatureAvailable(): Boolean = try {
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (t: Throwable) {
        false
    }

    suspend fun grantedPermissions(): Set<String> = try {
        client.permissionController.getGrantedPermissions()
    } catch (t: Throwable) {
        emptySet()
    }

    /**
     * Liest Pulsrohdaten der letzten [hrDays] Tage und alle übrigen Daten für [days] Tage.
     * Fehlende Berechtigungen führen nur zu leeren Listen, nicht zu Fehlern.
     */
    suspend fun readAll(days: Long = 365, hrDays: Long = 120, zone: ZoneId = ZoneId.systemDefault(), onProgress: (String) -> Unit = {}): HealthData {
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val from = today.minusDays(days - 1).minusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(from, now)
        val hrFrom = today.minusDays(hrDays - 1).minusDays(1).atStartOfDay(zone).toInstant()
        val hrRange = TimeRangeFilter.between(hrFrom, now)

        onProgress("Herzfrequenz")
        val hr = read(HeartRateRecord::class, hrRange).flatMap { r ->
            r.samples.map { HeartRateSample(it.time, it.beatsPerMinute.toInt()) }
        }
        val hrv = read(HeartRateVariabilityRmssdRecord::class, range).map { HrvSample(it.time, it.heartRateVariabilityMillis) }
        val resting = read(RestingHeartRateRecord::class, range).map { TimedValue(it.time, it.beatsPerMinute.toDouble()) }
        onProgress("Schlaf & HRV")
        val sleep = read(SleepSessionRecord::class, range).map { s ->
            SleepSession(s.startTime, s.endTime, s.stages.map { SleepSegment(it.startTime, it.endTime, mapStage(it.stage)) })
        }
        onProgress("Trainings")
        val sessions = read(ExerciseSessionRecord::class, range)
        val hrCutoff = hrFrom
        val workouts = sessions.map { s ->
            val type = mapExercise(s.exerciseType)
            // Detailwerte (Distanz, Kalorien, Schritte, Leistung) nur für die letzten Monate abfragen
            if (s.startTime.isBefore(hrCutoff)) {
                Workout(s.startTime, s.endTime, type, s.title)
            } else {
                val agg = sessionAggregates(s.startTime, s.endTime, type)
                Workout(s.startTime, s.endTime, type, s.title, agg.distance, agg.kcal, agg.steps, agg.power)
            }
        }
        onProgress("Vitalwerte")
        val spo2 = read(OxygenSaturationRecord::class, range).map { TimedValue(it.time, it.percentage.value) }
        val resp = read(RespiratoryRateRecord::class, range).map { TimedValue(it.time, it.rate) }
        val vo2 = read(Vo2MaxRecord::class, range).map { TimedValue(it.time, it.vo2MillilitersPerMinuteKilogram) }
        val longRange = TimeRangeFilter.between(now.minusSeconds(365L * 24 * 3600), now)
        val weight = read(WeightRecord::class, longRange).map { TimedValue(it.time, it.weight.inKilograms) }
        val height = read(HeightRecord::class, longRange).map { TimedValue(it.time, it.height.inMeters * 100.0) }
        val bodyFat = read(BodyFatRecord::class, longRange).map { TimedValue(it.time, it.percentage.value) }
        val bp = read(BloodPressureRecord::class, longRange).map {
            BloodPressure(it.time, it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury)
        }
        onProgress("Schritte")
        val daily = dailyActivity(today.minusDays(days - 1), today)
        val hourly = hourlySteps(hrFrom, now)

        return HealthData(
            heartRate = hr,
            hrv = hrv,
            restingHr = resting,
            sleep = sleep,
            workouts = workouts,
            daily = daily,
            spo2 = spo2,
            respiratoryRate = resp,
            vo2max = vo2,
            weight = weight,
            height = height,
            bodyFat = bodyFat,
            bloodPressure = bp,
            hourlySteps = hourly,
            zone = zone,
            fetchedAt = now,
            isDemo = false,
        )
    }

    private suspend fun <T : Record> read(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        try {
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageSize = 5000, pageToken = token),
                )
                out += response.records
                token = response.pageToken
            } while (!token.isNullOrEmpty())
        } catch (t: Throwable) {
            Log.w(TAG, "Lesen von ${type.simpleName} fehlgeschlagen: ${t.message}")
        }
        return out
    }

    private suspend fun dailyActivity(start: LocalDate, end: LocalDate): List<DailyActivity> {
        val range = TimeRangeFilter.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay())
        val steps = HashMap<LocalDate, Long>()
        val dist = HashMap<LocalDate, Double>()
        val active = HashMap<LocalDate, Double>()
        val total = HashMap<LocalDate, Double>()
        try {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = range, timeRangeSlicer = Period.ofDays(1)))
                .forEach { g -> g.result[StepsRecord.COUNT_TOTAL]?.let { steps[g.startTime.toLocalDate()] = it } }
        } catch (t: Throwable) {
            Log.w(TAG, "Schritte: ${t.message}")
        }
        try {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics = setOf(DistanceRecord.DISTANCE_TOTAL), timeRangeFilter = range, timeRangeSlicer = Period.ofDays(1)))
                .forEach { g -> g.result[DistanceRecord.DISTANCE_TOTAL]?.let { dist[g.startTime.toLocalDate()] = it.inMeters } }
        } catch (t: Throwable) {
            Log.w(TAG, "Distanz: ${t.message}")
        }
        try {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), timeRangeFilter = range, timeRangeSlicer = Period.ofDays(1)),
            ).forEach { g -> g.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { active[g.startTime.toLocalDate()] = it.inKilocalories } }
        } catch (t: Throwable) {
            Log.w(TAG, "Aktive Kalorien: ${t.message}")
        }
        try {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), timeRangeFilter = range, timeRangeSlicer = Period.ofDays(1)),
            ).forEach { g -> g.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { total[g.startTime.toLocalDate()] = it.inKilocalories } }
        } catch (t: Throwable) {
            Log.w(TAG, "Gesamtkalorien: ${t.message}")
        }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { d ->
            DailyActivity(d, steps[d], dist[d], active[d], total[d])
        }.toList()
    }

    /** Schritte pro Stunde, in 30-Tage-Abschnitten abgefragt. */
    private suspend fun hourlySteps(from: Instant, to: Instant): List<HourlySteps> {
        val out = ArrayList<HourlySteps>()
        var chunkStart = from
        while (chunkStart.isBefore(to)) {
            val chunkEnd = minOf(chunkStart.plus(Duration.ofDays(30)), to)
            try {
                client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(chunkStart, chunkEnd),
                        timeRangeSlicer = Duration.ofHours(1),
                    ),
                ).forEach { g -> out += HourlySteps(g.startTime, g.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
            } catch (t: Throwable) {
                Log.w(TAG, "Stündliche Schritte: ${t.message}")
            }
            chunkStart = chunkEnd
        }
        return out
    }

    private class SessionAgg(val distance: Double?, val kcal: Double?, val steps: Long?, val power: Double?)

    private suspend fun sessionAggregates(start: Instant, end: Instant, type: SportType): SessionAgg {
        val range = TimeRangeFilter.between(start, end)
        val distance = if (type in DISTANCE_SPORTS) {
            try {
                client.aggregate(AggregateRequest(metrics = setOf(DistanceRecord.DISTANCE_TOTAL), timeRangeFilter = range))[DistanceRecord.DISTANCE_TOTAL]?.inMeters
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
        val kcal = try {
            client.aggregate(AggregateRequest(metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), timeRangeFilter = range))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        } catch (t: Throwable) {
            null
        }
        val steps = if (type == SportType.RUNNING || type == SportType.WALKING || type == SportType.HIKING) {
            try {
                client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = range))[StepsRecord.COUNT_TOTAL]
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
        val power = if (type == SportType.CYCLING) {
            try {
                client.aggregate(AggregateRequest(metrics = setOf(PowerRecord.POWER_AVG), timeRangeFilter = range))[PowerRecord.POWER_AVG]?.inWatts
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
        return SessionAgg(distance?.takeIf { it > 0 }, kcal, steps?.takeIf { it > 0 }, power?.takeIf { it > 0 })
    }

    private fun mapStage(stage: Int): SleepStage = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.SLEEPING
        else -> SleepStage.UNKNOWN
    }

    private fun mapExercise(t: Int): SportType = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> SportType.RUNNING
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> SportType.WALKING
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> SportType.HIKING
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> SportType.CYCLING
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> SportType.SWIMMING
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> SportType.ROWING
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> SportType.ELLIPTICAL
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> SportType.STRENGTH
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> SportType.HIIT
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> SportType.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER,
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
        ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL,
        ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL,
        ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY,
        ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> SportType.TEAM_SPORT
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON,
        ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
        ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> SportType.RACKET
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> SportType.DANCE
        else -> SportType.OTHER
    }

    companion object {
        private const val TAG = "VitalCoach"
        private val DISTANCE_SPORTS = setOf(
            SportType.RUNNING, SportType.WALKING, SportType.HIKING, SportType.CYCLING, SportType.SWIMMING, SportType.ROWING,
        )
    }
}
