package de.vitalcoach.core

import java.time.Duration
import kotlin.math.roundToInt

/** Eine Bewertung mit Überschrift, Einordnung und konkreten Tipps. */
data class CoachText(val headline: String, val text: String, val tips: List<String>, val tone: Tone)

/**
 * Der eingebaute Coach: erstellt für jeden Bereich eine verständliche Bewertung und konkrete
 * Handlungsempfehlungen. Funktioniert komplett offline auf Basis deiner berechneten Werte.
 * (Für eine frei formulierte Analyse kann zusätzlich der Claude-KI-Coach genutzt werden.)
 */
object Coach {

    private fun calibrationNote(r: AnalysisResult): String? =
        if (r.quality.calibrating) {
            "Hinweis: Die App lernt noch deine persönlichen Normalwerte (${r.quality.baselineDays} von mind. 7 Tagen). Die Bewertung wird mit jedem Tag genauer."
        } else {
            null
        }

    // ------------------------------------------------------------------ Tagesfazit
    fun day(r: AnalysisResult): CoachText {
        val t = r.todayMetrics
        val rec = t?.recovery
        val ready = t?.readiness
        val rcm = r.load.recommendation
        if (t == null || rec == null) {
            return CoachText(
                "Noch nicht genug Daten",
                "Trage deine Uhr auch nachts, damit Schlaf, Ruhepuls und HRV erfasst werden. Nach der ersten Nacht erscheint hier deine Tagesbewertung.",
                listOf(
                    "Prüfe in Samsung Health → Einstellungen → Health Connect, ob alle Datentypen freigegeben sind.",
                    "Aktiviere auf der Uhr die kontinuierliche Herzfrequenzmessung und die Schlafaufzeichnung.",
                ),
                Tone.NEUTRAL,
            )
        }
        val parts = mutableListOf<String>()
        parts += when (rec.tone) {
            Tone.GOOD -> "Dein Körper hat sich gut erholt (Erholung ${rec.value})."
            Tone.OK -> "Du bist ordentlich erholt (Erholung ${rec.value}), aber noch nicht ganz auf Top-Niveau."
            Tone.WARN -> "Deine Erholung ist nur mäßig (${rec.value})."
            else -> "Deine Erholung ist niedrig (${rec.value}) – dein Körper braucht Ruhe."
        }
        val weakest = rec.components.minByOrNull { it.score }
        if (weakest != null && weakest.score < 55) parts += "Größter Bremsfaktor: ${weakest.name} (${weakest.detail})."
        val strongest = rec.components.maxByOrNull { it.score }
        if (strongest != null && strongest.score >= 75 && strongest != weakest) parts += "Positiv: ${strongest.name} (${strongest.detail})."
        if (rcm != null) parts += "Empfehlung: ${rcm.title}."
        calibrationNote(r)?.let { parts += it }
        val tips = mutableListOf<String>()
        rcm?.let { tips += it.text }
        if (rcm != null && rcm.targetLoadHigh > 0) {
            tips += "Zielbelastung heute: ${rcm.targetLoadLow}–${rcm.targetLoadHigh} TRIMP – z. B. ${rcm.zone2MinLow}–${rcm.zone2MinHigh} Minuten in Zone 2 (bei Intervallen entsprechend kürzer)."
        }
        r.sleep.debt7H?.let { if (it >= 2.5) tips += "Du hast ${Fmt.num(it, 1)} h Schlafschuld – plane heute Abend 30–60 Minuten früher ins Bett zu gehen." }
        return CoachText(
            headline = ready?.let { "Trainingsbereitschaft ${it.value}: ${rcm?.title ?: ""}" }?.trimEnd(':', ' ') ?: "Erholung ${rec.value}",
            text = parts.joinToString(" "),
            tips = tips,
            tone = ready?.tone ?: rec.tone,
        )
    }

    // ------------------------------------------------------------------ Erholung
    fun recovery(r: AnalysisResult): CoachText {
        val s = r.todayMetrics?.recovery ?: return noData("Erholung", "Für die Erholung braucht die App mindestens eine Nacht mit Schlaf- und Pulsdaten.")
        val headline = when (s.tone) {
            Tone.GOOD -> "Gut erholt"
            Tone.OK -> "Solide erholt"
            Tone.WARN -> "Teilweise erholt"
            else -> "Kaum erholt"
        }
        val text = buildString {
            append("Dein Erholungswert liegt heute bei ${s.value} von 100. ")
            s.components.sortedBy { it.score }.forEach { c ->
                append("${c.name}: ${c.detail} → ${c.score} Punkte. ")
            }
            if (!r.quality.hasHrv) {
                append("Deine Uhr liefert über Health Connect keine HRV-Werte, deshalb stützt sich die Erholung stärker auf Ruhepuls und Schlaf. ")
            }
            append("Verlässlichkeit: ${s.confidence.label}.")
        }
        val tips = mutableListOf<String>()
        for (c in s.components.sortedBy { it.score }.filter { it.score < 60 }) {
            tips += when (c.name) {
                "HRV" -> "Niedrige HRV bedeutet: dein Nervensystem ist im Stressmodus. Verzichte heute auf harte Intervalle. Alkohol, spätes schweres Essen und Stress drücken die HRV – 5 Minuten ruhiges Atmen (6 Atemzüge pro Minute) können akut helfen."
                "Ruhepuls" -> "Ein erhöhter Ruhepuls deutet auf unvollständige Erholung, Flüssigkeitsmangel, Alkohol, Hitze oder einen beginnenden Infekt hin. Trinke ausreichend und beobachte, ob er morgen wieder sinkt."
                "Schlaf" -> "Der Schlaf war der schwächste Punkt. Feste Bettzeit, ein dunkles, kühles Schlafzimmer (16–19 °C) und kein Bildschirm in der letzten halben Stunde verbessern ihn am stärksten."
                "Atemfrequenz" -> "Deine nächtliche Atemfrequenz ist erhöht – das kann ein frühes Zeichen für einen Infekt oder starke Belastung sein."
                else -> c.detail
            }
        }
        if (tips.isEmpty()) tips += "Alle Erholungswerte liegen im grünen Bereich – nutze den Tag für eine qualitativ gute Trainingseinheit."
        calibrationNote(r)?.let { tips += it }
        return CoachText(headline, text, tips, s.tone)
    }

    // ------------------------------------------------------------------ Bereitschaft
    fun readiness(r: AnalysisResult): CoachText {
        val t = r.todayMetrics
        val s = t?.readiness ?: return noData("Trainingsbereitschaft", "Sobald Erholungsdaten vorliegen, wird die Trainingsbereitschaft berechnet.")
        val rec = r.load.recommendation
        val text = buildString {
            append("Deine Trainingsbereitschaft liegt bei ${s.value} von 100. Sie kombiniert deine Erholung mit deiner Trainingsbelastung der letzten Wochen. ")
            s.components.forEach { append("${it.name}: ${it.detail} → ${it.score} Punkte. ") }
            if (t.illnessFlag) append("Wegen auffälliger Körperwerte wurde die Bereitschaft auf maximal 30 begrenzt. ")
        }
        val tips = mutableListOf<String>()
        rec?.let {
            tips += it.text
            if (it.targetLoadHigh > 0) tips += "Orientierung: ${it.targetLoadLow}–${it.targetLoadHigh} TRIMP ≈ ${it.zone2MinLow}–${it.zone2MinHigh} min in Zone 2 (${zoneRange(r, 2)}) – bei Intervallen entsprechend kürzer."
        }
        val load = s.components.firstOrNull { it.name == "Belastungssteigerung" }
        if (load != null && load.score < 60) tips += "Du hast deine Belastung zuletzt stark gesteigert. Halte die Wochenbelastung in den nächsten Tagen konstant, statt weiter draufzulegen."
        val form = s.components.firstOrNull { it.name == "Form" }
        if (form != null && form.score < 50) tips += "Deine Ermüdung übersteigt aktuell deine Fitness deutlich (negative Form). 1–2 lockere Tage bringen die Form schnell zurück."
        return CoachText(rec?.title ?: "Trainingsbereitschaft ${s.value}", text, tips, s.tone)
    }

    // ------------------------------------------------------------------ Schlaf
    fun sleep(r: AnalysisResult): CoachText {
        val sr = r.sleep
        val n = sr.lastNight ?: return noData("Schlaf", "Trage die Uhr nachts, damit Samsung Health deinen Schlaf aufzeichnet.")
        val goalMin = sr.goalHours * 60
        val headline = when (n.scoreTone) {
            Tone.GOOD -> "Erholsame Nacht"
            Tone.OK -> "Ordentliche Nacht"
            Tone.WARN -> "Mäßige Nacht"
            else -> "Unruhige / kurze Nacht"
        }
        val text = buildString {
            append("Schlaf-Score ${n.score}: Du hast ${Fmt.duration(n.asleepMin)} geschlafen (${Fmt.clock(n.bedtimeMin)}–${Fmt.clock(n.wakeMin)} Uhr). ")
            n.efficiency?.let { append("Schlafeffizienz ${Fmt.pct(it)}. ") }
            if (n.hasStages) {
                append("Tiefschlaf ${Fmt.duration(n.deepMin)} (${Fmt.pct(n.deepPct ?: 0.0)}), REM ${Fmt.duration(n.remMin)} (${Fmt.pct(n.remPct ?: 0.0)}). ")
            }
            sr.avgAsleep7Min?.let { append("Im 7-Tage-Schnitt schläfst du ${Fmt.duration(it)}. ") }
            sr.regularitySdMin?.let {
                append(
                    when {
                        it <= 30 -> "Dein Schlafrhythmus ist sehr regelmäßig (±${it.roundToInt()} min) – super. "
                        it <= 60 -> "Dein Schlafrhythmus schwankt um ±${it.roundToInt()} min – in Ordnung. "
                        else -> "Dein Schlafrhythmus schwankt stark (±${it.roundToInt()} min). Regelmäßigkeit ist für die Erholung fast so wichtig wie die Dauer. "
                    },
                )
            }
            sr.debt7H?.let { if (it >= 1.0) append("Schlafschuld der letzten 7 Nächte: ${Fmt.num(it, 1)} h.") }
        }
        val tips = mutableListOf<String>()
        if (n.asleepMin < goalMin - 30) {
            val bedtime = (sr.avgWakeMin ?: n.wakeMin) - goalMin - (n.inBedMin - n.asleepMin)
            tips += "Um dein Ziel von ${Fmt.hours(sr.goalHours)} zu erreichen, solltest du bei deiner üblichen Aufstehzeit gegen ${Fmt.clock(bedtime)} Uhr im Bett sein."
        }
        n.efficiency?.let { if (it < 0.85) tips += "Viel Wachzeit im Bett: Geh erst ins Bett, wenn du müde bist, und steh auf, wenn du länger als 20 Minuten wach liegst. Kein Koffein nach 14 Uhr." }
        n.deepPct?.let { if (it < 0.12) tips += "Wenig Tiefschlaf: Alkohol am Abend, ein warmes Schlafzimmer und spätes intensives Training reduzieren ihn. Regelmäßiges Ausdauertraining und ein kühler Raum fördern ihn." }
        n.remPct?.let { if (it < 0.17) tips += "Wenig REM-Schlaf: REM findet vor allem am Morgen statt – zu frühes Aufstehen und Alkohol kosten am meisten REM." }
        sr.regularitySdMin?.let { if (it > 60) tips += "Geh möglichst jeden Tag (auch am Wochenende) zur gleichen Zeit schlafen – max. ±30 Minuten Abweichung." }
        if (tips.isEmpty()) tips += "Weiter so! Halte deinen Rhythmus bei – er ist die Basis für gute Erholung."
        return CoachText(headline, text, tips, n.scoreTone)
    }

    // ------------------------------------------------------------------ Training
    fun training(r: AnalysisResult): CoachText {
        val l = r.load
        if (r.workouts.isEmpty()) {
            return CoachText(
                "Noch keine Trainings",
                "Sobald du Trainings mit der Uhr aufzeichnest, berechnet die App Belastung (TRIMP), Fitness, Ermüdung und Form.",
                listOf("Starte Trainings auf der Uhr, damit die Herzfrequenz in hoher Auflösung aufgezeichnet wird."),
                Tone.NEUTRAL,
            )
        }
        val text = buildString {
            append("Status: ${l.status.label}. ${l.status.description} ")
            append("Fitness (CTL) ${Fmt.int(l.ctl)}, Ermüdung (ATL) ${Fmt.int(l.atl)}, Form (TSB) ${Fmt.signed(l.tsb)}. ")
            l.acwr?.let { append("Belastungsverhältnis (ACWR) ${Fmt.num(it, 2)}. ") }
            append("Diese Woche: ${Fmt.int(l.weekLoad)} TRIMP in ${l.workouts7} Einheiten (Vorwoche ${Fmt.int(l.prevWeekLoad)}). ")
            if (l.easyPct != null) {
                append("Intensitätsverteilung (28 Tage): ${Fmt.pct(l.easyPct)} locker, ${Fmt.pct(l.moderatePct ?: 0.0)} mittel, ${Fmt.pct(l.hardPct ?: 0.0)} hart. ")
            }
            append("WHO-Aktivminuten (7 Tage): ${Fmt.int(l.whoMinutes7)} von 150.")
        }
        val tips = mutableListOf<String>()
        when (l.status) {
            LoadStatus.OVERREACHING -> tips += "Reduziere die Belastung in den nächsten 3–5 Tagen um 30–40 %, bis das ACWR wieder unter 1,3 liegt."
            LoadStatus.HIGH -> tips += "Du steigerst gerade stark. Plane in den nächsten Tagen bewusst einen Ruhe- oder lockeren Tag ein."
            LoadStatus.DETRAINING -> tips += "Deine Belastung ist niedriger als gewohnt. Als Erholungswoche ideal – länger als 2 Wochen kostet es aber Fitness."
            LoadStatus.OPTIMAL -> tips += "Perfekte Steigerung. Erhöhe die Wochenbelastung nicht mehr als ca. 10 % pro Woche."
            else -> Unit
        }
        if (l.easyPct != null && l.easyPct < 0.7 && (l.zoneMin28.sum() > 120)) {
            tips += "Nur ${Fmt.pct(l.easyPct)} deines Trainings sind locker. Ausdauersportler profitieren am meisten von ca. 80 % locker / 20 % intensiv (Seiler). Mach lockere Einheiten wirklich locker (Zone 2: ${zoneRange(r, 2)})."
        }
        if (l.hardPct != null && l.hardPct < 0.03 && r.load.workouts7 >= 3) {
            tips += "Fast kein intensives Training: Eine kurze Intervall-Einheit pro Woche (z. B. 4 × 4 min in Zone 4–5) ist der stärkste Reiz für die VO2max."
        }
        l.monotony?.let { if (it > 2.0) tips += "Monotonie ${Fmt.num(it, 1)}: Wechsle harte und leichte Tage stärker ab." }
        if (l.whoMinutes7 < 150) tips += "Für die Gesundheit empfiehlt die WHO mind. 150 Minuten moderate (oder 75 min intensive) Aktivität pro Woche – dir fehlen noch ${Fmt.int(150 - l.whoMinutes7)} Minuten."
        if (r.workouts.none { it.workout.type == SportType.STRENGTH && Duration.between(it.workout.start, r.generatedAt).toDays() < 14 }) {
            tips += "Kein Krafttraining in den letzten 2 Wochen: 2 Einheiten pro Woche schützen Gelenke, Knochen und Stoffwechsel und verbessern auch die Laufökonomie."
        }
        return CoachText("Trainingsbelastung: ${l.status.label}", text, tips, l.status.tone)
    }

    // ------------------------------------------------------------------ Fitness
    fun fitness(r: AnalysisResult): CoachText {
        val f = r.fitness
        val vo2 = f.vo2max ?: return noData("Fitnesslevel", "Für die VO2max-Schätzung braucht die App deinen Ruhepuls (einige Nächte mit Uhr) oder Läufe mit Distanz und Puls.")
        val text = buildString {
            append("Deine geschätzte VO2max liegt bei ${Fmt.num(vo2, 1)} ml/kg/min (${f.vo2Method?.label}). ")
            if (f.vo2CategoryLabel != null && f.age != null) {
                append("Für dein Alter (${f.age}) ist das „${f.vo2CategoryLabel}“. ")
                val avg = FitnessMath.averageVo2(f.age, r.profile.sex)
                append("Der Durchschnitt deiner Altersgruppe liegt bei ca. ${Fmt.num(avg, 1)}. ")
            }
            f.fitnessAge?.let { fa ->
                append("Dein Fitnessalter beträgt ca. $fa Jahre")
                if (f.age != null) {
                    val d = fa - f.age
                    append(
                        when {
                            d <= -3 -> " – ${-d} Jahre jünger als dein tatsächliches Alter. "
                            d >= 3 -> " – $d Jahre älter als dein tatsächliches Alter. "
                            else -> " – genau im Bereich deines tatsächlichen Alters. "
                        },
                    )
                } else {
                    append(". ")
                }
            }
            append("Die VO2max ist einer der stärksten Einzelprädiktoren für Gesundheit und Lebenserwartung.")
        }
        val tips = mutableListOf<String>()
        if (f.vo2Method == Vo2Method.RESTING_HR) tips += "Zeichne Läufe (draußen, mit GPS) auf – dann berechnet die App deine VO2max aus Tempo und Puls deutlich genauer."
        if (f.age == null || r.profile.sex == null) tips += "Trage in den Einstellungen Geburtsjahr und Geschlecht ein, damit Kategorien und Fitnessalter berechnet werden können."
        val idx = f.vo2CategoryIndex
        if (idx != null && idx <= 2) {
            tips += "Größter Hebel: 3–4 Ausdauereinheiten pro Woche, davon die meisten locker (Zone 2) und eine mit Intervallen (z. B. 4 × 4 min zügig). So sind +3–5 ml/kg/min in 2–3 Monaten realistisch."
        } else {
            tips += "Um dein hohes Niveau zu halten oder zu steigern: 1–2 intensive Einheiten pro Woche (Intervalle in Zone 4–5) plus viel lockerer Umfang."
        }
        return CoachText("VO2max ${Fmt.num(vo2, 1)} – ${f.vo2CategoryLabel ?: "Fitnesslevel"}", text, tips, when (idx) {
            null -> Tone.NEUTRAL
            0, 1 -> Tone.WARN
            2 -> Tone.OK
            else -> Tone.GOOD
        })
    }

    // ------------------------------------------------------------------ Ruhepuls
    fun restingHr(r: AnalysisResult): CoachText {
        val t = r.todayMetrics
        val f = r.fitness
        val now = t?.restingHr ?: f.restingHrNow ?: return noData("Ruhepuls", "Trage die Uhr nachts, damit der Ruhepuls im Schlaf gemessen werden kann.")
        val b = t?.rhrBaseline
        val text = buildString {
            append("Dein Ruhepuls heute: ${Fmt.int(now)} bpm")
            t?.restingHrSource?.let { append(" (${it.label})") }
            append(". ")
            if (b != null) append("Dein persönlicher Normalwert: ${Fmt.int(b.mean)} ± ${Fmt.num(b.sd, 1)} bpm. ")
            f.restingHrTrend30d?.let {
                append(
                    when {
                        it <= -1.0 -> "Trend: sinkend (${Fmt.signed(it, 1)} bpm pro Monat) – ein Zeichen wachsender Ausdauerfitness. "
                        it >= 1.0 -> "Trend: steigend (${Fmt.signed(it, 1)} bpm pro Monat) – mögliche Ursachen: Stress, Übertraining, wenig Schlaf. "
                        else -> "Trend: stabil. "
                    },
                )
            }
            append(
                when {
                    now < 50 -> "Werte unter 50 sind typisch für gut trainierte Ausdauersportler."
                    now < 60 -> "Das ist ein guter Wert (Erwachsene: meist 60–80, Trainierte darunter)."
                    now < 70 -> "Das liegt im normalen Bereich."
                    else -> "Das ist eher hoch – regelmäßiges Ausdauertraining senkt den Ruhepuls nachweislich."
                },
            )
        }
        val tips = mutableListOf<String>()
        if (b != null && now - b.mean >= 4) tips += "Heute ${Fmt.int(now - b.mean)} Schläge über normal: lieber locker trainieren, viel trinken, auf Alkohol verzichten."
        tips += "Der Ruhepuls ist am aussagekräftigsten im Trend – einzelne Ausreißer nach Alkohol, Hitze oder spätem Essen sind normal."
        return CoachText("Ruhepuls ${Fmt.int(now)} bpm", text, tips, if (b != null && now - b.mean >= 4) Tone.WARN else Tone.GOOD)
    }

    // ------------------------------------------------------------------ HRV
    fun hrv(r: AnalysisResult): CoachText {
        val t = r.todayMetrics
        val f = r.fitness
        if (!r.quality.hasHrv) {
            return CoachText(
                "Keine HRV-Daten verfügbar",
                "Die Herzfrequenzvariabilität (HRV) wird aktuell nicht über Health Connect bereitgestellt. Sobald Samsung Health HRV-Werte teilt, nutzt die App sie automatisch – " +
                    "bis dahin basiert die Erholung auf Ruhepuls, Schlaf und Atemfrequenz.",
                listOf("Prüfe in Health Connect → App-Berechtigungen → Samsung Health, ob „Herzfrequenzvariabilität“ zum Schreiben freigegeben ist."),
                Tone.NEUTRAL,
            )
        }
        val now = t?.hrv ?: f.hrvNow ?: return noData("HRV", "Heute Nacht wurde keine HRV gemessen.")
        val text = buildString {
            append("Deine HRV (RMSSD) heute Nacht: ${Fmt.int(now)} ms. ")
            if (f.hrvNormalLow != null && f.hrvNormalHigh != null) {
                append("Dein Normalbereich: ${Fmt.int(f.hrvNormalLow)}–${Fmt.int(f.hrvNormalHigh)} ms. ")
                append(
                    when {
                        now < f.hrvNormalLow -> "Du liegst unter deinem Normalbereich – Zeichen für Stress, Belastung oder schlechten Schlaf. "
                        now > f.hrvNormalHigh -> "Du liegst über deinem Normalbereich – meist ein Zeichen sehr guter Erholung. "
                        else -> "Du liegst in deinem Normalbereich. "
                    },
                )
            }
            append("Die HRV ist sehr individuell – vergleiche sie nur mit deinen eigenen Werten, nicht mit anderen.")
        }
        val tips = listOf(
            "HRV steigt durch: regelmäßigen Schlaf, Ausdauertraining in Zone 2, wenig Alkohol, Stressabbau (Atemübungen, Natur).",
            "HRV sinkt durch: Alkohol (schon 1–2 Gläser), späte Mahlzeiten, Infekte, Überlastung und psychischen Stress.",
        )
        val tone = if (f.hrvNormalLow != null && now < f.hrvNormalLow) Tone.WARN else Tone.GOOD
        return CoachText("HRV ${Fmt.int(now)} ms", text, tips, tone)
    }

    // ------------------------------------------------------------------ Aktivität
    fun activity(r: AnalysisResult): CoachText {
        val a = r.activity
        val text = buildString {
            a.stepsToday?.let { append("Heute bisher ${Fmt.int(it)} Schritte (Ziel ${Fmt.int(a.stepGoal.toLong())}). ") }
            a.steps7Avg?.let { append("7-Tage-Schnitt: ${Fmt.int(it)} Schritte. ") }
            append("Ziel an ${a.daysGoalReached7} von 7 Tagen erreicht. ")
            a.activeKcal7Avg?.let { append("Aktive Kalorien im Schnitt: ${Fmt.int(it)} kcal/Tag. ") }
            a.totalKcal7Avg?.let { append("Gesamtverbrauch im Schnitt: ${Fmt.int(it)} kcal/Tag. ") }
            append("Studien zeigen: Ab ca. 7.000–8.000 Schritten pro Tag sinkt das Sterblichkeitsrisiko deutlich (Paluch et al. 2022).")
        }
        val tips = mutableListOf<String>()
        val avg = a.steps7Avg ?: 0.0
        if (avg < a.stepGoal) tips += "Kleine Gewohnheiten helfen: Telefonate im Gehen, Treppe statt Aufzug, 10 Minuten Spaziergang nach jeder Mahlzeit (senkt auch den Blutzucker)."
        if (a.whoMinutes7 < 150) tips += "Für die WHO-Empfehlung fehlen diese Woche noch ${Fmt.int(150 - a.whoMinutes7)} Aktivminuten."
        if (tips.isEmpty()) tips += "Sehr aktiv – weiter so!"
        val tone = when {
            avg >= a.stepGoal -> Tone.GOOD
            avg >= a.stepGoal * 0.7 -> Tone.OK
            else -> Tone.WARN
        }
        return CoachText("Alltagsaktivität", text, tips, tone)
    }

    // ------------------------------------------------------------------ Vitalwerte
    fun vitals(r: AnalysisResult): CoachText {
        val f = r.fitness
        if (f.spo2Avg7 == null && f.respAvg7 == null) {
            return CoachText(
                "Keine Vitalwerte",
                "Sauerstoffsättigung und Atemfrequenz werden über Health Connect bisher nicht geliefert.",
                listOf("Aktiviere auf der Uhr in Samsung Health → Schlaf die Messung der Sauerstoffsättigung während des Schlafs."),
                Tone.NEUTRAL,
            )
        }
        val text = buildString {
            f.spo2Avg7?.let {
                append("Nächtliche Sauerstoffsättigung (7 Tage): ${Fmt.num(it, 1)} %. ")
                append(if (it >= 95) "Das ist im Normalbereich (95–100 %). " else "Das liegt unter dem Normalbereich – wiederholt niedrige Werte ärztlich abklären lassen. ")
            }
            f.respAvg7?.let {
                append("Atemfrequenz im Schlaf: ${Fmt.num(it, 1)} Atemzüge/min ")
                f.respBaseline?.let { b -> append("(Normalwert ${Fmt.num(b, 1)}). ") } ?: append(". ")
                append("Typisch sind 12–20 im Schlaf; ein Anstieg um mehr als 1–2 /min kann auf einen Infekt hindeuten.")
            }
        }
        val low = (f.spo2Avg7 ?: 100.0) < 94.0
        return CoachText("Vitalwerte im Schlaf", text, listOf("Werte von Uhren sind Schätzungen – entscheidend ist der Trend, nicht ein einzelner Messwert."), if (low) Tone.WARN else Tone.GOOD)
    }

    // ------------------------------------------------------------------ Körper
    fun body(r: AnalysisResult): CoachText {
        val f = r.fitness
        if (f.weightKg == null) return noData("Körper", "Trage dein Gewicht in den Einstellungen ein oder miss deine Körperzusammensetzung mit der Uhr.")
        val text = buildString {
            append("Gewicht: ${Fmt.num(f.weightKg, 1)} kg. ")
            f.bmi?.let { append("BMI: ${Fmt.num(it, 1)} (${FitnessMath.bmiLabel(it)}). ") }
            f.bodyFatPct?.let { append("Körperfett: ${Fmt.num(it, 1)} %${f.bodyFatLabel?.let { l -> " ($l)" } ?: ""}. ") }
            append("Der BMI berücksichtigt keine Muskelmasse – bei sportlichen Menschen ist der Körperfettanteil aussagekräftiger.")
        }
        return CoachText("Körperwerte", text, listOf("Miss Gewicht und Körperfett immer unter gleichen Bedingungen (morgens, nüchtern, nach dem Toilettengang)."), Tone.NEUTRAL)
    }

    // ------------------------------------------------------------------ einzelnes Training
    fun workout(w: WorkoutAnalysis, r: AnalysisResult): CoachText {
        val text = buildString {
            append("${w.workout.type.label}, ${Fmt.duration(w.durationMin)}. ")
            w.avgHr?.let { append("Ø Puls ${Fmt.int(it)} bpm, max. ${w.maxHr ?: "–"} bpm. ") }
            append("Belastung: ${Fmt.int(w.trimp)} TRIMP")
            if (w.estimated) append(" (geschätzt – keine Pulsdaten)")
            append(". Charakter: ${w.focus}. ")
            w.paceSecPerKm?.let { append(if (w.workout.type == SportType.CYCLING) "Ø ${Fmt.num(3600.0 / it, 1)} km/h. " else "Tempo: ${Fmt.pace(it)}. ") }
            w.vo2Estimate?.let { append("Geschätzte VO2max aus diesem Lauf: ${Fmt.num(it, 1)}. ") }
            val ctl = r.load.ctl.coerceAtLeast(1.0)
            val rel = w.trimp / ctl
            append(
                when {
                    rel >= 2.0 -> "Das war eine sehr fordernde Einheit für dein aktuelles Fitnessniveau – plane 48 h Erholung ein."
                    rel >= 1.2 -> "Eine fordernde Einheit – am Folgetag eher locker trainieren."
                    rel >= 0.6 -> "Eine normale Trainingseinheit für dein Niveau."
                    else -> "Eine leichte Einheit – gut für die Regeneration."
                },
            )
        }
        val tips = mutableListOf<String>()
        val total = w.zoneMin.sum()
        if (total > 0 && w.focus.startsWith("Gemischt")) {
            tips += "Diese Einheit lag im „Niemandsland“ zwischen locker und hart. Entscheide dich vorher bewusst: entweder wirklich locker (Zone 2) oder gezielt hart."
        }
        if (w.workout.type == SportType.RUNNING && (w.zoneMin.getOrElse(3) { 0.0 } + w.zoneMin.getOrElse(4) { 0.0 }) / total.coerceAtLeast(1.0) > 0.6 && w.durationMin > 40) {
            tips += "Lange Läufe überwiegend in Zone 3–4 ermüden stark. Für die Grundlagenausdauer ist ein langsameres Tempo effektiver."
        }
        return CoachText(w.workout.title ?: w.workout.type.label, text, tips, Tone.NEUTRAL)
    }

    private fun zoneRange(r: AnalysisResult, zone: Int): String =
        r.fitness.zones.firstOrNull { it.number == zone }?.let { z -> "${z.lowBpm}–${z.highBpm} bpm" + (z.paceText?.let { ", ca. $it" } ?: "") } ?: "–"

    private fun noData(area: String, text: String) = CoachText("$area: noch keine Daten", text, emptyList(), Tone.NEUTRAL)
}
