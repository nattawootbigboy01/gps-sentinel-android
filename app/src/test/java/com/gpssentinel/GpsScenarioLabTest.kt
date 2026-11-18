package com.gpssentinel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsScenarioLabTest {
    @Test
    fun everyOfflineScenarioMatchesItsExpectedRiskBand() {
        val results = GpsScenarioLab.runAll()
        assertEquals(8, results.size)
        assertTrue(results.filterNot { it.passed }.joinToString { "${it.name}: expected=${it.expected}, actual=${it.report.level}" },
            results.all { it.passed })
    }

    @Test
    fun scenarioLabDoesNotProduceEmptyReportsForAnomalies() {
        val results = GpsScenarioLab.runAll()
        assertTrue(results.filter { it.expected != RiskLevel.LOW }.all { it.report.findings.isNotEmpty() })
    }
}
