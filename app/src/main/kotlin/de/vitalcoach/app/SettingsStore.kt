package de.vitalcoach.app

import android.content.Context
import de.vitalcoach.core.DailySnapshot
import de.vitalcoach.core.DigestContent
import de.vitalcoach.core.Nicotine
import de.vitalcoach.core.Sex
import de.vitalcoach.core.SnapshotCodec
import de.vitalcoach.core.Tone
import java.io.File
import de.vitalcoach.core.UserProfile
import de.vitalcoach.core.ai.ClaudeModel

/** Speichert Profil und Einstellungen lokal und privat auf dem Gerät. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("vitalcoach", Context.MODE_PRIVATE)

    var profile: UserProfile
        get() = UserProfile(
            birthYear = prefs.getInt("birthYear", 0).takeIf { it > 0 },
            sex = prefs.getString("sex", null)?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
            heightCm = prefs.getFloat("heightCm", 0f).takeIf { it > 0f }?.toDouble(),
            weightKg = prefs.getFloat("weightKg", 0f).takeIf { it > 0f }?.toDouble(),
            maxHrOverride = prefs.getInt("maxHr", 0).takeIf { it > 0 },
            sleepGoalHours = prefs.getFloat("sleepGoal", 8f).toDouble(),
            stepGoal = prefs.getInt("stepGoal", 8000),
            nicotine = prefs.getString("nicotine", null)?.let { runCatching { Nicotine.valueOf(it) }.getOrNull() },
            bpSystolic = prefs.getInt("bpSys", 0).takeIf { it > 0 },
            bpDiastolic = prefs.getInt("bpDia", 0).takeIf { it > 0 },
        )
        set(p) {
            prefs.edit()
                .putInt("birthYear", p.birthYear ?: 0)
                .putString("sex", p.sex?.name)
                .putFloat("heightCm", (p.heightCm ?: 0.0).toFloat())
                .putFloat("weightKg", (p.weightKg ?: 0.0).toFloat())
                .putInt("maxHr", p.maxHrOverride ?: 0)
                .putFloat("sleepGoal", p.sleepGoalHours.toFloat())
                .putInt("stepGoal", p.stepGoal)
                .putString("nicotine", p.nicotine?.name)
                .putInt("bpSys", p.bpSystolic ?: 0)
                .putInt("bpDia", p.bpDiastolic ?: 0)
                .apply()
        }

    var demoMode: Boolean
        get() = prefs.getBoolean("demo", false)
        set(v) = prefs.edit().putBoolean("demo", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboardingDone", false)
        set(v) = prefs.edit().putBoolean("onboardingDone", v).apply()

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) = prefs.edit().putString("apiKey", v.trim()).apply()

    var model: ClaudeModel
        get() = prefs.getString("model", null)?.let { runCatching { ClaudeModel.valueOf(it) }.getOrNull() } ?: ClaudeModel.OPUS
        set(v) = prefs.edit().putString("model", v.name).apply()

    var lastAiText: String?
        get() = prefs.getString("aiText", null)
        set(v) = prefs.edit().putString("aiText", v).apply()

    var lastAiTime: Long
        get() = prefs.getLong("aiTime", 0L)
        set(v) = prefs.edit().putLong("aiTime", v).apply()

    var lastAiQuestion: String?
        get() = prefs.getString("aiQuestion", null)
        set(v) = prefs.edit().putString("aiQuestion", v).apply()

    // ---------- Tägliche Zusammenfassungen ----------

    fun digestEnabled(kind: String): Boolean = prefs.getBoolean("digestOn_$kind", true)
    fun setDigestEnabled(kind: String, on: Boolean) = prefs.edit().putBoolean("digestOn_$kind", on).apply()

    /** Uhrzeit in Minuten nach Mitternacht. */
    fun digestMinutes(kind: String, default: Int): Int = prefs.getInt("digestMin_$kind", default)
    fun setDigestMinutes(kind: String, minutes: Int) = prefs.edit().putInt("digestMin_$kind", minutes).apply()

    var askedNotifications: Boolean
        get() = prefs.getBoolean("askedNotif", false)
        set(v) = prefs.edit().putBoolean("askedNotif", v).apply()

    var askedBackgroundAccess: Boolean
        get() = prefs.getBoolean("askedBg", false)
        set(v) = prefs.edit().putBoolean("askedBg", v).apply()

    /** Zuletzt in der App berechnete Zusammenfassung – Rückfall, falls Lesen im Hintergrund nicht möglich ist. */
    fun saveDigest(kind: String, c: DigestContent, atMillis: Long, complete: Boolean) {
        val enc = listOf(c.title, c.text, c.tone.name, if (complete) "1" else "0", c.lines.joinToString(LINE_SEP)).joinToString(FIELD_SEP)
        prefs.edit().putString("digest_$kind", enc).putLong("digestAt_$kind", atMillis).apply()
    }

    /** Gespeicherte Zusammenfassung, Zeitpunkt und ob sie vollständig war (bei „Morgen“: Schlaf der letzten Nacht enthalten). */
    fun loadDigest(kind: String): Triple<DigestContent, Long, Boolean>? {
        val enc = prefs.getString("digest_$kind", null) ?: return null
        val f = enc.split(FIELD_SEP)
        if (f.size < 5) return null
        val tone = runCatching { Tone.valueOf(f[2]) }.getOrDefault(Tone.NEUTRAL)
        val lines = f[4].split(LINE_SEP).filter { it.isNotBlank() }
        return Triple(DigestContent(f[0], f[1], lines, tone), prefs.getLong("digestAt_$kind", 0L), f[3] == "1")
    }

    private companion object {
        const val FIELD_SEP = "\u001E"
        const val LINE_SEP = "\u001F"
    }
}

/** Lokaler Verlaufsspeicher für Tageswerte (für Langzeit-Trends). */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.tsv")

    fun load(): List<DailySnapshot> = try {
        if (file.exists()) SnapshotCodec.decode(file.readText()) else emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    fun save(list: List<DailySnapshot>) {
        try {
            val tmp = File(file.parentFile, "history.tmp")
            tmp.writeText(SnapshotCodec.encode(list))
            tmp.renameTo(file)
        } catch (t: Throwable) {
            // Verlauf ist optional – Fehler beim Speichern ignorieren
        }
    }
}
