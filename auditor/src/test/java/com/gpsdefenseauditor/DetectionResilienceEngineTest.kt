package com.gpsdefenseauditor

import org.junit.Assert.*
import org.junit.Test

class DetectionResilienceEngineTest {
    @Test fun createsAdversarialProbesFromObservedEvidence() {
        val report = DetectionResilienceEngine.buildProbes(listOf(
            AuditCase("clock", "REVIEW", "REVIEW", 20, true, "FUTURE_TIMESTAMP"),
            AuditCase("speed", "REVIEW", "LOW", 0, false, "SPEED_DISAGREEMENT")
        ))
        assertTrue(report.probes.any { it.mutation == "CLOCK_SKEW" })
        assertTrue(report.probes.any { it.mutation == "BOUNDARY_VALUE" })
        assertTrue(report.probes.any { it.mutation == "CONFLICTING_SIGNAL" })
    }

    @Test fun unknownEvidenceIsNotDiscardedOrPretendedToBeUnderstood() {
        val report = DetectionResilienceEngine.buildProbes(listOf(
            AuditCase("unknown", "REVIEW", "LOW", 0, false, "NOVEL_SENSOR_PATTERN")
        ))
        assertEquals(1, report.probes.size)
        assertEquals("UNCLASSIFIED_SIGNAL_REVIEW", report.probes.single().mutation)
        assertTrue(report.probes.single().purpose.contains("ยังไม่จัดหมวด"))
    }

    @Test fun deduplicatesAndBoundsGeneratedProbes() {
        val cases = (1..250).map {
            AuditCase("case$it", "REVIEW", "LOW", 0, false, "GPS_LOCATION_TIME_SPEED")
        }
        val report = DetectionResilienceEngine.buildProbes(cases)
        assertTrue(report.probes.size <= 30)
        assertEquals(report.probes.size, report.probes.map { it.id }.distinct().size)
        assertEquals(200, report.sourceCases)
    }

    @Test fun reportDoesNotClaimExecutionOrVerifiedVulnerability() {
        val report = DetectionResilienceEngine.buildProbes(emptyList())
        assertTrue(report.limitations.contains("ยังไม่ใช่การรัน"))
        assertTrue(report.probes.isEmpty())
    }
}
