package com.gpsdefenseauditor

data class GeneratedTestCase(
    val id: String,
    val expected: String,
    val findings: String,
    val reason: String
)

data class CandidateRule(
    val findingCode: String,
    val minimumSeverity: String,
    val evidenceCases: Int,
    val status: String = "DRAFT"
)

data class RuleValidation(
    val findingCode: String,
    val missedBefore: Int,
    val missedAfter: Int,
    val falseAlarmsBefore: Int,
    val falseAlarmsAfter: Int,
    val acceptedForReview: Boolean
)

data class EvolutionCycleReport(
    val timestampMs: Long,
    val sourceCases: Int,
    val generatedCases: List<GeneratedTestCase>,
    val candidateRules: List<CandidateRule>,
    val validations: List<RuleValidation>,
    val status: String,
    val notes: String
)

/**
 * Deterministic offline evolution pipeline. It generates test specifications
 * and validates candidate policies only. It never edits production detector rules.
 */
object MahathepEvolutionEngine {
    const val ENGINE_VERSION = "1.0.1"
    private const val MAX_GENERATED = 20
    private const val MAX_CANDIDATES = 10

    private val scenarioCatalog = mapOf(
        "ANDROID_MOCK_FLAG" to ("MOCK_PROVIDER_REPORTED" to "ตรวจว่าระบบแยก mock-provider signal ออกจากหลักฐานยืนยันอื่น"),
        "MOCK_LOCATION" to ("MOCK_PROVIDER_REPORTED" to "ทดสอบสัญญาณ mock provider ใน test build ที่ควบคุมได้"),
        "STALE_LOCATION" to ("STALE_FIX" to "ทดสอบอายุข้อมูลตำแหน่งและการปฏิเสธข้อมูลเก่า"),
        "STALE_FIX" to ("STALE_FIX" to "ทดสอบอายุข้อมูลตำแหน่งและการปฏิเสธข้อมูลเก่า"),
        "FUTURE_TIMESTAMP" to ("FUTURE_TIMESTAMP" to "ทดสอบ timestamp ที่อยู่ในอนาคต"),
        "NON_MONOTONIC_TIME" to ("NON_MONOTONIC_TIMESTAMP" to "ทดสอบ timestamp ซ้ำหรือย้อนลำดับ"),
        "TIMESTAMP" to ("NON_MONOTONIC_TIMESTAMP" to "ทดสอบความต่อเนื่องของเวลา"),
        "IMPLAUSIBLE_DISPLACEMENT" to ("IMPLAUSIBLE_JUMP" to "ทดสอบตำแหน่งกระโดดที่ไม่สัมพันธ์กับเวลา"),
        "IMPLAUSIBLE_ACCELERATION" to ("SPEED_DISPLACEMENT_MISMATCH" to "ทดสอบความไม่สอดคล้องของความเร็วกับระยะทาง"),
        "SPEED_DISAGREEMENT" to ("SPEED_DISPLACEMENT_MISMATCH" to "ทดสอบความไม่สอดคล้องของความเร็วกับระยะทาง"),
        "SPEED" to ("SPEED_DISPLACEMENT_MISMATCH" to "ทดสอบความไม่สอดคล้องของความเร็วกับระยะทาง"),
        "LOW_ACCURACY" to ("LOW_ACCURACY_CONTEXT" to "ทดสอบการพิจารณา accuracy ร่วมกับบริบท"),
        "COOLDOWN" to ("COOLDOWN_STATE" to "ทดสอบการเริ่มและสิ้นสุด cooldown ตามนโยบายใน test build"),
        "RATE_LIMIT" to ("RATE_LIMIT_STATE" to "ทดสอบขอบเขต rate limit ใน sandbox")
    )

    fun generateCases(cases: List<AuditCase>): List<GeneratedTestCase> {
        val generated = linkedMapOf<String, GeneratedTestCase>()
        cases.flatMap { it.findings.split(',', ';', '|') }
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .forEach { code ->
                val entry = scenarioCatalog.entries.firstOrNull { code == it.key || code.contains(it.key) }
                    ?: return@forEach
                val (id, reason) = entry.value
                if (id !in generated) {
                    val expected = when (id) {
                        "MOCK_PROVIDER_REPORTED" -> "HIGH"
                        "LOW_ACCURACY_CONTEXT" -> "LOW"
                        else -> "REVIEW"
                    }
                    generated[id] = GeneratedTestCase(
                        id = id,
                        expected = expected,
                        findings = code,
                        reason = reason
                    )
                }
            }
        return generated.values.take(MAX_GENERATED)
    }

    fun proposeRules(cases: List<AuditCase>): List<CandidateRule> {
        val proposals = linkedMapOf<String, CandidateRule>()
        cases.filter { severity(it.expected) > severity(it.actual) }
            .forEach { case ->
                case.findings.split(',', ';', '|').map { it.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .forEach { code ->
                        val previous = proposals[code]
                        val minimum = if (severity(case.expected) >= severity(previous?.minimumSeverity ?: "LOW")) {
                            case.expected.uppercase()
                        } else previous!!.minimumSeverity
                        proposals[code] = CandidateRule(
                            findingCode = code,
                            minimumSeverity = minimum,
                            evidenceCases = (previous?.evidenceCases ?: 0) + 1
                        )
                    }
            }
        return proposals.values.take(MAX_CANDIDATES)
    }

    fun validateRules(cases: List<AuditCase>, candidates: List<CandidateRule>): List<RuleValidation> =
        candidates.map { rule ->
            val affected = cases.filter { case ->
                case.findings.split(',', ';', '|').any { it.trim().equals(rule.findingCode, true) }
            }
            val beforeMissed = affected.count { severity(it.expected) > severity(it.actual) }
            val beforeFalse = affected.count { severity(it.expected) == 0 && severity(it.actual) > 0 }
            val after = affected.map { case ->
                val adjusted = maxOf(severity(case.actual), severity(rule.minimumSeverity))
                case.expected to adjusted
            }
            val afterMissed = after.count { (expected, actual) -> severity(expected) > actual }
            val afterFalse = after.count { (expected, actual) -> severity(expected) == 0 && actual > 0 }
            RuleValidation(
                findingCode = rule.findingCode,
                missedBefore = beforeMissed,
                missedAfter = afterMissed,
                falseAlarmsBefore = beforeFalse,
                falseAlarmsAfter = afterFalse,
                acceptedForReview = afterMissed <= beforeMissed && afterFalse <= beforeFalse
            )
        }

    fun runCycle(cases: List<AuditCase>, nowMs: Long = System.currentTimeMillis()): EvolutionCycleReport {
        val boundedCases = cases.takeLast(200)
        val generated = generateCases(boundedCases)
        val candidates = proposeRules(boundedCases)
        val validations = validateRules(boundedCases, candidates)
        val accepted = validations.count { it.acceptedForReview }
        val status = when {
            boundedCases.isEmpty() -> "WAITING_FOR_EVIDENCE"
            candidates.isEmpty() -> "NO_RULE_CHANGE_PROPOSED"
            accepted == candidates.size -> "CANDIDATES_VALIDATED_FOR_REVIEW"
            else -> "CANDIDATES_NEED_REVIEW"
        }
        return EvolutionCycleReport(
            timestampMs = nowMs,
            sourceCases = boundedCases.size,
            generatedCases = generated,
            candidateRules = candidates,
            validations = validations,
            status = status,
            notes = "สร้างข้อกำหนด Test Case จาก finding ที่รู้จักแล้วเท่านั้น รายการที่สร้างยังไม่ใช่การทดสอบที่รันจริง กฎเป็น Candidate ไม่เปลี่ยนระบบจริง ต้องนำไปเชื่อม test harness และรัน regression ใน test build ก่อนอนุมัติ"
        )
    }

    private fun severity(value: String): Int = when (value.trim().uppercase()) {
        "LOW" -> 0
        "REVIEW" -> 1
        "HIGH" -> 2
        else -> -1
    }
}
