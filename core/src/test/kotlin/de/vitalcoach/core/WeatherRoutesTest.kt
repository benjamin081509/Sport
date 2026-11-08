package de.vitalcoach.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

private fun check2(cond: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, cond)

class WeatherRoutesTest {
    private val meteo = """
        {"latitude":48.78,"longitude":9.18,"timezone":"Europe/Berlin",
         "hourly":{"time":["2026-11-08T17:00","2026-11-08T18:00","2026-11-08T19:00"],
                   "temperature_2m":[6.1,5.4,4.9],"precipitation_probability":[20,85,90],
                   "precipitation":[0.0,1.6,2.1],"weather_code":[3,63,65],"wind_gusts_10m":[20.0,31.5,null]},
         "daily":{"time":["2026-11-08","2026-11-09"],"sunset":["2026-11-08T16:47","2026-11-09T16:45"]}}
    """.trimIndent()

    @Test
    fun parsesOpenMeteoAndJudgesWeather() {
        val f = Weather.parse(meteo)
        assertNotNull(f)
        f!!
        assertEquals(3, f.hours.size)
        assertEquals(LocalTime.of(16, 47), f.sunset[java.time.LocalDate.of(2026, 11, 8)])
        val h = Weather.at(f, LocalDateTime.of(2026, 11, 8, 17, 50))!!
        assertEquals(18, h.time.hour)
        val v = Weather.verdict(h, f.sunset[h.time.toLocalDate()])
        check2(v.bad && v.reason!!.startsWith("Regen"), v.toString())
        check2(v.dark, "nach Sonnenuntergang")
        check2(v.summary.startsWith("18:00: Regen, 5 °C"), v.summary)
        val dry = Weather.verdict(f.hours[0])
        check2(!dry.bad, dry.toString())
        assertEquals(null, Weather.at(f, LocalDateTime.of(2026, 11, 9, 12, 0)))
        assertEquals(null, Weather.parse("kein json"))
        check2(Weather.verdict(WeatherHour(LocalDateTime.of(2026, 1, 1, 18, 0), -8.0, 0, 0.0, 0, 5.0)).bad, "Frost")
        check2(Weather.verdict(WeatherHour(LocalDateTime.of(2026, 1, 1, 18, 0), 2.0, 40, 0.2, 73, 5.0)).reason == "Schnee")
    }

    @Test
    fun loopGeometryAndLinks() {
        val start = GeoPoint(48.7758, 9.1829)
        val north = Routes.destination(start, 0.0, 1.0)
        assertEquals(1.0, Routes.distanceKm(start, north), 0.001)
        check2(north.lat > start.lat && Math.abs(north.lon - start.lon) < 1e-9)
        val r = Routes.initialRadiusKm(6.0)
        val wps = Routes.loopWaypoints(start, 90.0, r)
        assertEquals(4, wps.size)
        assertEquals(start, wps.first())
        assertEquals(start, wps.last())
        // Luftlinie × Umweg ≈ Ziel
        assertEquals(6.0, Routes.pathKm(wps) * Routes.DETOUR, 0.05)
        check2(wps[1].lon > start.lon && wps[2].lon > start.lon, "Wegpunkte im Osten")
        val url = Routes.googleMapsUrl(wps)
        check2(url.startsWith("https://www.google.com/maps/dir/?api=1&origin=48.775800,9.182900&destination=48.775800,9.182900&waypoints="), url)
        check2(url.endsWith("&travelmode=walking") && url.contains("%7C"), url)
        val osrm = Routes.osrmUrl("https://routing.openstreetmap.de/routed-foot/route/v1/driving/", wps)
        check2(osrm.contains("9.182900,48.775800;") && osrm.endsWith("?overview=full&geometries=geojson&steps=false"), osrm)
        assertEquals("Osten", Routes.directionName(90))
        assertEquals("Nordwesten", Routes.directionName(-45))
    }

    @Test
    fun parsesOsrmAndDetectsOutAndBack() {
        val json = """{"code":"Ok","routes":[{"geometry":{"coordinates":[[9.1829,48.7758],[9.1900,48.7800],[9.1950,48.7750],[9.1829,48.7758]],"type":"LineString"},
            "distance":3456.7,"duration":2500}],"waypoints":[{"location":[9.1830,48.7759],"name":"A"},{"location":[9.19,48.78],"name":"B"}]}"""
        val (path, km, snapped) = Routes.parseOsrm(json)!!
        assertEquals(4, path.size)
        assertEquals(3.4567, km, 1e-6)
        assertEquals(GeoPoint(48.7759, 9.1830), snapped.first())
        assertEquals(null, Routes.parseOsrm("""{"code":"NoRoute"}"""))
        // Hin und zurück auf derselben Linie → hoher Überlappungsanteil; Rundkurs → gering
        val line = (0..40).map { GeoPoint(48.0 + it * 0.0005, 9.0) }
        val outBack = line + line.reversed()
        check2(Routes.overlap(outBack) > 0.3, "out&back=${Routes.overlap(outBack)}")
        val square = (0..20).map { GeoPoint(48.0 + it * 0.0005, 9.0) } + (0..20).map { GeoPoint(48.01, 9.0 + it * 0.0007) } +
            (0..20).map { GeoPoint(48.01 - it * 0.0005, 9.014) } + (0..20).map { GeoPoint(48.0, 9.014 - it * 0.0007) }
        check2(Routes.overlap(square) < 0.1, "loop=${Routes.overlap(square)}")
        val opt = Routes.option(0, listOf(GeoPoint(48.0, 9.0)), outBack, 4.0, 390.0)
        check2(opt.name.contains("hin & zurück") && opt.minutes == 26, opt.toString())
    }
}
