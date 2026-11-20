package com.gpsdefenseauditor

import org.junit.Assert.*
import org.junit.Test

class DetectionSignalEngineTest {
    private fun sample(
        lat: Double = 16.4620,
        lon: Double = 102.8230,
        accuracy: Float = 8f,
        speed: Float = 0f,
        wall: Long = 2_000_000L,
        elapsed: Long = 1_000_000_000L,
        mock: Boolean = false,
        source: String = "AUTHORIZED_TEST_HARNESS"
    ) = LocationEvidence(lat, lon, accuracy, speed, wall, elapsed, mock, source)

    @Test fun emptyInputIsInsufficientRatherThanClear() {
        val result = DetectionSignalEngine.assess(emptyList(), 2_000_000L)
        assertEquals(SignalVerdict.INSUFFICIENT_DATA, result.verdict)
        assertEquals(0, result.riskScore)
    }

    @Test fun explicitMockFlagIsHighRiskAndNeverClaimedAsBlocked() {
        val result = DetectionSignalEngine.assess(listOf(sample(mock = true)), 2_000_000L)
        assertEquals(SignalVerdict.DETECTED, result.verdict)
        assertTrue(result.findings.any { it.code == "MOCK_PROVIDER_FLAG" })
        assertTrue(result.limitations.contains("BLOCKED ต้องยืนยัน"))
    }

    @Test fun invalidCoordinatesAreCritical() {
        val result = DetectionSignalEngine.assess(listOf(sample(lat = 95.0)), 2_000_000L)
        assertEquals(SignalVerdict.DETECTED, result.verdict)
        assertTrue(result.findings.any { it.severity == SignalSeverity.CRITICAL })
    }

    @Test fun staleAndFutureTimestampsAreSeparated() {
        val stale = DetectionSignalEngine.assess(listOf(sample(wall = 100_000L)), 500_000L)
        val future = DetectionSignalEngine.assess(listOf(sample(wall = 600_000L)), 500_000L)
        assertTrue(stale.findings.any { it.code == "STALE_TIMESTAMP" })
        assertTrue(future.findings.any { it.code == "FUTURE_TIMESTAMP" })
    }

    @Test fun sequenceAndMovementConflictsAreReported() {
        val first = sample(wall = 1_000_000L, elapsed = 2_000_000_000L)
        val second = sample(
            lat = 16.4720, lon = 102.8230, speed = 0f,
            wall = 1_000_000L, elapsed = 1_000_000_000L
        )
        val result = DetectionSignalEngine.assess(listOf(first, second), 1_000_000L)
        assertTrue(result.findings.any { it.code == "NON_MONOTONIC_WALL_CLOCK" })
        assertTrue(result.findings.any { it.code == "NON_MONOTONIC_ELAPSED_TIME" })
    }

    @Test fun cleanConsistentEvidenceDoesNotCreateAnAlert() {
        val result = DetectionSignalEngine.assess(
            listOf(
                sample(wall = 1_999_000L, elapsed = 1_000_000_000L),
                sample(lat = 16.46201, wall = 2_000_000L, elapsed = 2_000_000_000L, speed = 1.1f)
            ),
            2_000_000L
        )
        assertEquals(SignalVerdict.CLEAR, result.verdict)
        assertTrue(result.findings.isEmpty())
    }
}
