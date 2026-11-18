package com.gpsdefenseauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditReportTest {
    @Test
    fun parsesLabCasesAndFindsMissedDetection() {
        val raw = listOf(
            "GPS_LAB_REPORT_V1",
            "CASE|Normal|LOW|LOW|0|true|",
            "CASE|Jump|REVIEW|LOW|0|false|"
        ).joinToString("\n")
        val cases = AuditReportParser.parse(raw)
        val summary = AuditAnalyzer.summarize(cases)
        assertEquals(2, cases.size)
        assertEquals(1, summary.missed)
        assertEquals(1, summary.passed)
        assertTrue(summary.averageScore == 0)
    }

    @Test
    fun ignoresMalformedRowsAndClampsScores() {
        val raw = listOf(
            "CASE|Broken",
            "CASE|Out of range|HIGH|HIGH|150|true|FLAG"
        ).joinToString("\n")
        val cases = AuditReportParser.parse(raw)
        assertEquals(1, cases.size)
        assertEquals(100, cases.first().score)
    }
}
