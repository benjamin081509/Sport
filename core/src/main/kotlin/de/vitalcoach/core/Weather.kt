package de.vitalcoach.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wetter einer Stunde (Open-Meteo, Ortszeit). */
data class WeatherHour(
    val time: LocalDateTime,
    val tempC: Double?,
    val precipProb: Int?,
    val precipMm: Double?,
    val code: Int?,
    val gustKmh: Double?,
)

data class WeatherForecast(val hours: List<WeatherHour>, val sunset: Map<LocalDate, LocalTime>)

/** Einschätzung fürs Laufen draußen. */
data class WeatherVerdict(val bad: Boolean, val summary: String, val reason: String?, val dark: Boolean)

/**
 * Wetter-Einschätzung für den Lauf: bei Gewitter, Schnee, gefrierendem Regen, kräftigem Regen,
 * starkem Frost oder Sturm wird der Hometrainer vorgeschlagen. Daten: Open-Meteo (frei, ohne Schlüssel).
 */
object Weather {
    fun openMeteoUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(java.util.Locale.US, lat)}&longitude=${"%.3f".format(java.util.Locale.US, lon)}" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_gusts_10m&daily=sunset&forecast_days=2&timezone=auto"

    fun parse(json: String): WeatherForecast? = try {
        val root = ObjectMapper().readTree(json)
        val h = root.path("hourly")
        val times = h.path("time")
        val hours = (0 until times.size()).map { i ->
            WeatherHour(
                time = LocalDateTime.parse(times[i].asText()),
                tempC = h.path("temperature_2m").num(i),
                precipProb = h.path("precipitation_probability").num(i)?.roundToInt(),
                precipMm = h.path("precipitation").num(i),
                code = h.path("weather_code").num(i)?.roundToInt(),
                gustKmh = h.path("wind_gusts_10m").num(i),
            )
        }
        val d = root.path("daily")
        val sunset = (0 until d.path("time").size()).mapNotNull { i ->
            val day = d.path("time")[i]?.asText()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val ss = d.path("sunset")[i]?.asText()?.let { runCatching { LocalDateTime.parse(it).toLocalTime() }.getOrNull() } ?: return@mapNotNull null
            day to ss
        }.toMap()
        if (hours.isEmpty()) null else WeatherForecast(hours, sunset)
    } catch (t: Throwable) {
        null
    }

    private fun JsonNode.num(i: Int): Double? = get(i)?.takeIf { it.isNumber }?.asDouble()

    /** Stunde, die [at] am nächsten liegt (max. 90 min Abstand). */
    fun at(f: WeatherForecast, at: LocalDateTime): WeatherHour? =
        f.hours.minByOrNull { abs(java.time.Duration.between(it.time, at).toMinutes()) }
            ?.takeIf { abs(java.time.Duration.between(it.time, at).toMinutes()) <= 90 }

    fun describe(code: Int?): String = when (code) {
        null -> ""
        0 -> "klar"
        1, 2 -> "leicht bewölkt"
        3 -> "bewölkt"
        45, 48 -> "Nebel"
        51, 53, 55 -> "Niesel"
        56, 57 -> "gefrierender Niesel"
        61, 63 -> "Regen"
        65 -> "starker Regen"
        66, 67 -> "gefrierender Regen"
        71, 73, 75, 77 -> "Schnee"
        80, 81 -> "Regenschauer"
        82 -> "heftige Schauer"
        85, 86 -> "Schneeschauer"
        95, 96, 99 -> "Gewitter"
        else -> "wechselhaft"
    }

    fun verdict(h: WeatherHour, sunset: LocalTime? = null): WeatherVerdict {
        val code = h.code
        val reason = when {
            code != null && code >= 95 -> "Gewitter"
            code != null && code in setOf(56, 57, 66, 67) -> "Glättegefahr durch gefrierenden Regen"
            code != null && code in setOf(71, 73, 75, 77, 85, 86) -> "Schnee"
            code == 65 || code == 82 -> "starker Regen"
            (h.precipMm ?: 0.0) >= 1.0 -> "Regen (${Fmt.num(h.precipMm ?: 0.0, 1)} mm/h)"
            (h.precipProb ?: 0) >= 70 && (h.precipMm ?: 0.0) >= 0.3 -> "Regen sehr wahrscheinlich (${h.precipProb} %)"
            (h.tempC ?: 10.0) <= -5.0 -> "starker Frost (${Fmt.int(h.tempC ?: 0.0)} °C)"
            (h.gustKmh ?: 0.0) >= 60.0 -> "Sturmböen (${Fmt.int(h.gustKmh ?: 0.0)} km/h)"
            else -> null
        }
        val dark = sunset != null && h.time.toLocalTime().isAfter(sunset)
        val parts = listOfNotNull(
            describe(code).takeIf { it.isNotEmpty() },
            h.tempC?.let { "${Fmt.int(it)} °C" },
            h.precipProb?.takeIf { it >= 20 }?.let { "Regen $it %" },
        )
        val summary = String.format("%02d:00", h.time.hour) + ": " + parts.joinToString(", ")
        return WeatherVerdict(reason != null, summary, reason, dark)
    }
}
