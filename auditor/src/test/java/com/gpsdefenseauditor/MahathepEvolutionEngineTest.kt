package com.gpsdefenseauditor

import org.junit.Assert.*
import org.junit.Test

class MahathepEvolutionEngineTest {
    @Test fun generatesNewScenariosFromFindingEvidence() {
        val cases = listOf(AuditCase("old", "HIGH", "LOW", 0, false, "IMPLAUSIBLE_DISPLACEMENT"))
        val generated = MahathepEvolutionEngine.generateCases(cases)
        assertTrue(generated.any { it.id == "IMPLAUSIBLE_JUMP" })
        assertTrue(generated.all { it.expected == "HIGH" || it.expected == "REVIEW" })
    }

    @Test fun doesNotInventScenariosWithoutRecognizedEvidence() {
        assertTrue(MahathepEvolutionEngine.generateCases(listOf(
            AuditCase("unknown", "LOW", "LOW", 100, true, "UNCLASSIFIED_SIGNAL")
        )).isEmpty())
    }

    @Test fun proposesCandidateRuleForMissedDetectionWithoutApplyingIt() {
        val cases = listOf(AuditCase("jump", "HIGH", "LOW", 0, false, "IMPLAUSIBLE_DISPLACEMENT"))
        val candidates = MahathepEvolutionEngine.proposeRules(cases)
        assertEquals(1, candidates.size)
        assertEquals("HIGH", candidates.single().minimumSeverity)
        assertEquals("DRAFT", candidates.single().status)
    }

    @Test fun rejectsCandidateThatWouldCreateFalseAlarm() {
        val cases = listOf(
            AuditCase("missed", "HIGH", "LOW", 0, false, "SHARED_SIGNAL"),
            AuditCase("normal", "LOW", "LOW", 100, true, "SHARED_SIGNAL")
        )
        val candidate = MahathepEvolutionEngine.proposeRules(cases)
        val validation = MahathepEvolutionEngine.validateRules(cases, candidate).single()
        assertFalse(validation.acceptedForReview)
        assertEquals(1, validation.falseAlarmsAfter)
    }

    @Test fun acceptsCandidateWhenItReducesMissesWithoutNewFalseAlarms() {
        val cases = listOf(AuditCase("missed", "HIGH", "LOW", 0, false, "RARE_SIGNAL"))
        val validation = MahathepEvolutionEngine.validateRules(
            cases,
            MahathepEvolutionEngine.proposeRules(cases)
        ).single()
        assertTrue(validation.acceptedForReview)
        assertEquals(0, validation.missedAfter)
    }
}
