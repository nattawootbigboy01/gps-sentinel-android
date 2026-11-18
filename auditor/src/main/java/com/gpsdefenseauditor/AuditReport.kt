package com.gpsdefenseauditor

data class AuditCase(
    val name: String,
    val expected: String,
    val actual: String,
    val score: Int,
    val passed: Boolean,
    val findings: String
)

data class AuditSummary(
    val total: Int,
    val passed: Int,
    val missed: Int,
    val falseAlarms: Int,
    val averageScore: Int
)

object AuditReportParser {
    fun parse(raw: String): List<AuditCase> =
        raw.lineSequence()
            .filter { it.startsWith("CASE|") }
            .mapNotNull { line ->
                val parts = line.split("|", limit = 7)
                if (parts.size < 7) return@mapNotNull null
                val score = parts[4].toIntOrNull()?.coerceIn(0, 100) ?: return@mapNotNull null
                AuditCase(
                    name = parts[1],
                    expected = parts[2],
                    actual = parts[3],
                    score = score,
                    passed = parts[5].toBooleanStrictOrNull() ?: false,
                    findings = parts[6]
                )
            }.toList()
}

object AuditAnalyzer {
    fun summarize(cases: List<AuditCase>): AuditSummary {
        if (cases.isEmpty()) return AuditSummary(0, 0, 0, 0, 0)
        val missed = cases.count { it.expected != "LOW" && it.actual == "LOW" }
        val falseAlarms = cases.count { it.expected == "LOW" && it.actual != "LOW" }
        return AuditSummary(
            total = cases.size,
            passed = cases.count { it.passed },
            missed = missed,
            falseAlarms = falseAlarms,
            averageScore = cases.map { it.score }.average().toInt()
        )
    }
}
