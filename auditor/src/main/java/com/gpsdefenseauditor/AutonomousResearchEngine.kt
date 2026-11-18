package com.gpsdefenseauditor

data class ResearchHypothesis(
    val title: String,
    val evidence: String,
    val confidence: String,
    val safeExperiment: String,
    val defensiveAction: String
)

data class ResearchCycleReport(
    val createdAtMs: Long,
    val casesReviewed: Int,
    val evidenceCount: Int,
    val topicsCovered: List<String>,
    val hypotheses: List<ResearchHypothesis>,
    val limitations: String
)

/**
 * Evidence-led research for systems the operator owns or is explicitly authorized to test.
 * This engine does not probe other apps, bypass controls, or change production rules.
 */
object AutonomousResearchEngine {
    const val ENGINE_VERSION = "0.1.0"

    private val topicCatalog = listOf(
        "GPS & Location Integrity",
        "Android App / Device Integrity",
        "Timestamp & Sensor Consistency",
        "API Authentication & Authorization",
        "Cooldown / Rate-limit Correctness"
    )

    fun analyze(cases: List<AuditCase>, nowMs: Long = System.currentTimeMillis()): ResearchCycleReport {
        val evidence = cases.flatMap { case ->
            case.findings.split(',', ';', '|')
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { code -> code.uppercase() to case }
        }
        val hypotheses = mutableListOf<ResearchHypothesis>()

        fun addIf(matched: List<Pair<String, AuditCase>>, hypothesis: ResearchHypothesis) {
            if (matched.isNotEmpty()) hypotheses += hypothesis.copy(
                evidence = "${matched.size} รายการจาก ${matched.map { it.first }.distinct().joinToString()}",
                confidence = if (matched.size >= 3 && cases.size >= 5) "ปานกลาง" else "ต่ำ"
            )
        }

        addIf(evidence.filter { (code, _) ->
            code.contains("MOCK") || code.contains("GPS") || code.contains("LOCATION")
        }, ResearchHypothesis(
            "ตรวจสอบความน่าเชื่อถือของตำแหน่ง",
            "",
            "",
            "ใช้ชุดพิกัดสังเคราะห์ที่กำหนด expected result ไว้ล่วงหน้าใน test build ของแอปที่ได้รับอนุญาต",
            "รวมสัญญาณจาก mock-provider flag, accuracy, อายุข้อมูล และความต่อเนื่องของพิกัด โดยไม่ตัดสินจากสัญญาณเดียว"
        ))
        addIf(evidence.filter { (code, _) ->
            code.contains("TIME") || code.contains("STALE") || code.contains("FUTURE") || code.contains("SEQUENCE")
        }, ResearchHypothesis(
            "ตรวจสอบความสอดคล้องของเวลาและลำดับข้อมูล",
            "",
            "",
            "ป้อน timestamp สังเคราะห์แบบเก่า อนาคต และซ้ำใน unit test แล้วตรวจผลที่คาดหวัง",
            "ตรวจ monotonicity, อายุข้อมูล และความสัมพันธ์ระหว่าง wall clock กับ elapsed realtime"
        ))
        addIf(evidence.filter { (code, _) ->
            code.contains("SPEED") || code.contains("JUMP") || code.contains("DISPLACEMENT") || code.contains("ACCURACY")
        }, ResearchHypothesis(
            "ตรวจสอบความสมเหตุสมผลของการเคลื่อนที่",
            "",
            "",
            "ทดสอบเส้นทางสังเคราะห์ทั้งการเดินทางปกติและการเปลี่ยนตำแหน่งผิดธรรมชาติใน simulator ภายใน",
            "ประเมินระยะทาง ความเร็ว accuracy และช่วงเวลาร่วมกัน พร้อมช่วงผ่อนผันสำหรับ GPS จริง"
        ))
        addIf(evidence.filter { (code, _) ->
            code.contains("COOLDOWN") || code.contains("LOCKOUT") || code.contains("RATE_LIMIT") || code.contains("SUSPEND")
        }, ResearchHypothesis(
            "ตรวจสอบความถูกต้องของช่วงระงับการใช้งาน",
            "",
            "",
            "ใน test build ให้จำลองเวลาและตรวจว่าเริ่มจับเวลาเมื่อใด หมดเวลาเมื่อใด และเหตุการณ์ปกติถูกระงับผิดพลาดหรือไม่",
            "แยกเหตุผลการระงับ บันทึกเวลาเริ่ม/สิ้นสุด และทดสอบการกู้คืนตามนโยบาย โดยไม่ข้ามหรือปิดมาตรการจริง"
        ))
        addIf(evidence.filter { (code, _) ->
            code.contains("INTEGRITY") || code.contains("TAMPER") || code.contains("SIGNATURE") || code.contains("DEBUG")
        }, ResearchHypothesis(
            "ตรวจสอบความสมบูรณ์ของแอปและอุปกรณ์",
            "",
            "",
            "ใช้ build สำหรับทดสอบที่มีการเปลี่ยนแปลงไฟล์หรือสถานะที่กำหนดไว้ แล้วตรวจผลจากแอปของเราเอง",
            "ตรวจลายเซ็น build, ความสมบูรณ์ของไฟล์ และสถานะ runtime พร้อมแยกสัญญาณเตือนออกจากข้อยืนยัน"
        ))

        if (hypotheses.isEmpty()) {
            hypotheses += ResearchHypothesis(
                "หลักฐานยังไม่พอสำหรับตั้งสมมติฐานเฉพาะ",
                "ยังไม่พบ finding code ที่จำแนกได้จากรายงานที่ได้รับ",
                "ยังประเมินไม่ได้",
                "ส่งรายงานจาก test build ที่ได้รับอนุญาต โดยมี test case ID, expected, actual, app version และเวลาทดสอบ",
                "เพิ่ม telemetry ที่ไม่เก็บข้อมูลส่วนบุคคลเกินจำเป็น และระบุแหล่งที่มาของผลตรวจทุกครั้ง"
            )
        }

        val topics = buildList {
            if (evidence.any { it.first.contains("GPS") || it.first.contains("LOCATION") || it.first.contains("MOCK") }) add(topicCatalog[0])
            if (evidence.any { it.first.contains("INTEGRITY") || it.first.contains("TAMPER") || it.first.contains("DEBUG") }) add(topicCatalog[1])
            if (evidence.any { it.first.contains("TIME") || it.first.contains("STALE") || it.first.contains("FUTURE") || it.first.contains("SEQUENCE") }) add(topicCatalog[2])
            if (evidence.any { it.first.contains("API") || it.first.contains("AUTH") || it.first.contains("SESSION") }) add(topicCatalog[3])
            if (evidence.any { it.first.contains("COOLDOWN") || it.first.contains("LOCKOUT") || it.first.contains("RATE_LIMIT") || it.first.contains("SUSPEND") }) add(topicCatalog[4])
        }.distinct()

        return ResearchCycleReport(
            createdAtMs = nowMs,
            casesReviewed = cases.size,
            evidenceCount = evidence.size,
            topicsCovered = topics,
            hypotheses = hypotheses,
            limitations = "เป็นการวิเคราะห์จากรายงานที่ป้อนเข้ามาเท่านั้น ไม่ได้อ่านโค้ดหรือสอดส่องแอปอื่น ไม่ยืนยันกลไกภายในจากพฤติกรรมที่สังเกตได้ และไม่แก้กฎ production อัตโนมัติ"
        )
    }
}
