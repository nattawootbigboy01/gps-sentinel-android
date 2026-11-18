package com.gpsdefenseauditor

data class DefenseRecommendation(val priority: String, val title: String, val reason: String, val action: String)
data class DefenseAssessment(val analyzedCases: Int, val missedDetections: Int, val falseAlarms: Int, val recommendations: List<DefenseRecommendation>)

/** Turns authorized controlled-test results into defensive engineering advice only. */
object AdaptiveDefenseEngine {
    const val ENGINE_VERSION = "0.1.0"

    fun analyze(cases: List<AuditCase>): DefenseAssessment {
        val missed = cases.filter { it.expected != "LOW" && severity(it.actual) < severity(it.expected) }
        val falseAlarms = cases.filter { it.expected == "LOW" && it.actual != "LOW" }
        val recommendations = mutableListOf<DefenseRecommendation>()

        if (missed.isNotEmpty()) {
            val codes = missed.flatMap { it.findings.split(',', ';').map(String::trim) }
                .filter { it.isNotBlank() }.toSet()
            recommendations += DefenseRecommendation(
                "สูง", "ปิดช่องว่างการตรวจจับ ${missed.size} กรณี",
                "มีสถานการณ์ที่ระดับความเสี่ยงจริงสูงกว่าผลที่ระบบประเมิน",
                buildString {
                    append("ตรวจสอบข้อมูลต้นทางและเพิ่มการตรวจสอบแบบหลายสัญญาณ")
                    if (codes.isNotEmpty()) append(" โดยเริ่มจากรหัส: ${codes.joinToString()}")
                    append(" จากนั้นทดสอบซ้ำด้วยชุดข้อมูลเดิม")
                }
            )
        }
        if (falseAlarms.isNotEmpty()) {
            recommendations += DefenseRecommendation(
                "กลาง", "ลดการแจ้งเตือนเกิน ${falseAlarms.size} กรณี",
                "พบข้อมูลปกติที่ถูกจัดระดับสูงกว่า LOW",
                "ปรับเกณฑ์ให้พิจารณาความแม่นยำของตำแหน่ง อายุข้อมูล และบริบทการเคลื่อนที่ร่วมกัน ห้ามลดความเข้มงวดจากสัญญาณเดียวโดยไม่มีผลทดสอบรองรับ"
            )
        }
        if (cases.size < 5) {
            recommendations += DefenseRecommendation(
                "ข้อมูลยังน้อย", "เพิ่มจำนวนสถานการณ์ทดสอบ",
                "มีข้อมูลเพียง ${cases.size} กรณี ยังไม่เพียงพอสำหรับสรุปความแข็งแรงของระบบ",
                "เพิ่มกรณีปกติและกรณีผิดปกติหลายระดับ แล้วทดสอบซ้ำในสภาพแวดล้อมที่ได้รับอนุญาต"
            )
        }
        if (recommendations.isEmpty()) {
            recommendations += DefenseRecommendation(
                "ติดตามต่อ", "ยังไม่พบช่องว่างจากชุดทดสอบนี้",
                "ผลทดสอบที่ได้รับไม่พบ missed detection หรือ false alarm",
                "เก็บชุดทดสอบนี้เป็น regression baseline และเพิ่มสถานการณ์ใหม่ก่อนสรุปว่าระบบแข็งแรง"
            )
        }
        return DefenseAssessment(cases.size, missed.size, falseAlarms.size, recommendations)
    }

    private fun severity(value: String): Int = when (value.trim().uppercase()) {
        "LOW" -> 0
        "REVIEW" -> 1
        "HIGH" -> 2
        else -> -1
    }
}
