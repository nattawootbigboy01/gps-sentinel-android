package com.gpssentinel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

private val Ink = Color(0xFF202428)
private val RouteBlue = Color(0xFF1687E8)
private val Teal = Color(0xFF087F83)
private val DemoPoint = GeoPoint(16.4620, 102.8230)

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
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    var realPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var simPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var speed by remember { mutableIntStateOf(0) }
    var simSpeed by remember { mutableIntStateOf(35) }
    var simRunning by remember { mutableStateOf(false) }
    var useSimulator by remember { mutableStateOf(false) }
    var showResearch by remember { mutableStateOf(true) }
    var followLocation by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("กำลังตรวจสอบสิทธิ์ GPS") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
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
                    if (!useSimulator) status = "GPS จริง • ความแม่นยำประมาณ ${location.accuracy.roundToInt()} ม."
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

    LaunchedEffect(simRunning, useSimulator, simSpeed) {
        while (simRunning && useSimulator) {
            val point = simPoint ?: realPoint ?: DemoPoint
            val scale = simSpeed / 35.0
            simPoint = GeoPoint(point.latitude + 0.000035 * scale, point.longitude + 0.000055 * scale)
            speed = simSpeed
            status = "SIMULATOR • จำลองภายในแอปเท่านั้น"
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
                    displayedPoint?.let { point ->
                        val marker = Marker(this)
                        marker.position = point
                        marker.title = if (useSimulator) "ตำแหน่งจำลองใน GPS SENTINEL" else "ตำแหน่ง GPS ของอุปกรณ์"
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        overlays.add(marker)
                    }
                }
            },
            update = { map ->
                val point = displayedPoint
                if (point != null) {
                    val marker = map.overlays.filterIsInstance<Marker>().firstOrNull() ?: Marker(map).also {
                        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        map.overlays.add(it)
                    }
                    marker.position = point
                    marker.title = if (useSimulator) "ตำแหน่งจำลองใน GPS SENTINEL" else "ตำแหน่ง GPS ของอุปกรณ์"
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
                            Text(if (useSimulator) "การเคลื่อนที่จำลองในแอป" else "GPS SENTINEL • LIVE LOCATION", color = Color.White, fontSize = 13.sp)
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
                        useSimulator = false
                        simRunning = false
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
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(if (useSimulator) "SIMULATOR • ${simSpeed} km/h" else "GPS จริง • ตำแหน่งจากโทรศัพท์", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = Teal, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GPS SENTINEL", color = RouteBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(status, color = Color.DarkGray, fontSize = 11.sp, maxLines = 2)
                    }
                    TextButton(onClick = { showResearch = !showResearch }) { Text(if (showResearch) "ย่อ" else "เครื่องมือ") }
                }
                if (showResearch) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("ห้องทดลอง GPS", fontWeight = FontWeight.Bold, color = Ink)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useSimulator,
                            onClick = { simRunning = false; useSimulator = false; followLocation = true; status = "โหมด GPS จริงจากโทรศัพท์" },
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
                    if (useSimulator) {
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
                                        simPoint = simPoint ?: realPoint ?: DemoPoint
                                        followLocation = true
                                        simRunning = true
                                        status = "กำลังจำลองตำแหน่งภายในแอป"
                                    } else {
                                        simRunning = false
                                        status = "หยุดการจำลองแล้ว"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (simRunning) "หยุดจำลอง" else "เริ่มจำลอง") }
                            OutlinedButton(onClick = { simRunning = false; simPoint = realPoint ?: DemoPoint; speed = 0; status = "รีเซ็ตตำแหน่งจำลองแล้ว" }) {
                                Text("รีเซ็ต")
                            }
                        }
                        Text("หมายเหตุ: ข้อมูลจำลองแสดงเฉพาะใน GPS SENTINEL ไม่ส่งตำแหน่งปลอมให้แอปอื่น", color = Color.Gray, fontSize = 10.sp)
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
