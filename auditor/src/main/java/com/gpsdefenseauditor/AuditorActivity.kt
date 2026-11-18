package com.gpsdefenseauditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
private fun AuditorScreen(incomingReport: String) {
    val liveFix by LiveFeedStore.latest.collectAsState()
    val liveHistory by LiveFeedStore.history.collectAsState()
    var cases by remember { mutableStateOf(AuditReportParser.parse(incomingReport)) }
    val context = LocalContext.current
    var selfDefenseRuns by remember { mutableStateOf(SelfDefenseStore.history(context)) }
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
    val defenseAssessment = AdaptiveDefenseEngine.analyze(cases)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GPS DEFENSE AUDITOR", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("ระบบประเมินผลการทดสอบ GPS", color = MaterialTheme.colorScheme.primary)
            Text(sourceLabel, fontSize = 13.sp)
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MAHATHEP ADAPTIVE DEFENSE", fontWeight = FontWeight.Bold)
                    Text("Engine v${AdaptiveDefenseEngine.ENGINE_VERSION} • วิเคราะห์จากผลทดสอบที่ได้รับ")
                    Text("พบจุดพลาด ${defenseAssessment.missedDetections} • แจ้งเตือนเกิน ${defenseAssessment.falseAlarms} • วิเคราะห์ ${defenseAssessment.analyzedCases} กรณี")
                    defenseAssessment.recommendations.forEach { recommendation ->
                        Text("[${recommendation.priority}] ${recommendation.title}", fontWeight = FontWeight.SemiBold)
                        Text(recommendation.reason, fontSize = 12.sp)
                        Text("แนวทาง: ${recommendation.action}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("ระบบเสนอแนวทางเท่านั้น ยังไม่แก้กฎตรวจจับอัตโนมัติ ต้องตรวจสอบและอนุมัติก่อนนำไปใช้", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MAHATHEP SELF-DEFENSE", fontWeight = FontWeight.Bold)
                    Text("ระบบทดสอบสุขภาพระบบป้องกันตัวเอง • ทำงานในเครื่อง")
                    val latestRun = selfDefenseRuns.firstOrNull()
                    if (latestRun == null) {
                        Text("ยังไม่มีประวัติการทดสอบตัวเอง")
                    } else {
                        Text(
                            "ผลล่าสุด: ${if (latestRun.passed) "ผ่าน" else "ไม่ผ่าน"} • ${latestRun.checksPassed}/${latestRun.checksTotal}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (latestRun.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(latestRun.summary, fontSize = 12.sp)
                        Text("ทดสอบเมื่อ " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(latestRun.timestampMs)), fontSize = 11.sp)
                    }
                    Text("ประวัติการทดสอบ: ${selfDefenseRuns.size} รอบ • เก็บในเครื่องสูงสุด 100 รอบ", fontSize = 12.sp)
                    Button(
                        onClick = {
                            val result = SelfDefenseStore.runSelfTest(context)
                            selfDefenseRuns = SelfDefenseStore.history(context)
                            Toast.makeText(context, "Self-Defense: ${result.checksPassed}/${result.checksTotal} checks", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ทดสอบระบบป้องกันตัวเอง") }
                    Text(
                        "ใช้ข้อมูลสังเคราะห์เพื่อตรวจกลไกวิเคราะห์เท่านั้น ยังไม่ใช่การทดสอบเจาะระบบจริง และไม่มีการแก้ไขกฎหรือโค้ดอัตโนมัติ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = {
                    val clipboardText = buildString {
                        appendLine("MAHATHEP AUDITOR - SECURITY TEST REPORT")
                        appendLine("แหล่งข้อมูล: ${sourceLabel}")
                        appendLine("สรุป: ทั้งหมด ${summary.total}, ผ่าน ${summary.passed}, พลาดการตรวจจับ ${summary.missed}, แจ้งเตือนเกิน ${summary.falseAlarms}, คะแนนเฉลี่ย ${summary.averageScore}/100")
                        appendLine()
                        appendLine("[LIVE GPS]")
                        liveFix?.let { fix ->
                            appendLine("sequence=${fix.sequence}, source=${fix.source}")
                            appendLine("latitude=${fix.latitude}, longitude=${fix.longitude}")
                            appendLine("speedKmh=${fix.speedKmh}, accuracyMeters=${fix.accuracyMeters}, timestampMs=${fix.timestampMs}")
                        } ?: appendLine("ยังไม่มีข้อมูล GPS ล่าสุด")
                        appendLine()
                        appendLine("[GPS HISTORY - ล่าสุดไม่เกิน 50 รายการ]")
                        liveHistory.takeLast(50).forEach { fix ->
                            appendLine("#${fix.sequence}|${fix.latitude},${fix.longitude}|${fix.speedKmh} km/h|accuracy=${fix.accuracyMeters}m|time=${fix.timestampMs}|${fix.source}")
                        }
                        appendLine()
                        appendLine("[TEST CASES]")
                        if (cases.isEmpty()) appendLine("ไม่มีผลทดสอบ")
                        cases.forEach { item ->
                            appendLine("CASE|${item.name}|expected=${item.expected}|actual=${item.actual}|score=${item.score}|passed=${item.passed}|findings=${item.findings}")
                        }
                        appendLine()
                        appendLine("หมายเหตุ: รายงานนี้มีพิกัด GPS จริง/จำลอง โปรดตรวจสอบก่อนส่งต่อ และใช้เฉพาะข้อมูลที่ได้รับอนุญาต")
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MAHATHEP AUDITOR report", clipboardText))
                    Toast.makeText(context, "คัดลอกข้อมูล AUDITOR แล้ว", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("คัดลอกข้อมูลสำหรับส่งให้ ChatGPT")
            }
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
