package com.gpssentinel

import kotlin.math.*

/**
 * Deterministic, offline GPS risk analysis for locations collected by an app
 * that owns the device/session being tested. This class never changes device
 * location and never sends simulated fixes to other applications.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val elapsedRealtimeNanos: Long,
    val horizontalAccuracyMeters: Float? = null,
    val reportedSpeedMetersPerSecond: Float? = null,
    val isMockProvider: Boolean = false,
    val provider: String? = null
)

enum class RiskLevel { LOW, REVIEW, HIGH }

data class RiskFinding(
    val code: String,
    val points: Int,
    val explanation: String
)

data class GpsRiskReport(
    val score: Int,
    val level: RiskLevel,
    val findings: List<RiskFinding>,
    val derivedSpeedMetersPerSecond: Double?
)

/**
 * Explainable rules-based baseline. Scores are signals for review, not proof
 * of fraud. A production app should combine this with server-side session,
 * route and device-integrity evidence before taking action.
 */
class GpsRiskEngine(
    private val maxPlausibleSpeedMps: Double = 55.0,
    private val maxPlausibleAccelerationMps2: Double = 12.0,
    private val staleAfterNanos: Long = 30_000_000_000L
) {
    fun evaluate(
        current: LocationSample,
        previous: LocationSample? = null,
        evaluationElapsedRealtimeNanos: Long = current.elapsedRealtimeNanos
    ): GpsRiskReport {
        val findings = mutableListOf<RiskFinding>()

        if (!current.latitude.isFinite() || current.latitude !in -90.0..90.0 ||
            !current.longitude.isFinite() || current.longitude !in -180.0..180.0) {
            findings += RiskFinding("INVALID_COORDINATE", 100, "พิกัดอยู่นอกช่วงที่ถูกต้อง")
        }

        if (current.isMockProvider) {
            findings += RiskFinding(
                "ANDROID_MOCK_FLAG", 65,
                "Android ระบุว่าตำแหน่งนี้มาจาก Mock Location"
            )
        }

        val accuracy = current.horizontalAccuracyMeters
        if (accuracy != null && (!accuracy.isFinite() || accuracy < 0f)) {
            findings += RiskFinding("INVALID_ACCURACY", 25, "ค่าความแม่นยำไม่ถูกต้อง")
        } else if (accuracy != null && accuracy > 100f) {
            findings += RiskFinding("LOW_ACCURACY", 10, "ตำแหน่งมีความคลาดเคลื่อนสูง")
        }

        val age = evaluationElapsedRealtimeNanos - current.elapsedRealtimeNanos
        if (age > staleAfterNanos) {
            findings += RiskFinding("STALE_FIX", 20, "ข้อมูลตำแหน่งเก่าเกินเกณฑ์")
        } else if (age < 0L) {
            findings += RiskFinding("FUTURE_TIMESTAMP", 35, "เวลาอ้างอิงตำแหน่งอยู่ในอนาคต")
        }

        var derivedSpeed: Double? = null
        if (previous != null) {
            val deltaNanos = current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
            if (deltaNanos <= 0L) {
                findings += RiskFinding(
                    "NON_MONOTONIC_TIME", 35,
                    "เวลาของตำแหน่งซ้ำหรือย้อนกลับ"
                )
            } else if (current.latitude.isFinite() && current.longitude.isFinite() &&
                previous.latitude.isFinite() && previous.longitude.isFinite() &&
                current.latitude in -90.0..90.0 && previous.latitude in -90.0..90.0 &&
                current.longitude in -180.0..180.0 && previous.longitude in -180.0..180.0) {
                val seconds = deltaNanos / 1_000_000_000.0
                val distance = distanceMeters(
                    previous.latitude, previous.longitude,
                    current.latitude, current.longitude
                )
                derivedSpeed = distance / seconds
                if (derivedSpeed > maxPlausibleSpeedMps) {
                    findings += RiskFinding(
                        "IMPLAUSIBLE_DISPLACEMENT", 55,
                        "ระยะทางต่อเวลาบ่งชี้ความเร็วสูงผิดปกติ"
                    )
                }

                val previousSpeed = previous.reportedSpeedMetersPerSecond
                val currentSpeed = current.reportedSpeedMetersPerSecond
                if (previousSpeed != null && currentSpeed != null &&
                    previousSpeed.isFinite() && currentSpeed.isFinite() &&
                    seconds > 0.0) {
                    val acceleration = abs(currentSpeed - previousSpeed) / seconds
                    if (acceleration > maxPlausibleAccelerationMps2) {
                        findings += RiskFinding(
                            "IMPLAUSIBLE_ACCELERATION", 25,
                            "ความเร็วเปลี่ยนฉับพลันเกินเกณฑ์"
                        )
                    }
                }

                if (currentSpeed != null && currentSpeed.isFinite() &&
                    derivedSpeed > 5.0 &&
                    abs(currentSpeed.toDouble() - derivedSpeed) > max(15.0, derivedSpeed * 0.75)) {
                    findings += RiskFinding(
                        "SPEED_DISAGREEMENT", 15,
                        "ความเร็วจากอุปกรณ์ไม่สอดคล้องกับระยะทางและเวลา"
                    )
                }
            }
        }

        val score = findings.sumOf { it.points }.coerceIn(0, 100)
        val level = when {
            score >= 60 -> RiskLevel.HIGH
            score >= 25 -> RiskLevel.REVIEW
            else -> RiskLevel.LOW
        }
        return GpsRiskReport(score, level, findings.distinctBy { it.code }, derivedSpeed)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
        return 2.0 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
