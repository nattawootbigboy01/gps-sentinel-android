package com.gpsdefenseauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MahathepSecurityCoreTest {
    @Test fun offlineSelfTestExercisesSyntheticCases() {
        val report = MahathepSecurityCore.runOfflineSelfTest()
        assertEquals(8, report.total)
        assertEquals(7, report.passed)
        assertFalse(report.allPassed)
        assertTrue(report.checks.any { it.id == "MISSED_DETECTION_GUARD" && !it.passed })
    }

    @Test fun detectsWhenActualSeverityIsBelowExpected() {
        val result = MahathepSecurityCore.evaluate(
            AuditCase("missed", "HIGH", "LOW", 0, false, "SYNTHETIC")
        )
        assertFalse(result.passed)
        assertTrue(result.detail.contains("ช่องว่าง"))
    }

    @Test fun acceptsDetectionAtOrAboveExpectedSeverity() {
        val result = MahathepSecurityCore.evaluate(
            AuditCase("detected", "REVIEW", "HIGH", 90, true, "SYNTHETIC")
        )
        assertTrue(result.passed)
    }

    @Test fun rejectsUnknownSeverityInsteadOfAssumingSafe() {
        val result = MahathepSecurityCore.evaluate(
            AuditCase("unknown", "CRITICAL", "LOW", 0, false, "SYNTHETIC")
        )
        assertFalse(result.passed)
    }
}
