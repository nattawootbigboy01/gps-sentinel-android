package com.gpsdefenseauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDefenseEngineTest {
    @Test fun recommendsClosingMissedDetectionGaps() {
        val result = AdaptiveDefenseEngine.analyze(listOf(AuditCase("Jump", "HIGH", "LOW", 0, false, "IMPLAUSIBLE_DISPLACEMENT")))
        assertEquals(1, result.missedDetections)
        assertTrue(result.recommendations.any { it.title.contains("ช่องว่างการตรวจจับ") })
        assertTrue(result.recommendations.any { it.action.contains("IMPLAUSIBLE_DISPLACEMENT") })
    }
    @Test fun recommendsReducingFalseAlarmsSeparately() {
        val result = AdaptiveDefenseEngine.analyze(listOf(AuditCase("Normal", "LOW", "REVIEW", 20, false, "LOW_ACCURACY")))
        assertEquals(1, result.falseAlarms)
        assertTrue(result.recommendations.any { it.title.contains("แจ้งเตือนเกิน") })
    }
    @Test fun asksForMoreCasesWhenEvidenceIsLimited() {
        val result = AdaptiveDefenseEngine.analyze(listOf(AuditCase("Normal", "LOW", "LOW", 0, true, "")))
        assertTrue(result.recommendations.any { it.title.contains("เพิ่มจำนวนสถานการณ์") })
    }
    @Test fun keepsMissedDetectionAndFalseAlarmMetricsIndependent() {
        val result = AdaptiveDefenseEngine.analyze(listOf(
            AuditCase("Jump", "HIGH", "LOW", 0, false, ""),
            AuditCase("Normal", "LOW", "HIGH", 90, false, "")
        ))
        assertEquals(1, result.missedDetections)
        assertEquals(1, result.falseAlarms)
    }
}
