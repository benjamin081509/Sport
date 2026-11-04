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
}

/** Erklärungen „Was ist das?“ und „So wird es berechnet“ – in einfacher Sprache mit Quellen. */
object Explain {

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
    }

    fun how(key: MetricKey): String = when (key) {
        MetricKey.RECOVERY ->
            "Für HRV, Ruhepuls und Atemfrequenz wird berechnet, wie stark der heutige Wert von deinem 30-Tage-Schnitt abweicht " +
                "(in Standardabweichungen, HRV logarithmiert nach Plews et al. 2012). Normal = 65 Punkte, jede Standardabweichung " +
                "besser/schlechter = ±17,5 Punkte. Gewichtung: HRV 40 %, Ruhepuls 25 %, Schlaf-Score 30 %, Atemfrequenz 5 %. " +
                "Ohne HRV: Ruhepuls 45 %, Schlaf 45 %, Atemfrequenz 10 %. Die ersten 4–7 Tage dienen der Kalibrierung."
        MetricKey.READINESS ->
            "Erholung 50 % + Belastungssteigerung 20 % (Acute:Chronic Workload Ratio, exponentiell gewichtet nach Williams et al. 2017; " +
                "optimal 0,8–1,3, ab 1,5 erhöhtes Verletzungsrisiko nach Gabbett 2016) + Form 20 % (TSB relativ zur Fitness, Banister-Modell) + " +
                "Schlafschuld 10 %. Bei Warnzeichen für einen Infekt wird der Wert auf maximal 30 begrenzt."
        MetricKey.SLEEP ->
            "Dauer 50 % (100 Punkte ab deinem Schlafziel, 0 Punkte bei ≤ 4 h), Effizienz 15 % (100 Punkte ab 92 %), Tiefschlaf 12,5 % " +
                "(100 Punkte ab 15 %), REM 12,5 % (100 Punkte ab 20 %), Regelmäßigkeit 10 % (Abweichung der Schlafmitte von deinem " +
                "14-Tage-Median, bis 30 min = 100 Punkte). Referenzwerte: AASM/SRS 2015, National Sleep Foundation (Ohayon et al. 2017)."
        MetricKey.RESTING_HR ->
            "Niedrigster gleitender 30-Minuten-Mittelwert der Herzfrequenz während deines Hauptschlafs (mind. 3 Messwerte). " +
                "Ohne Schlafdaten: Ruhepuls-Messung der Uhr bzw. niedrigster 30-Minuten-Schnitt des Tages. Trend = lineare Regression über 60 Tage."
        MetricKey.HRV ->
            "RMSSD-Werte, die Samsung Health während des Schlafs misst, als Nacht-Mittelwert. Dein Normalbereich = Mittelwert ± 1 " +
                "Standardabweichung der letzten 30 Tage (auf logarithmischer Skala, weil HRV-Werte schief verteilt sind)."
        MetricKey.LOAD ->
            "TRIMP nach Banister: Minuten × HFR × 0,64 × e^(1,92 × HFR) für Männer bzw. 0,86 × e^(1,67 × HFR) für Frauen; HFR = " +
                "(Puls − Ruhepuls) / (HFmax − Ruhepuls). Berechnet Sekunde für Sekunde aus deinen Pulsdaten. Fitness (CTL) und Ermüdung (ATL) " +
                "sind exponentiell gewichtete Mittel über 42 bzw. 7 Tage; Form (TSB) = CTL − ATL. Monotonie = Ø Tageslast / Standardabweichung " +
                "(Foster 1998). Trainings ohne Puls werden aus Sportart und Dauer geschätzt."
        MetricKey.VO2MAX ->
            "Priorität: 1) VO2max-Messung der Uhr (falls über Health Connect verfügbar). 2) Aus deinen Läufen: ACSM-Laufgleichung " +
                "(VO2 = 3,5 + 0,2 × Geschwindigkeit in m/min) und Herzfrequenzreserve (Swain & Leutholtz 1997), Median der letzten 5 Läufe. " +
                "3) Uth-Formel (15,3 × HFmax / Ruhepuls). Kategorien: Cooper-Institut-Normwerte. Fitnessalter: Altersmittelwerte der " +
                "HUNT-Studie (Loe et al. 2013)."
        MetricKey.ACTIVITY ->
            "Schritte und Kalorien kommen direkt aus Samsung Health (über Health Connect, ohne Doppelzählung von Handy und Uhr). " +
                "Aktivminuten nach WHO: Minuten mit ≥ 40 % Herzfrequenzreserve zählen einfach, ab 60 % doppelt; Ziel 150 pro Woche."
        MetricKey.VITALS ->
            "Mittelwert der während deines Schlafs gemessenen Werte. Normal: SpO2 95–100 %, Atemfrequenz 12–20 /min. " +
                "Ein Anstieg der Atemfrequenz um ≥ 1 /min und 2 Standardabweichungen über deinem Schnitt fließt in die Frühwarnung ein."
        MetricKey.BODY ->
            "BMI = Gewicht / Größe² (WHO-Kategorien). Körperfett-Kategorien nach American Council on Exercise. Neueste Messung aus Health Connect " +
                "bzw. deine Angaben im Profil."
    }
}
