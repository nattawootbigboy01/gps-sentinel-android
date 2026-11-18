package com.gpssentinel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRiskEngineTest {
    private val engine = GpsRiskEngine()

    @Test
    fun normalMovementRemainsLowRisk() {
        val previous = LocationSample(16.46000, 102.82000, 1_000_000_000L, 8f, 3f)
        val current = LocationSample(16.46005, 102.82000, 6_000_000_000L, 8f, 3f)

        val report = engine.evaluate(current, previous)

        assertEquals(RiskLevel.LOW, report.level)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun androidMockFlagRaisesHighRiskSignal() {
        val sample = LocationSample(
            16.46, 102.82, 10_000_000_000L,
            horizontalAccuracyMeters = 5f,
            isMockProvider = true
        )

        val report = engine.evaluate(sample)

        assertEquals(RiskLevel.HIGH, report.level)
        assertTrue(report.findings.any { it.code == "ANDROID_MOCK_FLAG" })
    }

    @Test
    fun impossibleDisplacementIsDetected() {
        val previous = LocationSample(16.46000, 102.82000, 1_000_000_000L)
        val current = LocationSample(16.47000, 102.82000, 2_000_000_000L)

        val report = engine.evaluate(current, previous)

        assertTrue(report.findings.any { it.code == "IMPLAUSIBLE_DISPLACEMENT" })
        assertEquals(RiskLevel.REVIEW, report.level)
    }

    @Test
    fun staleFixIsFlagged() {
        val sample = LocationSample(16.46, 102.82, 1_000_000_000L)

        val report = engine.evaluate(
            sample,
            evaluationElapsedRealtimeNanos = 60_000_000_000L
        )

        assertTrue(report.findings.any { it.code == "STALE_FIX" })
    }

    @Test
    fun repeatedTimestampIsFlagged() {
        val previous = LocationSample(16.46, 102.82, 5_000_000_000L)
        val current = LocationSample(16.46001, 102.82, 5_000_000_000L)

        val report = engine.evaluate(current, previous)

        assertTrue(report.findings.any { it.code == "NON_MONOTONIC_TIME" })
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        val sample = LocationSample(120.0, 102.82, 1_000_000_000L)

        val report = engine.evaluate(sample)

        assertEquals(100, report.score)
        assertTrue(report.findings.any { it.code == "INVALID_COORDINATE" })
    }
}
