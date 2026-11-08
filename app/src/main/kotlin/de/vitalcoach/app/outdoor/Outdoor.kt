package de.vitalcoach.app.outdoor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import de.vitalcoach.app.BuildConfig
import de.vitalcoach.core.GeoPoint
import de.vitalcoach.core.RouteOption
import de.vitalcoach.core.Routes
import de.vitalcoach.core.Weather
import de.vitalcoach.core.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.abs

/** Einfache HTTP-Abfrage (nur Wetter und Routen – es werden keine Gesundheitsdaten gesendet). */
object Http {
    private val USER_AGENT = "VitalCoach/${BuildConfig.VERSION_NAME} (Android; private Trainings-App)"

    suspend fun get(url: String, timeoutMs: Int = 15_000): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                c.disconnect()
            }
        } catch (t: Throwable) {
            null
        }
    }
}

/** Aktueller Standort über den Android-Standortdienst (ohne Google-Dienste). */
object Locator {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): GeoPoint? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
                LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                null
            } else {
                withTimeoutOrNull(15_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            }
        } else {
            null
        }
        val loc = fresh ?: last ?: return null
        val p = GeoPoint(loc.latitude, loc.longitude)
        de.vitalcoach.app.SettingsStore(ctx).homeLocation = p
        return p
    }
}

object WeatherRepo {
    suspend fun forecast(p: GeoPoint): WeatherForecast? = Http.get(Weather.openMeteoUrl(p.lat, p.lon))?.let { Weather.parse(it) }
}

/**
 * Sucht Rundkurse in vier Himmelsrichtungen über den freien Fußgänger-Router von OpenStreetMap
 * (routing.openstreetmap.de, höchstens eine Anfrage pro Sekunde). Jede Runde wird einmal nachjustiert,
 * damit die Länge zur Zieldistanz passt.
 */
object RouteFinder {
    private const val OSRM_FOOT = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/"
    private const val TOLERANCE = 0.12

    suspend fun find(
        start: GeoPoint,
        targetKm: Double,
        paceSec: Double,
        onRoute: (RouteOption) -> Unit,
        onProgress: (Int, Int) -> Unit,
    ): Int {
        var found = 0
        Routes.BEARINGS.forEachIndexed { i, bearing ->
            onProgress(i, Routes.BEARINGS.size)
            var radius = Routes.initialRadiusKm(targetKm)
            var best: RouteOption? = null
            for (attempt in 0 until 2) {
                val wps = Routes.loopWaypoints(start, bearing.toDouble(), radius)
                val json = Http.get(Routes.osrmUrl(OSRM_FOOT, wps))
                delay(1_100) // Nutzungsregeln des Dienstes: max. 1 Anfrage/Sekunde
                val parsed = json?.let { Routes.parseOsrm(it) } ?: break
                val (path, km, snapped) = parsed
                val opt = Routes.option(bearing, if (snapped.size == wps.size) snapped else wps, path, km, paceSec)
                if (best == null || abs(km - targetKm) < abs(best.distanceKm - targetKm)) best = opt
                if (abs(km - targetKm) / targetKm <= TOLERANCE || km <= 0.1) break
                radius *= targetKm / km
            }
            best?.let {
                found++
                onRoute(it)
            }
        }
        onProgress(Routes.BEARINGS.size, Routes.BEARINGS.size)
        return found
    }
}
