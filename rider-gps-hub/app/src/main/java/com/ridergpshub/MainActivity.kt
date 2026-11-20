package com.ridergpshub

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { RiderGpsHubScreen() }
    }
}

@Composable
private fun RiderGpsHubScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = remember {
        context.getSystemService(ComponentActivity.LOCATION_SERVICE) as LocationManager
    }
    var location by remember { mutableStateOf<Location?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var gpsEnabled by remember { mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(4.0)
        }
    }
    val userMarker = remember(mapView) {
        Marker(mapView).apply {
            title = "ตำแหน่งของคุณ"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    DisposableEffect(permissionGranted, locationManager) {
        if (!permissionGranted) return@DisposableEffect onDispose { }
        val listener = object : LocationListener {
            override fun onLocationChanged(newLocation: Location) {
                location = newLocation
                gpsEnabled = true
            }
            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) { gpsEnabled = true }
            override fun onProviderDisabled(provider: String) { gpsEnabled = false }
        }
        try {
            val fineGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val provider = if (fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                null
            }
            provider?.let {
                val interval = if (it == LocationManager.GPS_PROVIDER) 2000L else 5000L
                val distance = if (it == LocationManager.GPS_PROVIDER) 3f else 10f
                locationManager.requestLocationUpdates(it, interval, distance, listener)
                locationManager.getLastKnownLocation(it)?.let { last -> location = last }
            }
        } catch (_: SecurityException) {
            permissionGranted = false
        }
        onDispose { locationManager.removeUpdates(listener) }
    }

    LaunchedEffect(location) {
        location?.let {
            val point = GeoPoint(it.latitude, it.longitude)
            userMarker.position = point
            if (!mapView.overlays.contains(userMarker)) mapView.overlays.add(userMarker)
            mapView.controller.animateTo(point)
            mapView.invalidate()
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF087E65),
        secondary = Color(0xFF168BD2),
        background = Color(0xFFF4F6F5),
        surface = Color.White
    )) {
        Box(Modifier.fillMaxSize().background(Color(0xFFE7E9E4))) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 5.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("RIDER GPS HUB", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF123D35))
                            Text("แผนที่นำทางสำหรับไรเดอร์", fontSize = 12.sp, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(if (permissionGranted && location != null) Color(0xFF16A765) else Color(0xFFE5A02B), CircleShape))
                            Spacer(Modifier.size(7.dp))
                            Text(
                                when {
                                    !permissionGranted -> "รอสิทธิ์ GPS"
                                    !gpsEnabled -> "GPS ปิดอยู่"
                                    location != null -> "GPS พร้อมใช้"
                                    else -> "กำลังค้นหา GPS"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF33433E)
                            )
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(7.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Route, contentDescription = null, tint = Color(0xFF087E65))
                                Spacer(Modifier.size(8.dp))
                                Text("จุดหมายของคุณ", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF172C27))
                            }
                            Text(
                                if (location != null) "พิกัดปัจจุบัน %.5f, %.5f".format(location!!.latitude, location!!.longitude)
                                else "กำลังรอตำแหน่งจริงจากโทรศัพท์",
                                color = Color(0xFF59645F), fontSize = 13.sp
                            )
                            if (!permissionGranted) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("อนุญาตให้ใช้ GPS") }
                            } else if (location == null) {
                                Text("ตรวจสอบว่าเปิดบริการตำแหน่งของโทรศัพท์แล้ว", color = Color(0xFF9A6515), fontSize = 12.sp)
                            }
                            Text("การเชื่อมต่อ Grab · Shopee · LINE MAN จะเพิ่มในขั้นตอนถัดไป", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatformPill("GRAB", Color(0xFF159B61), Modifier.weight(1f))
                        PlatformPill("SHOPEE", Color(0xFFE66A27), Modifier.weight(1f))
                        PlatformPill("LINE MAN", Color(0xFF16A36A), Modifier.weight(1f))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomItem(Icons.Default.LocationOn, "แผนที่", true)
                            BottomItem(Icons.Default.Navigation, "นำทาง", false)
                            BottomItem(Icons.Default.Route, "ประวัติ", false)
                            BottomItem(Icons.Default.MyLocation, "GPS", false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)) {
        Text(label, modifier = Modifier.padding(vertical = 11.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = label, tint = if (selected) Color(0xFF087E65) else Color(0xFF65716C), modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) Color(0xFF087E65) else Color(0xFF65716C), fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
