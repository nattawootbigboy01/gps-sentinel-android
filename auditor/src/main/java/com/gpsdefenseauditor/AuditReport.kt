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

object AuditRouteAnalyzer {
    fun distanceKm(raw: String): Double? {
        val points = raw.lineSequence().mapNotNull { line ->
            if (!line.startsWith("POINT|")) return@mapNotNull null
            val parts = line.split("|")
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return@mapNotNull null
            lat to lon
        }.toList()
        if (points.size < 2) return null
        return points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.first, a.second, b.first, b.second) } / 1000.0
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2.0 * radius * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
    }
}
