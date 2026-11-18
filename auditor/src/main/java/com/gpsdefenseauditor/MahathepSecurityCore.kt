package com.gpsdefenseauditor

data class CoreCheckResult(
    val id: String,
    val expected: String,
    val actual: String,
    val passed: Boolean,
    val detail: String
)

data class CoreSelfTestReport(
    val createdAtMs: Long,
    val checks: List<CoreCheckResult>,
    val passed: Int,
    val total: Int
) {
    val allPassed: Boolean get() = total > 0 && passed == total
}

/**
 * Offline, deterministic security core. Uses synthetic inputs only and never
 * reads, changes, or probes another application's runtime state.
 */
object MahathepSecurityCore {
    const val ENGINE_VERSION = "1.0.0"

    fun evaluate(case: AuditCase): CoreCheckResult {
        val expected = severity(case.expected)
        val actual = severity(case.actual)
        val passed = expected >= 0 && actual >= 0 && actual >= expected
        val detail = when {
            expected < 0 || actual < 0 -> "ระดับความเสี่ยงไม่รู้จัก ต้องตรวจสอบข้อมูล"
            actual < expected -> "ตรวจพบช่องว่าง: ผลประเมินต่ำกว่าระดับที่คาดหวัง"
            else -> "ผลประเมินไม่ต่ำกว่าเกณฑ์ที่คาดหวัง"
        }
        return CoreCheckResult(case.name, case.expected, case.actual, passed, detail)
    }

    fun runOfflineSelfTest(): CoreSelfTestReport {
        val syntheticCases = listOf(
            AuditCase("NORMAL_BASELINE", "LOW", "LOW", 0, true, "SYNTHETIC"),
            AuditCase("MOCK_PROVIDER_SIGNAL", "HIGH", "HIGH", 80, true, "SYNTHETIC"),
            AuditCase("STALE_LOCATION", "REVIEW", "REVIEW", 55, true, "SYNTHETIC"),
            AuditCase("FUTURE_TIMESTAMP", "HIGH", "HIGH", 85, true, "SYNTHETIC"),
            AuditCase("LOW_ACCURACY", "REVIEW", "REVIEW", 45, true, "SYNTHETIC"),
            AuditCase("NON_MONOTONIC_TIME", "HIGH", "HIGH", 90, true, "SYNTHETIC"),
            AuditCase("IMPLAUSIBLE_JUMP", "HIGH", "HIGH", 90, true, "SYNTHETIC"),
            AuditCase("MISSED_DETECTION_GUARD", "HIGH", "LOW", 0, false, "SYNTHETIC")
        )
        val checks = syntheticCases.map(::evaluate)
        return CoreSelfTestReport(
            createdAtMs = System.currentTimeMillis(),
            checks = checks,
            passed = checks.count { it.passed },
            total = checks.size
        )
    }

    private fun severity(value: String): Int = when (value.trim().uppercase()) {
        "LOW" -> 0
        "REVIEW" -> 1
        "HIGH" -> 2
        else -> -1
    }
}
