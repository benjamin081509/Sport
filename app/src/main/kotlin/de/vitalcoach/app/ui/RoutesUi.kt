@file:OptIn(ExperimentalMaterial3Api::class)

package de.vitalcoach.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.vitalcoach.app.BuildConfig
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.UiState
import de.vitalcoach.app.outdoor.Locator
import de.vitalcoach.app.outdoor.RouteFinder
import de.vitalcoach.core.Coaching
import de.vitalcoach.core.Fmt
import de.vitalcoach.core.GeoPoint
import de.vitalcoach.core.RouteOption
import de.vitalcoach.core.Routes
import de.vitalcoach.core.SessionKind
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint as OsmPoint

private val ROUTE_COLORS = listOf(Palette.Orange, Palette.Blue, Palette.Teal, Palette.Purple)

/** Luftbilder von Esri (World Imagery) – Kachel-Adresse ist z/y/x. */
private val ESRI_IMAGERY = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Luftbild © Esri, Maxar, Earthstar Geographics",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

/** Rundkurse um den aktuellen Standort, passend zur heutigen Einheit. */
@Composable
fun RoutesScreen(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val r = state.result
    val plan = state.runPlan
    val session = remember(plan, r) { if (plan != null && r != null) Coaching.today(plan, r) else null }
    val paceSec = remember(r) { r?.let { Coaching.Zones.of(it).easyPaceSec } ?: Coaching.DEFAULT_PACE_SEC }
    val planKm = session?.takeIf { it.kind != SessionKind.REST && it.targetKm >= 1.0 }?.targetKm ?: roundKm(35 * 60.0 / paceSec)
    var targetKm by rememberSaveable { mutableStateOf(planKm) }
    var start by remember { mutableStateOf<GeoPoint?>(null) }
    val routes = remember { mutableStateListOf<RouteOption>() }
    var selected by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var satellite by rememberSaveable { mutableStateOf(true) }
    var hasPerm by remember { mutableStateOf(Locator.hasPermission(ctx)) }
    var searchNo by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = Locator.hasPermission(ctx)
    }

    LaunchedEffect(hasPerm, targetKm, searchNo) {
        if (!hasPerm) return@LaunchedEffect
        busy = true
        routes.clear()
        selected = 0
        status = "Bestimme deinen Standort …"
        val p = Locator.current(ctx)
        if (p == null) {
            status = "Standort nicht verfügbar – bitte Standort (GPS) einschalten und erneut suchen."
            busy = false
            return@LaunchedEffect
        }
        start = p
        val n = RouteFinder.find(
            p, targetKm, paceSec,
            onRoute = { routes += it },
            onProgress = { i, total -> status = if (i < total) "Suche Rundkurse … ${i + 1}/$total" else null },
        )
        if (n == 0) status = "Keine Strecken gefunden – Internetverbindung prüfen oder eine andere Länge wählen."
        busy = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Strecken für heute") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
                actions = {
                    if (hasPerm && !busy) IconButton(onClick = { searchNo++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Neu suchen") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    session?.let { "${it.kind.emoji} ${it.title}" } ?: "🟢 Lockerer Lauf",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Ziel ≈ ${Fmt.num(targetKm, 1)} km · ≈ ${(targetKm * paceSec / 60).roundToInt()} min in deinem Zone-2-Tempo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(planKm) + listOf(3.0, 5.0, 8.0, 10.0, 12.0).filter { abs(it - planKm) > 0.6 }).forEach { km ->
                        FilterChip(
                            selected = abs(targetKm - km) < 0.05,
                            onClick = { if (!busy) targetKm = km },
                            label = { Text(if (km == planKm) "Plan · ${Fmt.num(km, 1)} km" else "${Fmt.int(km)} km") },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (!hasPerm) {
                SectionCard(modifier = Modifier.padding(16.dp), title = "Standort benötigt", icon = Icons.Filled.MyLocation) {
                    Text(
                        "Für Rundkurse um dich herum braucht VitalCoach deinen Standort. Er bleibt auf deinem Handy – an den Routendienst wird nur der Startpunkt gesendet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Standort erlauben") }
                }
                return@Column
            }
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                RouteMap(start, routes, selected, satellite, Modifier.fillMaxSize())
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .clickable { satellite = !satellite }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (satellite) "Satellit" else "Karte", style = MaterialTheme.typography.labelLarge)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(routes.size) { i ->
                    RouteRow(routes[i], ROUTE_COLORS[i % ROUTE_COLORS.size], i == selected, onSelect = { selected = i }) {
                        openUrl(ctx, Routes.googleMapsUrl(routes[i].waypoints))
                    }
                }
                item {
                    Text(
                        "Strecken: © OpenStreetMap-Mitwirkende · Routing: OSRM / FOSSGIS · ${if (satellite) "Luftbild © Esri, Maxar, Earthstar Geographics" else "Karte © OpenStreetMap"}. " +
                            "Vor dem Lauf kurz prüfen: Wege können gesperrt oder unbeleuchtet sein.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteRow(route: RouteOption, color: Color, selected: Boolean, onSelect: () -> Unit, onMaps: () -> Unit) {
    SectionCard(
        modifier = if (selected) Modifier.border(2.dp, color, RoundedCornerShape(24.dp)) else Modifier,
        onClick = onSelect,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Fmt.num(route.distanceKm, 1)} km · ≈ ${route.minutes} min · Rundkurs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onMaps) { Text("Google Maps") }
        }
    }
}

/** Karte (OpenStreetMap bzw. Luftbild) mit allen Rundkursen; der ausgewählte ist hervorgehoben. */
@Composable
private fun RouteMap(start: GeoPoint?, routes: List<RouteOption>, selected: Int, satellite: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val map = remember {
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        MapView(ctx).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(14.0)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    val routesSnapshot = routes.toList()
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { m ->
            val source = if (satellite) ESRI_IMAGERY else TileSourceFactory.MAPNIK
            if (m.tileProvider.tileSource.name() != source.name()) m.setTileSource(source)
            m.overlays.clear()
            fun line(rt: RouteOption, c: Color, widthDp: Float, alpha: Float) = Polyline(m).apply {
                setPoints(rt.path.map { OsmPoint(it.lat, it.lon) })
                outlinePaint.color = c.copy(alpha = alpha).toArgb()
                outlinePaint.strokeWidth = widthDp * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            routesSnapshot.forEachIndexed { i, rt -> if (i != selected) m.overlays.add(line(rt, ROUTE_COLORS[i % ROUTE_COLORS.size], 4f, 0.55f)) }
            routesSnapshot.getOrNull(selected)?.let { m.overlays.add(line(it, ROUTE_COLORS[selected % ROUTE_COLORS.size], 7f, 1f)) }
            start?.let { s ->
                m.overlays.add(
                    Marker(m).apply {
                        position = OsmPoint(s.lat, s.lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Start / Ziel"
                    },
                )
            }
            m.overlays.add(CopyrightOverlay(ctx))
            val focus = routesSnapshot.getOrNull(selected)?.path ?: emptyList()
            if (focus.size >= 2) {
                val box = BoundingBox.fromGeoPoints(focus.map { OsmPoint(it.lat, it.lon) })
                m.post { m.zoomToBoundingBox(box, true, (40 * density).roundToInt()) }
            } else if (start != null) {
                m.controller.setZoom(15.0)
                m.controller.setCenter(OsmPoint(start.lat, start.lon))
            }
            m.invalidate()
        },
    )
}

private fun roundKm(km: Double): Double = (km * 2).roundToInt() / 2.0

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // kein Browser / keine Karten-App
    }
}
