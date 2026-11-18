package com.gpsdefenseauditor

import android.content.Intent
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

class AuditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var cases by remember { mutableStateOf(AuditReportParser.parse(incomingReport)) }
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
