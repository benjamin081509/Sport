package de.vitalcoach.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Herzstück der App: berechnet aus den Rohdaten alle Tageswerte, persönlichen Referenzwerte,
 * Scores und Berichte.
 *
 * Erholung (0–100): Abweichung von HRV, Ruhepuls und Atemfrequenz zu deinem persönlichen
 *   30-Tage-Schnitt (z-Werte, HRV logarithmiert – Plews et al. 2012, Buchheit 2014) plus Schlaf-Score.
 * Trainingsbereitschaft (0–100): Erholung + Belastungssteigerung (ACWR, Gabbett 2016; EWMA nach
 *   Williams et al. 2017) + Form (TSB, Banister-Modell) + Schlafschuld.
 * Belastung: Banister-TRIMP, Fitness (CTL, 42 Tage) und Ermüdung (ATL, 7 Tage) als exponentiell
 *   gewichtete Mittel, Monotonie & Strain nach Foster (1998).
 */
object Analyzer {

    private const val MAX_DAYS = 120L
    private val K_ATL = 1.0 - exp(-1.0 / 7.0)
    private val K_CTL = 1.0 - exp(-1.0 / 42.0)
    private const val L_ACUTE = 2.0 / (7.0 + 1.0)
    private const val L_CHRONIC = 2.0 / (28.0 + 1.0)

    fun analyze(
        data: HealthData,
        profile: UserProfile,
        today: LocalDate = LocalDate.now(data.zone),
    ): AnalysisResult {
        val zone = data.zone
        val hr = data.heartRate.sortedBy { it.time }
        val hrv = data.hrv.sortedBy { it.time }
        val resp = data.respiratoryRate.sortedBy { it.time }
        val spo2 = data.spo2.sortedBy { it.time }
        val rhrRecords = data.restingHr.sortedBy { it.time }
        val age = profile.age(today)
        val sex = profile.sex

        // ---------- Zeitraum ----------
        val earliestData = listOfNotNull(
            hr.firstOrNull()?.time?.atZone(zone)?.toLocalDate(),
            data.sleep.minOfOrNull { it.end }?.atZone(zone)?.toLocalDate(),
            data.workouts.minOfOrNull { it.start }?.atZone(zone)?.toLocalDate(),
            data.daily.filter { (it.steps ?: 0) > 0 }.minOfOrNull { it.date },
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
            }
            if (rhr != null) rhrByDate[d] = rhr

            val hrvVals = (
                night?.let { hrv.sliceByTime(it.start, it.end) { s -> s.time } }.orEmpty()
                    .ifEmpty { hrv.sliceByTime(ds, ds.plusSeconds(12 * 3600)) { it.time } }
                    .ifEmpty { hrv.sliceByTime(ds, de) { it.time } }
                ).map { it.rmssdMs }.filter { it in 3.0..300.0 }
            if (hrvVals.isNotEmpty()) hrvByDate[d] = hrvVals.average()

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
        val workouts = workoutsSorted.map { w ->
            HeartMath.analyzeWorkout(w, hr.sliceByTime(w.start, w.end) { it.time }, restRef, hrMax, sex)
        }
        val workoutsByDate = workouts.groupBy { it.workout.start.atZone(zone).toLocalDate() }
        val dailyByDate = data.daily.associateBy { it.date }

        // ---------- Belastungsmodell ----------
        val loads = dates.map { d -> workoutsByDate[d].orEmpty().sumOf { it.trimp } }
        val init = if (n > 0) loads.take(minOf(14, n)).average() else 0.0
        var atl = init
        var ctl = init
        var acute = init
        var chronic = init
        val atlArr = DoubleArray(n)
        val ctlArr = DoubleArray(n)
        val tsbArr = DoubleArray(n)
        val acwrArr = arrayOfNulls<Double>(n)
        for (i in 0 until n) {
            tsbArr[i] = ctl - atl
            atl += K_ATL * (loads[i] - atl)
            ctl += K_CTL * (loads[i] - ctl)
            acute = L_ACUTE * loads[i] + (1 - L_ACUTE) * acute
            chronic = L_CHRONIC * loads[i] + (1 - L_CHRONIC) * chronic
            atlArr[i] = atl
            ctlArr[i] = ctl
            acwrArr[i] = if (i >= 20 && chronic > 1.0) acute / chronic else null
        }

        // ---------- Tagesmetriken mit Referenzwerten ----------
        val days = ArrayList<DayMetrics>(n)
        var prevElevated = false
        for ((i, d) in dates.withIndex()) {
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

            val debt = sleepDebt(dates.subList(maxOf(0, i - 6), i + 1).mapNotNull { nightByDate[it] }, goal)
            val recovery = recoveryScore(rhr?.first, rhrB, dayHrv, lnHrvB, night, dayResp, respB)
            val readiness = readinessScore(
                recovery = recovery,
                acwr = if (i > 0) acwrArr[i - 1] else null,
                tsb = tsbArr[i],
                ctl = if (i > 0) ctlArr[i - 1] else init,
                historyDays = i,
                debt = debt,
                illness = illness,
            )
            val daily = dailyByDate[d]
            days += DayMetrics(
                date = d,
                sleep = night,
                restingHr = rhr?.first,
                restingHrSource = rhr?.second,
                hrv = dayHrv,
                respRate = dayResp,
                spo2 = spo2ByDate[d]?.first,
                spo2Min = spo2ByDate[d]?.second,
                steps = daily?.steps,
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
            )
        }

        val fitness = fitnessReport(data, profile, today, days, workouts, hrMax, hrMaxSource, restRef, age, zone)
        val load = loadReport(days, workouts, today, zone, sex)
        val sleep = sleepReport(days, nightByDate, today, goal)
        val activity = activityReport(days, profile)
        val quality = DataQuality(
            daysWithData = days.count { it.restingHr != null || it.sleep != null || (it.steps ?: 0) > 0 || it.load > 0 },
            hasHeartRate = hr.isNotEmpty(),
            hasHrv = hrv.isNotEmpty(),
            hasSleep = nightByDate.isNotEmpty(),
            hasSleepStages = nightByDate.values.any { it.hasStages },
            hasWorkouts = workouts.isNotEmpty(),
            hasSteps = data.daily.any { (it.steps ?: 0) > 0 },
            hasSpo2 = spo2.isNotEmpty(),
            hasRespiratoryRate = resp.isNotEmpty(),
            hasVo2Device = data.vo2max.isNotEmpty(),
            baselineDays = days.lastOrNull()?.rhrBaseline?.n ?: 0,
        )
        val warnings = warnings(days, load, sleep, fitness)
        return AnalysisResult(
            today = today,
            profile = profile,
            days = days,
            workouts = workouts.sortedByDescending { it.workout.start },
            fitness = fitness,
            load = load,
            sleep = sleep,
            activity = activity,
            warnings = warnings,
            quality = quality,
            isDemo = data.isDemo,
            generatedAt = data.fetchedAt,
            zone = zone,
        )
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

    private fun sub(z: Double): Int = (65.0 + 17.5 * z).clamp(0.0, 100.0).roundToInt()

    internal fun recoveryScore(
        rhr: Double?,
        rhrB: Baseline?,
        hrv: Double?,
        lnHrvB: Baseline?,
        sleep: SleepNight?,
        resp: Double?,
        respB: Baseline?,
    ): Score? {
        val hasHrv = hrv != null && lnHrvB != null
        val comps = mutableListOf<ScoreComponent>()
        if (hrv != null && lnHrvB != null) {
            val z = (ln(hrv) - lnHrvB.mean) / max(lnHrvB.sd, 0.08)
            val ref = exp(lnHrvB.mean)
            comps += ScoreComponent(
                "HRV",
                sub(z),
                0.40,
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
        if (sleep != null) {
            comps += ScoreComponent("Schlaf", sleep.score, if (hasHrv) 0.30 else 0.45, "Schlaf-Score ${sleep.score} (${Fmt.duration(sleep.asleepMin)})")
        }
        if (resp != null && respB != null) {
            val zUp = (resp - respB.mean) / max(respB.sd, 0.6)
            val s = (85.0 - 17.5 * max(0.0, zUp)).clamp(0.0, 100.0).roundToInt()
            comps += ScoreComponent(
                "Atemfrequenz",
                s,
                if (hasHrv) 0.05 else 0.10,
                "${Fmt.num(resp, 1)} /min – ${Fmt.signed(resp - respB.mean, 1)} zu deinem Schnitt",
            )
        }
        if (comps.isEmpty()) return null
        val wsum = comps.sumOf { it.weight }
        val value = (comps.sumOf { it.score * it.weight } / wsum).roundToInt().coerceIn(0, 100)
        val physio = listOfNotNull(rhrB?.takeIf { rhr != null }, lnHrvB?.takeIf { hrv != null })
        val minN = physio.minOfOrNull { it.n } ?: 0
        val confidence = when {
            hasHrv && rhr != null && rhrB != null && sleep != null && minN >= 14 -> Confidence.HIGH
            physio.isNotEmpty() && (sleep != null || physio.size == 2) -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return Score(value, comps, confidence)
    }

    internal fun acwrScore(acwr: Double): Int = when {
        acwr <= 1.0 -> 90.0
        acwr <= 1.3 -> 90.0 - (acwr - 1.0) / 0.3 * 15.0
        acwr <= 2.0 -> 75.0 - (acwr - 1.3) / 0.7 * 60.0
        else -> 10.0
    }.roundToInt()

    private fun readinessScore(
        recovery: Score?,
        acwr: Double?,
        tsb: Double,
        ctl: Double,
        historyDays: Int,
        debt: Double?,
        illness: Boolean,
    ): Score? {
        if (recovery == null) return null
        val comps = mutableListOf<ScoreComponent>()
        comps += ScoreComponent("Erholung", recovery.value, 0.5, "Erholungswert ${recovery.value} von 100")
        if (acwr != null) {
            comps += ScoreComponent("Belastungssteigerung", acwrScore(acwr), 0.2, "ACWR ${Fmt.num(acwr, 2)} (optimal 0,8–1,3)")
        }
        if (historyDays >= 14) {
            val rel = tsb / max(ctl, 15.0)
            val s = (80.0 + 100.0 * rel).clamp(10.0, 100.0).roundToInt()
            comps += ScoreComponent("Form", s, 0.2, "Form (TSB) ${Fmt.signed(tsb)} bei Fitness ${Fmt.int(ctl)}")
        }
        if (debt != null) {
            val s = (100.0 - debt * 12.0).clamp(0.0, 100.0).roundToInt()
            comps += ScoreComponent("Schlafschuld", s, 0.1, "${Fmt.num(debt, 1)} h Schlafdefizit in den letzten 7 Nächten")
        }
        val wsum = comps.sumOf { it.weight }
        var value = (comps.sumOf { it.score * it.weight } / wsum).roundToInt().coerceIn(0, 100)
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
        val runVo2 = workouts.filter { it.vo2Estimate != null }
            .filter { ChronoUnit.DAYS.between(it.workout.start.atZone(zone).toLocalDate(), today) <= 90 }
            .sortedBy { it.workout.start }
        val restNow = Stats.median(days.takeLast(14).mapNotNull { it.restingHr })
        val (vo2, method) = when {
            recentDevice.isNotEmpty() -> Stats.median(recentDevice.takeLast(3).map { it.value }) to Vo2Method.DEVICE
            runVo2.size >= 2 -> Stats.median(runVo2.takeLast(5).mapNotNull { it.vo2Estimate }) to Vo2Method.RUNNING
            restNow != null -> HeartMath.vo2FromRestingHr(hrMax, restNow) to Vo2Method.RESTING_HR
            else -> null to null
        }
        val history = when (method) {
            Vo2Method.DEVICE -> deviceVo2.map { it.time.atZone(zone).toLocalDate() to it.value }
            Vo2Method.RUNNING -> runVo2.map { it.workout.start.atZone(zone).toLocalDate() to it.vo2Estimate!! }
            else -> emptyList()
        }
        val catIdx = if (vo2 != null && age != null) FitnessMath.vo2Category(vo2, age, sex) else null

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
            zones = HeartMath.zones(restRef, hrMax),
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
        val monotony = if (sd7 != null && sd7 > 0.5 && mean7 > 0) mean7 / sd7 else null
        val weekLoad = loads7.sum()
        val recent28 = workouts.filter { ChronoUnit.DAYS.between(it.workout.start.atZone(zone).toLocalDate(), today) in 0..27 }
        val z = DoubleArray(6)
        recent28.forEach { w -> w.zoneMin.forEachIndexed { i, m -> z[i] += m } }
        val zTotal = z.sum()
        val acwr = t?.acwr
        val status = when {
            t == null || workouts.isEmpty() || acwr == null -> LoadStatus.NO_DATA
            acwr < 0.8 -> LoadStatus.DETRAINING
            acwr < 1.0 -> LoadStatus.MAINTAIN
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
        )
    }

    private fun recommend(t: DayMetrics?, sex: Sex?): TrainingRecommendation? {
        val r = t?.readiness?.value ?: return null
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
            r >= 67 -> rec(
                "Bereit für intensives Training",
                "Dein Körper ist bereit für eine fordernde Einheit: Intervalle, ein Tempolauf, schweres Krafttraining oder eine lange Ausdauereinheit.",
                Tone.GOOD, base * 1.2, base * 1.6,
            )
            r >= 50 -> rec(
                "Moderates Training",
                "Gut für eine normale Einheit, überwiegend in Zone 2–3. Sehr harte Intervalle besser auf einen frischeren Tag verschieben.",
                Tone.OK, base * 0.8, base * 1.2,
            )
            r >= 34 -> rec(
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

    private fun sleepReport(days: List<DayMetrics>, nights: Map<LocalDate, SleepNight>, today: LocalDate, goal: Double): SleepReport {
        val last = nights[today] ?: nights[today.minusDays(1)]
        val n7 = days.takeLast(7).mapNotNull { it.sleep }
        val n14 = days.takeLast(14).mapNotNull { it.sleep }
        val n30 = days.takeLast(30).mapNotNull { it.sleep }
        val staged = n14.filter { it.hasStages }
        return SleepReport(
            lastNight = last,
            avgAsleep7Min = Stats.mean(n7.map { it.asleepMin }),
            avgAsleep30Min = Stats.mean(n30.map { it.asleepMin }),
            debt7H = days.lastOrNull()?.sleepDebtH,
            regularitySdMin = if (n14.size >= 5) Stats.sd(n14.map { it.midpointMin }) else null,
            avgBedtimeMin = Stats.mean(n14.map { it.bedtimeMin }),
            avgWakeMin = Stats.mean(n14.map { it.wakeMin }),
            avgDeepPct = Stats.mean(staged.mapNotNull { it.deepPct }),
            avgRemPct = Stats.mean(staged.mapNotNull { it.remPct }),
            avgEfficiency = Stats.mean(n14.mapNotNull { it.efficiency }),
            avgScore7 = Stats.mean(n7.map { it.score.toDouble() }),
            nights = n30.size,
            goalHours = goal,
        )
    }

    private fun activityReport(days: List<DayMetrics>, profile: UserProfile): ActivityReport {
        val last7 = days.takeLast(7)
        val complete7 = days.dropLast(1).takeLast(7)
        return ActivityReport(
            stepsToday = days.lastOrNull()?.steps,
            stepsYesterday = days.dropLast(1).lastOrNull()?.steps,
            steps7Avg = Stats.mean(complete7.mapNotNull { it.steps?.toDouble() }),
            stepGoal = profile.stepGoal,
            daysGoalReached7 = last7.count { (it.steps ?: 0) >= profile.stepGoal },
            activeKcalToday = days.lastOrNull()?.activeKcal,
            activeKcal7Avg = Stats.mean(complete7.mapNotNull { it.activeKcal }),
            totalKcal7Avg = Stats.mean(complete7.mapNotNull { it.totalKcal }),
            whoMinutes7 = last7.sumOf { it.moderateMin + 2.0 * it.vigorousMin },
        )
    }

    private fun warnings(days: List<DayMetrics>, load: LoadReport, sleep: SleepReport, fitness: FitnessReport): List<HealthWarning> {
        val out = mutableListOf<HealthWarning>()
        val t = days.lastOrNull() ?: return out
        if (t.illnessFlag) {
            val diff = t.restingHr!! - t.rhrBaseline!!.mean
            out += HealthWarning(
                "Frühwarnung: Körper unter Stress",
                "Dein Ruhepuls liegt ${Fmt.int(diff)} Schläge über deinem Normalwert und weitere Werte (HRV/Atmung) sind auffällig. " +
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
