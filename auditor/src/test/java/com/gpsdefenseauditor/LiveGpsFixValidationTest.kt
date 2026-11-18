package com.gpsdefenseauditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGpsFixValidationTest {
    private fun validFix() = LiveGpsFix(
        sequence = 1L,
        latitude = 16.46,
        longitude = 102.82,
        speedKmh = 35,
        accuracyMeters = 5f,
        timestampMs = 1_800_000_000_000L,
        source = "GPS_LAB_SIMULATOR"
    )

    @Test
    fun acceptsWellFormedTelemetry() {
        assertTrue(LiveFeedStore.isValid(validFix()))
    }

    @Test
    fun acceptsSequenceRestartWhenTimestampAdvances() {
        val previous = validFix().copy(sequence = 85L, timestampMs = 1_800_000_000_000L)
        val nextSession = validFix().copy(sequence = 1L, timestampMs = 1_800_000_001_000L)
        assertTrue(LiveFeedStore.isNewer(nextSession, previous))
    }

    @Test
    fun rejectsDuplicateOrOlderTimestamp() {
        val previous = validFix().copy(timestampMs = 1_800_000_000_000L)
        assertFalse(LiveFeedStore.isNewer(validFix().copy(timestampMs = previous.timestampMs), previous))
        assertFalse(LiveFeedStore.isNewer(validFix().copy(timestampMs = previous.timestampMs - 1L), previous))
    }

    @Test
    fun rejectsInvalidCoordinates() {
        assertFalse(LiveFeedStore.isValid(validFix().copy(latitude = 91.0)))
        assertFalse(LiveFeedStore.isValid(validFix().copy(longitude = Double.NaN)))
    }

    @Test
    fun rejectsInvalidSpeedAndAccuracy() {
        assertFalse(LiveFeedStore.isValid(validFix().copy(speedKmh = 251)))
        assertFalse(LiveFeedStore.isValid(validFix().copy(accuracyMeters = Float.NaN)))
        assertFalse(LiveFeedStore.isValid(validFix().copy(accuracyMeters = -1f)))
    }

    @Test
    fun rejectsMissingTimestampAndOversizedSource() {
        assertFalse(LiveFeedStore.isValid(validFix().copy(timestampMs = 0L)))
        assertFalse(LiveFeedStore.isValid(validFix().copy(source = "x".repeat(65))))
    }
}
