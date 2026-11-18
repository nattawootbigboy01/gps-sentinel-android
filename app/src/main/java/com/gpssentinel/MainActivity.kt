package com.gpssentinel

import android.Manifest
import android.view.MotionEvent
import android.graphics.Canvas
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

private val Ink = Color(0xFF202428)
private val RouteBlue = Color(0xFF1687E8)
private val Teal = Color(0xFF087F83)
private val DemoPoint = GeoPoint(16.4620, 102.8230)

private class RouteDrawOverlay(
    var drawingEnabled: Boolean,
    var onRouteFinished: (List<GeoPoint>) -> Unit
) : Overlay() {
    private val points = mutableListOf<GeoPoint>()
    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!drawingEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                points.add(mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val p = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                val last = points.lastOrNull()
                if (last == null || last.distanceToAsDouble(p) > 4.0) points.add(p)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val p = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                if (points.isEmpty() || points.last().distanceToAsDouble(p) > 1.0) points.add(p)
                if (points.size >= 2) onRouteFinished(points.toList())
                points.clear()
                mapView.invalidate()
                return true
            }
        }
        return true
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { MaterialTheme { SentinelScreen() } }
    }
}

@Composable
private fun SentinelScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val mockLocationController = remember { MockLocationController(locationManager) }
    var systemMockEnabled by remember { mutableStateOf(false) }
    var showMockAuthorizationDialog by remember { mutableStateOf(false) }
    var mockScopeConfirmed by remember { mutableStateOf(false) }
    DisposableEffect(mockLocationController) {
        onDispose { mockLocationController.stop() }
    }
    var realPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var simPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var speed by remember { mutableIntStateOf(0) }
    var simSpeed by remember { mutableIntStateOf(35) }
    var simRunning by remember { mutableStateOf(false) }
    var useSimulator by remember { mutableStateOf(false) }
    var showResearch by remember { mutableStateOf(false) }
    var followLocation by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("กำลังตรวจสอบสิทธิ์ GPS") }
    var mockSignal by remember { mutableStateOf(false) }
    var anomalySignal by remember { mutableStateOf("") }
    var lastMeasuredLocation by remember { mutableStateOf<Location?>(null) }
    var auditReport by remember { mutableStateOf("ยังไม่ได้ตรวจสภาพแวดล้อม Android") }
    var scenarioReport by remember { mutableStateOf("ยังไม่ได้รันทดสอบสถานการณ์จำลอง") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var drawingRoute by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var routeProgressMeters by remember { mutableDoubleStateOf(0.0) }
    var routeLoading by remember { mutableStateOf(false) }
    var routeInfo by remember { mutableStateOf("ยังไม่มีเส้นทางอัตโนมัติ") }
    var stopAtRouteEnd by remember { mutableStateOf(false) }
    var liveSequence by remember { mutableLongStateOf(0L) }
    var testSessionStartElapsedMs by remember { mutableLongStateOf(0L) }
    var testSessionElapsedMs by remember { mutableLongStateOf(0L) }
    val maxTestDurationMs = 2L * 60L * 60L * 1000L
    val launchableApps = remember { TargetAppCatalog.load(context) }
    var selectedTarget by remember { mutableStateOf(TargetAppCatalog.restore(context)) }
    var showTargetPicker by remember { mutableStateOf(false) }
    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission.value =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        status = if (hasLocationPermission.value) "ได้รับอนุญาต GPS แล้ว กำลังหาตำแหน่ง" else "ยังไม่ได้รับอนุญาตให้เข้าถึงตำแหน่ง"
    }

    fun requestLocationPermission() {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission.value) requestLocationPermission()
    }

    DisposableEffect(hasLocationPermission.value) {
        if (hasLocationPermission.value) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    realPoint = GeoPoint(location.latitude, location.longitude)
                    speed = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
                    mockSignal = location.isFromMockProvider
                    val previous = lastMeasuredLocation
                    anomalySignal = if (previous != null) {
                        val elapsedSeconds = (location.time - previous.time) / 1000f
                        val measuredSpeed = if (elapsedSeconds > 0f) previous.distanceTo(location) / elapsedSeconds else 0f
                        if (measuredSpeed > 55f) "พบการเปลี่ยนตำแหน่งเร็วผิดปกติ" else ""
                    } else ""
                    lastMeasuredLocation = Location(location)
                    if (!useSimulator) {
                        status = when {
                            mockSignal -> "พบสัญญาณ Mock Location จาก Android"
                            anomalySignal.isNotEmpty() -> anomalySignal
                            else -> "GPS จริง • ความแม่นยำประมาณ ${location.accuracy.roundToInt()} ม."
                        }
                    }
                }
            }
            try {
                val providers = locationManager.getProviders(true)
                val provider = when {
                    providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    locationManager.getLastKnownLocation(provider)?.let {
                        realPoint = GeoPoint(it.latitude, it.longitude)
                        speed = if (it.hasSpeed()) (it.speed * 3.6f).roundToInt() else 0
                        status = "รับตำแหน่งจากอุปกรณ์แล้ว"
                    }
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
                if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener)
                }
                status = if (realPoint == null) "กำลังค้นหาสัญญาณ GPS..." else "GPS จริง • พร้อมใช้งาน"
            } catch (_: SecurityException) {
                status = "ไม่มีสิทธิ์เข้าถึงตำแหน่ง กรุณาอนุญาต GPS"
            } catch (_: Exception) {
                status = "ยังรับตำแหน่งไม่ได้ กรุณาเปิดบริการตำแหน่ง"
            }
            onDispose { try { locationManager.removeUpdates(listener) } catch (_: Exception) { } }
        } else {
            status = "ต้องอนุญาตตำแหน่งเพื่อใช้ GPS จริง"
            onDispose { }
        }
    }

    LaunchedEffect(simRunning, useSimulator, simSpeed, systemMockEnabled, stopAtRouteEnd) {
        while (simRunning && useSimulator) {
            val sessionElapsed = if (testSessionStartElapsedMs > 0L) SystemClock.elapsedRealtime() - testSessionStartElapsedMs else 0L
            testSessionElapsedMs = sessionElapsed.coerceAtLeast(0L)
            if (sessionElapsed >= maxTestDurationMs) {
                simRunning = false
                mockLocationController.stop()
                systemMockEnabled = false
                speed = 0
                status = "ครบเวลาทดสอบ 2 ชั่วโมงแล้ว • ปิด Android Mock Location อัตโนมัติ"
                break
            }
            if (routePoints.size >= 2) {
                val frame = RoutePlaybackEngine.advance(
                    route = routePoints,
                    currentProgressMeters = routeProgressMeters,
                    speedKmh = simSpeed,
                    elapsedSeconds = 1.0,
                    stopAtEnd = stopAtRouteEnd
                )
                if (frame != null) {
                    routeProgressMeters = frame.progressMeters
                    simPoint = frame.point
                    when {
                        frame.reachedEnd -> {
                            simRunning = false
                            status = "ถึงปลายทางแล้ว • หยุดจำลองอัตโนมัติ"
                        }
                        frame.looped -> status = "วนเส้นทางต่อเนื่อง • กดหยุดเพื่อจบการจำลอง"
                    }
                }
            } else {
                val point = simPoint ?: realPoint ?: DemoPoint
                val scale = simSpeed / 35.0
                simPoint = GeoPoint(point.latitude + 0.000035 * scale, point.longitude + 0.000055 * scale)
            }
            speed = simSpeed
            var providerPublishCallReturned = false
            if (systemMockEnabled) {
                try {
                    val point = simPoint ?: realPoint ?: DemoPoint
                    mockLocationController.update(point.latitude, point.longitude, simSpeed.toFloat(), 5f)
                    providerPublishCallReturned = true
                } catch (_: SecurityException) {
                    systemMockEnabled = false
                    status = "Android ปฏิเสธ Mock Location: ตรวจว่าเลือก มหาเทพ LAB ใน Developer Options แล้ว"
                } catch (_: Exception) {
                    systemMockEnabled = false
                    status = "ส่งตำแหน่งทดสอบเข้า Android ไม่สำเร็จ"
                }
            }
            if (simRunning) {
                liveSequence += 1L
                val live = simPoint ?: realPoint ?: DemoPoint
                val feed = Intent("com.gpsdefenseauditor.action.LIVE_FIX").apply {
                    setPackage("com.gpsdefenseauditor")
                    putExtra("sequence", liveSequence)
                    putExtra("latitude", live.latitude)
                    putExtra("longitude", live.longitude)
                    putExtra("speed_kmh", simSpeed)
                    putExtra("accuracy_m", 5.0f)
                    putExtra("timestamp_ms", System.currentTimeMillis())
                    putExtra("source", "GPS_LAB_SIMULATOR")
                    putExtra("session_elapsed_ms", testSessionElapsedMs)
                    putExtra("session_limit_ms", maxTestDurationMs)
                    putExtra("mock_provider_enabled", systemMockEnabled)
                    putExtra("mock_provider_publish_call_returned", providerPublishCallReturned)
                    putExtra("target_package", selectedTarget?.packageName ?: "UNSELECTED")
                }
                context.sendBroadcast(feed, "com.gpsdefenseauditor.permission.LIVE_FEED")
                status = "SIMULATOR • ส่งข้อมูลสดไปยัง GPS DEFENSE AUDITOR"
            }
            delay(1000L)
        }
    }

    val displayedPoint = if (useSimulator) simPoint ?: realPoint ?: DemoPoint else realPoint
    LaunchedEffect(displayedPoint?.latitude, displayedPoint?.longitude, followLocation) {
        val point = displayedPoint
        val map = mapView
        if (point != null && map != null && followLocation) {
            map.controller.animateTo(point)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE7E7E4))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(realPoint ?: DemoPoint)
                    mapView = this
                    overlays.add(RouteDrawOverlay(drawingRoute) { points ->
                        routePoints = points
                        routeProgressMeters = 0.0
                        status = "วางเส้นทางแล้ว • ${points.size} จุด"
                    })
                    displayedPoint?.let { point ->
                        val marker = Marker(this)
                        marker.position = point
                        marker.title = if (useSimulator) "ตำแหน่งจำลองใน GPS LAB" else "ตำแหน่ง GPS ของอุปกรณ์"
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        overlays.add(marker)
                    }
                }
            },
            update = { map ->
                val drawOverlay = map.overlays.filterIsInstance<RouteDrawOverlay>().firstOrNull()
                drawOverlay?.drawingEnabled = drawingRoute
                drawOverlay?.onRouteFinished = { points ->
                    routePoints = points
                    routeProgressMeters = 0.0
                    drawingRoute = false
                    status = "วางเส้นทางแล้ว • ${points.size} จุด"
                }
                map.overlays.removeAll { it is Polyline }
                if (routePoints.size >= 2) {
                    val line = Polyline(map).apply {
                        setPoints(routePoints)
                        outlinePaint.color = android.graphics.Color.rgb(22, 135, 232)
                        outlinePaint.strokeWidth = 10f
                    }
                    map.overlays.add(line)
                }
                val point = displayedPoint
                if (point != null) {
                    val marker = map.overlays.filterIsInstance<Marker>().firstOrNull() ?: Marker(map).also {
                        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        map.overlays.add(it)
                    }
                    marker.position = point
                    marker.title = if (useSimulator) "ตำแหน่งจำลองใน GPS LAB" else "ตำแหน่ง GPS ของอุปกรณ์"
                    map.invalidate()
                }
            }
        )

        Column(Modifier.fillMaxWidth().padding(top = 38.dp, start = 12.dp, end = 12.dp)) {
            Surface(color = Ink, shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (useSimulator) "โหมดจำลอง GPS" else "ตำแหน่งปัจจุบัน", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text(if (useSimulator) "การเคลื่อนที่จำลองในแอป" else "GPS LAB • LIVE LOCATION", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray)
                    Row(Modifier.fillMaxWidth().background(Color.Black).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(status, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 230.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 5.dp) {
                Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$speed", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("km/h", fontSize = 12.sp, color = Ink)
                }
            }
            Button(
                onClick = { showResearch = !showResearch },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Teal),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Shield, null)
                Spacer(Modifier.width(5.dp))
                Text("ศูนย์ความปลอดภัย", fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FloatingActionButton(
                onClick = {
                    if (!hasLocationPermission.value) requestLocationPermission()
                    else {
                        simRunning = false
                        mockLocationController.stop()
                        systemMockEnabled = false
                        useSimulator = false
                        followLocation = true
                        realPoint?.let { mapView?.controller?.animateTo(it) }
                        status = if (realPoint == null) "กำลังค้นหาตำแหน่ง GPS จริง..." else "แสดงตำแหน่ง GPS จริง"
                    }
                },
                containerColor = Color.White, contentColor = Ink
            ) { Icon(Icons.Default.MyLocation, "ตำแหน่งปัจจุบัน") }
            FloatingActionButton(onClick = { showResearch = !showResearch }, containerColor = Color.White, contentColor = Ink) {
                Icon(Icons.Default.Tune, "เครื่องมือทดลอง")
            }
        }

        Surface(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .navigationBarsPadding(),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(if (useSimulator) "SIMULATOR • ${simSpeed} km/h" else "GPS จริง • ตำแหน่งจากโทรศัพท์", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = Teal, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GPS LAB", color = RouteBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(status, color = Color.DarkGray, fontSize = 11.sp, maxLines = 2)
                    }
                    TextButton(onClick = { showResearch = !showResearch }) { Text(if (showResearch) "ย่อ" else "เครื่องมือ") }
                }
                if (showResearch) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("ห้องทดลอง GPS", fontWeight = FontWeight.Bold, color = Ink)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("TARGET APP • แอปเป้าหมาย", fontWeight = FontWeight.Bold)
                            Text(selectedTarget?.let { it.label + "\n" + it.packageName + " • " + it.versionName } ?: "ยังไม่ได้เลือกแอปเดลิเวอรี่")
                            OutlinedButton(onClick = { showTargetPicker = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Apps, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (selectedTarget == null) "เลือกแอปที่ติดตั้ง" else "เปลี่ยนแอปเป้าหมาย")
                            }
                            Text("การเลือกแอปใช้ระบุขอบเขตและแนบชื่อ/เวอร์ชันในรายงานเท่านั้น ไม่ได้ให้ LAB อ่านข้อมูลภายในหรือฉีด GPS ไปยังแอปนั้นโดยตรง", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    if (showTargetPicker) {
                        AlertDialog(
                            onDismissRequest = { showTargetPicker = false },
                            title = { Text("เลือกแอปเป้าหมาย") },
                            text = {
                                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                    launchableApps.forEach { app ->
                                        TextButton(onClick = { selectedTarget = app; TargetAppCatalog.save(context, app); showTargetPicker = false; status = "เลือกเป้าหมายแล้ว: " + app.label }, modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.fillMaxWidth()) {
                                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                                Text(app.packageName + " • " + app.versionName, fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                    if (launchableApps.isEmpty()) Text("ไม่พบแอปที่เปิดจากหน้าจอแอปได้")
                                }
                            },
                            confirmButton = { TextButton(onClick = { showTargetPicker = false }) { Text("ปิด") } },
                            dismissButton = { TextButton(onClick = { selectedTarget = null; TargetAppCatalog.clear(context); showTargetPicker = false }) { Text("ล้างเป้าหมาย") } }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            auditReport = MockEnvironmentAudit.inspect(context, lastMeasuredLocation).summary
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ตรวจสอบ Mock Location และ Developer Options")
                    }
                    Text(auditReport, color = Color.DarkGray, fontSize = 11.sp)
                    OutlinedButton(
                        onClick = {
                            val results = GpsScenarioLab.runAll()
                            val passed = results.count { it.passed }
                            scenarioReport = "ผ่าน $passed/${results.size} สถานการณ์\\n" + results.joinToString("\\n") { result ->
                                val mark = if (result.passed) "ผ่าน" else "ต้องปรับ"
                                "$mark • ${result.name}: ${result.report.level} (${result.report.score}/100)"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Science, null)
                        Spacer(Modifier.width(6.dp))
                        Text("รันทดสอบ GPS เชิงลึก 8 สถานการณ์")
                    }
                    Text(scenarioReport, color = Color.DarkGray, fontSize = 11.sp)
                    OutlinedButton(
                        onClick = {
                            val exportText = buildString {
                                appendLine("GPS_LAB_REPORT_V1")
                                selectedTarget?.let { target -> appendLine("APP|" + target.packageName + "|" + target.label + "|" + target.versionName) } ?: appendLine("APP|UNSELECTED|ยังไม่ได้เลือกแอป|")
                                GpsScenarioLab.runAll().forEach { result ->
                                    val findingCodes = result.report.findings.joinToString(",") { it.code }
                                    appendLine("CASE|" + result.name + "|" + result.expected + "|" + result.report.level + "|" + result.report.score + "|" + result.passed + "|" + findingCodes)
                                }
                                appendLine("ROUTE|" + routePoints.size)
                                routePoints.forEach { point ->
                                    appendLine("POINT|" + point.latitude + "|" + point.longitude)
                                }
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "GPS LAB test report")
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "ส่งผลทดสอบไป GPS DEFENSE AUDITOR"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ส่งผลทดสอบไป GPS DEFENSE AUDITOR")
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useSimulator,
                            onClick = { simRunning = false; mockLocationController.stop(); systemMockEnabled = false; useSimulator = false; followLocation = true; status = "โหมด GPS จริงจากโทรศัพท์" },
                            label = { Text("GPS จริง") },
                            leadingIcon = { Icon(Icons.Default.GpsFixed, null) }
                        )
                        FilterChip(
                            selected = useSimulator,
                            onClick = {
                                useSimulator = true
                                simPoint = simPoint ?: realPoint ?: DemoPoint
                                followLocation = true
                                status = "พร้อมเริ่มจำลองตำแหน่งภายในแอป"
                            },
                            label = { Text("GPS Simulator") },
                            leadingIcon = { Icon(Icons.Default.Route, null) }
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            simRunning = false
                            drawingRoute = !drawingRoute
                            followLocation = false
                            status = if (!drawingRoute) "ยกเลิกการวางเส้นทาง" else "ลากนิ้วบนแผนที่จากต้นทางไปปลายทาง"
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (drawingRoute) "กำลังวาดเส้นทาง..." else "วางเส้นทางด้วยนิ้ว")
                        }
                        OutlinedButton(
                            onClick = {
                                val origin = realPoint
                                if (origin == null) {
                                    status = "ยังไม่มี GPS ปัจจุบัน กรุณารอรับตำแหน่งก่อน"
                                } else if (routeLoading) {
                                    status = "กำลังค้นหาเส้นทางตามถนน..."
                                } else {
                                    simRunning = false
                                    routeLoading = true
                                    routeInfo = "กำลังค้นหาเส้นทางถนนในรัศมี 1 กม."
                                    status = "กำลังค้นหาเส้นทางตามถนน..."
                                    coroutineScope.launch {
                                        try {
                                            val result = RoadRoutePlanner.createNearbyRoute(origin)
                                            routePoints = result.points
                                            routeProgressMeters = 0.0
                                            stopAtRouteEnd = true
                                            drawingRoute = false
                                            followLocation = false
                                            routeInfo = "เส้นทางถนน %.0f เมตร • จำกัดไม่เกิน 1 กม.".format(result.distanceMeters)
                                            mapView?.controller?.animateTo(origin)
                                            status = "สร้างเส้นทางตามถนนสำเร็จ • ตรวจสอบเส้นทางก่อนเริ่ม"
                                        } catch (error: Exception) {
                                            routeInfo = error.message ?: "สร้างเส้นทางไม่สำเร็จ"
                                            status = routeInfo
                                        } finally {
                                            routeLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !routeLoading && !simRunning
                        ) {
                            Text(if (routeLoading) "กำลังหา..." else "สร้างเส้นทางถนนอัตโนมัติ")
                        }
                        OutlinedButton(onClick = {
                            routePoints = emptyList()
                            routeProgressMeters = 0.0
                            stopAtRouteEnd = false
                            routeInfo = "ยังไม่มีเส้นทางอัตโนมัติ"
                            simRunning = false
                            status = "ล้างเส้นทางแล้ว"
                        }, modifier = Modifier.weight(1f)) { Text("ล้างเส้นทาง") }
                    }
                    if (routePoints.size >= 2) {
                        val km = routePoints.zipWithNext().sumOf { (a, b) -> a.distanceToAsDouble(b) } / 1000.0
                        Text("ระยะเส้นทาง %.2f กม.".format(km), color = RouteBlue, fontSize = 12.sp)
                        Text(routeInfo, color = Color.DarkGray, fontSize = 11.sp)
                    }
                    Text("เส้นทางอัตโนมัติใช้บริการ OpenStreetMap OSRM ผ่านอินเทอร์เน็ต ระบบจะไม่เริ่มจำลองจนกว่าคุณกดเริ่ม", color = Color.Gray, fontSize = 10.sp)
                    if (useSimulator) {
                        OutlinedButton(
                            onClick = {
                                if (systemMockEnabled) {
                                    mockLocationController.stop()
                                    systemMockEnabled = false
                                    status = "ปิด Android Mock Location แล้ว"
                                } else {
                                    mockScopeConfirmed = false
                                    showMockAuthorizationDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LocationOn, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (systemMockEnabled) "ปิด Android Mock Location" else "เปิด Android Mock Location")
                        }
                        Text(
                            if (systemMockEnabled) "กำลังส่งตำแหน่งทดสอบผ่าน Android Location Provider" else "ต้องเลือก มหาเทพ LAB ใน Developer Options ก่อนเปิดใช้งาน",
                            color = Color.Gray, fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ความเร็ว", color = Ink)
                            Text("$simSpeed km/h", fontWeight = FontWeight.Bold, color = RouteBlue)
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { simSpeed = (simSpeed - 5).coerceAtLeast(5) }) { Text("-") }
                            OutlinedButton(onClick = { simSpeed = (simSpeed + 5).coerceAtMost(120) }) { Text("+") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (!simRunning) {
                                        simPoint = if (routePoints.size >= 2) routePoints.first() else simPoint ?: realPoint ?: DemoPoint
                                        routeProgressMeters = 0.0
                                        stopAtRouteEnd = false
                                        testSessionStartElapsedMs = SystemClock.elapsedRealtime()
                                        testSessionElapsedMs = 0L
                                        followLocation = true
                                        simRunning = true
                                        status = "เริ่มทดสอบต่อเนื่อง 2 ชั่วโมง • เส้นทางจะวนซ้ำ"
                                    } else {
                                        simRunning = false
                                        testSessionStartElapsedMs = 0L
                                        mockLocationController.stop()
                                        systemMockEnabled = false
                                        status = "หยุดการจำลองและปิด Android Mock Location แล้ว"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (simRunning) "หยุดทดสอบ" else "เริ่มทดสอบ 2 ชั่วโมง") }
                            OutlinedButton(onClick = { simRunning = false; mockLocationController.stop(); systemMockEnabled = false; simPoint = realPoint ?: DemoPoint; speed = 0; status = "รีเซ็ตและปิด Android Mock Location แล้ว" }) {
                                Text("รีเซ็ต")
                            }
                        }
                        val elapsedMinutes = testSessionElapsedMs / 60000L
                        val elapsedSeconds = (testSessionElapsedMs / 1000L) % 60L
                        Text("ระยะเวลาทดสอบ: %02d:%02d / 120:00 นาที".format(elapsedMinutes, elapsedSeconds), color = RouteBlue, fontSize = 12.sp)
                        Text("คำเตือน: Android Mock Location เป็นผู้ให้บริการระดับระบบ แอปอื่นที่ร้องขอ GPS อาจได้รับตำแหน่งทดสอบด้วย ใช้กับแอปที่คุณเป็นเจ้าของหรือได้รับอนุญาต และปิดทันทีเมื่อจบทดสอบ", color = Color.Gray, fontSize = 10.sp)
                        if (showMockAuthorizationDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showMockAuthorizationDialog = false
                                    mockScopeConfirmed = false
                                },
                                title = { Text("ยืนยันขอบเขตการทดสอบ") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Android Mock Location เป็นผู้ให้บริการระดับระบบ แอปอื่นที่ร้องขอตำแหน่งอาจได้รับข้อมูลจำลองนี้ด้วย")
                                        Text(
                                            selectedTarget?.let { "แอปเป้าหมาย: ${it.label} (${it.packageName})" }
                                                ?: "กรุณาเลือกแอปเป้าหมายก่อนเปิด Mock Location",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = mockScopeConfirmed,
                                                onCheckedChange = { mockScopeConfirmed = it }
                                            )
                                            Text("ฉันเป็นเจ้าของแอปเป้าหมายหรือได้รับอนุญาตให้ทดสอบ และจะปิด Mock Location เมื่อเสร็จ")
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        enabled = mockScopeConfirmed && selectedTarget != null,
                                        onClick = {
                                            showMockAuthorizationDialog = false
                                            try {
                                                mockLocationController.start()
                                                systemMockEnabled = true
                                                val point = simPoint ?: realPoint ?: DemoPoint
                                                mockLocationController.update(point.latitude, point.longitude, simSpeed.toFloat(), 5f)
                                                status = "เปิด Android Mock Location แล้ว • ขอบเขต: ${selectedTarget?.label}"
                                            } catch (_: SecurityException) {
                                                systemMockEnabled = false
                                                status = "สิทธิ์ไม่พร้อม: เลือก มหาเทพ LAB ใน Developer Options > Select mock location app"
                                            } catch (_: Exception) {
                                                systemMockEnabled = false
                                                status = "เปิด Android Mock Location ไม่สำเร็จ"
                                            } finally {
                                                mockScopeConfirmed = false
                                            }
                                        }
                                    ) { Text("ยืนยันและเปิด") }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showMockAuthorizationDialog = false
                                            mockScopeConfirmed = false
                                        }
                                    ) { Text("ยกเลิก") }
                                }
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (!hasLocationPermission.value) requestLocationPermission() else status = "กำลังค้นหาสัญญาณ GPS จริง..." }, modifier = Modifier.weight(1f)) {
                                Text("ตรวจสอบ GPS")
                            }
                            OutlinedButton(onClick = { followLocation = true; realPoint?.let { mapView?.controller?.animateTo(it) } }, modifier = Modifier.weight(1f)) {
                                Text("กลับตำแหน่งฉัน")
                            }
                        }
                    }
                }
            }
        }
    }
}
