# VitalCoach – dein persönlicher Körper-Coach für die Galaxy Watch

VitalCoach ist eine Android-App für dein **Samsung Galaxy S23**. Sie liest die Daten deiner **Galaxy Watch 8** (über Samsung Health → Health Connect) und berechnet daraus deutlich mehr, als Samsung Health anzeigt. Zu jedem Wert gibt es eine verständliche **Bewertung mit konkreten Tipps**.

| Bereich | Was du bekommst |
|---|---|
| **Heute** | **Energie** über den Tag (wie ein Akku: morgens aus der Erholung, sinkt durch Training, Stress und Alltag), Trainingsbereitschaft *jetzt*, Erholung, Schlaf-Score, **Tages-Briefing** (wichtigste Veränderungen + mögliche Erklärungen), **Prognose** der Bereitschaft für 1–3 Tage, Frühwarnungen, Kacheln mit Herkunftsangabe |
| **Erholung** | Persönlicher Erholungswert (HRV, Ruhepuls, Schlaf, Atmung, Trainingsbelastung), Regenerationsindex & Erholungsdauer, Ermüdungsindex, Belastungs-/Erholungsverhältnis, Überlastungsindikator, HRV-Trend, Ruhepuls-Abweichung |
| **Training** | Empfehlung für heute, Fitness/Ermüdung/Form, ACWR, Belastung 7/28/90 Tage, Intensitätsverteilung, Monotonie, Analyse jedes Trainings |
| **Schlaf** | Schlafbedarf heute Nacht inkl. Bettzeit, persönlicher Schlafbedarf, Schlaf-Konsistenz-Score (SRI), Schlafschuld 7/14 Tage, Effizienz, Phasen, nächtliche Erholung (Pulsabsenkung, Tiefpunkt, HRV, Pulskurve) |
| **Analysen** | Herzgesundheit (AHA Life's Essential 8), Herz & Kreislauf (Ø Puls, HRV-/Ruhepuls-Trends bis 1 Jahr, Pulserholung HRR, Stabilität, Zonen), Stress-Index, Fitness & Leistung (VO2max, Fitnessalter, aerobe Effizienz, Laktatschwelle, Pace), Bewegung & Alltag (inaktive Zeit, Tagesverteilung, Wochenbilanz, Alltagsbelastung), Langzeit-Trends, persönliche Normalwerte, Zusammenhänge in deinen Daten |
| **Benachrichtigungen** | Morgens (Standard 7:30) und abends (21:30) eine übersichtliche Zusammenfassung: Schlaf, Erholung, Empfehlung bzw. Energie, Training, Schritte, Stress, Schlafbedarf – Uhrzeiten in den Einstellungen änderbar |
| **Diagramme** | Alle Verläufe antippen oder mit dem Finger abfahren: eine Blase zeigt Datum bzw. Uhrzeit („Gestern“, „vor 1 h“) und die Werte |
| **KI-Coach** | Eingebauter Coach (offline, kostenlos) **plus** optional eine ausführliche Analyse durch die KI *Claude*, der du auch eigene Fragen stellen kannst |

Jeder Wert ist gekennzeichnet als **Gemessen** (Originaldaten der Uhr), **Berechnet** (aus mehreren Messwerten) oder **Geschätzt** (Schätzung/Prognose).

Alle Formeln stammen aus der Sportwissenschaft. In der App unter **Mehr → So rechnet VitalCoach** findest du alle Formeln und Quellen.

---

## 📲 Installation (komplett am Handy)

1. Öffne auf deinem Handy diese GitHub-Seite und tippe rechts bzw. unten auf **Releases**. Du kannst auch direkt https://github.com/benjamin081509/Sport/releases/latest öffnen.
2. Tippe bei der neuesten Version unter **Assets** auf **`VitalCoach.apk`**.
3. Öffne die heruntergeladene Datei. Wenn Android fragt, erlaube einmalig **„Apps aus dieser Quelle installieren“** (für Chrome bzw. „Eigene Dateien“).
4. Tippe auf **Installieren**. Erscheint eine Warnung von Google Play Protect, tippe auf „Trotzdem installieren“. Die App ist selbst gebaut und deshalb nicht im Play Store.

**Updates:** Einfach die neueste `VitalCoach.apk` herunterladen und darüber installieren. Deine Einstellungen bleiben erhalten.

## ⚙️ Einrichtung (einmalig, ca. 3 Minuten)

1. **Samsung Health → Health Connect:** Samsung Health öffnen → Menü → *Einstellungen* → *Health Connect*. Dort erlauben, dass Samsung Health alle Daten (Aktivität, Schlaf, Vitalwerte, Körpermaße) **schreibt**.
2. **VitalCoach öffnen** → *Zugriff erlauben* → im Fenster **„Alle zulassen“** wählen.
3. **Profil ausfüllen:** Geburtsjahr und Geschlecht sind für korrekte Berechnungen wichtig.
4. **Auf der Uhr** (Galaxy Wearable-App → Uhreinstellungen → Samsung Health):
   - Herzfrequenz: **„Kontinuierlich messen“**
   - Schlaf: Aufzeichnung aktiv, **Sauerstoffsättigung im Schlaf** messen
   - Trainings möglichst auf der Uhr starten (dann gibt es Pulsdaten in hoher Auflösung)

> In den ersten **7 Tagen** lernt die App deine persönlichen Normalwerte. Danach werden Erholung und Warnungen deutlich genauer.

## 🤖 KI-Coach mit Claude (optional)

Die eingebaute Bewertung funktioniert ohne Internet und kostet nichts. Für eine ausführliche, frei formulierte Analyse und eigene Fragen:

1. Auf https://console.anthropic.com ein Konto anlegen und etwas Guthaben aufladen (eine Analyse kostet je nach Modell etwa 1–10 Cent).
2. Unter *API Keys* einen Schlüssel erstellen.
3. In VitalCoach unter **Mehr → KI-Coach** einfügen und speichern.

Standardmodell ist **Claude Opus 5.5**, die beste Qualität. Günstiger geht es mit Sonnet 5.5 oder Haiku 5.5. Übertragen wird nur eine anonyme Zusammenfassung deiner Kennzahlen, und auch das nur, wenn du auf „KI-Analyse“ tippst.

## 🔒 Datenschutz

- VitalCoach liest nur aus Health Connect und schreibt oder ändert nichts.
- Alle Berechnungen laufen auf deinem Handy. Es gibt keinen Server, kein Konto und keine Werbung.
- **Kein Medizinprodukt:** Die Werte dienen der Information und der Trainingssteuerung. Sie ersetzen keine ärztliche Diagnose.

## ❓ Häufige Fragen

**Die App zeigt keine Daten.** Prüfe in Samsung Health → Einstellungen → Health Connect, ob alle Kategorien freigegeben sind. Öffne danach einmal Samsung Health, damit synchronisiert wird. Tippe dann in VitalCoach oben rechts auf ↻.

**Keine HRV-Werte?** Ob Samsung Health die Herzfrequenzvariabilität an Health Connect weitergibt, hängt von der Samsung-Health-Version ab. Ohne HRV berechnet VitalCoach die Erholung aus Ruhepuls, Schlaf und Atemfrequenz. Sobald HRV-Daten kommen, nutzt die App sie automatisch.

**Kann ich die App ausprobieren, ohne dass schon Daten da sind?** Ja: **Mehr → Demo-Modus** zeigt realistische Beispieldaten.

---

## 🛠️ Für Änderungen (ohne Programmierkenntnisse)

Die App wird komplett automatisch gebaut. Bei jeder Änderung am Code erstellt GitHub über *Actions* eine neue `VitalCoach.apk` und veröffentlicht sie unter *Releases*. Änderungswünsche beschreibst du einfach Claude Code, zum Beispiel in der Claude-App am Handy. Nach ein paar Minuten liegt dann die neue Version zum Download bereit.

Technischer Aufbau:

- `core/`: Rechen-Engine in reinem Kotlin (Erholung, Schlaf, Belastung, VO2max, Coach-Texte, KI-Anbindung) mit Unit-Tests
- `app/`: Android-App (Jetpack Compose, Health Connect)
- `.github/workflows/build.yml`: automatischer Build und Release
- `keystore/vitalcoach.jks`: Signaturschlüssel, damit sich Updates über die alte Version installieren lassen. Er ist nur für diese private App gedacht.
