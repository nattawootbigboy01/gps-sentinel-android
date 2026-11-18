package com.gpsdefenseauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousResearchEngineTest {
    @Test fun identifiesCooldownResearchFromExplicitEvidenceOnly() {
        val report = AutonomousResearchEngine.analyze(listOf(
            AuditCase("Cooldown", "REVIEW", "REVIEW", 40, true, "COOLDOWN_TRIGGER")
        ), 123L)
        assertEquals(123L, report.createdAtMs)
        assertTrue(report.topicsCovered.contains("Cooldown / Rate-limit Correctness"))
        assertTrue(report.hypotheses.any { it.title.contains("ช่วงระงับ") })
        assertTrue(report.hypotheses.first().safeExperiment.contains("test build"))
    }

    @Test fun doesNotInventFindingsWhenNoEvidenceWasProvided() {
        val report = AutonomousResearchEngine.analyze(emptyList(), 456L)
        assertEquals(0, report.evidenceCount)
        assertEquals("ยังประเมินไม่ได้", report.hypotheses.first().confidence)
        assertTrue(report.limitations.contains("ไม่ได้อ่านโค้ด"))
    }

    @Test fun mapsLocationAndTimestampSignalsToResearchTopics() {
        val report = AutonomousResearchEngine.analyze(listOf(
            AuditCase("Mock", "HIGH", "HIGH", 90, true, "ANDROID_MOCK_FLAG"),
            AuditCase("Stale", "REVIEW", "REVIEW", 50, true, "STALE_TIMESTAMP")
        ))
        assertTrue(report.topicsCovered.contains("GPS & Location Integrity"))
        assertTrue(report.topicsCovered.contains("Timestamp & Sensor Consistency"))
        assertTrue(report.hypotheses.size >= 2)
    }

    @Test fun labelsConfidenceAsLowForSmallEvidenceSamples() {
        val report = AutonomousResearchEngine.analyze(listOf(
            AuditCase("Jump", "HIGH", "HIGH", 90, true, "IMPLAUSIBLE_DISPLACEMENT")
        ))
        assertTrue(report.hypotheses.all { it.confidence == "ต่ำ" })
    }
}
