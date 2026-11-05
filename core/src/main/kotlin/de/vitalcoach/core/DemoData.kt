package de.vitalcoach.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Realistische Beispieldaten (90 Tage), damit man die App ohne Uhr ausprobieren kann.
 * Die Werte sind erfunden und werden in der App deutlich als „Demo“ gekennzeichnet.
 */
object DemoData {

    val profile = UserProfile(
        birthYear = LocalDate.now().year - 34,
        sex = Sex.MALE,
        heightCm = 182.0,
        weightKg = 78.0,
        sleepGoalHours = 8.0,
        stepGoal = 8000,
        nicotine = Nicotine.NEVER,
    )

    private val HOUR_WEIGHTS = mapOf(
        7 to 0.06, 8 to 0.10, 9 to 0.04, 10 to 0.04, 11 to 0.05, 12 to 0.10, 13 to 0.06, 14 to 0.04,
        15 to 0.04, 16 to 0.05, 17 to 0.10, 18 to 0.08, 19 to 0.08, 20 to 0.08, 21 to 0.05, 22 to 0.03,
    )

    fun generate(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now(), days: Int = 365): HealthData {
        val rnd = Random(42)
        val today = now.atZone(zone).toLocalDate()
        val hr = ArrayList<HeartRateSample>(days * 300)
        val hrv = mutableListOf<HrvSample>()
        val sleep = mutableListOf<SleepSession>()
        val workouts = mutableListOf<Workout>()
        val daily = mutableListOf<DailyActivity>()
        val spo2 = mutableListOf<TimedValue>()
        val resp = mutableListOf<TimedValue>()
        val weight = mutableListOf<TimedValue>()
        val hourly = mutableListOf<HourlySteps>()
        var fatigue = 0.0

        for (k in days - 1 downTo 0) {
            val date = today.minusDays(k.toLong())
            val progress = (days - k).toDouble() / days
            // leichte Erkältung vor ca. 3 Wochen
            val sick = k in 19..21
            val rhrBase = 57.0 - 3.5 * progress + fatigue * 1.2 + (if (sick) 7.0 else 0.0) + rnd.nextDouble(-1.5, 1.5)
            val lnHrv = kotlin.math.ln(48.0 + 9.0 * progress) - fatigue * 0.07 - (if (sick) 0.35 else 0.0) + rnd.nextDouble(-0.12, 0.12)

            // ---- Nacht (endet am Morgen von `date`) ----
            val bed = date.minusDays(1).atTime(LocalTime.of(23, 0)).atZone(zone).toInstant()
                .plusSeconds(rnd.nextLong(-40 * 60L, 50 * 60L))
            val sleepMin = rnd.nextDouble(380.0, 495.0) - (if (k % 7 == 2) 50.0 else 0.0)
            val wake = bed.plusSeconds((sleepMin * 60 * 1.06).toLong())
            val segs = mutableListOf<SleepSegment>()
            var t = bed
            segs += SleepSegment(t, t.plusSeconds(12 * 60), SleepStage.LIGHT).also { t = it.end }
            while (t.isBefore(wake)) {
                val cycle = listOf(
                    SleepStage.LIGHT to rnd.nextInt(20, 35),
                    SleepStage.DEEP to (if (Duration.between(bed, t).toMinutes() < 240) rnd.nextInt(18, 32) else rnd.nextInt(0, 8)),
                    SleepStage.LIGHT to rnd.nextInt(10, 20),
                    SleepStage.REM to (if (Duration.between(bed, t).toMinutes() < 180) rnd.nextInt(8, 16) else rnd.nextInt(20, 32)),
                    SleepStage.AWAKE to rnd.nextInt(1, 6),
                )
                for ((stage, min) in cycle) {
                    if (min <= 0 || !t.isBefore(wake)) continue
                    val e = minOf(t.plusSeconds(min * 60L), wake)
                    segs += SleepSegment(t, e, stage)
                    t = e
                }
            }
            sleep += SleepSession(bed, wake, segs)
            // Nacht-Herzfrequenz alle 5 Minuten
            var ts = bed
            while (ts.isBefore(wake)) {
                val minutesIn = Duration.between(bed, ts).toMinutes().toDouble()
                val dip = if (minutesIn in 120.0..330.0) 0.0 else 4.0
                hr += HeartRateSample(ts, (rhrBase + dip + rnd.nextDouble(0.0, 4.0)).roundToInt())
                ts = ts.plusSeconds(300)
            }
            hrv += HrvSample(bed.plusSeconds(3 * 3600), exp(lnHrv))
            hrv += HrvSample(bed.plusSeconds(5 * 3600), exp(lnHrv + rnd.nextDouble(-0.08, 0.08)))
            spo2 += TimedValue(bed.plusSeconds(2 * 3600), rnd.nextDouble(94.5, 98.0))
            spo2 += TimedValue(bed.plusSeconds(5 * 3600), rnd.nextDouble(94.5, 98.0))
            resp += TimedValue(bed.plusSeconds(4 * 3600), 13.6 + (if (sick) 1.6 else 0.0) + rnd.nextDouble(-0.5, 0.5))

            // ---- Training ----
            val dow = date.dayOfWeek.value
            val plan: Triple<SportType, Double, String>? = when {
                sick -> null
                dow == 1 -> Triple(SportType.STRENGTH, 50.0, "Krafttraining")
                dow == 2 -> Triple(SportType.RUNNING, 45.0, "Lockerer Lauf")
                dow == 4 -> Triple(SportType.RUNNING, 50.0, "Intervalle")
                dow == 6 -> Triple(SportType.RUNNING, 80.0 + rnd.nextDouble(0.0, 15.0), "Langer Lauf")
                dow == 7 && rnd.nextBoolean() -> Triple(SportType.CYCLING, 70.0, "Radtour")
                else -> null
            }
            val workoutStart = date.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().plusSeconds(rnd.nextLong(-1800, 1800))
            val isPastOrDone = workoutStart.plusSeconds(3600 * 2).isBefore(now)
            var workoutKcal = 0.0
            if (plan != null && isPastOrDone) {
                val (type, dur, title) = plan
                val end = workoutStart.plusSeconds((dur * 60).toLong())
                var tt = workoutStart
                val rest = rhrBase
                while (tt.isBefore(end)) {
                    val min = Duration.between(workoutStart, tt).seconds / 60.0
                    val warm = (min / 8.0).coerceAtMost(1.0)
                    val target = when {
                        type == SportType.STRENGTH -> if ((min.toInt() / 3) % 2 == 0) 128.0 else 108.0
                        title == "Intervalle" -> if (min > 12 && min < 42 && (min.toInt() % 5) < 3) 174.0 else 138.0
                        type == SportType.CYCLING -> 132.0
                        title == "Langer Lauf" -> 143.0 + min * 0.08
                        else -> 145.0
                    }
                    val bpm = rest + (target - rest) * (0.55 + 0.45 * warm) + rnd.nextDouble(-3.0, 3.0)
                    hr += HeartRateSample(tt, bpm.roundToInt())
                    tt = tt.plusSeconds(10)
                }
                // Pulserholung nach Trainingsende (2,5 Minuten)
                val endHr = hr.last().bpm.toDouble()
                var pt = end
                while (pt.isBefore(end.plusSeconds(150))) {
                    val sec = Duration.between(end, pt).seconds.toDouble()
                    val drop = (if (type == SportType.STRENGTH) 18.0 else 27.0) * (1 - kotlin.math.exp(-sec / 55.0)) / (1 - kotlin.math.exp(-60.0 / 55.0))
                    hr += HeartRateSample(pt, (endHr - drop + rnd.nextDouble(-2.0, 2.0)).roundToInt())
                    pt = pt.plusSeconds(5)
                }
                val dist = when (type) {
                    SportType.RUNNING -> dur * 60.0 / (if (title == "Intervalle") 345.0 else 380.0) * 1000.0
                    SportType.CYCLING -> dur / 60.0 * 24_000.0
                    else -> null
                }
                workoutKcal = dur * (if (type == SportType.STRENGTH) 6.0 else 11.0)
                workouts += Workout(
                    workoutStart, end, type, title, dist, workoutKcal,
                    steps = if (type == SportType.RUNNING) (dur * rnd.nextDouble(162.0, 170.0)).toLong() else null,
                    avgPowerW = if (type == SportType.CYCLING) rnd.nextDouble(165.0, 185.0) else null,
                )
                fatigue = (fatigue * 0.6 + dur / 60.0 * (if (title == "Intervalle") 1.6 else 1.0)).coerceAtMost(3.0)
            } else {
                fatigue *= 0.55
            }

            // ---- Schritte je Stunde ----
            val dayTotal = rnd.nextInt(5500, 13500).toLong()
            val workday = date.dayOfWeek.value <= 5
            val stepsByHour = HashMap<Int, Long>()
            for ((h, w) in HOUR_WEIGHTS) stepsByHour[h] = (dayTotal * w * rnd.nextDouble(0.5, 1.5)).toLong()
            workouts.lastOrNull()?.takeIf { it.start.atZone(zone).toLocalDate() == date }?.steps?.let { ws ->
                val h = workoutStart.atZone(zone).hour
                stepsByHour[h] = (stepsByHour[h] ?: 0L) + ws
            }
            if (workday) {
                // Bürotag: lange Sitzphasen vormittags und nachmittags
                listOf(9, 10, 14, 15).forEach { stepsByHour[it] = rnd.nextLong(30, 220) }
            }

            // ---- Tages-Herzfrequenz alle 10 Minuten (bis jetzt) ----
            var dt = wake
            val dayEnd = date.atTime(LocalTime.of(23, 0)).atZone(zone).toInstant()
            while (dt.isBefore(dayEnd) && dt.isBefore(now)) {
                val inWorkout = workouts.lastOrNull()?.let { !dt.isBefore(it.start) && !dt.isAfter(it.end.plusSeconds(150)) } ?: false
                if (!inWorkout) {
                    val hour = dt.atZone(zone).hour
                    val st = stepsByHour[hour] ?: 0L
                    val base = when {
                        st >= 800 -> rnd.nextDouble(28.0, 55.0)
                        st >= 250 -> rnd.nextDouble(14.0, 28.0)
                        else -> rnd.nextDouble(6.0, 20.0)
                    }
                    val stress = if (workday && hour in listOf(10, 11, 14, 15) && rnd.nextDouble() < 0.5) rnd.nextDouble(5.0, 14.0) else 0.0
                    hr += HeartRateSample(dt, (rhrBase + base + stress).roundToInt())
                }
                dt = dt.plusSeconds(600)
            }

            var steps = 0L
            for (h in 0 until 24) {
                val hs = date.atTime(h, 0).atZone(zone).toInstant()
                if (!hs.plusSeconds(3600).isAfter(now) || hs.isBefore(now)) {
                    val v = if (hs.plusSeconds(3600).isAfter(now)) ((stepsByHour[h] ?: 0L) * Duration.between(hs, now).seconds / 3600) else (stepsByHour[h] ?: 0L)
                    if (hs.isBefore(now)) {
                        steps += v
                        if (k < 120) hourly += HourlySteps(hs, v)
                    }
                }
            }
            val active = steps * 0.035 + workoutKcal
            daily += DailyActivity(date, steps, steps * 0.75, active, 1780.0 + active)
            if (k % 5 == 0) weight += TimedValue(date.atTime(7, 30).atZone(zone).toInstant(), 78.8 - 1.2 * progress + rnd.nextDouble(-0.3, 0.3))
        }
        return HealthData(
            heartRate = hr.filter { it.time.isBefore(now) },
            hrv = hrv,
            sleep = sleep,
            workouts = workouts,
            daily = daily,
            spo2 = spo2,
            respiratoryRate = resp,
            weight = weight,
            bodyFat = listOf(TimedValue(today.minusDays(3).atTime(7, 30).atZone(zone).toInstant(), 17.5)),
            bloodPressure = listOf(
                BloodPressure(today.minusDays(20).atTime(8, 0).atZone(zone).toInstant(), 124.0, 80.0),
                BloodPressure(today.minusDays(6).atTime(8, 0).atZone(zone).toInstant(), 119.0, 77.0),
            ),
            hourlySteps = hourly,
            zone = zone,
            fetchedAt = now,
            isDemo = true,
        )
    }
}
