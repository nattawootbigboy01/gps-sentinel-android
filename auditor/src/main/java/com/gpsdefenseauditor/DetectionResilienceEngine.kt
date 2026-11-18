package com.gpsdefenseauditor

/**
 * Builds bounded adversarial test specifications for an owned/authorized test harness.
 * These are test mutations of synthetic evidence, not production GPS spoofing or bypass code.
 */
data class AdversarialProbe(
    val id: String,
    val sourceFinding: String,
    val mutation: String,
    val purpose: String,
    val expectedHandling: String = "REVIEW_OR_REJECT_WITH_EVIDENCE"
)

data class DetectionResilienceReport(
    val engineVersion: String,
    val sourceCases: Int,
    val probes: List<AdversarialProbe>,
    val limitations: String
)

object DetectionResilienceEngine {
    const val ENGINE_VERSION = "0.1.0"
    private const val MAX_PROBES = 30

    private val knownFamilies = mapOf(
        "GPS" to listOf("SINGLE_SIGNAL_ABLATION", "BOUNDARY_VALUE", "CONFLICTING_SIGNAL"),
        "LOCATION" to listOf("SINGLE_SIGNAL_ABLATION", "BOUNDARY_VALUE", "CONFLICTING_SIGNAL"),
        "MOCK" to listOf("SINGLE_SIGNAL_ABLATION", "CONFLICTING_SIGNAL"),
        "TIME" to listOf("CLOCK_SKEW", "EVENT_REORDERING", "BOUNDARY_VALUE"),
        "STALE" to listOf("CLOCK_SKEW", "BOUNDARY_VALUE"),
        "FUTURE" to listOf("CLOCK_SKEW", "BOUNDARY_VALUE"),
        "SPEED" to listOf("BOUNDARY_VALUE", "CONFLICTING_SIGNAL", "SINGLE_SIGNAL_ABLATION"),
        "JUMP" to listOf("BOUNDARY_VALUE", "CONFLICTING_SIGNAL"),
        "DISPLACEMENT" to listOf("BOUNDARY_VALUE", "CONFLICTING_SIGNAL"),
        "ACCURACY" to listOf("BOUNDARY_VALUE", "CONFLICTING_SIGNAL"),
        "INTEGRITY" to listOf("SINGLE_SIGNAL_ABLATION", "CONFLICTING_SIGNAL"),
        "TAMPER" to listOf("SINGLE_SIGNAL_ABLATION", "CONFLICTING_SIGNAL"),
        "RATE_LIMIT" to listOf("BOUNDARY_VALUE", "EVENT_REORDERING"),
        "COOLDOWN" to listOf("BOUNDARY_VALUE", "EVENT_REORDERING")
    )

    private val descriptions = mapOf(
        "SINGLE_SIGNAL_ABLATION" to "ละสัญญาณสังเคราะห์ทีละรายการ เพื่อดูว่าการตัดสินใจพึ่งสัญญาณเดียวเกินไปหรือไม่",
        "BOUNDARY_VALUE" to "ทดสอบค่าบริเวณขอบเขตที่กำหนดใน test build เพื่อค้นหาช่องว่างระหว่างเกณฑ์",
        "CONFLICTING_SIGNAL" to "สร้างหลักฐานสังเคราะห์ที่ขัดแย้งกัน เพื่อทดสอบการรวมหลายสัญญาณ",
        "CLOCK_SKEW" to "จำลองความคลาดเคลื่อนของเวลาในชุดทดสอบ โดยไม่เปลี่ยนนาฬิกาเครื่องจริง",
        "EVENT_REORDERING" to "สลับลำดับเหตุการณ์สังเคราะห์ เพื่อตรวจการพึ่งพาลำดับข้อมูล"
    )

    fun buildProbes(cases: List<AuditCase>): DetectionResilienceReport {
        val probes = linkedMapOf<String, AdversarialProbe>()
        cases.takeLast(200).forEach { case ->
            val findings = case.findings.split(',', ';', '|')
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
            findings.forEach { finding ->
                val family = knownFamilies.keys.firstOrNull { finding.contains(it) }
                val mutations = family?.let { knownFamilies[it] }
                    ?: listOf("UNCLASSIFIED_SIGNAL_REVIEW")
                mutations.forEach { mutation ->
                    val id = "${finding}_${mutation}"
                    probes.putIfAbsent(id, AdversarialProbe(
                        id = id,
                        sourceFinding = finding,
                        mutation = mutation,
                        purpose = descriptions[mutation]
                            ?: "ยังไม่จัดหมวดสัญญาณนี้ ให้เก็บหลักฐานและออกแบบการทดลองใน test harness ก่อน"
                    ))
                }
            }
        }
        return DetectionResilienceReport(
            engineVersion = ENGINE_VERSION,
            sourceCases = cases.takeLast(200).size,
            probes = probes.values.take(MAX_PROBES),
            limitations = "เป็นแผนทดสอบเชิง adversarial จากรายงานเท่านั้น ยังไม่ใช่การรันกับแอปเป้าหมาย ไม่ยืนยันว่าพบช่องโหว่ และไม่สร้างกลไกหลบการตรวจจับสำหรับแอปภายนอก ต้องรันใน synthetic harness หรือ test build ที่ได้รับอนุญาต"
        )
    }
}
