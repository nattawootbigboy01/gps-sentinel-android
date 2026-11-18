package com.gpsdefenseauditor

/**
 * Correlates observable events across the simulator and an explicitly authorized
 * target test build. It does not inspect, hook, or alter third-party applications.
 * Absence of a target callback is never treated as proof of rejection.
 */
enum class DeliveryEventType {
    SIMULATOR_TICK,
    ANDROID_PROVIDER_PUBLISHED,
    TARGET_LOCATION_CALLBACK,
    TARGET_REJECTION_REPORTED
}

data class DeliveryEvent(
    val type: DeliveryEventType,
    val elapsedRealtimeMs: Long,
    val detail: String = ""
)

enum class DeliveryDiagnosis {
    INSUFFICIENT_EVIDENCE,
    SIMULATOR_ONLY,
    PROVIDER_NOT_PUBLISHING,
    TARGET_UPDATE_NOT_OBSERVED,
    TARGET_REJECTION_CONFIRMED,
    TARGET_UPDATE_OBSERVED
}

data class DeliveryDiagnostic(
    val diagnosis: DeliveryDiagnosis,
    val simulatorTicks: Int,
    val providerPublications: Int,
    val targetCallbacks: Int,
    val explicitRejections: Int,
    val lastSimulatorTickMs: Long?,
    val lastProviderPublicationMs: Long?,
    val lastTargetCallbackMs: Long?,
    val evidence: List<String>,
    val limitation: String
)

object SignalDeliveryAnalyzer {
    fun analyze(events: List<DeliveryEvent>): DeliveryDiagnostic {
        val ordered = events.sortedBy { it.elapsedRealtimeMs }
        val ticks = ordered.filter { it.type == DeliveryEventType.SIMULATOR_TICK }
        val publications = ordered.filter { it.type == DeliveryEventType.ANDROID_PROVIDER_PUBLISHED }
        val callbacks = ordered.filter { it.type == DeliveryEventType.TARGET_LOCATION_CALLBACK }
        val rejections = ordered.filter {
            it.type == DeliveryEventType.TARGET_REJECTION_REPORTED && it.detail.isNotBlank()
        }

        val diagnosis = when {
            rejections.isNotEmpty() -> DeliveryDiagnosis.TARGET_REJECTION_CONFIRMED
            callbacks.isNotEmpty() -> DeliveryDiagnosis.TARGET_UPDATE_OBSERVED
            ticks.isEmpty() -> DeliveryDiagnosis.INSUFFICIENT_EVIDENCE
            publications.isEmpty() -> DeliveryDiagnosis.PROVIDER_NOT_PUBLISHING
            else -> DeliveryDiagnosis.TARGET_UPDATE_NOT_OBSERVED
        }

        val evidence = buildList {
            if (ticks.isNotEmpty()) add("SIMULATOR_TICKS=${ticks.size}")
            if (publications.isNotEmpty()) add("ANDROID_PROVIDER_PUBLICATIONS=${publications.size}")
            if (callbacks.isNotEmpty()) add("AUTHORIZED_TARGET_CALLBACKS=${callbacks.size}")
            rejections.forEach { add("EXPLICIT_TARGET_REJECTION=${it.detail}") }
            if (ticks.isNotEmpty() && publications.isEmpty()) {
                add("ตำแหน่งเคลื่อนในตัวจำลอง แต่ยังไม่มีหลักฐานว่า Android Provider เผยแพร่ตำแหน่ง")
            }
            if (publications.isNotEmpty() && callbacks.isEmpty() && rejections.isEmpty()) {
                add("มีการเผยแพร่จาก Provider แต่ไม่มี callback จาก target test build; ยังสรุปว่าถูกบล็อกไม่ได้")
            }
        }

        return DeliveryDiagnostic(
            diagnosis = diagnosis,
            simulatorTicks = ticks.size,
            providerPublications = publications.size,
            targetCallbacks = callbacks.size,
            explicitRejections = rejections.size,
            lastSimulatorTickMs = ticks.lastOrNull()?.elapsedRealtimeMs,
            lastProviderPublicationMs = publications.lastOrNull()?.elapsedRealtimeMs,
            lastTargetCallbackMs = callbacks.lastOrNull()?.elapsedRealtimeMs,
            evidence = evidence,
            limitation = "การไม่มี target callback ไม่ใช่หลักฐานยืนยันการปฏิเสธ; การยืนยันต้องมาจาก instrumentation หรือ log ของ test build ที่ได้รับอนุญาต"
        )
    }
}
