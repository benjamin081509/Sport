package de.vitalcoach.core

/** Alle Kennzahlen, für die es eine Detailseite gibt. */
enum class MetricKey(val title: String) {
    RECOVERY("Erholung"),
    READINESS("Trainingsbereitschaft"),
    SLEEP("Schlaf"),
    RESTING_HR("Ruhepuls"),
    HRV("Herzfrequenzvariabilität"),
    LOAD("Trainingsbelastung"),
    VO2MAX("VO2max & Fitnessalter"),
    ACTIVITY("Alltagsaktivität"),
    VITALS("Sauerstoff & Atmung"),
    BODY("Körperzusammensetzung"),
    REGENERATION("Regeneration & Ermüdung"),
    HEART_HEALTH("Herzgesundheit"),
    HEART("Herz & Kreislauf"),
    STRESS("Stress & Nervensystem"),
    PERFORMANCE("Fitness & Leistung"),
    FORECAST("Bereitschafts-Prognose"),
    BASELINES("Persönliche Normalwerte"),
    CORRELATIONS("Zusammenhänge"),
    TRENDS("Langzeit-Trends"),
    ENERGY("Energie"),
}

/** Erklärungen „Was ist das?“ und „So wird es berechnet“ – in einfacher Sprache mit Quellen. */
object Explain {

    /** Herkunft des Hauptwerts einer Kennzahl. */
    fun provenance(key: MetricKey): Provenance = when (key) {
        MetricKey.ACTIVITY, MetricKey.VITALS, MetricKey.BODY -> Provenance.MEASURED
        MetricKey.VO2MAX, MetricKey.REGENERATION, MetricKey.STRESS, MetricKey.FORECAST, MetricKey.PERFORMANCE, MetricKey.ENERGY -> Provenance.ESTIMATED
        else -> Provenance.CALCULATED
    }

    fun what(key: MetricKey): String = when (key) {
        MetricKey.RECOVERY ->
            "Die Erholung zeigt, wie gut sich dein Körper über Nacht von Belastung und Stress erholt hat. " +
                "Grundlage sind Signale deines autonomen Nervensystems (HRV, Ruhepuls, Atemfrequenz) und dein Schlaf – " +
                "immer verglichen mit DEINEN persönlichen Normalwerten, nicht mit anderen Menschen."
        MetricKey.READINESS ->
            "Die Trainingsbereitschaft beantwortet die Frage: „Wie hart sollte ich heute trainieren?“ Sie verbindet die Erholung " +
                "mit deiner Trainingsbelastung der letzten Wochen und deiner Schlafschuld."
        MetricKey.SLEEP ->
            "Schlaf ist die wichtigste Erholungsphase. Der Schlaf-Score bewertet Dauer, Effizienz (wie viel der Bettzeit du " +
                "wirklich schläfst), Tiefschlaf (körperliche Erholung), REM-Schlaf (mentale Erholung, Gedächtnis) und Regelmäßigkeit."
        MetricKey.RESTING_HR ->
            "Der Ruhepuls ist die niedrigste Herzfrequenz in völliger Ruhe. Ein sinkender Ruhepuls über Wochen zeigt bessere " +
                "Ausdauerfitness; ein plötzlicher Anstieg um 5+ Schläge ist ein typisches Warnsignal für Überlastung oder einen Infekt."
        MetricKey.HRV ->
            "Die Herzfrequenzvariabilität (HRV) misst die feinen Schwankungen zwischen zwei Herzschlägen. Eine hohe HRV bedeutet: " +
                "dein Nervensystem ist entspannt und erholt. Eine niedrige HRV zeigt Stress, Müdigkeit oder Krankheit an."
        MetricKey.LOAD ->
            "Die Trainingsbelastung (TRIMP) bewertet jedes Training nach Dauer UND Intensität. Daraus entstehen drei Werte: " +
                "Fitness (langfristige Belastung, 42 Tage), Ermüdung (kurzfristige Belastung, 7 Tage) und Form (Fitness minus Ermüdung)."
        MetricKey.VO2MAX ->
            "Die VO2max ist die maximale Sauerstoffmenge, die dein Körper pro Minute und Kilogramm aufnehmen kann – der " +
                "Goldstandard für Ausdauerfitness und einer der stärksten Prädiktoren für Lebenserwartung. Das Fitnessalter zeigt, " +
                "welchem Alter deine VO2max im Durchschnitt entspricht."
        MetricKey.ACTIVITY ->
            "Schritte, aktive Kalorien und Aktivminuten zeigen, wie viel du dich im Alltag bewegst – unabhängig vom Sport. " +
                "Regelmäßige Alltagsbewegung senkt das Risiko für Herz-Kreislauf-Erkrankungen deutlich."
        MetricKey.VITALS ->
            "Die Sauerstoffsättigung (SpO2) zeigt, wie viel Sauerstoff dein Blut transportiert. Die Atemfrequenz im Schlaf ist sehr " +
                "stabil – ein Anstieg ist oft eines der ersten Zeichen einer Erkrankung."
        MetricKey.BODY ->
            "Gewicht, BMI und Körperfettanteil beschreiben deine Körperzusammensetzung. Die Galaxy Watch misst den Körperfettanteil " +
                "per Bioimpedanz (BIA)."
        MetricKey.REGENERATION ->
            "Die Regeneration zeigt, wie weit sich dein Körper von deinen letzten Trainings erholt hat, wie viel Ermüdung sich angesammelt hat " +
                "und ob Belastung und Erholung im Gleichgewicht sind. Der Überlastungsindikator fasst mehrere Warnsignale zusammen."
        MetricKey.HEART_HEALTH ->
            "Die Herzgesundheit fasst die wichtigsten Faktoren für ein gesundes Herz-Kreislauf-System zusammen: Bewegung, Schlaf, Gewicht, " +
                "Blutdruck, Nikotin sowie Ruhepuls und Ausdauerfitness."
        MetricKey.HEART ->
            "Hier siehst du, wie dein Herz im Alltag, nachts und im Training arbeitet: Durchschnittspuls, nächtliche Pulsabsenkung, " +
                "HRV- und Ruhepuls-Trends, Stabilität und wie schnell dein Puls nach Belastung wieder sinkt (Herzfrequenzerholung)."
        MetricKey.STRESS ->
            "Stress aktiviert den Sympathikus – dein Puls steigt, auch wenn du dich nicht bewegst. Der Stress-Index schätzt, wie stark " +
                "dein Nervensystem im Alltag angespannt ist."
        MetricKey.PERFORMANCE ->
            "Fitness & Leistung zeigt deine langfristige Entwicklung: VO2max, aerobe Effizienz (Tempo bei gleichem Puls), Laktatschwelle, " +
                "hochintensive Belastung und Radleistung."
        MetricKey.FORECAST ->
            "Die Prognose schätzt, wie deine Trainingsbereitschaft in den nächsten 1–3 Tagen aussieht – je nachdem, ob du dich ausruhst, " +
                "locker, normal oder hart trainierst."
        MetricKey.BASELINES ->
            "Jeder Mensch hat eigene Normalwerte. Diese Übersicht zeigt für alle wichtigen Messwerte deinen persönlichen Normalbereich und " +
                "ob der aktuelle Wert davon abweicht."
        MetricKey.CORRELATIONS ->
            "Die Zusammenhangs-Analyse sucht in DEINEN Daten nach Mustern, z. B. ob du nach viel Schlaf eine höhere HRV hast oder nach " +
                "späten Trainings schlechter schläfst. Zusammenhang bedeutet nicht automatisch Ursache."
        MetricKey.TRENDS ->
            "Langzeit-Trends zeigen, wie sich deine Werte über Monate entwickeln – das sagt mehr über deine Gesundheit aus als einzelne Tage."
        MetricKey.ENERGY ->
            "Die Energie zeigt wie ein Akku, wie viel Kraft du gerade noch hast. Morgens wird sie durch Schlaf und Erholung aufgeladen, " +
                "über den Tag verbrauchen Training, Stress und Alltagsbewegung Energie – deshalb sinkt auch deine Trainingsbereitschaft bis zum Abend."
    }

    fun how(key: MetricKey): String = when (key) {
        MetricKey.RECOVERY ->
            "Für HRV, Ruhepuls und Atemfrequenz wird berechnet, wie stark der heutige Wert von deinem 30-Tage-Schnitt abweicht " +
                "(in Standardabweichungen, HRV logarithmiert nach Plews et al. 2012). Dein Normalwert = 60 Punkte (solide), jede " +
                "Standardabweichung besser/schlechter = ±15 Punkte – „gut“ (≥ 75) ist nur, was besser als dein Normal ist. Gewichtung: " +
                "HRV 35 %, Ruhepuls 25 %, Schlaf-Score 25 %, Trainingsbelastung 10 %, Atemfrequenz 5 % (ohne HRV: Ruhepuls 45 %, Schlaf 33 %, " +
                "Belastung 12 %, Atmung 10 %). Nach weniger als 6 h Schlaf maximal 55, nach weniger als 5 h maximal 40 Punkte. " +
                "Die ersten 4–7 Tage dienen der Kalibrierung."
        MetricKey.READINESS ->
            "Erholung 60 % + Belastungssteigerung 15 % (Acute:Chronic Workload Ratio, exponentiell gewichtet nach Williams et al. 2017; " +
                "optimal 0,8–1,3, ab 1,5 erhöhtes Verletzungsrisiko nach Gabbett 2016) + Form 15 % (TSB relativ zur Fitness, Banister-Modell) + " +
                "Schlafschuld 10 %. Die Bereitschaft liegt nie mehr als 5 Punkte über der Erholung; nach < 6 h Schlaf maximal 59 (kein intensives " +
                "Training), bei Warnzeichen für einen Infekt maximal 30. Stufen: ≥ 75 intensiv, ≥ 60 moderat, ≥ 45 locker, darunter Ruhetag."
        MetricKey.SLEEP ->
            "Dauer 55 % (Ziel = 100, 7 h = 85, 6 h = 50, 5 h = 20, ≤ 4 h = 0 Punkte), Effizienz 15 % (100 Punkte ab 93 %), Tiefschlaf 10 % " +
                "(Ziel ≥ 15 % und ≥ 75 min), REM 10 % (Ziel ≥ 20 % und ≥ 90 min), Regelmäßigkeit 10 % (Abweichung der Schlafmitte vom " +
                "14-Tage-Median, bis 20 min = 100 Punkte). Kurze Nächte werden begrenzt: < 7 h max. 74, < 6 h max. 50, < 5 h max. 35. " +
                "Referenzwerte: AASM/SRS 2015 (≥ 7 h für Erwachsene), National Sleep Foundation (Ohayon et al. 2017)."
        MetricKey.RESTING_HR ->
            "Niedrigster gleitender 30-Minuten-Mittelwert der Herzfrequenz während deines Hauptschlafs (mind. 3 Messwerte). " +
                "Ohne Schlafdaten: Ruhepuls-Messung der Uhr bzw. niedrigster 30-Minuten-Schnitt des Tages. Trend = lineare Regression über 60 Tage."
        MetricKey.HRV ->
            "RMSSD-Werte, die Samsung Health während des Schlafs misst, als Nacht-Mittelwert. Dein Normalbereich = Mittelwert ± 1 " +
                "Standardabweichung der letzten 30 Tage (auf logarithmischer Skala, weil HRV-Werte schief verteilt sind)."
        MetricKey.LOAD ->
            "TRIMP nach Banister: Minuten × HFR × 0,64 × e^(1,92 × HFR) für Männer bzw. 0,86 × e^(1,67 × HFR) für Frauen; HFR = " +
                "(Puls − Ruhepuls) / (HFmax − Ruhepuls). Berechnet Sekunde für Sekunde aus deinen Pulsdaten. Fitness (CTL) und Ermüdung (ATL) " +
                "sind exponentiell gewichtete Mittel über 42 bzw. 7 Tage; Form (TSB) = CTL − ATL. Belastungssteigerung (ACWR) = akute ÷ " +
                "chronische Belastung als exponentiell gewichtete Mittel über 7 bzw. 28 Tage (Williams et al. 2017) – frische Trainings zählen " +
                "stärker als ältere. Einordnung: < 0,8 weniger als gewohnt, 0,8–1,1 wie gewohnt, 1,1–1,3 kontrollierter Aufbau, 1,3–1,5 starke " +
                "Steigerung, > 1,5 Belastungssprung mit erhöhtem Verletzungsrisiko (Gabbett 2016). Monotonie = Ø Tageslast / Standardabweichung " +
                "(Foster 1998), nur ab 3 Trainingstagen pro Woche. Einordnung einer Einheit: < 40 leicht, 40–80 moderat, 80–130 fordernd, " +
                "130–200 hart, > 200 sehr hart. Trainings ohne Puls werden aus Sportart und Dauer geschätzt."
        MetricKey.VO2MAX ->
            "Priorität: 1) VO2max-Messung der Uhr (falls über Health Connect verfügbar). 2) Aus deinen Läufen: ACSM-Laufgleichung " +
                "(VO2 = 3,5 + 0,2 × Geschwindigkeit in m/min) und Herzfrequenzreserve (Swain & Leutholtz 1997), Median der letzten 5 Läufe. " +
                "3) Uth-Formel (15,3 × HFmax / Ruhepuls). Kategorien: Cooper-Institut-Normwerte. Fitnessalter: Altersmittelwerte der " +
                "HUNT-Studie (Loe et al. 2013). Pace je Herzfrequenzzone: Laufgeschwindigkeit ≈ k × Anteil der Herzfrequenzreserve; " +
                "k = Median aus deinen letzten Läufen (Ø Tempo ÷ Ø %HFR), ohne Läufe aus der VO2max ((VO2max − 3,5) ÷ 0,2). Gilt für " +
                "flaches Gelände – bei Hitze, Steigungen oder Müdigkeit ist der Puls bei gleichem Tempo höher."
        MetricKey.ACTIVITY ->
            "Schritte und Kalorien kommen direkt aus Samsung Health (über Health Connect, ohne Doppelzählung von Handy und Uhr). " +
                "Aktivminuten nach WHO: Minuten mit ≥ 40 % Herzfrequenzreserve zählen einfach, ab 60 % doppelt; Ziel 150 pro Woche."
        MetricKey.VITALS ->
            "Mittelwert der während deines Schlafs gemessenen Werte. Normal: SpO2 95–100 %, Atemfrequenz 12–20 /min. " +
                "Ein Anstieg der Atemfrequenz um ≥ 1 /min und 2 Standardabweichungen über deinem Schnitt fließt in die Frühwarnung ein."
        MetricKey.BODY ->
            "BMI = Gewicht / Größe² (WHO-Kategorien). Körperfett-Kategorien nach American Council on Exercise. Neueste Messung aus Health Connect " +
                "bzw. deine Angaben im Profil."
        MetricKey.REGENERATION ->
            "Erholungsdauer je Training (Schätzung) = 18 h × (TRIMP ÷ dein Fitness-Niveau)^1,1, +12 h bei ≥ 25 % Zeit in Zone 4–5, Krafttraining " +
                "mind. 30 h. Regenerationsindex = 60 % Zeitfortschritt + 40 % autonome Erholung (HRV/Ruhepuls relativ zu deinem Normalwert). " +
                "Ermüdungsindex = 50 × Ermüdung (ATL) ÷ Fitness (CTL). Belastungs-/Erholungsverhältnis = (ATL ÷ CTL) ÷ (Ø Erholung 7 Tage ÷ 65). " +
                "Überlastungsindikator: Anzahl gleichzeitiger Warnsignale (ACWR > 1,3, Monotonie > 2, HRV-Trend, Ruhepuls-Trend, niedrige Erholung, " +
                "Schlafdefizit, hohe Ermüdung, sinkende Bereitschaft)."
        MetricKey.HEART_HEALTH ->
            "Punkteskalen der American Heart Association „Life's Essential 8“ (Lloyd-Jones et al. 2022): Bewegung (≥ 150 min/Woche = 100), Schlaf " +
                "(7–9 h = 100), BMI (< 25 = 100), Blutdruck (< 120/80 = 100), Nikotin (nie = 100). Ergänzt um Ruhepuls (< 60 = 100; Zhang et al. 2016) " +
                "und VO2max-Kategorie (Kodama et al. 2009). Ergebnis = Mittelwert der erfassten Bereiche. Ernährung, Blutfette und Blutzucker " +
                "können nicht gemessen werden; die Samsung-Gefäßbelastung ist nicht abrufbar."
        MetricKey.HEART ->
            "Ø Puls: zeitgewichtet über 10-Minuten-Fenster. Nächtliche Absenkung = (Ø Puls am Vorabend − Ø Puls im Schlaf) ÷ Ø Puls am Vorabend " +
                "(abgeschwächt < 10 %, Ben-Dov et al. 2007). Herzfrequenzerholung = Pulsabfall in 60 s nach Belastungsende bzw. nach der stärksten " +
                "Belastungsspitze (Cole et al. 1999). Stabilität = Standardabweichung des Ruhepulses über 14 Tage; HRV-Schwankung = Variationskoeffizient " +
                "der logarithmierten HRV (Plews et al. 2012)."
        MetricKey.STRESS ->
            "Schätzung: Für jede Pulsmessung außerhalb von Trainings in Stunden mit < 100 Schritten wird die Erhöhung über deinem Ruhepuls berechnet " +
                "(in % der Herzfrequenzreserve). 5 % = 0 Punkte, 30 % = 100 Punkte. Tageswert = Mittelwert. Der Samsung-Stresswert (HRV-basiert) ist " +
                "über Health Connect nicht verfügbar – dieser Wert ist daher nicht identisch damit."
        MetricKey.PERFORMANCE ->
            "Aerobe Effizienz (Efficiency Factor) = Geschwindigkeit ÷ Ø Puls bei lockeren Läufen (50–85 % HFR); Vergleich der letzten 4 Wochen " +
                "mit den 4 Wochen davor. Laktatschwelle (Schätzung, Friel-Methode) = höchster 20-Minuten-Pulsschnitt, wenn ≥ 85 % HFmax. Anaerobe " +
                "Leistung ist ohne Leistungs-/Laktatdaten nicht messbar – angezeigt wird stattdessen die Zeit in Zone 4–5."
        MetricKey.FORECAST ->
            "Erholung(morgen) − Ø = φ × (Erholung(heute) − Ø) + β × relative Belastung; φ und β werden ab 21 Tagen per Regression aus deinen Daten " +
                "geschätzt. Fitness, Ermüdung und ACWR werden mit dem Banister-Modell fortgeschrieben. Bereich = 80-%-Prognoseintervall."
        MetricKey.BASELINES ->
            "Normalbereich = Mittelwert ± 1 Standardabweichung der letzten 30 Tage (ohne heute). z-Wert = Abweichung in Standardabweichungen: " +
                "|z| < 1 normal, 1–2 leicht erhöht/erniedrigt, ≥ 2 ungewöhnlich."
        MetricKey.CORRELATIONS ->
            "Pearson-Korrelation r zwischen zwei Messgrößen (z. B. Belastung heute → HRV in der Nacht danach), Signifikanz über Fisher-z-Test. " +
                "Als Zusammenhang gilt |r| ≥ 0,2 bei p < 0,1. Zusätzlich Vergleich des oberen und unteren Drittels der Tage."
        MetricKey.TRENDS ->
            "Wochenmittelwerte über bis zu 52 Wochen, Vergleich der letzten 30 mit den 30 Tagen davor, der letzten 90 mit den 90 davor und mit dem " +
                "Wert vor einem Jahr. Ältere Tageswerte speichert die App lokal auf deinem Handy."
        MetricKey.ENERGY ->
            "Angelehnt an das Prinzip von Garmin Body Battery (Firstbeat), aber mit offenem Rechenweg: Startwert beim Aufwachen = dein " +
                "Erholungswert. Danach in 15-Minuten-Schritten: Wachzeit −1,2 Punkte/h; Stress in Ruhephasen (Stunden mit < 100 Schritten) " +
                "bis −3,5 Punkte/h, proportional zum Stresswert aus Puls über Ruhepuls; Bewegung im Alltag und Training −0,2 Punkte je " +
                "Trainingspunkt (TRIMP); sehr entspannte Ruhe (Stresswert < 10) lädt bis +2 Punkte/h auf. Die Trainingsbereitschaft sinkt " +
                "über den Tag: durch Trainings um 50 × (1 − e^(−TRIMP/150)) Punkte – die Trainingspunkte zählen bei niedriger Fitness stärker " +
                "(Faktor 40 ÷ Fitness-Wert, 0,7–1,4) und klingen mit 24 h Zeitkonstante ab (leicht ≈ −12, fordernd ≈ −24, hart ≈ −32) –, " +
                "dazu um die Hälfte der Belastung durch Stress und Alltag (nicht durch die reine Wachzeit). Ohne HRV-Messung " +
                "tagsüber ist das eine Schätzung – aussagekräftig ist vor allem der Verlauf."
    }
}
