package de.vitalcoach.app

import android.content.Context
import de.vitalcoach.core.Sex
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
}
