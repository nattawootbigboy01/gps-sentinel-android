package com.gpsdefenseauditor

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class AuditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveFeedStore.restore(applicationContext)
        setContent {
            MaterialTheme {
                AuditorScreen(intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            }
        }
    }
}

@Composable
private data class TargetApp(val packageName: String, val label: String)

private fun AuditorScreen(incomingReport: String) {
    val liveFix by LiveFeedStore.latest.collectAsState()
    val liveHistory by LiveFeedStore.history.collectAsState()
    var cases by remember { mutableStateOf(AuditReportParser.parse(incomingReport)) }
    val context = LocalContext.current
    val targetApps = remember {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { resolve ->
                val packageName = resolve.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName || packageName == "com.gpssentinel") return@mapNotNull null
                val label = runCatching {
                    resolve.loadLabel(context.packageManager).toString()
                }.getOrDefault(packageName)
                TargetApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val targetPrefs = remember {
        context.getSharedPreferences("auditor_target_app", android.content.Context.MODE_PRIVATE)
    }
    var selectedTargetPackage by remember {
        mutableStateOf(targetPrefs.getString("package", null).orEmpty())
    }
    var showTargetApps by remember { mutableStateOf(false) }
    val selectedTarget = targetApps.firstOrNull { it.packageName == selectedTargetPackage }
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(16.4620, 102.8230))
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    LaunchedEffect(liveFix) {
        liveFix?.let { fix ->
            val point = GeoPoint(fix.latitude, fix.longitude)
            val marker = mapView.overlays.filterIsInstance<Marker>().firstOrNull()
                ?: Marker(mapView).also {
                    it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mapView.overlays.add(it)
                }
            marker.position = point
            marker.title = "LAB • ${fix.speedKmh} กม./ชม."
            mapView.controller.animateTo(point)
            mapView.invalidate()
        }
    }
    var sourceLabel by remember {
        mutableStateOf(if (incomingReport.isBlank()) "ยังไม่มีรายงานจาก GPS LAB" else "รับข้อมูลจากการแชร์แล้ว")
    }

    LaunchedEffect(incomingReport) {
        if (incomingReport.isNotBlank()) {
            cases = AuditReportParser.parse(incomingReport)
            sourceLabel = "รับข้อมูลจากการแชร์แล้ว"
        }
    }

    val summary = AuditAnalyzer.summarize(cases)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GPS DEFENSE AUDITOR", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("ระบบประเมินผลการทดสอบ GPS", color = MaterialTheme.colorScheme.primary)
            Text(sourceLabel, fontSize = 13.sp)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("แอปเป้าหมายที่ต้องการประเมิน", fontWeight = FontWeight.Bold)
                    if (selectedTarget == null) {
                        Text("ยังไม่ได้เลือกแอป")
                    } else {
                        Text(selectedTarget.label, fontWeight = FontWeight.SemiBold)
                        Text(selectedTarget.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { showTargetApps = !showTargetApps }) {
                        Text(if (showTargetApps) "ซ่อนรายชื่อแอป" else "เลือกแอปที่ติดตั้ง")
                    }
                    if (showTargetApps) {
                        if (targetApps.isEmpty()) {
                            Text("ไม่พบแอปที่เปิดจากหน้าจอแอปได้")
                        } else {
                            Text("พบ ${targetApps.size} แอป • เลือกแอปที่คุณเป็นเจ้าของหรือได้รับอนุญาตให้ทดสอบ", fontSize = 12.sp)
                            targetApps.forEach { app ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, fontWeight = FontWeight.Medium)
                                        Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = {
                                        selectedTargetPackage = app.packageName
                                        targetPrefs.edit().putString("package", app.packageName).apply()
                                        showTargetApps = false
                                    }) {
                                        Text(if (app.packageName == selectedTargetPackage) "เลือกแล้ว" else "เลือก")
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    Text(
                        "การเลือกแอปเป็นการกำหนดเป้าหมายและผูกผลรายงานเท่านั้น ไม่ได้ทำให้ Android ส่ง Mock Location เฉพาะแอปนั้น",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("บันทึกเหตุการณ์ในเครื่อง: ${liveHistory.size}/200", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ข้อมูล GPS แบบเรียลไทม์", fontWeight = FontWeight.Bold)
                    if (liveFix == null) {
                        Text("รอข้อมูลจาก GPS LAB • เปิด LAB แล้วเริ่มจำลองเส้นทาง")
                    } else {
                        val fix = liveFix!!
                        Text("รับแพ็กเก็ตลำดับ #${fix.sequence} • ${fix.source}")
                        Text("พิกัด: %.6f, %.6f".format(fix.latitude, fix.longitude))
                        Text("ความเร็ว: ${fix.speedKmh} กม./ชม. • Accuracy: %.1f ม.".format(fix.accuracyMeters))
                        Text("เวลาอุปกรณ์: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(fix.timestampMs)))
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("แผนที่ติดตามตำแหน่งที่รับได้", fontWeight = FontWeight.Bold)
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(230.dp))
                    Text(if (liveFix == null) "รอพิกัดจาก LAB" else "หมุดจะแสดงพิกัดล่าสุดที่ AUDITOR รับสำเร็จ", fontSize = 11.sp)
                }
            }
            if (liveHistory.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ประวัติข้อมูล GPS ล่าสุด", fontWeight = FontWeight.Bold)
                        liveHistory.takeLast(10).asReversed().forEach { fix ->
                            Text(
                                "#${fix.sequence} • ${"%.5f".format(fix.latitude)}, ${"%.5f".format(fix.longitude)}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                "${fix.speedKmh} กม./ชม. • ความแม่นยำ ${"%.1f".format(fix.accuracyMeters)} ม. • ${fix.source}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            AuditRouteAnalyzer.distanceKm(incomingReport)?.let { km ->
                Text("ระยะทางเส้นทางที่แนบ: %.2f กม.".format(km), fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("ทดสอบทั้งหมด", summary.total.toString(), Modifier.weight(1f))
                MetricCard("ผ่านเกณฑ์", "${summary.passed}/${summary.total}", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("พลาดการตรวจจับ", summary.missed.toString(), Modifier.weight(1f))
                MetricCard("แจ้งเตือนเกิน", summary.falseAlarms.toString(), Modifier.weight(1f))
            }
            MetricCard("คะแนนความเสี่ยงเฉลี่ย", summary.averageScore.toString() + "/100", Modifier.fillMaxWidth())
            Text("รายละเอียดแต่ละสถานการณ์", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (cases.isEmpty()) {
                Text("เปิด GPS LAB แล้วกด “ส่งผลทดสอบไป GPS DEFENSE AUDITOR” เพื่อส่งรายงานเข้ามา")
                OutlinedButton(onClick = {
                    val sample = listOf(
                        "CASE|การเดินทางปกติ|LOW|LOW|0|true|",
                        "CASE|ตำแหน่งกระโดด|REVIEW|REVIEW|55|true|IMPLAUSIBLE_DISPLACEMENT",
                        "CASE|ข้อมูล Mock Provider|HIGH|HIGH|65|true|ANDROID_MOCK_FLAG",
                        "CASE|ตัวอย่างที่ตรวจไม่พบ|HIGH|LOW|0|false|"
                    ).joinToString("\n")
                    cases = AuditReportParser.parse(sample)
                    sourceLabel = "ชุดข้อมูลตัวอย่างสำหรับตรวจหน้ารายงาน"
                }) { Text("โหลดข้อมูลตัวอย่าง") }
            } else {
                cases.forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text("คาดหวัง: ${item.expected} • ผลจริง: ${item.actual}")
                            Text("คะแนน: ${item.score}/100 • ${if (item.passed) "ผ่าน" else "ไม่ผ่าน"}")
                            if (item.findings.isNotBlank()) Text("สัญญาณ: ${item.findings}", fontSize = 12.sp)
                        }
                    }
                }
                Button(onClick = { cases = emptyList(); sourceLabel = "ล้างรายงานแล้ว" }) {
                    Text("ล้างรายงาน")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "หมายเหตุ: คะแนนเป็นเครื่องมือช่วยตรวจสอบ ไม่ใช่หลักฐานยืนยันการทุจริต " +
                    "ควรใช้ข้อมูลจากระบบที่คุณเป็นเจ้าของหรือได้รับอนุญาตเท่านั้น",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
