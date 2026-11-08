package de.vitalcoach.core

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

/** Ein vorgeschlagener Rundkurs. */
data class RouteOption(
    val name: String,
    val bearing: Int,
    /** Start, Wegpunkte, Ziel (= Start) – für den Google-Maps-Link. */
    val waypoints: List<GeoPoint>,
    /** Strecke entlang von Wegen und Straßen. */
    val path: List<GeoPoint>,
    val distanceKm: Double,
    val minutes: Int,
    /** Anteil der Strecke, der doppelt gelaufen wird (hin und zurück). */
    val overlap: Double,
)

/**
 * Rundkurse um den Standort: drei Punkte (Start + zwei Wegpunkte in einer Himmelsrichtung) werden über
 * einen Fußgänger-Router (OSRM, OpenStreetMap-Daten) zu einer echten Strecke verbunden und so skaliert,
 * dass die Länge zur heutigen Einheit passt.
 */
object Routes {
    private const val EARTH_KM = 6371.0
    /** Umweg-Faktor: echte Wege sind länger als die Luftlinie. */
    const val DETOUR = 1.3
    /** Halber Öffnungswinkel des Dreiecks (Grad). */
    private const val SPREAD = 35.0
    val BEARINGS = listOf(0, 90, 180, 270)

    fun destination(p: GeoPoint, bearingDeg: Double, distKm: Double): GeoPoint {
        val d = distKm / EARTH_KM
        val b = Math.toRadians(bearingDeg)
        val la1 = Math.toRadians(p.lat)
        val lo1 = Math.toRadians(p.lon)
        val la2 = asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
        val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return GeoPoint(Math.toDegrees(la2), Math.toDegrees(lo2))
    }

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun pathKm(path: List<GeoPoint>): Double = path.zipWithNext { a, b -> distanceKm(a, b) }.sum()

    /** Luftlinien-Umfang des Dreiecks pro km Radius: r + 2 r sin(Spreizung) + r. */
    private val PERIMETER_PER_R = 2.0 + 2.0 * sin(Math.toRadians(SPREAD))

    /** Erster Radius für eine gewünschte Länge. */
    fun initialRadiusKm(targetKm: Double): Double = targetKm / (PERIMETER_PER_R * DETOUR)

    /** Start → Wegpunkt links → Wegpunkt rechts → Start. */
    fun loopWaypoints(start: GeoPoint, bearingDeg: Double, radiusKm: Double): List<GeoPoint> = listOf(
        start,
        destination(start, bearingDeg - SPREAD, radiusKm),
        destination(start, bearingDeg + SPREAD, radiusKm),
        start,
    )

    /** OSRM-Anfrage (Profil steckt in [base], z. B. „…/routed-foot/route/v1/driving/“). */
    fun osrmUrl(base: String, pts: List<GeoPoint>): String =
        base + pts.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat) } +
            "?overview=full&geometries=geojson&steps=false"

    /** Strecke, Länge (km) und auf Wege „eingerastete“ Wegpunkte aus der OSRM-Antwort. */
    fun parseOsrm(json: String): Triple<List<GeoPoint>, Double, List<GeoPoint>>? = try {
        val root = ObjectMapper().readTree(json)
        if (root.path("code").asText() != "Ok") {
            null
        } else {
            val route = root.path("routes")[0]
            val coords = route.path("geometry").path("coordinates")
            val path = (0 until coords.size()).map { GeoPoint(coords[it][1].asDouble(), coords[it][0].asDouble()) }
            val wps = root.path("waypoints")
            val snapped = (0 until wps.size()).map { val l = wps[it].path("location"); GeoPoint(l[1].asDouble(), l[0].asDouble()) }
            if (path.size < 2) null else Triple(path, route.path("distance").asDouble() / 1000.0, snapped)
        }
    } catch (t: Throwable) {
        null
    }

    /** Link für Google Maps (Fußweg) mit denselben Wegpunkten – dort ist auch die Satellitenansicht verfügbar. */
    fun googleMapsUrl(waypoints: List<GeoPoint>): String {
        fun f(p: GeoPoint) = String.format(Locale.US, "%.6f,%.6f", p.lat, p.lon)
        val start = waypoints.first()
        val mid = waypoints.drop(1).dropLast(1)
        return "https://www.google.com/maps/dir/?api=1&origin=${f(start)}&destination=${f(waypoints.last())}" +
            (if (mid.isNotEmpty()) "&waypoints=" + mid.joinToString("%7C") { f(it) } else "") +
            "&travelmode=walking"
    }

    fun directionName(bearing: Int): String = when (((bearing % 360) + 360) % 360) {
        in 0..22, in 338..359 -> "Norden"
        in 23..67 -> "Nordosten"
        in 68..112 -> "Osten"
        in 113..157 -> "Südosten"
        in 158..202 -> "Süden"
        in 203..247 -> "Südwesten"
        in 248..292 -> "Westen"
        else -> "Nordwesten"
    }

    /**
     * Anteil der Strecke, der doppelt gelaufen wird: Punkte alle ~40 m auf ein ~30-m-Raster legen und zählen,
     * welche Rasterfelder in zwei getrennten Abschnitten vorkommen.
     */
    fun overlap(path: List<GeoPoint>): Double {
        if (path.size < 3) return 0.0
        val samples = ArrayList<GeoPoint>()
        samples += path.first()
        var acc = 0.0
        for ((a, b) in path.zipWithNext()) {
            acc += distanceKm(a, b)
            if (acc >= 0.04) {
                samples += b
                acc = 0.0
            }
        }
        val cellLat = 0.0003
        val cellLon = 0.0003 / cos(Math.toRadians(path.first().lat)).coerceAtLeast(0.1)
        val firstSeen = HashMap<Pair<Long, Long>, Int>()
        var repeated = 0
        samples.forEachIndexed { i, p ->
            val key = (p.lat / cellLat).roundToInt().toLong() to (p.lon / cellLon).roundToInt().toLong()
            val prev = firstSeen[key]
            if (prev == null) firstSeen[key] = i else if (i - prev > 6) repeated++
        }
        return repeated.toDouble() / samples.size
    }

    fun option(bearing: Int, waypoints: List<GeoPoint>, path: List<GeoPoint>, km: Double, paceSec: Double): RouteOption {
        val ov = overlap(path)
        val name = "Runde Richtung ${directionName(bearing)}" + if (ov > 0.35) " (viel hin & zurück)" else ""
        return RouteOption(name, bearing, waypoints, path, km, (km * paceSec / 60.0).roundToInt(), ov)
    }
}
