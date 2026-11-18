package com.gpsdefenseauditor

import kotlin.math.*

/** Evidence-based signal correlation for synthetic or explicitly authorized test builds. */
data class LocationEvidence(
    val latitude: Double, val longitude: Double, val accuracyMeters: Float,
    val reportedSpeedMps: Float, val wallClockMs: Long, val elapsedRealtimeNanos: Long,
    val isMockProvider: Boolean, val source: String
)
data class DetectionSignalFinding(
    val code: String, val severity: SignalSeverity, val confidence: Double, val evidence: String
)
enum class SignalSeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
enum class SignalVerdict { CLEAR, REVIEW, DETECTED, INSUFFICIENT_DATA }
data class DetectionSignalAssessment(
    val engineVersion: String, val verdict: SignalVerdict, val riskScore: Int,
    val confidence: Double, val findings: List<DetectionSignalFinding>, val limitations: String
)

/**
 * Evaluates supplied telemetry. It never changes locations, conceals mock status,
 * interacts with third-party apps, or claims a target app blocked anything.
 */
object DetectionSignalEngine {
    const val ENGINE_VERSION = "1.0.0"
    private const val MAX_AGE_MS = 120_000L
    private const val FUTURE_TOLERANCE_MS = 30_000L
    private const val MAX_PLAUSIBLE_SPEED_MPS = 55.0

    fun assess(samples: List<LocationEvidence>, nowWallClockMs: Long): DetectionSignalAssessment {
        if (samples.isEmpty()) return result(SignalVerdict.INSUFFICIENT_DATA, 0, 0.0, emptyList())
        val findings = mutableListOf<DetectionSignalFinding>()
        fun add(code: String, severity: SignalSeverity, evidence: String) {
            val confidence = when (severity) {
                SignalSeverity.CRITICAL -> 0.98
                SignalSeverity.HIGH -> 0.90
                SignalSeverity.MEDIUM -> 0.78
                SignalSeverity.LOW -> 0.62
                SignalSeverity.INFO -> 0.45
            }
            findings += DetectionSignalFinding(code, severity, confidence, evidence)
        }

        samples.forEachIndexed { index, sample ->
            if (!validCoordinates(sample))
                add("INVALID_COORDINATES", SignalSeverity.CRITICAL, "sample=$index พิกัดอยู่นอกช่วงหรือไม่เป็นตัวเลข")
            if (sample.isMockProvider)
                add("MOCK_PROVIDER_FLAG", SignalSeverity.HIGH, "sample=$index Android ระบุว่าเป็น mock provider")
            if (!sample.accuracyMeters.isFinite() || sample.accuracyMeters < 0f)
                add("INVALID_ACCURACY", SignalSeverity.HIGH, "sample=$index ค่า accuracy ไม่ถูกต้อง")
            else if (sample.accuracyMeters >= 500f)
                add("VERY_POOR_ACCURACY", SignalSeverity.MEDIUM, "sample=$index accuracy=${sample.accuracyMeters}m")
            else if (sample.accuracyMeters >= 100f)
                add("POOR_ACCURACY", SignalSeverity.LOW, "sample=$index accuracy=${sample.accuracyMeters}m")
            val age = nowWallClockMs - sample.wallClockMs
            if (age > MAX_AGE_MS)
                add("STALE_TIMESTAMP", SignalSeverity.MEDIUM, "sample=$index age=${age}ms")
            else if (age < -FUTURE_TOLERANCE_MS)
                add("FUTURE_TIMESTAMP", SignalSeverity.HIGH, "sample=$index futureBy=${-age}ms")
            if (sample.reportedSpeedMps.isFinite() && sample.reportedSpeedMps < 0f)
                add("INVALID_REPORTED_SPEED", SignalSeverity.MEDIUM, "sample=$index reported speed ติดลบ")
            if (sample.source.isBlank())
                add("MISSING_PROVENANCE", SignalSeverity.LOW, "sample=$index ไม่มีข้อมูลแหล่งที่มา")
        }

        samples.zipWithNext().forEachIndexed { index, pair ->
            val previous = pair.first
            val current = pair.second
            if (current.wallClockMs <= previous.wallClockMs)
                add("NON_MONOTONIC_WALL_CLOCK", SignalSeverity.MEDIUM, "คู่ sample=$index เวลาไม่เพิ่มขึ้น")
            if (current.elapsedRealtimeNanos <= previous.elapsedRealtimeNanos)
                add("NON_MONOTONIC_ELAPSED_TIME", SignalSeverity.HIGH, "คู่ sample=$index elapsed realtime ไม่เพิ่มขึ้น")
            val elapsedSeconds = (current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            if (elapsedSeconds > 0 && validCoordinates(previous) && validCoordinates(current)) {
                val derivedSpeed = distanceMeters(previous, current) / elapsedSeconds
                if (derivedSpeed > MAX_PLAUSIBLE_SPEED_MPS)
                    add("IMPLAUSIBLE_DISPLACEMENT", SignalSeverity.HIGH, "คู่ sample=$index derivedSpeed=${"%.2f".format(java.util.Locale.US, derivedSpeed)}m/s")
                val reported = current.reportedSpeedMps.toDouble()
                if (reported.isFinite() && reported >= 0.0) {
                    val tolerance = max(8.0, derivedSpeed * 0.60)
                    if (abs(reported - derivedSpeed) > tolerance)
                        add("SPEED_DISAGREEMENT", SignalSeverity.MEDIUM, "คู่ sample=$index derived=${"%.2f".format(java.util.Locale.US, derivedSpeed)}m/s reported=${"%.2f".format(java.util.Locale.US, reported)}m/s")
                }
            }
        }

        val unique = findings.distinctBy { it.code to it.evidence }
        val risk = unique.sumOf { finding ->
            when (finding.code) {
                "MOCK_PROVIDER_FLAG" -> 50
                "INVALID_COORDINATES" -> 35
                "FUTURE_TIMESTAMP" -> 20
                "STALE_TIMESTAMP" -> 15
                "NON_MONOTONIC_WALL_CLOCK", "NON_MONOTONIC_ELAPSED_TIME" -> 15
                "IMPLAUSIBLE_DISPLACEMENT" -> 25
                "SPEED_DISAGREEMENT" -> 10
                "VERY_POOR_ACCURACY" -> 18
                "POOR_ACCURACY" -> 8
                "INVALID_ACCURACY" -> 25
                "INVALID_REPORTED_SPEED" -> 12
                "MISSING_PROVENANCE" -> 5
                else -> 0
            }
        }.coerceAtMost(100)
        val verdict = when {
            unique.any { it.severity == SignalSeverity.CRITICAL } -> SignalVerdict.DETECTED
            risk >= 50 -> SignalVerdict.DETECTED
            risk >= 15 -> SignalVerdict.REVIEW
            else -> SignalVerdict.CLEAR
        }
        val confidence = if (unique.isEmpty()) 0.55
            else (0.45 + unique.map { it.confidence }.average() * 0.5).coerceIn(0.0, 0.95)
        return result(verdict, risk, confidence, unique)
    }

    private fun result(verdict: SignalVerdict, risk: Int, confidence: Double, findings: List<DetectionSignalFinding>) =
        DetectionSignalAssessment(
            ENGINE_VERSION, verdict, risk, confidence, findings,
            "ประเมินจาก telemetry ที่ป้อนให้ engine เท่านั้น ไม่ได้พิสูจน์ว่าแอปเป้าหมายตรวจพบหรือบล็อกจริง; BLOCKED ต้องยืนยันจากผลตอบสนองของ test build ที่ได้รับอนุญาต"
        )

    private fun validCoordinates(s: LocationEvidence) =
        s.latitude.isFinite() && s.latitude in -90.0..90.0 &&
            s.longitude.isFinite() && s.longitude in -180.0..180.0

    private fun distanceMeters(a: LocationEvidence, b: LocationEvidence): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * radius * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
