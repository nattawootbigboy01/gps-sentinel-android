package com.gpssentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val Ink = Color(0xFF202428)
private val RouteBlue = Color(0xFF1687E8)
private val Teal = Color(0xFF087F83)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { SentinelScreen() }
    }
}

@Composable
private fun SentinelScreen() {
    var speed by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("กำลังรอข้อมูล GPS") }
    var showResearch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFFE7E7E4))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.5)
                    controller.setCenter(GeoPoint(16.4620, 102.8230))
                    val marker = Marker(this)
                    marker.position = GeoPoint(16.4620, 102.8230)
                    marker.title = "จุดทดสอบ GPS SENTINEL"
                    overlays.add(marker)
                }
            }
        )

        Column(Modifier.fillMaxWidth().padding(top = 42.dp, start = 14.dp, end = 14.dp)) {
            Surface(color = Ink, shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TurnRight, null, tint = Color.White, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("30 ม.", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Text("ถนนทดสอบ GPS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray)
                    Row(Modifier.fillMaxWidth().background(Color.Black).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TurnRight, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("ถนนถัดไป", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 270.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$speed", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("km/h", fontSize = 13.sp)
                }
            }
            Button(
                onClick = { showResearch = !showResearch },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Teal),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Shield, null)
                Spacer(Modifier.width(6.dp))
                Text("ศูนย์ความปลอดภัย", fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(onClick = { status = "ยังไม่ได้เปิดสิทธิ์ตำแหน่งบนอุปกรณ์" }, containerColor = Color.White, contentColor = Ink) {
                Icon(Icons.Default.MyLocation, "ตำแหน่งปัจจุบัน")
            }
            FloatingActionButton(onClick = { showResearch = !showResearch }, containerColor = Color.White, contentColor = Ink) {
                Icon(Icons.Default.Settings, "ตั้งค่า")
            }
        }

        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("11 นาที  •  7.5 กม.", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = Teal, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GPS SENTINEL", color = RouteBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(status, color = Color.DarkGray, fontSize = 13.sp)
                    }
                    IconButton(onClick = { showResearch = !showResearch }) {
                        Icon(Icons.Default.Tune, "เครื่องมือทดสอบ", tint = Teal)
                    }
                }
                if (showResearch) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text("โหมดวิจัย GPS", fontWeight = FontWeight.Bold)
                    Text("แผนที่ตัวอย่าง • ยังไม่อ่านพิกัดจริง • ยังไม่เปิดระบบจำลอง", color = Color.Gray, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { status = "พร้อมตรวจสอบข้อมูลตำแหน่งจริง" }, modifier = Modifier.weight(1f)) {
                            Text("ตรวจสอบ GPS")
                        }
                        OutlinedButton(onClick = { status = "โหมดจำลองใช้ได้เฉพาะห้องทดลอง" }, modifier = Modifier.weight(1f)) {
                            Text("ห้องทดลอง")
                        }
                    }
                }
            }
        }
    }
}
