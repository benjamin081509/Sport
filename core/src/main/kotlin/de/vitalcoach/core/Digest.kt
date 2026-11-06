package de.vitalcoach.core

import kotlin.math.roundToInt

/** Inhalt einer Benachrichtigung: Titel, Kurztext (eingeklappt) und bis zu 6 übersichtliche Zeilen (ausgeklappt). */
data class DigestContent(val title: String, val text: String, val lines: List<String>, val tone: Tone)

/** Morgen- und Abend-Zusammenfassung für die täglichen Benachrichtigungen. */
object Digest {

    const val MAX_LINES = 6

    /** Ob die Nacht zu heute schon in den Daten ist (Samsung Health synchronisiert teils verzögert). */
    fun hasLastNight(r: AnalysisResult): Boolean = r.todayMetrics?.sleep?.date == r.today

    fun morning(r: AnalysisResult): DigestContent {
        val t = r.todayMetrics
        val night = t?.sleep?.takeIf { it.date == r.today }
        val rec = t?.recovery?.value
        val ready = t?.readiness?.value
        val title = buildString {
            append("☀️ Guten Morgen")
            if (rec != null) append(" · Erholung $rec")
            if (ready != null) append(" · Bereitschaft $ready")
        }
        val lines = mutableListOf<String>()
        if (night != null) {
            val diff = night.asleepMin - r.profile.sleepGoalHours * 60
            lines += "😴 Schlaf ${hm(night.asleepMin)} · Score ${night.score}" +
                if (diff <= -30) " (${hm(-diff)} unter Ziel)" else ""
        } else {
            lines += "😴 Schlaf von letzter Nacht noch nicht synchronisiert"
        }
        val rhr = t?.restingHr
        if (rhr != null) {
            val dev = t.rhrBaseline?.let { rhr - it.mean }
            val devText = when {
                dev == null -> ""
                dev >= 3 -> " (+${Fmt.int(dev)} über normal)"
                dev <= -3 -> " (${Fmt.int(dev)} unter normal)"
                else -> " (normal)"
            }
            lines += "❤️ Ruhepuls ${Fmt.int(rhr)}$devText" + (t.hrv?.let { " · HRV ${Fmt.int(it)} ms" } ?: "")
        }
        r.energy?.let { lines += "🔋 Energie ${it.morning} – ${it.label}" }
        val rc = r.load.recommendation
        if (rc != null) {
            lines += "🏃 ${rc.title}" + if (rc.targetLoadHigh > 0) ": ${rc.zone2MinLow}–${rc.zone2MinHigh} min Zone 2" else ""
        }
        r.warnings.firstOrNull()?.let { lines += "⚠️ ${it.title}" }
        r.sleep.debt7H?.let { if (it >= 3) lines += "⏳ Schlafschuld (7 Tage): ${Fmt.num(it, 1)} h" }
        val text = when {
            rc != null -> rc.title
            rec != null -> "Erholung $rec"
            else -> "Tippe für deine Tageswerte"
        }
        return DigestContent(title, text, lines.take(MAX_LINES), t?.readiness?.tone ?: t?.recovery?.tone ?: Tone.NEUTRAL)
    }

    fun evening(r: AnalysisResult): DigestContent {
        val t = r.todayMetrics?.takeIf { it.date == r.today }
        val e = r.energy
        val title = if (e != null) "🌙 Dein Tag · Energie ${e.now} (${signedInt(e.now - e.morning)})" else "🌙 Dein Tag"
        val lines = mutableListOf<String>()
        if (e != null) {
            val parts = listOf("Training" to e.drainTraining, "Stress" to e.drainStress, "Alltag" to e.drainEveryday, "Wachzeit" to e.drainAwake)
                .filter { it.second >= 1.0 }
                .sortedByDescending { it.second }
                .take(3)
                .joinToString(", ") { "${it.first} −${it.second.roundToInt()}" }
            lines += "🔋 Energie ${e.morning} → ${e.now}" + if (parts.isNotEmpty()) " · $parts" else ""
        }
        val todays = r.workouts.filter { it.workout.start.atZone(r.zone).toLocalDate() == r.today && it.durationMin >= 10 }
        lines += if (todays.isEmpty()) {
            "🏃 Heute kein Training"
        } else {
            "🏃 " + todays.reversed().joinToString(" · ") {
                "${it.workout.type.label} ${hm(it.durationMin)} · ${Fmt.int(it.trimp)} Pkt (${Meaning.sessionLevel(it.trimp).label})"
            }
        }
        t?.steps?.let { st ->
            lines += "👣 ${Fmt.int(st)} Schritte" + if (st >= r.profile.stepGoal) " · Ziel erreicht ✓" else " · Ziel ${Fmt.int(r.profile.stepGoal.toLong())}"
        }
        val sv = r.stress.today
        if (sv != null && r.stress.reliable) {
            val emoji = if (sv >= 50) "😰" else "😌"
            lines += "$emoji Stress Ø ${Fmt.int(sv)} – ${HeartExtras.stressLabel(sv).first}"
        }
        val rn = e?.readinessNow
        if (rn != null) lines += "🎯 Trainingsbereitschaft jetzt $rn (morgens ${e.readinessMorning})"
        r.sleep.needTonightMin?.let { need ->
            lines += "🛏️ Schlafbedarf ${hm(need)}" + (r.sleep.recommendedBedtimeMin?.let { " → ins Bett ca. ${Fmt.clock(it)}" } ?: "")
        }
        val text = when {
            todays.isNotEmpty() -> todays.first().let { "${it.workout.type.label} · ${Fmt.int(it.trimp)} Punkte" }
            e != null -> "Energie ${e.morning} → ${e.now}"
            else -> "Tippe für deinen Tagesrückblick"
        }
        return DigestContent(title, text, lines.take(MAX_LINES), e?.tone ?: Tone.NEUTRAL)
    }

    /** Kompakte Dauer, z. B. „5:59 h“. */
    fun hm(minutes: Double): String {
        val m = minutes.roundToInt().coerceAtLeast(0)
        return if (m < 60) "$m min" else String.format("%d:%02d h", m / 60, m % 60)
    }

    private fun signedInt(v: Int): String = if (v > 0) "+$v" else if (v < 0) "−${-v}" else "±0"
}
