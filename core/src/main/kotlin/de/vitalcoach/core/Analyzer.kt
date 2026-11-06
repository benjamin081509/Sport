package de.vitalcoach.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Herzstück der App: berechnet aus den Rohdaten alle Tageswerte, persönlichen Referenzwerte,
 * Scores und Berichte.
 *
 * Persönlicher Erholungswert (0–100): Abweichung von HRV, Ruhepuls und Atemfrequenz zu deinem persönlichen
 *   30-Tage-Schnitt (z-Werte, HRV logarithmiert – Plews et al. 2012, Buchheit 2014), Schlaf-Score und
 *   Rest-Belastung aus dem Regenerationsmodell.
 * Trainingsbereitschaft (0–100): Erholung + Belastungssteigerung (ACWR, Gabbett 2016; EWMA nach
 *   Williams et al. 2017) + Form (TSB, Banister-Modell) + Schlafschuld.
 * Belastung: Banister-TRIMP, Fitness (CTL, 42 Tage) und Ermüdung (ATL, 7 Tage) als exponentiell
 *   gewichtete Mittel, Monotonie & Strain nach Foster (1998).
 */
object Analyzer {

    private const val MAX_DAYS = 370L
    internal val K_ATL = 1.0 - exp(-1.0 / 7.0)
    internal val K_CTL = 1.0 - exp(-1.0 / 42.0)
    internal const val L_ACUTE = 2.0 / (7.0 + 1.0)
    internal const val L_CHRONIC = 2.0 / (28.0 + 1.0)

    fun analyze(
        data: HealthData,
        profile: UserProfile,
        today: LocalDate = LocalDate.now(data.zone),
    ): AnalysisResult {
        val zone = data.zone
        val now = data.fetchedAt
        val hr = data.heartRate.sortedBy { it.time }
        val hrv = data.hrv.sortedBy { it.time }
        val resp = data.respiratoryRate.sortedBy { it.time }
        val spo2 = data.spo2.sortedBy { it.time }
        val rhrRecords = data.restingHr.sortedBy { it.time }
        val snapByDate = data.history.associateBy { it.date }
        val hourlyMap = HashMap<Long, Long>()
        data.hourlySteps.forEach { hourlyMap[it.start.epochSecond / 3600] = it.steps }
        val hourlyDates = data.hourlySteps.map { it.start.atZone(zone).toLocalDate() }.toSet()
        val age = profile.age(today)
        val sex = profile.sex

        // ---------- Zeitraum ----------
        val earliestData = listOfNotNull(
            hr.firstOrNull()?.time?.atZone(zone)?.toLocalDate(),
            data.sleep.minOfOrNull { it.end }?.atZone(zone)?.toLocalDate(),
            data.workouts.minOfOrNull { it.start }?.atZone(zone)?.toLocalDate(),
            data.daily.filter { (it.steps ?: 0) > 0 }.minOfOrNull { it.date },
            data.history.minOfOrNull { it.date },
        ).minOrNull() ?: today
        val start = maxOf(earliestData, today.minusDays(MAX_DAYS - 1)).let { if (it.isAfter(today)) today else it }
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val n = dates.size

        // ---------- Schlaf ----------
        val goal = profile.sleepGoalHours
        val rawNights = SleepMath.nights(data.sleep, zone).filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        val midpoints = mutableListOf<Double>()
        val nightByDate = HashMap<LocalDate, SleepNight>()
        for (night in rawNights) {
            val scored = SleepMath.score(night, goal, midpoints.toList())
            midpoints += night.midpointMin
            nightByDate[night.date] = scored
        }

        // ---------- Tageswerte: Ruhepuls, HRV, Atmung, SpO2 ----------
        val rhrByDate = HashMap<LocalDate, Pair<Double, RhrSource>>()
        val hrvByDate = HashMap<LocalDate, Double>()
        val respByDate = HashMap<LocalDate, Double>()
        val spo2ByDate = HashMap<LocalDate, Pair<Double, Double>>()
        for (d in dates) {
            val (ds, de) = dayBounds(d, zone)
            val night = nightByDate[d]
            val nightHr = night?.let { hr.sliceByTime(it.start, it.end) { s -> s.time } }.orEmpty()
            val fromSleep = if (nightHr.size >= 3) HeartMath.restingFromSamples(nightHr) else null
            val device = rhrRecords.sliceByTime(ds, de) { it.time }.map { it.value }.filter { it in 30.0..120.0 }
            val rhr = when {
                fromSleep != null -> fromSleep to RhrSource.SLEEP
                device.isNotEmpty() -> device.average() to RhrSource.DEVICE
                else -> HeartMath.restingFromSamples(hr.sliceByTime(ds, de) { it.time })?.let { it to RhrSource.DAY }
                    ?: snapByDate[d]?.restingHr?.let { it to RhrSource.HISTORY }
            }
            if (rhr != null) rhrByDate[d] = rhr

            val hrvVals = (
                night?.let { hrv.sliceByTime(it.start, it.end) { s -> s.time } }.orEmpty()
                    .ifEmpty { hrv.sliceByTime(ds, ds.plusSeconds(12 * 3600)) { it.time } }
                    .ifEmpty { hrv.sliceByTime(ds, de) { it.time } }
                ).map { it.rmssdMs }.filter { it in 3.0..300.0 }
            if (hrvVals.isNotEmpty()) hrvByDate[d] = hrvVals.average() else snapByDate[d]?.hrv?.let { hrvByDate[d] = it }

            val respVals = windowValues(resp, night, ds, de).filter { it in 5.0..40.0 }
            if (respVals.isNotEmpty()) respByDate[d] = respVals.average()

            val spoVals = windowValues(spo2, night, ds, de).filter { it in 70.0..100.0 }
            if (spoVals.isNotEmpty()) spo2ByDate[d] = spoVals.average() to spoVals.min()
        }

        // ---------- Maximalpuls ----------
        val workoutsSorted = data.workouts.filter { it.end.isAfter(it.start) }.sortedBy { it.start }
        val formula = age?.let { HeartMath.tanakaMaxHr(it) }
        val observed = workoutsSorted
            .filter { it.type.endurance || it.type == SportType.HIIT || it.type == SportType.TEAM_SPORT }
            .mapNotNull { HeartMath.robustMax(hr.sliceByTime(it.start, it.end) { s -> s.time }) }
            .maxOrNull()
        val (hrMax, hrMaxSource) = when {
            profile.maxHrOverride != null -> profile.maxHrOverride.coerceIn(120, 230) to HrMaxSource.USER
            observed != null && (formula == null || observed > formula) && observed in 140..220 -> observed to HrMaxSource.MEASURED
            formula != null -> formula to HrMaxSource.FORMULA
            else -> 190 to HrMaxSource.DEFAULT
        }

        val recentRhr = dates.takeLast(30).mapNotNull { rhrByDate[it]?.first }
        val restRef = Stats.median(recentRhr) ?: 60.0

        // ---------- Trainings ----------
        val hrrByWorkout = HashMap<Instant, Triple<Int, Int, Boolean>>()
        val best20ByWorkout = HashMap<Instant, Double>()
        val workouts = workoutsSorted.map { w ->
            val samples = hr.sliceByTime(w.start, w.end) { it.time }
            if (samples.size >= 20) {
                HeartExtras.heartRateRecovery(hr.sliceByTime(w.start, w.end.plusSeconds(180)) { it.time }, w.end, restRef, hrMax)
                    ?.let { hrrByWorkout[w.start] = it }
                if (w.type.endurance || w.type == SportType.HIIT) HeartExtras.best20MinAvg(samples)?.let { best20ByWorkout[w.start] = it }
            }
            HeartMath.analyzeWorkout(w, samples, restRef, hrMax, sex)
        }
        val workoutsByDate = workouts.groupBy { it.workout.start.atZone(zone).toLocalDate() }
        val dailyByDate = data.daily.associateBy { it.date }

        // ---------- Belastungsmodell ----------
        val loadFromHr = BooleanArray(n)
        val loads = dates.mapIndexed { i, d ->
            val ws = workoutsByDate[d].orEmpty()
            val computed = ws.sumOf { it.trimp }
            val snapLoad = snapByDate[d]?.load
            val fromHr = ws.isNotEmpty() && ws.none { it.estimated }
            when {
                fromHr -> {
                    loadFromHr[i] = true
                    computed
                }
                snapLoad != null && snapLoad > 0 -> {
                    loadFromHr[i] = true
                    snapLoad
                }
                else -> computed
            }
        }
        val init = if (n > 0) loads.take(minOf(14, n)).average() else 0.0
        var atl = init
        var ctl = init
        var acute = init
        var chronic = init
        val atlArr = DoubleArray(n)
        val ctlArr = DoubleArray(n)
        val tsbArr = DoubleArray(n)
        val acuteArr = DoubleArray(n)
        val chronicArr = DoubleArray(n)
        val acwrArr = arrayOfNulls<Double>(n)
        for (i in 0 until n) {
            tsbArr[i] = ctl - atl
            atl += K_ATL * (loads[i] - atl)
            ctl += K_CTL * (loads[i] - ctl)
            acute = L_ACUTE * loads[i] + (1 - L_ACUTE) * acute
            chronic = L_CHRONIC * loads[i] + (1 - L_CHRONIC) * chronic
            atlArr[i] = atl
            ctlArr[i] = ctl
            acuteArr[i] = acute
            chronicArr[i] = chronic
            acwrArr[i] = if (i >= 20 && chronic > 1.0) acute / chronic else null
        }
        val dateIndex = dates.withIndex().associate { it.value to it.index }

        // ---------- Erholungszeiten je Training ----------
        val regenInfos = workouts.map { w ->
            val idx = dateIndex[w.workout.start.atZone(zone).toLocalDate()] ?: 0
            val ctlBefore = if (idx > 0) ctlArr[idx - 1] else init
            w to Regeneration.recoveryHours(w, ctlBefore)
        }

        // ---------- Tagesmetriken ----------
        val days = ArrayList<DayMetrics>(n)
        var prevElevated = false
        val awakeAvgByDate = HashMap<LocalDate, Double>()
        val stressHourSum = DoubleArray(24)
        val stressHourCount = IntArray(24)
        var stressRest = 0
        var stressHigh = 0
        var stressTotal = 0
        for ((i, d) in dates.withIndex()) {
            val (ds, de) = dayBounds(d, zone)
            val window = dates.subList(maxOf(0, i - 30), i)
            val rhrB = Stats.baseline(window.mapNotNull { rhrByDate[it]?.first })
            val lnHrvB = Stats.baseline(window.mapNotNull { hrvByDate[it]?.let(::ln) })
            val respB = Stats.baseline(window.mapNotNull { respByDate[it] })
            val rhr = rhrByDate[d]
            val dayHrv = hrvByDate[d]
            val dayResp = respByDate[d]
            val night = nightByDate[d]
            val dayWorkouts = workoutsByDate[d].orEmpty()

            val rhrElevated = rhr != null && rhrB != null &&
                (rhr.first - rhrB.mean) / max(rhrB.sd, 2.0) >= 2.0 && rhr.first - rhrB.mean >= 5.0
            val hrvLow = dayHrv != null && lnHrvB != null && (ln(dayHrv) - lnHrvB.mean) / max(lnHrvB.sd, 0.08) <= -1.5
            val respHigh = dayResp != null && respB != null &&
                dayResp - respB.mean >= 1.0 && (dayResp - respB.mean) / max(respB.sd, 0.6) >= 2.0
            val illness = rhrElevated && (hrvLow || respHigh || prevElevated)
            prevElevated = rhrElevated

            // Wachphase des Tages
            val wakeT = night?.end ?: d.atTime(7, 0).atZone(zone).toInstant()
            val nextBed = nightByDate[d.plusDays(1)]?.start ?: d.atTime(23, 0).atZone(zone).toInstant()
            val dayEndT = if (nextBed.isAfter(now)) now else nextBed
            val workoutWindows = (dayWorkouts + workoutsByDate[d.minusDays(1)].orEmpty())
                .map { it.workout.start to it.workout.end.plusSeconds(15 * 60) }
            fun inWorkout(t: Instant) = workoutWindows.any { !t.isBefore(it.first) && !t.isAfter(it.second) }
            val wakingSamples = if (dayEndT.isAfter(wakeT)) hr.sliceByTime(wakeT, dayEndT) { it.time } else emptyList()
            val nonWorkout = wakingSamples.filter { !inWorkout(it.time) }
            HeartExtras.timeWeightedMean(nonWorkout, 10, 12)?.let { awakeAvgByDate[d] = it }

            // Herzfrequenz
            val dayHr = hr.sliceByTime(ds, de) { it.time }
            val avgHr = HeartExtras.timeWeightedMean(dayHr, 10, 24)
            val nightHr = night?.let { hr.sliceByTime(it.start, it.end) { s -> s.time } }.orEmpty()
            val sleepAvg = HeartExtras.timeWeightedMean(nightHr, 5, 12)
            val prevAwake = awakeAvgByDate[d.minusDays(1)]
            val dip = if (sleepAvg != null && prevAwake != null && prevAwake > sleepAvg * 0.8) (prevAwake - sleepAvg) / prevAwake * 100 else null
            val lowest = if (night != null && nightHr.size >= 6) HeartExtras.lowestWindow(nightHr) else null
            val lowestPos = if (night != null && lowest != null) {
                (Duration.between(night.start, lowest.second).seconds.toDouble() / Duration.between(night.start, night.end).seconds).clamp(0.0, 1.0)
            } else {
                null
            }

            // Stress (Schätzung) – nur Phasen ohne Bewegung
            val dayHasHourly = d in hourlyDates
            // Mit stündlichen Schritten: nur Stunden ohne Bewegung. Ohne diese Daten (gar keine vorhanden): grober Filter.
            val calmSamples = when {
                dayHasHourly -> nonWorkout.filter { s -> (hourlyMap[s.time.epochSecond / 3600] ?: 0L) < 100L }
                hourlyDates.isEmpty() -> nonWorkout.filter { s -> (s.bpm - restRef) / (hrMax - restRef) < 0.4 }
                else -> emptyList()
            }
            val stressVals = calmSamples.map { it to HeartExtras.stressValue(it.bpm, restRef, hrMax) }
            val stressIndex = if (stressVals.size >= 6) stressVals.map { it.second }.average() else null
            if (i >= n - 14) {
                stressVals.forEach { (s, v) ->
                    val h = s.time.atZone(zone).hour
                    stressHourSum[h] += v
                    stressHourCount[h]++
                }
            }
            if (i >= n - 7) {
                stressVals.forEach { (_, v) ->
                    stressTotal++
                    if (v < 25) stressRest++
                    if (v > 50) stressHigh++
                }
            }

            // Aktive / inaktive Stunden
            var activeH: Int? = null
            var inactiveH: Int? = null
            if (dayHasHourly && dayEndT.isAfter(wakeT)) {
                var a = 0
                var b = 0
                var h = (wakeT.epochSecond + 3599) / 3600
                while ((h + 1) * 3600 <= dayEndT.epochSecond) {
                    val hs = Instant.ofEpochSecond(h * 3600)
                    val he = Instant.ofEpochSecond((h + 1) * 3600)
                    val steps = hourlyMap[h] ?: 0L
                    val workoutOverlap = workoutWindows.any { it.first.isBefore(he) && it.second.isAfter(hs) }
                    if (steps >= 250 || workoutOverlap) a++ else b++
                    h++
                }
                activeH = a
                inactiveH = b
            }
            val everyday = HeartExtras.everydayTrimp(nonWorkout, restRef, hrMax, sex)

            // Regeneration am Morgen
            val regen = Regeneration.progress(Regeneration.pending(regenInfos, wakeT))

            val debt = sleepDebt(dates.subList(maxOf(0, i - 6), i + 1).mapNotNull { nightByDate[it] }, goal)
            val recovery = recoveryScore(rhr?.first, rhrB, dayHrv, lnHrvB, night, dayResp, respB, regen)
            val readiness = readinessScore(
                recovery = recovery,
                acwr = if (i > 0) acwrArr[i - 1] else null,
                tsb = tsbArr[i],
                ctl = if (i > 0) ctlArr[i - 1] else init,
                historyDays = i,
                debt = debt,
                illness = illness,
                sleepMin = night?.asleepMin,
            )
            val nightRec = nightRecovery(dip, lowestPos, lowest?.second, recovery, zone)

            // Schlafbedarf für die kommende Nacht
            val rel = loads[i] / max(if (i > 0) ctlArr[i - 1] else init, 25.0)
            val loadAdd = when {
                rel >= 2.0 -> 30.0
                rel >= 1.3 -> 20.0
                rel >= 0.8 -> 10.0
                else -> 0.0
            }
            val debtAdd = minOf(60.0, (debt ?: 0.0) * 60.0 / 5.0)
            val need = goal * 60.0 + loadAdd + debtAdd

            val daily = dailyByDate[d]
            val weight = data.weight.filter { it.time.atZone(zone).toLocalDate() == d }.maxByOrNull { it.time }?.value
                ?: snapByDate[d]?.weight
            days += DayMetrics(
                date = d,
                sleep = night,
                restingHr = rhr?.first,
                restingHrSource = rhr?.second,
                hrv = dayHrv,
                respRate = dayResp,
                spo2 = spo2ByDate[d]?.first,
                spo2Min = spo2ByDate[d]?.second,
                steps = daily?.steps ?: snapByDate[d]?.steps,
                activeKcal = daily?.activeKcal,
                totalKcal = daily?.totalKcal,
                distanceM = daily?.distanceMeters,
                load = loads[i],
                moderateMin = dayWorkouts.sumOf { it.moderateMin },
                vigorousMin = dayWorkouts.sumOf { it.vigorousMin },
                workoutCount = dayWorkouts.size,
                atl = atlArr[i],
                ctl = ctlArr[i],
                tsb = tsbArr[i],
                acwr = acwrArr[i],
                rhrBaseline = rhrB,
                lnHrvBaseline = lnHrvB,
                respBaseline = respB,
                recovery = recovery,
                readiness = readiness,
                sleepDebtH = debt,
                illnessFlag = illness,
                loadFromHr = loadFromHr[i],
                avgHr = avgHr ?: snapByDate[d]?.avgHr,
                sleepAvgHr = sleepAvg,
                hrDipPct = dip,
                lowestHrPos = lowestPos,
                nightRecovery = nightRec,
                stressIndex = stressIndex ?: snapByDate[d]?.stress,
                activeHours = activeH,
                inactiveHours = inactiveH,
                everydayLoad = everyday,
                regenProgress = regen,
                lateWorkout = dayWorkouts.any { it.workout.end.atZone(zone).hour >= 20 },
                sleepNeedMin = need,
                weight = weight,
                acuteLoad = acuteArr[i],
                chronicLoad = chronicArr[i],
            )
        }

        val fitness = fitnessReport(data, profile, today, days, workouts, hrMax, hrMaxSource, restRef, age, zone)
        val load = loadReport(days, workouts, today, zone, sex)
        val sleep = sleepReport(days, nightByDate, today, goal, data.sleep, zone, loads, ctlArr)
        val activity = activityReport(days, profile, workouts, zone, today, hourlyMap, hourlyDates)
        val quality = DataQuality(
            daysWithData = days.count { it.restingHr != null || it.sleep != null || (it.steps ?: 0) > 0 || it.load > 0 },
            hasHeartRate = hr.isNotEmpty(),
            hasHrv = hrv.isNotEmpty() || data.history.any { it.hrv != null },
            hasSleep = nightByDate.isNotEmpty(),
            hasSleepStages = nightByDate.values.any { it.hasStages },
            hasWorkouts = workouts.isNotEmpty(),
            hasSteps = data.daily.any { (it.steps ?: 0) > 0 },
            hasSpo2 = spo2.isNotEmpty(),
            hasRespiratoryRate = resp.isNotEmpty(),
            hasVo2Device = data.vo2max.isNotEmpty(),
            baselineDays = days.lastOrNull()?.rhrBaseline?.n ?: 0,
        )
        val regeneration = regenerationReport(days, regenInfos, now, load)
        val heart = heartReport(days, workouts, hrrByWorkout, nightByDate[today] ?: nightByDate[today.minusDays(1)], hr, zone, today, load)
        val stress = stressReport(days, stressHourSum, stressHourCount, stressRest, stressHigh, stressTotal, restRef, hrMax, data.hourlySteps.isNotEmpty())
        val performance = performanceReport(days, workouts, best20ByWorkout, fitness, zone, today, profile)
        val warnings = warnings(days, load, sleep, fitness)
        val energy = energyReport(days, nightByDate, hr, workoutsByDate, hourlyMap, hourlyDates, restRef, hrMax, sex, now, today, zone)
        val loadNow = energy?.readinessNow?.let { rn -> load.copy(recommendation = recommend(days.lastOrNull(), sex, rn)) } ?: load
        val base = AnalysisResult(
            today = today,
            profile = profile,
            days = days,
            workouts = regenInfos.map { (w, h) -> w.copy(recoveryH = h) }.sortedByDescending { it.workout.start },
            fitness = fitness,
            load = loadNow,
            sleep = sleep,
            activity = activity,
            warnings = warnings,
            quality = quality,
            isDemo = data.isDemo,
            generatedAt = now,
            zone = zone,
            regeneration = regeneration,
            energy = energy,
            heart = heart,
            stress = stress,
            performance = performance,
        )
        val bpDevice = data.bloodPressure.filter { ChronoUnit.DAYS.between(it.time.atZone(zone).toLocalDate(), today) <= 90 }
            .sortedBy { it.time }.takeLast(3)
        val bp = if (bpDevice.isNotEmpty()) {
            bpDevice.map { it.systolic }.average() to bpDevice.map { it.diastolic }.average()
        } else if (profile.bpSystolic != null && profile.bpDiastolic != null) {
            profile.bpSystolic.toDouble() to profile.bpDiastolic.toDouble()
        } else {
            null
        }
        val withHeart = base.copy(heartHealth = HeartHealth.build(base, bp, bpDevice.isNotEmpty()), forecast = Forecast.build(days))
        val baselines = Insights.baselines(withHeart)
        val withBase = withHeart.copy(baselines = baselines, correlations = Insights.correlations(withHeart), trends = Insights.trends(withHeart))
        return withBase.copy(briefing = Insights.briefing(withBase))
    }

    private fun dayBounds(d: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
        val s = d.atStartOfDay(zone).toInstant()
        val e = d.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
        return s to e
    }

    private fun windowValues(list: List<TimedValue>, night: SleepNight?, ds: Instant, de: Instant): List<Double> {
        val inNight = night?.let { list.sliceByTime(it.start, it.end) { v -> v.time } }.orEmpty()
        return inNight.ifEmpty { list.sliceByTime(ds, de) { it.time } }.map { it.value }
    }

    private fun sleepDebt(nights: List<SleepNight>, goalH: Double): Double? {
        if (nights.size < 3) return null
        return nights.sumOf { goalH - it.asleepMin / 60.0 }.coerceAtLeast(0.0)
    }

    /** z-Wert → Punkte. Dein Normalwert = 60 Punkte („solide“); gut ist nur, was besser als normal ist. */
    private fun sub(z: Double): Int = (60.0 + 15.0 * z).clamp(0.0, 100.0).roundToInt()

    internal fun recoveryScore(
        rhr: Double?,
        rhrB: Baseline?,
        hrv: Double?,
        lnHrvB: Baseline?,
        sleep: SleepNight?,
        resp: Double?,
        respB: Baseline?,
        regen: Int? = null,
    ): Score? {
        val hasHrv = hrv != null && lnHrvB != null
        val comps = mutableListOf<ScoreComponent>()
        if (hrv != null && lnHrvB != null) {
            val z = (ln(hrv) - lnHrvB.mean) / max(lnHrvB.sd, 0.08)
            val ref = exp(lnHrvB.mean)
            comps += ScoreComponent(
                "HRV",
                sub(z),
                0.35,
                "${Fmt.int(hrv)} ms – ${Fmt.signed((hrv / ref - 1) * 100)} % zu deinem Schnitt (${Fmt.int(ref)} ms)",
            )
        }
        if (rhr != null && rhrB != null) {
            val z = -(rhr - rhrB.mean) / max(rhrB.sd, 2.0)
            comps += ScoreComponent(
                "Ruhepuls",
                sub(z),
                if (hasHrv) 0.25 else 0.45,
                "${Fmt.num(rhr, 1)} bpm – ${Fmt.signed(rhr - rhrB.mean, 1)} zu deinem Schnitt (${Fmt.num(rhrB.mean, 1)} bpm)",
            )
        }
        val sleepCap = sleep?.let {
            when {
                it.asleepMin < 300 -> 40
                it.asleepMin < 360 -> 55
                else -> null
            }
        }
        if (sleep != null) {
            comps += ScoreComponent(
                "Schlaf", sleep.score, if (hasHrv) 0.25 else 0.33,
                "Schlaf-Score ${sleep.score} (${Fmt.duration(sleep.asleepMin)})" + (sleepCap?.let { " → Erholung max. $it" } ?: ""),
            )
        }
        if (resp != null && respB != null) {
            val zUp = (resp - respB.mean) / max(respB.sd, 0.6)
            val s = (80.0 - 17.5 * max(0.0, zUp)).clamp(0.0, 100.0).roundToInt()
            comps += ScoreComponent(
                "Atemfrequenz",
                s,
                if (hasHrv) 0.05 else 0.10,
                "${Fmt.num(resp, 1)} /min – ${Fmt.signed(resp - respB.mean, 1)} zu deinem Schnitt",
            )
        }
        if (comps.isEmpty()) return null
        if (regen != null) {
            comps += ScoreComponent(
                "Trainingsbelastung",
                regen,
                if (hasHrv) 0.10 else 0.12,
                if (regen >= 100) "Keine offene Erholung aus Trainings" else "Regeneration nach letztem Training zu $regen % abgeschlossen",
            )
        }
        val wsum = comps.sumOf { it.weight }
        var value = (comps.sumOf { it.score * it.weight } / wsum).roundToInt().coerceIn(0, 100)
        // Kurze Nacht begrenzt die Erholung (Schlafmangel beeinträchtigt Regeneration, Leistung und Immunsystem).
        if (sleepCap != null) value = minOf(value, sleepCap)
        val physio = listOfNotNull(rhrB?.takeIf { rhr != null }, lnHrvB?.takeIf { hrv != null })
        val minN = physio.minOfOrNull { it.n } ?: 0
        val confidence = when {
            hasHrv && rhr != null && rhrB != null && sleep != null && minN >= 14 -> Confidence.HIGH
            physio.isNotEmpty() && (sleep != null || physio.size == 2) -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return Score(value, comps, confidence)
    }

    private fun nightRecovery(dip: Double?, lowestPos: Double?, lowestTime: Instant?, recovery: Score?, zone: ZoneId): Score? {
        val comps = mutableListOf<ScoreComponent>()
        if (dip != null) {
            comps += ScoreComponent(
                "Pulsabsenkung", (100.0 * (dip / 15.0).clamp(0.0, 1.0)).roundToInt(), 0.3,
                "Puls im Schlaf ${Fmt.num(dip, 0)} % niedriger als am Vorabend (gut: 10–20 %)",
            )
        }
        if (lowestPos != null && lowestTime != null) {
            val s = if (lowestPos <= 0.6) 100.0 else 100.0 - (lowestPos - 0.6) / 0.4 * 60.0
            val lt = lowestTime.atZone(zone).toLocalTime()
            comps += ScoreComponent(
                "Zeitpunkt des Tiefstwerts", s.roundToInt(), 0.2,
                String.format(java.util.Locale.GERMANY, "Tiefster Puls um %02d:%02d Uhr (nach %d %% der Nacht)", lt.hour, lt.minute, (lowestPos * 100).roundToInt()),
            )
        }
        recovery?.components?.firstOrNull { it.name == "HRV" }?.let { comps += it.copy(name = "Nächtliche HRV", weight = 0.3) }
        recovery?.components?.firstOrNull { it.name == "Ruhepuls" }?.let { comps += it.copy(name = "Nächtlicher Ruhepuls", weight = 0.2) }
        if (comps.size < 2) return null
        val v = (comps.sumOf { it.score * it.weight } / comps.sumOf { it.weight }).roundToInt()
        return Score(v, comps, Confidence.MEDIUM)
    }

    internal fun acwrScore(acwr: Double): Int = when {
        acwr <= 1.1 -> 85.0
        acwr <= 1.3 -> 85.0 - (acwr - 1.1) / 0.2 * 15.0
        acwr <= 1.5 -> 70.0 - (acwr - 1.3) / 0.2 * 30.0
        acwr <= 2.0 -> 40.0 - (acwr - 1.5) / 0.5 * 30.0
        else -> 10.0
    }.roundToInt()

    /** Form-Punkte: ausgeglichene Form (TSB 0) = 70. */
    internal fun formScore(tsb: Double, ctl: Double): Int = (70.0 + 100.0 * tsb / max(ctl, 15.0)).clamp(10.0, 100.0).roundToInt()

    internal fun debtScore(debtH: Double): Int = (100.0 - debtH * 15.0).clamp(0.0, 100.0).roundToInt()

    private fun readinessScore(
        recovery: Score?,
        acwr: Double?,
        tsb: Double,
        ctl: Double,
        historyDays: Int,
        debt: Double?,
        illness: Boolean,
        sleepMin: Double? = null,
    ): Score? {
        if (recovery == null) return null
        val comps = mutableListOf<ScoreComponent>()
        comps += ScoreComponent("Erholung", recovery.value, 0.6, "Persönlicher Erholungswert ${recovery.value} von 100")
        if (acwr != null) {
            comps += ScoreComponent("Belastungssteigerung", acwrScore(acwr), 0.15, "ACWR ${Fmt.num(acwr, 2)} (optimal 0,8–1,3)")
        }
        if (historyDays >= 14) {
            comps += ScoreComponent("Form", formScore(tsb, ctl), 0.15, "Form (TSB) ${Fmt.signed(tsb)} bei Fitness ${Fmt.int(ctl)}")
        }
        if (debt != null) {
            comps += ScoreComponent("Schlafschuld", debtScore(debt), 0.1, "${Fmt.num(debt, 1)} h Schlafdefizit in den letzten 7 Nächten")
        }
        val wsum = comps.sumOf { it.weight }
        var value = (comps.sumOf { it.score * it.weight } / wsum).roundToInt().coerceIn(0, 100)
        // Bereitschaft kann nicht deutlich über der Erholung liegen; nach < 6 h Schlaf kein „intensiv“.
        value = minOf(value, recovery.value + 5)
        if (sleepMin != null && sleepMin < 360) value = minOf(value, 59)
        if (illness) value = minOf(value, 30)
        val conf = if (historyDays < 21 && recovery.confidence == Confidence.HIGH) Confidence.MEDIUM else recovery.confidence
        return Score(value, comps, conf)
    }

    private fun fitnessReport(
        data: HealthData,
        profile: UserProfile,
        today: LocalDate,
        days: List<DayMetrics>,
        workouts: List<WorkoutAnalysis>,
        hrMax: Int,
        hrMaxSource: HrMaxSource,
        restRef: Double,
        age: Int?,
        zone: ZoneId,
    ): FitnessReport {
        val sex = profile.sex
        val deviceVo2 = data.vo2max.filter { it.value in 15.0..90.0 }.sortedBy { it.time }
        val recentDevice = deviceVo2.filter { ChronoUnit.DAYS.between(it.time.atZone(zone).toLocalDate(), today) <= 60 }
        val runVo2All = workouts.filter { it.vo2Estimate != null }.sortedBy { it.workout.start }
        val runVo2 = runVo2All.filter { ChronoUnit.DAYS.between(it.workout.start.atZone(zone).toLocalDate(), today) <= 90 }
        val restNow = Stats.median(days.takeLast(14).mapNotNull { it.restingHr })
        val (vo2, method) = when {
            recentDevice.isNotEmpty() -> Stats.median(recentDevice.takeLast(3).map { it.value }) to Vo2Method.DEVICE
            runVo2.size >= 2 -> Stats.median(runVo2.takeLast(5).mapNotNull { it.vo2Estimate }) to Vo2Method.RUNNING
            restNow != null -> HeartMath.vo2FromRestingHr(hrMax, restNow) to Vo2Method.RESTING_HR
            else -> null to null
        }
        val history = when (method) {
            Vo2Method.DEVICE -> deviceVo2.map { it.time.atZone(zone).toLocalDate() to it.value }
            Vo2Method.RUNNING -> runVo2All.map { it.workout.start.atZone(zone).toLocalDate() to it.vo2Estimate!! }
            else -> emptyList()
        }
        val catIdx = if (vo2 != null && age != null) FitnessMath.vo2Category(vo2, age, sex) else null

        // Persönlicher Puls-Tempo-Zusammenhang: Geschwindigkeit ≈ k × Anteil der Herzfrequenzreserve
        // (ACSM-Laufgleichung + %HFR ≈ %VO2-Reserve, Swain & Leutholtz 1997), k aus deinen Läufen.
        val ratios = workouts.filter { w ->
            w.workout.type == SportType.RUNNING && (w.workout.distanceMeters ?: 0.0) >= 1000 && w.durationMin >= 15 &&
                w.avgHr != null && ChronoUnit.DAYS.between(w.workout.start.atZone(zone).toLocalDate(), today) <= 120
        }.mapNotNull { w ->
            val f = (w.avgHr!! - restRef) / (hrMax - restRef)
            val speed = w.workout.distanceMeters!! / w.durationMin
            if (f in 0.5..0.92 && speed * 0.06 in 6.0..22.0) speed / f else null
        }
        val (k, paceSource) = when {
            ratios.size >= 2 -> Stats.median(ratios.takeLast(10)) to "aus ${minOf(ratios.size, 10)} deiner Läufe (letzte 4 Monate)"
            vo2 != null -> (vo2 - 3.5) / 0.2 to "aus deiner VO2max geschätzt (noch zu wenige Läufe mit GPS)"
            else -> null to null
        }
        val zones = HeartMath.zones(restRef, hrMax).map { z ->
            if (k == null || k <= 0) {
                z
            } else {
                val fLow = 0.4 + 0.1 * z.number
                z.copy(paceSlowSecKm = 60000.0 / (k * fLow), paceFastSecKm = 60000.0 / (k * (fLow + 0.1)))
            }
        }

        val last7 = days.takeLast(7)
        val last30 = days.takeLast(30)
        val rhrSeries = days.takeLast(60).withIndex().filter { it.value.restingHr != null }
        val trend = Stats.slope(rhrSeries.map { it.index.toDouble() }, rhrSeries.map { it.value.restingHr!! })
            ?.takeIf { rhrSeries.size >= 14 }?.times(30.0)
        val hrvLn30 = last30.mapNotNull { it.hrv?.let(::ln) }
        val hrvB = Stats.baseline(hrvLn30)
        val weight = data.weight.maxByOrNull { it.time }?.value ?: profile.weightKg
        val height = profile.heightCm ?: data.height.maxByOrNull { it.time }?.value
        val bmi = if (weight != null && height != null && height > 100) FitnessMath.bmi(weight, height) else null
        val bodyFat = data.bodyFat.maxByOrNull { it.time }?.value
        return FitnessReport(
            vo2max = vo2,
            vo2Method = method,
            vo2CategoryIndex = catIdx,
            vo2CategoryLabel = catIdx?.let { FitnessMath.CATEGORY_LABELS[it] },
            fitnessAge = vo2?.let { FitnessMath.fitnessAge(it, sex) },
            age = age,
            vo2History = history,
            hrMax = hrMax,
            hrMaxSource = hrMaxSource,
            restingHrNow = Stats.median(last7.mapNotNull { it.restingHr }),
            restingHrBaseline = Stats.mean(last30.mapNotNull { it.restingHr }),
            restingHrTrend30d = trend,
            hrvNow = Stats.mean(last7.mapNotNull { it.hrv }),
            hrvBaseline = hrvB?.let { exp(it.mean) },
            hrvNormalLow = hrvB?.takeIf { it.n >= 7 }?.let { exp(it.mean - it.sd) },
            hrvNormalHigh = hrvB?.takeIf { it.n >= 7 }?.let { exp(it.mean + it.sd) },
            zones = zones,
            zonePaceSource = paceSource,
            speedPerHrr = k,
            weightKg = weight,
            heightCm = height,
            bmi = bmi,
            bodyFatPct = bodyFat,
            bodyFatLabel = bodyFat?.let { FitnessMath.bodyFatLabel(it, sex) },
            spo2Avg7 = Stats.mean(last7.mapNotNull { it.spo2 }),
            respAvg7 = Stats.mean(last7.mapNotNull { it.respRate }),
            respBaseline = Stats.mean(last30.mapNotNull { it.respRate }),
        )
    }

    private fun energyReport(
        days: List<DayMetrics>,
        nightByDate: Map<LocalDate, SleepNight>,
        hr: List<HeartRateSample>,
        workoutsByDate: Map<LocalDate, List<WorkoutAnalysis>>,
        hourlyMap: Map<Long, Long>,
        hourlyDates: Set<LocalDate>,
        restRef: Double,
        hrMax: Int,
        sex: Sex?,
        now: Instant,
        today: LocalDate,
        zone: ZoneId,
    ): EnergyReport? {
        fun sim(day: DayMetrics): Pair<Instant, Energy.Sim>? {
            val e0 = day.recovery?.value ?: return null
            val wake = day.sleep?.end ?: day.date.atTime(7, 0).atZone(zone).toInstant()
            val bed = nightByDate[day.date.plusDays(1)]?.start ?: day.date.atTime(23, 0).atZone(zone).toInstant()
            val end = minOf(bed, now)
            val ws = (workoutsByDate[day.date].orEmpty() + workoutsByDate[day.date.minusDays(1)].orEmpty())
                .filter { it.workout.end.isAfter(wake) && it.workout.start.isBefore(end) }
            val samples = if (end.isAfter(wake)) hr.sliceByTime(wake, end) { it.time } else emptyList()
            val steps = if (day.date in hourlyDates) hourlyMap else null
            return wake to Energy.simulate(e0.toDouble(), wake, end, samples, ws, steps, restRef, hrMax, sex)
        }
        val t = days.lastOrNull()?.takeIf { it.date == today } ?: return null
        val (wake, s) = sim(t) ?: return null
        val history = days.takeLast(8).mapNotNull { d ->
            val r = if (d === t) s else sim(d)?.second
            r?.let { EnergyDay(d.date, it.curve.first().value.roundToInt(), it.end.roundToInt()) }
        }
        val nowV = s.end.roundToInt()
        val (label, tone) = Energy.label(nowV)
        val readinessMorning = t.readiness?.value
        return EnergyReport(
            now = nowV,
            morning = s.curve.first().value.roundToInt(),
            wakeTime = wake,
            curve = s.curve,
            drainAwake = s.awake,
            drainStress = s.stress,
            drainEveryday = s.everyday,
            drainTraining = s.training,
            charge = s.charge,
            readinessMorning = readinessMorning,
            readinessNow = Energy.readinessNow(readinessMorning, s),
            history = history,
            label = label,
            tone = tone,
            hasDayHeartRate = s.hadHeartRate,
        )
    }

    private fun loadReport(
        days: List<DayMetrics>,
        workouts: List<WorkoutAnalysis>,
        today: LocalDate,
        zone: ZoneId,
        sex: Sex?,
    ): LoadReport {
        // Ohne Training heute zählen die Werte vom Ende des Vortags (sonst „sinkt“ die Belastung morgens künstlich).
        val t = days.lastOrNull()?.let { last -> if (last.load > 0 || days.size < 2) last else days[days.size - 2] }
        val last7 = days.takeLast(7)
        val prev7 = days.dropLast(7).takeLast(7)
        val loads7 = last7.map { it.load }
        val mean7 = loads7.average().takeIf { !it.isNaN() } ?: 0.0
        val sd7 = Stats.sd(loads7)
        val trainingDays7 = loads7.count { it >= 15.0 }
        // Monotonie (Foster) setzt regelmäßiges Training voraus – bei weniger als 3 Trainingstagen nicht aussagekräftig.
        val monotony = if (trainingDays7 >= 3 && sd7 != null && sd7 > 0.5 && mean7 > 0) mean7 / sd7 else null
        val weekLoad = loads7.sum()
        val load28 = days.takeLast(28)
        val before = days.dropLast(28).takeLast(62)
        val weekAvgBefore = if (before.size >= 28) before.sumOf { it.load } / before.size * 7.0 else null
        val lastWorkoutDate = workouts.filter { it.durationMin >= 10 }.maxOfOrNull { it.workout.start.atZone(zone).toLocalDate() }
        val recent28 = workouts.filter { ChronoUnit.DAYS.between(it.workout.start.atZone(zone).toLocalDate(), today) in 0..27 }
        val z = DoubleArray(6)
        recent28.forEach { w -> w.zoneMin.forEachIndexed { i, m -> z[i] += m } }
        val zTotal = z.sum()
        val acwr = t?.acwr
        val status = when {
            t == null || workouts.isEmpty() || acwr == null -> LoadStatus.NO_DATA
            acwr < 0.8 -> LoadStatus.DETRAINING
            acwr < 1.1 -> LoadStatus.MAINTAIN
            acwr <= 1.3 -> LoadStatus.OPTIMAL
            acwr <= 1.5 -> LoadStatus.HIGH
            else -> LoadStatus.OVERREACHING
        }
        return LoadReport(
            atl = t?.atl ?: 0.0,
            ctl = t?.ctl ?: 0.0,
            tsb = t?.let { it.ctl - it.atl } ?: 0.0,
            acwr = acwr,
            monotony = monotony,
            strain = monotony?.let { it * weekLoad },
            weekLoad = weekLoad,
            prevWeekLoad = prev7.sumOf { it.load },
            status = status,
            easyPct = if (zTotal > 0) (z[0] + z[1] + z[2]) / zTotal else null,
            moderatePct = if (zTotal > 0) z[3] / zTotal else null,
            hardPct = if (zTotal > 0) (z[4] + z[5]) / zTotal else null,
            zoneMin28 = z.toList(),
            whoMinutes7 = last7.sumOf { it.moderateMin + 2.0 * it.vigorousMin },
            workouts7 = last7.sumOf { it.workoutCount },
            recommendation = recommend(days.lastOrNull(), sex),
            historyDays = days.size,
            acuteWeek = t?.let { it.acuteLoad * 7.0 },
            chronicWeek = t?.let { it.chronicLoad * 7.0 },
            trainingDays7 = trainingDays7,
            weekAvg28 = if (load28.isNotEmpty()) load28.sumOf { it.load } / load28.size * 7.0 else 0.0,
            weekAvgBefore = weekAvgBefore,
            daysSinceWorkout = lastWorkoutDate?.let { ChronoUnit.DAYS.between(it, today).toInt() },
            ctl28Ago = if (days.size > 28) days[days.size - 29].ctl else null,
        )
    }

    /** Empfehlung aus der Trainingsbereitschaft – tagsüber aus dem aktuellen Wert [readinessNow] (sinkt durch Training, Stress, Alltag). */
    private fun recommend(t: DayMetrics?, sex: Sex?, readinessNow: Int? = null): TrainingRecommendation? {
        val r = readinessNow ?: t?.readiness?.value ?: return null
        t ?: return null
        val base = max(t.ctl, 30.0)
        val z2Rate = HeartMath.trimpPerMinute(0.65, sex)
        fun rec(title: String, text: String, tone: Tone, lo: Double, hi: Double) = TrainingRecommendation(
            title, text, tone,
            lo.roundToInt(), hi.roundToInt(),
            (lo / z2Rate / 5).roundToInt() * 5, (hi / z2Rate / 5).roundToInt() * 5,
        )
        return when {
            t.illnessFlag -> rec(
                "Ruhetag empfohlen",
                "Deine Werte deuten auf eine mögliche beginnende Erkrankung oder starke Überlastung hin. Verzichte heute auf Training – Schlaf, Flüssigkeit und ein ruhiger Spaziergang helfen jetzt am meisten.",
                Tone.BAD, 0.0, base * 0.2,
            )
            r >= 75 -> rec(
                "Bereit für intensives Training",
                "Dein Körper ist bereit für eine fordernde Einheit: Intervalle, ein Tempolauf, schweres Krafttraining oder eine lange Ausdauereinheit.",
                Tone.GOOD, base * 1.2, base * 1.6,
            )
            r >= 60 -> rec(
                "Moderates Training",
                "Gut für eine normale Einheit, überwiegend in Zone 2–3. Sehr harte Intervalle besser auf einen frischeren Tag verschieben.",
                Tone.OK, base * 0.8, base * 1.2,
            )
            r >= 45 -> rec(
                "Lockeres Training / aktive Erholung",
                "Bleib heute in Zone 1–2: lockeres Radfahren, Spazierengehen, Mobility oder Dehnen fördern die Erholung.",
                Tone.WARN, base * 0.3, base * 0.6,
            )
            else -> rec(
                "Ruhetag",
                "Dein Körper ist noch nicht erholt. Ein Ruhetag bringt dir heute mehr als Training – höchstens ein entspannter Spaziergang.",
                Tone.BAD, 0.0, base * 0.3,
            )
        }
    }

    private fun sleepReport(
        days: List<DayMetrics>,
        nights: Map<LocalDate, SleepNight>,
        today: LocalDate,
        goal: Double,
        sessions: List<SleepSession>,
        zone: ZoneId,
        loads: List<Double>,
        ctlArr: DoubleArray,
    ): SleepReport {
        val last = nights[today] ?: nights[today.minusDays(1)]
        val n7 = days.takeLast(7).mapNotNull { it.sleep }
        val n14 = days.takeLast(14).mapNotNull { it.sleep }
        val n30 = days.takeLast(30).mapNotNull { it.sleep }
        val staged = n14.filter { it.hasStages }
        val t = days.lastOrNull()

        // persönlicher Schlafbedarf
        val pairs = days.takeLast(120).mapNotNull { d ->
            val night = d.sleep ?: return@mapNotNull null
            val zs = mutableListOf<Double>()
            val hrv = d.hrv
            val hb = d.lnHrvBaseline
            if (hrv != null && hb != null) zs += (ln(hrv) - hb.mean) / max(hb.sd, 0.08)
            val rhr = d.restingHr
            val rb = d.rhrBaseline
            if (rhr != null && rb != null) zs += -(rhr - rb.mean) / max(rb.sd, 2.0)
            if (zs.isEmpty()) null else night.asleepMin to zs.average()
        }
        val personal = SleepMath.personalNeed(pairs)

        val reasons = mutableListOf<String>()
        if (t != null && days.size >= 2) {
            val i = days.size - 1
            val rel = loads[i] / max(ctlArr[i - 1], 25.0)
            when {
                rel >= 2.0 -> reasons += "+30 min wegen sehr hoher Trainingsbelastung heute"
                rel >= 1.3 -> reasons += "+20 min wegen hoher Trainingsbelastung heute"
                rel >= 0.8 -> reasons += "+10 min wegen Training heute"
            }
            val debt = t.sleepDebtH ?: 0.0
            if (debt > 0.25) reasons += "+${Fmt.int(minOf(60.0, debt * 60.0 / 5.0))} min zum Abbau deiner Schlafschuld"
        }
        val need = t?.sleepNeedMin
        val avgWake = Stats.mean(n14.map { it.wakeMin })
        val avgAwake = Stats.mean(n7.map { it.inBedMin - it.asleepMin }) ?: 0.0
        val bedtime = if (need != null && avgWake != null) avgWake - need - avgAwake else null
        val d14 = days.takeLast(14).mapNotNull { it.sleep }
        return SleepReport(
            lastNight = last,
            avgAsleep7Min = Stats.mean(n7.map { it.asleepMin }),
            avgAsleep30Min = Stats.mean(n30.map { it.asleepMin }),
            debt7H = t?.sleepDebtH,
            regularitySdMin = if (n14.size >= 5) Stats.sd(n14.map { it.midpointMin }) else null,
            avgBedtimeMin = Stats.mean(n14.map { it.bedtimeMin }),
            avgWakeMin = avgWake,
            avgDeepPct = Stats.mean(staged.mapNotNull { it.deepPct }),
            avgRemPct = Stats.mean(staged.mapNotNull { it.remPct }),
            avgEfficiency = Stats.mean(n14.mapNotNull { it.efficiency }),
            avgScore7 = Stats.mean(n7.map { it.score.toDouble() }),
            nights = n30.size,
            goalHours = goal,
            debt14H = if (d14.size >= 5) d14.sumOf { goal - it.asleepMin / 60.0 }.coerceAtLeast(0.0) else null,
            sri = SleepMath.sri(sessions, today, zone, 14),
            personalNeedH = personal?.first,
            personalNeedNights = personal?.second ?: 0,
            needTonightMin = need,
            needReasons = reasons,
            recommendedBedtimeMin = bedtime,
            avgInBed7Min = Stats.mean(n7.map { it.inBedMin }),
            nightRecovery = t?.nightRecovery,
            nightRestingHr = t?.restingHr?.takeIf { t.restingHrSource == RhrSource.SLEEP },
            nightHrv = t?.hrv,
        )
    }

    private fun activityReport(
        days: List<DayMetrics>,
        profile: UserProfile,
        workouts: List<WorkoutAnalysis>,
        zone: ZoneId,
        today: LocalDate,
        hourlyMap: Map<Long, Long>,
        hourlyDates: Set<LocalDate>,
    ): ActivityReport {
        val last7 = days.takeLast(7)
        val complete7 = days.dropLast(1).takeLast(7)
        val prev7 = days.dropLast(7).takeLast(7)
        val t = days.lastOrNull()

        // Schritte je Tagesstunde (14 Tage)
        val byHour = DoubleArray(24)
        val hourDays = days.takeLast(15).dropLast(1).map { it.date }.filter { it in hourlyDates }
        for (d in hourDays) {
            for (h in 0 until 24) {
                val idx = d.atTime(h, 0).atZone(zone).toInstant().epochSecond / 3600
                byHour[h] += (hourlyMap[idx] ?: 0L).toDouble()
            }
        }
        val stepsByHour = if (hourDays.isNotEmpty()) byHour.map { it / hourDays.size } else emptyList()

        val activeAvg = Stats.mean(complete7.mapNotNull { it.activeHours?.toDouble() })
        val inactiveAvg = Stats.mean(complete7.mapNotNull { it.inactiveHours?.toDouble() })
        val steps7 = Stats.mean(complete7.mapNotNull { it.steps?.toDouble() })
        val who = last7.sumOf { it.moderateMin + 2.0 * it.vigorousMin }
        val comps = mutableListOf<ScoreComponent>()
        if (steps7 != null) {
            comps += ScoreComponent(
                "Schritte", (100.0 * (steps7 / profile.stepGoal).clamp(0.0, 1.0)).roundToInt(), 0.4,
                "Ø ${Fmt.int(steps7)} von ${Fmt.int(profile.stepGoal.toLong())} Schritten",
            )
        }
        if (activeAvg != null && inactiveAvg != null && activeAvg + inactiveAvg > 0) {
            val share = activeAvg / (activeAvg + inactiveAvg)
            comps += ScoreComponent(
                "Aktive Stunden", (100.0 * (share / 0.75).clamp(0.0, 1.0)).roundToInt(), 0.3,
                "Ø ${Fmt.num(activeAvg, 1)} von ${Fmt.num(activeAvg + inactiveAvg, 1)} Wachstunden mit ≥ 250 Schritten",
            )
        }
        comps += ScoreComponent(
            "Aktivminuten", (100.0 * (who / 150.0).clamp(0.0, 1.0)).roundToInt(), 0.3,
            "${Fmt.int(who)} von 150 WHO-Minuten in 7 Tagen",
        )
        val movement = if (comps.size >= 2) {
            Score((comps.sumOf { it.score * it.weight } / comps.sumOf { it.weight }).roundToInt(), comps, Confidence.MEDIUM)
        } else {
            null
        }
        val steps14 = days.dropLast(1).takeLast(14).mapNotNull { it.steps?.toDouble() }
        val cv = if (steps14.size >= 7) {
            val m = steps14.average()
            if (m > 0) (Stats.sd(steps14) ?: 0.0) / m else null
        } else {
            null
        }

        fun balance(list: List<DayMetrics>) = WeekBalance(
            steps = list.sumOf { it.steps ?: 0L },
            activeMinutes = list.sumOf { it.moderateMin + it.vigorousMin },
            load = list.sumOf { it.load },
            activeKcal = list.sumOf { it.activeKcal ?: 0.0 },
            workouts = list.sumOf { it.workoutCount },
        )

        val every7 = Stats.mean(complete7.map { it.everydayLoad })
        val load7avg = complete7.map { it.load }.average().takeIf { !it.isNaN() } ?: 0.0
        val actK = complete7.sumOf { it.activeKcal ?: 0.0 }
        val totK = complete7.sumOf { it.totalKcal ?: 0.0 }

        // Erholungsphasen zwischen harten Einheiten
        val ctlNow = t?.ctl ?: 30.0
        val hard = workouts.filter { w ->
            ChronoUnit.DAYS.between(w.workout.start.atZone(zone).toLocalDate(), today) in 0..27 &&
                (w.focus.contains("hart") || w.trimp >= 1.3 * max(ctlNow, 30.0))
        }.sortedBy { it.workout.start }
        val gaps = hard.zipWithNext { a, b -> Duration.between(a.workout.end, b.workout.start).seconds / 3600.0 }
        val runs = workouts.filter {
            it.workout.type == SportType.RUNNING && it.workout.steps != null && it.durationMin >= 10 &&
                ChronoUnit.DAYS.between(it.workout.start.atZone(zone).toLocalDate(), today) <= 28
        }
        val cadence = Stats.median(runs.map { it.workout.steps!! / it.durationMin }.filter { it in 100.0..230.0 })
        return ActivityReport(
            stepsToday = t?.steps,
            stepsYesterday = days.dropLast(1).lastOrNull()?.steps,
            steps7Avg = steps7,
            stepGoal = profile.stepGoal,
            daysGoalReached7 = last7.count { (it.steps ?: 0) >= profile.stepGoal },
            activeKcalToday = t?.activeKcal,
            activeKcal7Avg = Stats.mean(complete7.mapNotNull { it.activeKcal }),
            totalKcal7Avg = Stats.mean(complete7.mapNotNull { it.totalKcal }),
            whoMinutes7 = who,
            activeHoursToday = t?.activeHours,
            inactiveHoursToday = t?.inactiveHours,
            inactiveHours7Avg = inactiveAvg,
            activeHours7Avg = activeAvg,
            stepsByHour = stepsByHour,
            movementIndex = movement,
            regularity = cv?.let { (100.0 * (1.0 - it)).clamp(0.0, 100.0).roundToInt() },
            stepsCv14 = cv,
            thisWeek = balance(last7),
            lastWeek = if (prev7.size >= 7) balance(prev7) else null,
            everydayLoadToday = t?.everydayLoad ?: 0.0,
            everydayLoad7Avg = every7,
            everydayShare7 = every7?.let { if (it + load7avg > 0) it / (it + load7avg) else null },
            activeKcalShare7 = if (totK > 0) actK / totK else null,
            hardSessionGapAvgH = Stats.mean(gaps),
            shortGaps28 = gaps.count { it < 48 },
            runCadence = cadence,
            hasHourlySteps = hourlyDates.isNotEmpty(),
        )
    }

    private fun regenerationReport(
        days: List<DayMetrics>,
        infos: List<Pair<WorkoutAnalysis, Double>>,
        now: Instant,
        load: LoadReport,
    ): RegenerationReport {
        val t = days.lastOrNull()
        val pendingNow = Regeneration.pending(infos, now)
        val binding = pendingNow.filter { it.remainingH > 0 }.maxByOrNull { it.remainingH }
        val timeProgress = Regeneration.progress(pendingNow)
        val auto = t?.recovery?.components?.filter { it.name == "HRV" || it.name == "Ruhepuls" }?.map { it.score.toDouble() }
            ?.takeIf { it.isNotEmpty() }?.average()
        // Autonome Erholung: 65 Punkte = persönlicher Normalwert → 100 %
        val autoNorm = auto?.let { (it / 65.0 * 100.0).clamp(0.0, 100.0) }
        val index = if (autoNorm != null) (0.6 * timeProgress + 0.4 * autoNorm).roundToInt() else timeProgress
        val modifier = when {
            auto == null -> 1.0
            auto < 50 -> 1.2
            auto >= 70 -> 0.85
            else -> 1.0
        }
        val remaining = (binding?.remainingH ?: 0.0) * modifier
        val recent = infos.takeLast(6).reversed().map { (w, h) ->
            val elapsed = Duration.between(w.workout.end, now).seconds / 3600.0
            RecoveryTimeInfo(w, h, (h - elapsed).coerceAtLeast(0.0), w.workout.end.plusSeconds((h * 3600).toLong()))
        }
        val fatigue = if (load.ctl > 0 || load.atl > 0) Regeneration.fatigueIndex(load.atl, load.ctl) else null
        val (fLabel, fTone) = fatigue?.let { Regeneration.fatigueLabel(it) } ?: ("keine Daten" to Tone.NEUTRAL)
        val strainRel = if (load.ctl > 0) load.atl / max(load.ctl, 15.0) else null
        val rec7 = Stats.mean(days.takeLast(7).mapNotNull { it.recovery?.value?.toDouble() })
        val recoveryRel = rec7?.let { it / 65.0 }
        val balance = if (strainRel != null && recoveryRel != null && recoveryRel > 0) strainRel / recoveryRel else null
        val (bLabel, bTone) = balance?.let { Regeneration.balanceLabel(it) } ?: ("keine Daten" to Tone.NEUTRAL)

        // Überlastungsindikator
        val signals = mutableListOf<String>()
        val checks = mutableListOf<Pair<String, Boolean>>()
        fun check(name: String, active: Boolean) { checks += name to active }
        load.acwr?.let { if (it > 1.5) signals += "Belastung sprunghaft gestiegen (ACWR ${Fmt.num(it, 2)})" else if (it > 1.3) signals += "Belastung deutlich gesteigert (ACWR ${Fmt.num(it, 2)})" }
        load.monotony?.let { if (it > 2.0) signals += "Sehr gleichförmiges Training (Monotonie ${Fmt.num(it, 1)})" }
        val hb = t?.lnHrvBaseline
        val hrv7 = days.takeLast(7).mapNotNull { it.hrv?.let(::ln) }
        if (hb != null && hb.n >= 14 && hrv7.size >= 4 && hrv7.average() < hb.mean - 0.75 * hb.sd) signals += "HRV seit Tagen unter Normalbereich"
        val rb = t?.rhrBaseline
        val rhr7 = Stats.median(days.takeLast(7).mapNotNull { it.restingHr })
        if (rb != null && rhr7 != null && rhr7 - rb.mean >= 3.0) signals += "Ruhepuls seit Tagen ${Fmt.num(rhr7 - rb.mean, 1)} bpm erhöht"
        if (rec7 != null && rec7 < 50) signals += "Erholung im 7-Tage-Schnitt niedrig (${Fmt.int(rec7)})"
        t?.sleepDebtH?.let { if (it > 5) signals += "Schlafdefizit ${Fmt.num(it, 1)} h in 7 Tagen" }
        if (fatigue != null && fatigue > 75) signals += "Hoher Ermüdungsindex ($fatigue)"
        val r3 = days.takeLast(4).mapNotNull { it.readiness?.value }
        if (r3.size == 4 && r3.zipWithNext().all { (a, b) -> b < a } && r3.first() - r3.last() >= 10) signals += "Trainingsbereitschaft sinkt seit 3 Tagen"
        check("Belastungssprung", (load.acwr ?: 0.0) > 1.3)
        check("Monotonie", (load.monotony ?: 0.0) > 2.0)
        check("HRV-Trend", signals.any { it.startsWith("HRV") })
        check("Ruhepuls-Trend", signals.any { it.startsWith("Ruhepuls") })
        check("Erholung 7 Tage", signals.any { it.startsWith("Erholung") })
        check("Schlafdefizit", signals.any { it.startsWith("Schlafdefizit") })
        check("Ermüdung", signals.any { it.startsWith("Hoher Ermüdungsindex") })
        check("Bereitschaft sinkt", signals.any { it.startsWith("Trainingsbereitschaft sinkt") })
        val level = when {
            signals.size >= 3 -> 2
            signals.size == 2 -> 1
            else -> 0
        }
        val (oLabel, oTone) = when (level) {
            2 -> "Deutliche Hinweise auf Überlastung" to Tone.BAD
            1 -> "Erste Hinweise auf Überlastung" to Tone.WARN
            else -> (if (signals.isEmpty()) "Keine Hinweise auf Überlastung" else "Kaum Hinweise auf Überlastung") to Tone.GOOD
        }
        val hrv30 = Stats.mean(days.takeLast(30).mapNotNull { it.hrv })
        val hrv7m = Stats.mean(days.takeLast(7).mapNotNull { it.hrv })
        val rhrNow = t?.restingHr
        return RegenerationReport(
            regenerationIndex = if (infos.isEmpty() && auto == null) null else index.coerceIn(0, 100),
            timeProgress = timeProgress,
            remainingHours = remaining,
            readyAt = if (remaining > 0) now.plusSeconds((remaining * 3600).toLong()) else null,
            binding = binding,
            recent = recent,
            fatigueIndex = fatigue,
            fatigueLabel = fLabel,
            fatigueTone = fTone,
            strainRel = strainRel,
            recoveryRel = recoveryRel,
            balance = balance,
            balanceLabel = bLabel,
            balanceTone = bTone,
            load7 = days.takeLast(7).sumOf { it.load },
            load28 = days.takeLast(28).sumOf { it.load },
            load90 = days.takeLast(90).sumOf { it.load },
            overloadSignals = signals,
            overloadLevel = level,
            overloadLabel = oLabel,
            overloadTone = oTone,
            hrvTrendPct = if (hrv7m != null && hrv30 != null && hrv30 > 0) (hrv7m / hrv30 - 1) * 100 else null,
            rhrDeviation = if (rhrNow != null && rb != null) rhrNow - rb.mean else null,
            rhrDeviationZ = if (rhrNow != null && rb != null) (rhrNow - rb.mean) / max(rb.sd, 1.0) else null,
            overloadChecks = checks,
        )
    }

    private fun weekly(days: List<DayMetrics>, weeks: Int, pick: (DayMetrics) -> Double?): List<Pair<LocalDate, Double>> =
        days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
            .mapNotNull { (wk, list) -> list.mapNotNull(pick).takeIf { it.size >= 2 }?.let { wk to it.average() } }
            .takeLast(weeks)

    private fun heartReport(
        days: List<DayMetrics>,
        workouts: List<WorkoutAnalysis>,
        hrr: Map<Instant, Triple<Int, Int, Boolean>>,
        lastNight: SleepNight?,
        hr: List<HeartRateSample>,
        zone: ZoneId,
        today: LocalDate,
        load: LoadReport,
    ): HeartReport {
        val t = days.lastOrNull()
        val complete = days.dropLast(1)
        fun meanOf(list: List<DayMetrics>, f: (DayMetrics) -> Double?) = Stats.mean(list.mapNotNull(f))
        val hrrList = workouts.filter { hrr.containsKey(it.workout.start) && ChronoUnit.DAYS.between(it.workout.start.atZone(zone).toLocalDate(), today) <= 120 }
            .sortedByDescending { it.workout.start }
            .map { w ->
                val (peak, drop, after) = hrr.getValue(w.workout.start)
                HrrResult(w.workout.start.atZone(zone).toLocalDate(), w.workout.title ?: w.workout.type.label, peak, drop, after)
            }
        val hrrTrend = if (hrrList.size >= 6) {
            (Stats.median(hrrList.take(3).map { it.drop60.toDouble() }) ?: 0.0) - (Stats.median(hrrList.drop(3).take(3).map { it.drop60.toDouble() }) ?: 0.0)
        } else {
            null
        }
        val ln7 = days.takeLast(7).mapNotNull { it.hrv?.let(::ln) }
        val cv = if (ln7.size >= 5) (Stats.sd(ln7) ?: 0.0) / ln7.average() * 100 else null
        val yearAgo = days.filter { ChronoUnit.DAYS.between(it.date, today) in 335..365 }.mapNotNull { it.restingHr }
        return HeartReport(
            avgHrToday = t?.avgHr,
            avgHr7 = meanOf(complete.takeLast(7)) { it.avgHr },
            avgHr30 = meanOf(complete.takeLast(30)) { it.avgHr },
            sleepAvgHr = t?.sleepAvgHr,
            dipPct = t?.hrDipPct,
            dipAvg7 = meanOf(days.takeLast(7)) { it.hrDipPct },
            lowestHrPos = t?.lowestHrPos,
            nightCurve = lastNight?.let { HeartExtras.curve(hr.sliceByTime(it.start, it.end) { s -> s.time }, 5) }.orEmpty(),
            rhrSd14 = Stats.sd(days.takeLast(14).mapNotNull { it.restingHr })?.takeIf { days.takeLast(14).count { it.restingHr != null } >= 7 },
            hrvCv7 = cv,
            hrv7 = meanOf(days.takeLast(7)) { it.hrv },
            hrv30 = meanOf(days.takeLast(30)) { it.hrv },
            hrv90 = meanOf(days.takeLast(90)) { it.hrv },
            hrv180 = meanOf(days.takeLast(180)) { it.hrv },
            hrvWeekly = weekly(days.takeLast(182), 26) { it.hrv },
            rhr7 = meanOf(days.takeLast(7)) { it.restingHr },
            rhr30 = meanOf(days.takeLast(30)) { it.restingHr },
            rhr90 = meanOf(days.takeLast(90)) { it.restingHr },
            rhr365 = if (yearAgo.size >= 7) yearAgo.average() else null,
            rhrWeekly = weekly(days.takeLast(365), 52) { it.restingHr },
            hrr = hrrList,
            hrrTrend = hrrTrend,
            trainingHighHrPct28 = load.hardPct,
        )
    }

    private fun stressReport(
        days: List<DayMetrics>,
        hourSum: DoubleArray,
        hourCount: IntArray,
        rest: Int,
        high: Int,
        total: Int,
        restRef: Double,
        hrMax: Int,
        reliable: Boolean,
    ): StressReport {
        val t = days.lastOrNull()
        val today = t?.stressIndex
        val avg7 = Stats.mean(days.takeLast(7).mapNotNull { it.stressIndex })
        val ref = avg7 ?: today
        val (label, tone) = ref?.let { HeartExtras.stressLabel(it) } ?: ("keine Daten" to Tone.NEUTRAL)
        val nightHr = t?.sleepAvgHr
        return StressReport(
            today = today,
            avg7 = avg7,
            avg30 = Stats.mean(days.takeLast(30).mapNotNull { it.stressIndex }),
            label = label,
            tone = tone,
            byHour = (0 until 24).map { h -> if (hourCount[h] >= 3) hourSum[h] / hourCount[h] else null },
            restPct = if (total >= 20) rest.toDouble() / total else null,
            highPct = if (total >= 20) high.toDouble() / total else null,
            nightStress = nightHr?.let { HeartExtras.stressValue(it.roundToInt(), restRef, hrMax) },
            daily = days.takeLast(30).map { it.date to it.stressIndex },
            reliable = reliable,
        )
    }

    private fun performanceReport(
        days: List<DayMetrics>,
        workouts: List<WorkoutAnalysis>,
        best20: Map<Instant, Double>,
        fitness: FitnessReport,
        zone: ZoneId,
        today: LocalDate,
        profile: UserProfile,
    ): PerformanceReport {
        fun age(w: WorkoutAnalysis) = ChronoUnit.DAYS.between(w.workout.start.atZone(zone).toLocalDate(), today)
        val hrMax = fitness.hrMax
        val rest = fitness.zones.firstOrNull()?.let { z -> (z.lowBpm - 0.5 * hrMax) / 0.5 } ?: 60.0
        val runs = workouts.filter { it.workout.type == SportType.RUNNING && it.workout.distanceMeters != null && it.durationMin >= 20 && age(it) <= 90 }
            .sortedBy { it.workout.start }
        val steady = runs.filter { w ->
            val avg = w.avgHr ?: return@filter false
            val hrr = (avg - rest) / (hrMax - rest)
            hrr in 0.5..0.85
        }
        fun ef(w: WorkoutAnalysis) = (w.workout.distanceMeters!! / w.durationMin) / w.avgHr!!
        val efRecent = Stats.median(steady.filter { age(it) <= 27 }.map(::ef))
        val efPrev = Stats.median(steady.filter { age(it) in 28..55 }.map(::ef))
        val z2 = fitness.zones.firstOrNull { it.number == 2 }
        val z2Hr = z2?.let { (it.lowBpm + it.highBpm) / 2 }
        val lthrCand = workouts.filter { best20.containsKey(it.workout.start) && age(it) <= 90 }
            .maxByOrNull { best20.getValue(it.workout.start) }
        val lthr = lthrCand?.let { best20.getValue(it.workout.start) }?.takeIf { it >= 0.85 * hrMax }
        val recent28 = workouts.filter { age(it) in 0..27 }
        val rides = workouts.filter { it.workout.type == SportType.CYCLING && age(it) <= 90 }
        val power = Stats.mean(rides.mapNotNull { it.workout.avgPowerW }.filter { it > 20 })
        val weight = fitness.weightKg ?: profile.weightKg
        val speed = Stats.mean(rides.mapNotNull { w -> w.workout.distanceMeters?.let { it / 1000.0 / (w.durationMin / 60.0) } }.filter { it in 8.0..60.0 })
        val hist = fitness.vo2History.sortedBy { it.first }
        val vo2Change = if (hist.size >= 2) {
            val latest = hist.last()
            val old = hist.lastOrNull { ChronoUnit.DAYS.between(it.first, latest.first) >= 60 }
            old?.let { latest.second - it.second }
        } else {
            null
        }
        return PerformanceReport(
            efRecent = efRecent,
            efPrevious = efPrev,
            efChangePct = if (efRecent != null && efPrev != null) (efRecent / efPrev - 1) * 100 else null,
            paceAtZ2SecKm = fitness.speedPerHrr?.let { 60000.0 / (it * 0.65) }
                ?: if (efRecent != null && z2Hr != null) 60000.0 / (efRecent * z2Hr) else null,
            z2Hr = z2Hr,
            runPaces = runs.mapNotNull { w -> w.paceSecPerKm?.let { w.workout.start.atZone(zone).toLocalDate() to it } },
            efSeries = steady.map { it.workout.start.atZone(zone).toLocalDate() to ef(it) },
            lthr = lthr?.roundToInt(),
            lthrDate = if (lthr != null) lthrCand?.workout?.start?.atZone(zone)?.toLocalDate() else null,
            hardMin28 = recent28.sumOf { it.zoneMin.getOrElse(4) { 0.0 } + it.zoneMin.getOrElse(5) { 0.0 } },
            peakHr90 = workouts.filter { age(it) <= 90 }.mapNotNull { it.maxHr }.maxOrNull(),
            hardSessions28 = recent28.count { it.focus.contains("hart") },
            cyclingPowerAvg = power,
            cyclingWkg = if (power != null && weight != null) power / weight else null,
            cyclingSpeedKmh = speed,
            load7 = days.takeLast(7).sumOf { it.load },
            load28 = days.takeLast(28).sumOf { it.load },
            load90 = days.takeLast(90).sumOf { it.load },
            vo2Change90 = vo2Change,
            runCount90 = runs.size,
        )
    }

    private fun warnings(days: List<DayMetrics>, load: LoadReport, sleep: SleepReport, fitness: FitnessReport): List<HealthWarning> {
        val out = mutableListOf<HealthWarning>()
        val t = days.lastOrNull() ?: return out
        val tRhr = t.restingHr
        val tBase = t.rhrBaseline
        if (t.illnessFlag && tRhr != null && tBase != null) {
            out += HealthWarning(
                "Frühwarnung: Körper unter Stress",
                "Dein Ruhepuls liegt ${Fmt.int(tRhr - tBase.mean)} Schläge über deinem Normalwert und weitere Werte (HRV/Atmung) sind auffällig. " +
                    "Dieses Muster tritt häufig 1–2 Tage vor einer Erkältung oder bei starker Überlastung auf. Schone dich heute.",
                Tone.BAD,
            )
        }
        t.spo2?.let { if (it < 92.0) out += HealthWarning(
            "Niedrige Sauerstoffsättigung",
            "Deine nächtliche Sauerstoffsättigung lag im Schnitt bei ${Fmt.num(it, 1)} %. Normal sind 95–100 %. Einzelne Messfehler sind bei Uhren möglich – " +
                "wiederholt sich das, sprich bitte mit einer Ärztin oder einem Arzt (z. B. wegen Schlafapnoe).",
            Tone.WARN,
        ) }
        sleep.debt7H?.let { if (it >= 5.0) out += HealthWarning(
            "Schlafdefizit",
            "In den letzten 7 Nächten fehlen dir rund ${Fmt.num(it, 1)} h Schlaf gegenüber deinem Ziel. Das bremst Erholung, Leistung und Immunsystem.",
            Tone.WARN,
        ) }
        if (load.status == LoadStatus.OVERREACHING) out += HealthWarning(
            "Belastung zu schnell gesteigert",
            "Deine akute Belastung ist ${Fmt.num(load.acwr ?: 0.0, 2)}-mal so hoch wie dein gewohntes Niveau. Ab 1,5 steigt laut Studien das Verletzungsrisiko deutlich.",
            Tone.BAD,
        )
        val base = t.lnHrvBaseline
        val hrv7 = days.takeLast(7).mapNotNull { it.hrv?.let(::ln) }
        if (base != null && base.n >= 14 && hrv7.size >= 5 && hrv7.average() < base.mean - 0.75 * base.sd) {
            out += HealthWarning(
                "HRV-Trend sinkt",
                "Deine HRV liegt seit einigen Tagen unter deinem Normalbereich. Das spricht für anhaltenden Stress oder nicht ausreichende Erholung.",
                Tone.WARN,
            )
        }
        val rb = fitness.restingHrBaseline
        val rn = fitness.restingHrNow
        if (rb != null && rn != null && days.count { it.restingHr != null } >= 21 && rn - rb >= 3.0 && !t.illnessFlag) {
            out += HealthWarning(
                "Ruhepuls seit Tagen erhöht",
                "Dein Ruhepuls liegt in den letzten 7 Tagen ${Fmt.int(rn - rb)} Schläge über deinem 30-Tage-Schnitt. Mögliche Ursachen: Trainingsbelastung, Stress, Alkohol, Hitze oder ein Infekt.",
                Tone.WARN,
            )
        }
        load.monotony?.let { if (it > 2.0 && load.weekLoad > 150) out += HealthWarning(
            "Sehr gleichförmiges Training",
            "Deine Trainingsmonotonie liegt bei ${Fmt.num(it, 1)} (über 2,0 gilt als ungünstig). Wechsel harte und lockere Tage bewusster ab.",
            Tone.WARN,
        ) }
        return out
    }
}
