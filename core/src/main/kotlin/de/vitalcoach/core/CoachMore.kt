package de.vitalcoach.core

import java.time.Duration
import kotlin.math.roundToInt

private fun empty(area: String, text: String) = CoachText("$area: noch keine Daten", text, emptyList(), Tone.NEUTRAL)

/** Bewertung von Regeneration, Ermüdung und Überlastung. */
fun Coach.regeneration(r: AnalysisResult): CoachText {
    val g = r.regeneration
    val idx = g.regenerationIndex ?: return empty("Regeneration", "Sobald Trainings und Nächte mit Uhr vorliegen, wird deine Regeneration berechnet.")
    val text = buildString {
        append("Dein Regenerationsindex liegt bei $idx %. ")
        val b = g.binding
        if (b != null && g.remainingHours >= 1) {
            append("Nach „${b.workout.workout.title ?: b.workout.workout.type.label}“ (${Fmt.int(b.workout.trimp)} TRIMP) brauchst du noch ca. ${Fmt.int(g.remainingHours)} h, bis du wieder voll belastbar bist. ")
        } else {
            append("Von deinen letzten Trainings bist du rechnerisch vollständig erholt. ")
        }
        g.fatigueIndex?.let { append("Ermüdungsindex: $it (${g.fatigueLabel}). ") }
        g.balance?.let { append("Belastungs-/Erholungsverhältnis: ${Fmt.num(it, 2)} – ${g.balanceLabel}. ") }
        append("Trainingsbelastung: ${Fmt.int(g.load7)} TRIMP (7 Tage), ${Fmt.int(g.load28)} TRIMP (28 Tage). ")
        append(g.overloadLabel + ".")
    }
    val tips = mutableListOf<String>()
    if (g.overloadSignals.isNotEmpty()) tips += "Auffällig: " + g.overloadSignals.joinToString("; ") + "."
    when (g.overloadLevel) {
        2 -> tips += "Plane 2–3 Tage mit sehr lockerem Training oder Pause ein und achte besonders auf Schlaf (Ziel: ≥ ${Fmt.hours(r.profile.sleepGoalHours)})."
        1 -> tips += "Behalte die nächsten Tage im Blick: keine weitere Steigerung, lieber ein lockerer Tag mehr."
        else -> Unit
    }
    if (g.remainingHours >= 12) tips += "Harte Einheiten erst wieder, wenn die Rest-Erholung abgelaufen ist – locker bewegen (Zone 1–2) beschleunigt die Regeneration."
    g.fatigueIndex?.let { if (it >= 75) tips += "Deine kurzfristige Ermüdung ist deutlich höher als dein Trainingsniveau – reduziere die Umfänge diese Woche um ca. 30 %." }
    if (tips.isEmpty()) tips += "Gute Balance zwischen Belastung und Erholung – so kannst du dich nachhaltig steigern."
    val tone = when {
        g.overloadLevel == 2 -> Tone.BAD
        g.overloadLevel == 1 || idx < 50 -> Tone.WARN
        idx < 75 -> Tone.OK
        else -> Tone.GOOD
    }
    return CoachText("Regeneration $idx %", text, tips, tone)
}

/** Bewertung des Stress-Index (Schätzung aus Puls und Bewegung). */
fun Coach.stress(r: AnalysisResult): CoachText {
    val s = r.stress
    val v = s.avg7 ?: s.today ?: return empty(
        "Stress",
        "Für den Stress-Index braucht die App Pulsmessungen tagsüber und stündliche Schrittdaten. Aktiviere auf der Uhr die kontinuierliche Pulsmessung.",
    )
    val text = buildString {
        append("Dein Stress-Index liegt im 7-Tage-Schnitt bei ${Fmt.int(v)} von 100 (${s.label}). ")
        s.today?.let { append("Heute bisher: ${Fmt.int(it)}. ") }
        s.restPct?.let { append("${Fmt.pct(it)} deiner ruhigen Phasen waren entspannt, ${Fmt.pct(s.highPct ?: 0.0)} deutlich angespannt. ") }
        val peak = s.byHour.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }
        if (peak != null && (peak.value ?: 0.0) >= 35) append("Am höchsten ist dein Stress meist gegen ${peak.index}:00 Uhr. ")
        append("Der Wert ist eine Schätzung: Er misst, wie stark dein Puls in Phasen ohne Bewegung über deinem Ruhepuls liegt (z. B. durch Anspannung, Koffein, Hitze oder Krankheit).")
        if (!s.reliable) append(" Ohne stündliche Schrittdaten ist die Schätzung ungenauer.")
    }
    val tips = mutableListOf<String>()
    if (v >= 50) {
        tips += "Plane bewusst Pausen ein: 3–5 Minuten langsames Atmen (4 s ein, 6 s aus) senkt Puls und Anspannung messbar."
        tips += "Koffein nach 14 Uhr meiden und abends Bildschirmzeit reduzieren – beides verbessert die nächtliche Erholung."
    } else {
        tips += "Dein Stressniveau ist gut im Griff. Regelmäßige Bewegung und ausreichend Schlaf halten es niedrig."
    }
    return CoachText("Stress-Index ${Fmt.int(v)} – ${s.label}", text, tips, s.tone)
}

/** Bewertung von Herz & Kreislauf. */
fun Coach.heart(r: AnalysisResult): CoachText {
    val h = r.heart
    val f = r.fitness
    val text = buildString {
        h.avgHr7?.let { append("Deine durchschnittliche Herzfrequenz über 24 h liegt bei ${Fmt.int(it)} bpm. ") }
        f.restingHrNow?.let { append("Ruhepuls (7 Tage): ${Fmt.int(it)} bpm") }
        h.rhr365?.let { append(", vor einem Jahr ${Fmt.int(it)} bpm") }
        if (f.restingHrNow != null) append(". ")
        h.hrvTrendText()?.let { append(it) }
        h.dipPct?.let {
            append("Nachts sinkt dein Puls um ${Fmt.int(it)} % ")
            append(if (it >= 10) "(gesunde nächtliche Absenkung). " else "(abgeschwächte Absenkung – häufig nach spätem Essen, Alkohol, spätem Training oder bei Stress). ")
        }
        h.rhrSd14?.let { append("Stabilität des Ruhepulses: ±${Fmt.num(it, 1)} bpm (14 Tage). ") }
        h.hrr.firstOrNull()?.let { x ->
            append("Pulserholung: ${x.drop60} Schläge in 60 s ${if (x.afterExercise) "nach Trainingsende" else "nach einer Belastungsspitze"} (${x.title}). ")
        }
    }
    val tips = mutableListOf<String>()
    h.dipPct?.let { if (it < 10) tips += "Für eine bessere nächtliche Absenkung: letzte Mahlzeit 3 h vor dem Schlafen, kein Alkohol, harte Trainings nicht direkt vor dem Zubettgehen." }
    h.hrr.firstOrNull()?.let { if (it.afterExercise && it.drop60 < 12) tips += "Eine Pulserholung unter 12 Schlägen pro Minute nach Belastungsende ist auffällig. Beobachte den Wert und lass ihn bei wiederholt niedrigen Werten ärztlich abklären." }
    h.hrrTrend?.let { if (it >= 3) tips += "Deine Pulserholung verbessert sich – ein gutes Zeichen für wachsende Fitness." }
    tips += "Ausdauertraining in Zone 2 (3–4 × pro Woche) senkt langfristig den Ruhepuls und erhöht die HRV."
    return CoachText("Herz & Kreislauf", text.ifEmpty { "Noch keine Pulsdaten vorhanden." }, tips, Tone.NEUTRAL)
}

private fun HeartReport.hrvTrendText(): String? {
    val a = hrv7 ?: return null
    val b = hrv30 ?: return null
    val pct = (a / b - 1) * 100
    val dir = when {
        pct >= 5 -> "steigt (gutes Zeichen)"
        pct <= -5 -> "sinkt (Belastung/Stress prüfen)"
        else -> "ist stabil"
    }
    return "HRV-Trend: 7 Tage ${Fmt.int(a)} ms vs. 30 Tage ${Fmt.int(b)} ms – ${Fmt.signed(pct)} %, deine HRV $dir. "
}

/** Bewertung der Herzgesundheit. */
fun Coach.heartHealth(r: AnalysisResult): CoachText {
    val h = r.heartHealth
    val s = h.score ?: return empty("Herzgesundheit", "Es fehlen noch Daten. Trage Größe, Gewicht, Rauchstatus und Blutdruck im Profil ein.")
    val weakest = h.components.filter { it.score != null }.minByOrNull { it.score!! }
    val missing = h.components.filter { it.score == null }
    val text = buildString {
        append("Deine Herzgesundheit liegt bei $s von 100 (${h.label}). Bewertet wurden ${h.covered} von ${h.components.size} Bereichen. ")
        append("Die Bewertung orientiert sich am wissenschaftlichen „Life's Essential 8“-Modell der American Heart Association. ")
        if (weakest != null && (weakest.score ?: 100) < 80) append("Größtes Verbesserungspotenzial: ${weakest.name} (${weakest.value}). ")
    }
    val tips = mutableListOf<String>()
    if (weakest != null && (weakest.score ?: 100) < 80) tips += weakest.hint
    missing.forEach { tips += "${it.name} fehlt: ${it.hint}" }
    tips += "Die Samsung-„Gefäßbelastung“ kann von anderen Apps nicht gelesen werden – sie fließt daher nicht ein."
    return CoachText("Herzgesundheit $s", text, tips, h.tone)
}

/** Bewertung von Fitness & Leistungsentwicklung. */
fun Coach.performance(r: AnalysisResult): CoachText {
    val p = r.performance
    val f = r.fitness
    val text = buildString {
        f.vo2max?.let { append("VO2max: ${Fmt.num(it, 1)}") }
        p.vo2Change90?.let { append(" (${Fmt.signed(it, 1)} in den letzten Monaten)") }
        if (f.vo2max != null) append(". ")
        f.fitnessAge?.let { append("Fitnessalter: $it Jahre. ") }
        val chg = p.efChangePct
        if (chg != null) {
            append(
                when {
                    chg >= 2 -> "Deine aerobe Effizienz steigt: Bei gleichem Puls läufst du ${Fmt.num(chg, 1)} % schneller als vor 4 Wochen. "
                    chg <= -2 -> "Bei gleichem Puls läufst du ${Fmt.num(-chg, 1)} % langsamer als vor 4 Wochen – oft ein Zeichen von Ermüdung, Hitze oder weniger Training. "
                    else -> "Deine aerobe Effizienz ist im Vergleich zu vor 4 Wochen stabil. "
                },
            )
        } else if (p.efRecent != null) {
            append("Für den Vergleich der aeroben Effizienz braucht es lockere Läufe in zwei aufeinanderfolgenden 4-Wochen-Zeiträumen. ")
        }
        p.paceAtZ2SecKm?.let { append("Geschätztes Tempo bei ${p.z2Hr} bpm (Zone 2): ${Fmt.pace(it)}. ") }
        p.lthr?.let { append("Geschätzte Laktatschwelle: mindestens $it bpm (aus deiner härtesten 20-Minuten-Belastung). ") }
        append("Hochintensive Minuten (Zone 4–5, 28 Tage): ${Fmt.int(p.hardMin28)}. ")
        p.cyclingPowerAvg?.let { append("Ø Leistung beim Radfahren: ${Fmt.int(it)} W" + (p.cyclingWkg?.let { w -> " (${Fmt.num(w, 1)} W/kg)" } ?: "") + ". ") }
    }
    val tips = mutableListOf<String>()
    if (p.lthr == null) tips += "Für eine Schätzung deiner Laktatschwelle: einmal 20–30 Minuten gleichmäßig hart laufen oder radeln (so schnell, wie du das Tempo halten kannst)."
    if (p.hardMin28 < 20) tips += "Die anaerobe Leistungsfähigkeit lässt sich ohne Leistungs- oder Laktatdaten nicht direkt messen. Als Reiz dafür helfen kurze, harte Intervalle (z. B. 6 × 1 min)."
    if (p.cyclingPowerAvg == null) tips += "Leistungsdaten beim Radfahren (Watt) erscheinen, sobald ein Wattmesser mit Samsung Health verbunden ist."
    tips += "Lockere Läufe wirklich locker halten – so wird die Effizienz-Messung („schneller bei gleichem Puls“) am aussagekräftigsten."
    return CoachText("Fitness & Leistung", text.ifEmpty { "Zeichne Läufe mit GPS auf, damit die Leistungsentwicklung berechnet werden kann." }, tips, Tone.NEUTRAL)
}

/** Ergänzende Bewertung der Alltagsaktivität. */
fun Coach.dailyActivity(r: AnalysisResult): CoachText {
    val a = r.activity
    val text = buildString {
        a.movementIndex?.let { append("Bewegungsindex: ${it.value} von 100. ") }
        if (a.hasHourlySteps) {
            a.inactiveHours7Avg?.let { append("Inaktive Stunden (< 250 Schritte): Ø ${Fmt.num(it, 1)} pro Tag") }
            a.activeHours7Avg?.let { append(", aktive Stunden: Ø ${Fmt.num(it, 1)}. ") }
            val peak = a.stepsByHour.withIndex().maxByOrNull { it.value }
            if (peak != null && peak.value > 0) append("Am aktivsten bist du meist gegen ${peak.index}:00 Uhr. ")
        }
        a.everydayLoad7Avg?.let { append("Alltagsbelastung (außerhalb von Trainings): Ø ${Fmt.int(it)} TRIMP pro Tag") }
        a.everydayShare7?.let { append(" – ${Fmt.pct(it)} deiner Gesamtbelastung. ") }
        a.regularity?.let { append("Regelmäßigkeit der Bewegung: $it von 100. ") }
        val tw = a.thisWeek
        val lw = a.lastWeek
        if (tw != null && lw != null && lw.steps > 0) append("Wochenbilanz: ${Fmt.int(tw.steps)} Schritte (Vorwoche ${Fmt.int(lw.steps)}). ")
        a.hardSessionGapAvgH?.let { append("Zwischen harten Einheiten liegen im Schnitt ${Fmt.int(it)} h") }
        if (a.hardSessionGapAvgH != null) append(if (a.shortGaps28 > 0) " (${a.shortGaps28}× weniger als 48 h). " else ". ")
        a.runCadence?.let { append("Laufkadenz: ${Fmt.int(it)} Schritte/min. ") }
    }
    val tips = mutableListOf<String>()
    a.inactiveHours7Avg?.let { if (it >= 6) tips += "Lange Sitzphasen sind ein eigener Gesundheitsrisikofaktor. Stell dir einen Stundenwecker und geh jede Stunde 3–5 Minuten (≥ 250 Schritte)." }
    if (a.shortGaps28 >= 2) tips += "Mehrmals lagen weniger als 48 h zwischen harten Einheiten. Plane dazwischen lockere Tage ein."
    a.runCadence?.let { if (it < 160) tips += "Eine etwas höhere Laufkadenz (160–180 Schritte/min, kürzere Schritte) kann die Belastung für Knie und Hüfte senken." }
    if (tips.isEmpty()) tips += "Gute Verteilung der Bewegung über den Tag – weiter so!"
    return CoachText("Bewegung im Alltag", text.ifEmpty { "Noch zu wenige Aktivitätsdaten." }, tips, Tone.NEUTRAL)
}

/** Text zur Prognose. */
fun Coach.forecast(r: AnalysisResult): CoachText {
    val f = r.forecast ?: return empty("Prognose", "Die Prognose erscheint, sobald ein Erholungswert berechnet wurde.")
    val rest = f.scenarios[ForecastScenario.REST].orEmpty()
    val hard = f.scenarios[ForecastScenario.HARD].orEmpty()
    val normal = f.scenarios[ForecastScenario.NORMAL].orEmpty()
    val text = buildString {
        if (normal.isNotEmpty()) append("Bei normalem Training liegt deine Bereitschaft morgen voraussichtlich bei ${normal[0].expected} (${normal[0].low}–${normal[0].high}). ")
        if (rest.isNotEmpty() && hard.isNotEmpty()) {
            append("Mit einem Ruhetag heute: ${rest[0].expected}, nach einer harten Einheit: ${hard[0].expected}. ")
            if (hard.size >= 3) append("Drei harte Tage in Folge würden dich auf ca. ${hard[2].expected} bringen. ")
        }
        append(
            if (f.personalModel) "Das Modell ist an deine Daten angepasst (typische Abweichung ±${Fmt.int(f.sigma)} Punkte)."
            else "Noch Standardmodell – ab 3 Wochen Daten wird die Prognose an dich angepasst.",
        )
    }
    val tips = listOf("Prognosen sind Wahrscheinlichkeiten – dein tatsächliches Befinden und Schlaf können sie verändern. Der Bereich zeigt, wo der Wert mit 80 % Wahrscheinlichkeit liegt.")
    return CoachText("Bereitschafts-Prognose", text, tips, Tone.NEUTRAL)
}

/** Text zu den Normalwerten. */
fun Coach.baselines(r: AnalysisResult): CoachText {
    val unusual = r.baselines.filter { it.status == BaselineStatus.HIGH || it.status == BaselineStatus.LOW }
    val slight = r.baselines.filter { it.status == BaselineStatus.SLIGHT_HIGH || it.status == BaselineStatus.SLIGHT_LOW }
    val text = when {
        r.baselines.all { it.status == BaselineStatus.NO_DATA } -> "Die App sammelt noch Daten, um deine persönlichen Normalwerte zu bestimmen (mind. 5 Tage)."
        unusual.isNotEmpty() -> "Ungewöhnlich (mehr als 2 Standardabweichungen von deinem Normalwert): " + unusual.joinToString(", ") { "${it.name} ${it.status.label}" } + "."
        slight.isNotEmpty() -> "Alles im Rahmen. Leichte Abweichungen: " + slight.joinToString(", ") { "${it.name} ${it.status.label}" } + "."
        else -> "Alle Werte liegen in deinem persönlichen Normalbereich."
    }
    return CoachText(
        "Persönliche Normalwerte",
        "$text Normalbereich = dein 30-Tage-Durchschnitt ± 1 Standardabweichung. Ein Ruhepuls von 55 kann für dich normal und für jemand anderen auffällig sein – deshalb vergleicht VitalCoach immer mit DIR.",
        emptyList(),
        if (unusual.isNotEmpty()) Tone.WARN else Tone.GOOD,
    )
}

/** Hilfsfunktion: Stunden bis zu einem Zeitpunkt. */
fun hoursUntil(r: AnalysisResult, t: java.time.Instant?): Int? = t?.let { (Duration.between(r.generatedAt, it).seconds / 3600.0).roundToInt().takeIf { h -> h > 0 } }
