package de.vitalcoach.core.ai

import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.Sex

/**
 * Baut eine kompakte, anonyme Text-Zusammenfassung aller berechneten Werte für den KI-Coach.
 * Es werden keine Namen, Orte oder Rohdaten übertragen – nur Kennzahlen.
 */
object AiReport {

    fun build(r: AnalysisResult): String = buildString {
        val p = r.profile
        val f = r.fitness
        val t = r.todayMetrics
        val zone = r.zone
        appendLine("# Profil")
        appendLine("Alter: ${f.age ?: "unbekannt"}, Geschlecht: ${when (p.sex) { Sex.MALE -> "männlich"; Sex.FEMALE -> "weiblich"; null -> "unbekannt" }}")
        f.heightCm?.let { appendLine("Größe: ${Fmt.int(it)} cm") }
        f.weightKg?.let { appendLine("Gewicht: ${Fmt.num(it, 1)} kg" + (f.bmi?.let { b -> ", BMI ${Fmt.num(b, 1)}" } ?: "") + (f.bodyFatPct?.let { b -> ", Körperfett ${Fmt.num(b, 1)} %" } ?: "")) }
        appendLine("Schlafziel: ${Fmt.hours(p.sleepGoalHours)}, Schrittziel: ${p.stepGoal}")
        appendLine("Datentage: ${r.quality.daysWithData}; HRV verfügbar: ${ja(r.quality.hasHrv)}; Schlafphasen: ${ja(r.quality.hasSleepStages)}; SpO2: ${ja(r.quality.hasSpo2)}")
        if (r.isDemo) appendLine("ACHTUNG: Es handelt sich um Demo-Daten.")
        appendLine()

        appendLine("# Heute (${Fmt.day(r.today)})")
        t?.recovery?.let { s ->
            appendLine("Erholung: ${s.value}/100 (Verlässlichkeit ${s.confidence.label})")
            s.components.forEach { appendLine("  - ${it.name}: ${it.score} Punkte – ${it.detail}") }
        } ?: appendLine("Erholung: keine Daten")
        t?.readiness?.let { s ->
            appendLine("Trainingsbereitschaft: ${s.value}/100")
            s.components.forEach { appendLine("  - ${it.name}: ${it.score} Punkte – ${it.detail}") }
        }
        r.load.recommendation?.let { appendLine("App-Empfehlung: ${it.title} (Zielbelastung ${it.targetLoadLow}–${it.targetLoadHigh} TRIMP)") }
        r.sleep.lastNight?.let { n ->
            append("Letzte Nacht: ${Fmt.duration(n.asleepMin)} Schlaf, Score ${n.score}, ${Fmt.clock(n.bedtimeMin)}–${Fmt.clock(n.wakeMin)}")
            n.efficiency?.let { append(", Effizienz ${Fmt.pct(it)}") }
            if (n.hasStages) append(", Tief ${Fmt.duration(n.deepMin)}, REM ${Fmt.duration(n.remMin)}, Leicht ${Fmt.duration(n.lightMin)}, Wach ${Fmt.duration(n.awakeMin)}")
            appendLine()
        }
        if (r.warnings.isNotEmpty()) {
            appendLine("Warnhinweise der App:")
            r.warnings.forEach { appendLine("  - ${it.title}: ${it.text}") }
        }
        appendLine()

        appendLine("# Verlauf der letzten 14 Tage")
        appendLine("Datum | Ruhepuls | HRV ms | Atemfr. | SpO2 | Schlaf | Schlaf-Score | TRIMP | Schritte | Erholung | Bereitschaft")
        r.days.takeLast(14).forEach { d ->
            appendLine(
                listOf(
                    Fmt.dayShort(d.date),
                    d.restingHr?.let { Fmt.num(it, 1) } ?: "–",
                    d.hrv?.let { Fmt.int(it) } ?: "–",
                    d.respRate?.let { Fmt.num(it, 1) } ?: "–",
                    d.spo2?.let { Fmt.num(it, 1) } ?: "–",
                    d.sleep?.let { Fmt.duration(it.asleepMin) } ?: "–",
                    d.sleep?.score?.toString() ?: "–",
                    Fmt.int(d.load),
                    d.steps?.toString() ?: "–",
                    d.recovery?.value?.toString() ?: "–",
                    d.readiness?.value?.toString() ?: "–",
                ).joinToString(" | "),
            )
        }
        appendLine()

        appendLine("# Schlaf (Durchschnitt)")
        val s = r.sleep
        s.avgAsleep7Min?.let { appendLine("Ø Schlaf 7 Tage: ${Fmt.duration(it)}; 30 Tage: ${s.avgAsleep30Min?.let(Fmt::duration) ?: "–"}") }
        s.debt7H?.let { appendLine("Schlafschuld 7 Tage: ${Fmt.num(it, 1)} h") }
        s.regularitySdMin?.let { appendLine("Regelmäßigkeit (SD Schlafmitte): ±${Fmt.int(it)} min") }
        if (s.avgBedtimeMin != null && s.avgWakeMin != null) appendLine("Ø Zubettgehen ${Fmt.clock(s.avgBedtimeMin)}, Ø Aufstehen ${Fmt.clock(s.avgWakeMin)}")
        s.avgDeepPct?.let { appendLine("Ø Tiefschlaf ${Fmt.pct(it)}, Ø REM ${s.avgRemPct?.let(Fmt::pct) ?: "–"}, Ø Effizienz ${s.avgEfficiency?.let(Fmt::pct) ?: "–"}") }
        appendLine()

        appendLine("# Training & Belastung (Banister-Modell, TRIMP)")
        val l = r.load
        appendLine("Status: ${l.status.label}; Fitness CTL ${Fmt.int(l.ctl)}, Ermüdung ATL ${Fmt.int(l.atl)}, Form TSB ${Fmt.signed(l.tsb)}, ACWR ${l.acwr?.let { Fmt.num(it, 2) } ?: "–"}")
        appendLine("Wochenbelastung ${Fmt.int(l.weekLoad)} TRIMP (Vorwoche ${Fmt.int(l.prevWeekLoad)}), Einheiten 7 Tage: ${l.workouts7}, Monotonie ${l.monotony?.let { Fmt.num(it, 1) } ?: "–"}")
        if (l.easyPct != null) appendLine("Intensitätsverteilung 28 Tage: locker ${Fmt.pct(l.easyPct)}, mittel ${Fmt.pct(l.moderatePct ?: 0.0)}, hart ${Fmt.pct(l.hardPct ?: 0.0)}")
        appendLine("WHO-Aktivminuten 7 Tage: ${Fmt.int(l.whoMinutes7)} (Ziel 150)")
        appendLine("Letzte Trainings:")
        r.workouts.take(8).forEach { w ->
            appendLine(
                "  - ${Fmt.dayShort(w.workout.start.atZone(zone).toLocalDate())} ${w.workout.type.label}" +
                    (w.workout.title?.let { " „$it“" } ?: "") +
                    ": ${Fmt.duration(w.durationMin)}, TRIMP ${Fmt.int(w.trimp)}${if (w.estimated) " (geschätzt)" else ""}" +
                    (w.avgHr?.let { ", Ø Puls ${Fmt.int(it)}" } ?: "") +
                    (w.workout.distanceMeters?.let { ", ${Fmt.num(it / 1000, 1)} km" } ?: "") +
                    ", ${w.focus}",
            )
        }
        appendLine()

        appendLine("# Fitness & Herz")
        appendLine("VO2max: ${f.vo2max?.let { Fmt.num(it, 1) } ?: "–"} (${f.vo2Method?.label ?: "–"}), Kategorie ${f.vo2CategoryLabel ?: "–"}, Fitnessalter ${f.fitnessAge ?: "–"}")
        appendLine("HFmax: ${f.hrMax} (${f.hrMaxSource.label}); Ruhepuls 7 Tage ${f.restingHrNow?.let { Fmt.num(it, 1) } ?: "–"}, 30-Tage-Schnitt ${f.restingHrBaseline?.let { Fmt.num(it, 1) } ?: "–"}, Trend ${f.restingHrTrend30d?.let { Fmt.signed(it, 1) + " bpm/Monat" } ?: "–"}")
        appendLine("HRV 7 Tage ${f.hrvNow?.let { Fmt.int(it) } ?: "–"} ms, Normalbereich ${f.hrvNormalLow?.let { Fmt.int(it) } ?: "–"}–${f.hrvNormalHigh?.let { Fmt.int(it) } ?: "–"} ms")
        appendLine("SpO2 7 Tage ${f.spo2Avg7?.let { Fmt.num(it, 1) } ?: "–"} %, Atemfrequenz ${f.respAvg7?.let { Fmt.num(it, 1) } ?: "–"} /min")
        val a = r.activity
        appendLine("Schritte: heute ${a.stepsToday ?: "–"}, Ø 7 Tage ${a.steps7Avg?.let { Fmt.int(it) } ?: "–"}; aktive kcal Ø ${a.activeKcal7Avg?.let { Fmt.int(it) } ?: "–"}")
        appendLine("Inaktive Stunden Ø ${a.inactiveHours7Avg?.let { Fmt.num(it, 1) } ?: "–"}, Bewegungsindex ${a.movementIndex?.value ?: "–"}, Alltagsbelastung Ø ${a.everydayLoad7Avg?.let { Fmt.int(it) } ?: "–"} TRIMP/Tag")
        appendLine()

        val g = r.regeneration
        appendLine("# Regeneration")
        appendLine("Regenerationsindex ${g.regenerationIndex ?: "–"} %, Rest-Erholung ${Fmt.int(g.remainingHours)} h, Ermüdungsindex ${g.fatigueIndex ?: "–"} (${g.fatigueLabel}), Belastungs-/Erholungsverhältnis ${g.balance?.let { Fmt.num(it, 2) } ?: "–"} (${g.balanceLabel})")
        appendLine("Belastung 7/28/90 Tage: ${Fmt.int(g.load7)}/${Fmt.int(g.load28)}/${Fmt.int(g.load90)} TRIMP; Überlastung: ${g.overloadLabel}" + if (g.overloadSignals.isNotEmpty()) " (${g.overloadSignals.joinToString("; ")})" else "")
        r.energy?.let { e ->
            appendLine(
                "Energie (Schätzung, wie Body Battery): morgens ${e.morning}, jetzt ${e.now} (${e.label}); Verbrauch Training ${Fmt.int(e.drainTraining)}, " +
                    "Stress ${Fmt.int(e.drainStress)}, Alltag ${Fmt.int(e.drainEveryday)}, Wachzeit ${Fmt.int(e.drainAwake)}, Aufladung ${Fmt.int(e.charge)}; " +
                    "Trainingsbereitschaft jetzt ${e.readinessNow ?: "–"} (morgens ${e.readinessMorning ?: "–"})",
            )
        }
        appendLine()

        val h = r.heart
        appendLine("# Herz & Nervensystem")
        appendLine("Ø Puls 24 h: ${h.avgHr7?.let { Fmt.int(it) } ?: "–"}; Schlafpuls ${h.sleepAvgHr?.let { Fmt.int(it) } ?: "–"}; nächtliche Absenkung ${h.dipPct?.let { Fmt.int(it) + " %" } ?: "–"}; Ruhepuls-Stabilität ±${h.rhrSd14?.let { Fmt.num(it, 1) } ?: "–"} bpm")
        appendLine("HRV Ø 7/30/90/180 Tage: ${h.hrv7?.let { Fmt.int(it) } ?: "–"}/${h.hrv30?.let { Fmt.int(it) } ?: "–"}/${h.hrv90?.let { Fmt.int(it) } ?: "–"}/${h.hrv180?.let { Fmt.int(it) } ?: "–"} ms; Ruhepuls vor 1 Jahr ${h.rhr365?.let { Fmt.int(it) } ?: "–"}")
        h.hrr.firstOrNull()?.let { appendLine("Herzfrequenzerholung: ${it.drop60} Schläge/60 s (${if (it.afterExercise) "nach Trainingsende" else "nach Belastungsspitze"}, ${it.title})") }
        val st = r.stress
        appendLine("Stress-Index (Schätzung aus Puls ohne Bewegung): heute ${st.today?.let { Fmt.int(it) } ?: "–"}, Ø 7 Tage ${st.avg7?.let { Fmt.int(it) } ?: "–"} (${st.label})")
        appendLine()

        val pf = r.performance
        appendLine("# Leistungsentwicklung")
        appendLine("Aerobe Effizienz-Änderung (4 Wochen): ${pf.efChangePct?.let { Fmt.signed(it, 1) + " %" } ?: "–"}; Tempo in Zone 2: ${pf.paceAtZ2SecKm?.let { Fmt.pace(it) } ?: "–"}; Laktatschwelle (Schätzung) ${pf.lthr ?: "–"} bpm; Zone-4–5-Minuten 28 Tage ${Fmt.int(pf.hardMin28)}")
        appendLine()

        val hh = r.heartHealth
        appendLine("# Herzgesundheit (angelehnt an AHA Life's Essential 8)")
        appendLine("Gesamt ${hh.score ?: "–"} (${hh.label}); " + hh.components.joinToString("; ") { "${it.name} ${it.score ?: "–"} (${it.value})" })
        r.forecast?.let { fc ->
            appendLine()
            appendLine("# Prognose Trainingsbereitschaft (Erwartung, 80-%-Bereich)")
            fc.scenarios.forEach { (sc, pts) -> appendLine("${sc.label}: " + pts.joinToString(", ") { "${Fmt.dayShort(it.date)} ${it.expected} (${it.low}–${it.high})" }) }
        }
        val unusual = r.baselines.filter { it.status != de.vitalcoach.core.BaselineStatus.NORMAL && it.status != de.vitalcoach.core.BaselineStatus.NO_DATA }
        if (unusual.isNotEmpty()) {
            appendLine()
            appendLine("# Abweichungen von den persönlichen Normalwerten")
            unusual.forEach { appendLine("${it.name}: ${it.current?.let { v -> Fmt.num(v, it.decimals) } ?: "–"} vs. Ø ${it.mean30?.let { v -> Fmt.num(v, it.decimals) } ?: "–"} (${it.status.label})") }
        }
        val sig = r.correlations.filter { it.significant }
        if (sig.isNotEmpty()) {
            appendLine()
            appendLine("# Erkannte persönliche Zusammenhänge")
            sig.forEach { appendLine("${it.title}: ${it.text}") }
        }
    }

    private fun ja(b: Boolean) = if (b) "ja" else "nein"
}
