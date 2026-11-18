package com.gpsdefenseauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDeliveryAnalyzerTest {
    @Test fun simulatorMovingWithoutProviderPublicationIsNotProviderDelivery() {
        val result = SignalDeliveryAnalyzer.analyze(
            listOf(
                DeliveryEvent(DeliveryEventType.SIMULATOR_TICK, 100),
                DeliveryEvent(DeliveryEventType.SIMULATOR_TICK, 200)
            )
        )
        assertEquals(DeliveryDiagnosis.PROVIDER_NOT_PUBLISHING, result.diagnosis)
        assertEquals(2, result.simulatorTicks)
        assertEquals(0, result.providerPublications)
    }

    @Test fun providerPublishedButNoTargetCallbackIsUnconfirmed() {
        val result = SignalDeliveryAnalyzer.analyze(
            listOf(
                DeliveryEvent(DeliveryEventType.SIMULATOR_TICK, 100),
                DeliveryEvent(DeliveryEventType.ANDROID_PROVIDER_PUBLISHED, 110)
            )
        )
        assertEquals(DeliveryDiagnosis.TARGET_UPDATE_NOT_OBSERVED, result.diagnosis)
        assertEquals(0, result.targetCallbacks)
        assertTrue(result.limitation.contains("ไม่ใช่หลักฐานยืนยัน"))
    }

    @Test fun authorizedTargetCallbackConfirmsObservedUpdate() {
        val result = SignalDeliveryAnalyzer.analyze(
            listOf(
                DeliveryEvent(DeliveryEventType.ANDROID_PROVIDER_PUBLISHED, 100),
                DeliveryEvent(DeliveryEventType.TARGET_LOCATION_CALLBACK, 120, "test-build callback")
            )
        )
        assertEquals(DeliveryDiagnosis.TARGET_UPDATE_OBSERVED, result.diagnosis)
        assertEquals(1, result.targetCallbacks)
    }

    @Test fun onlyExplicitTargetEvidenceConfirmsRejection() {
        val result = SignalDeliveryAnalyzer.analyze(
            listOf(
                DeliveryEvent(DeliveryEventType.ANDROID_PROVIDER_PUBLISHED, 100),
                DeliveryEvent(DeliveryEventType.TARGET_REJECTION_REPORTED, 120, "mock location rejected by test adapter")
            )
        )
        assertEquals(DeliveryDiagnosis.TARGET_REJECTION_CONFIRMED, result.diagnosis)
        assertEquals(1, result.explicitRejections)
    }

    @Test fun emptyEventsAreInsufficientRatherThanClear() {
        assertEquals(
            DeliveryDiagnosis.INSUFFICIENT_EVIDENCE,
            SignalDeliveryAnalyzer.analyze(emptyList()).diagnosis
        )
    }
}
