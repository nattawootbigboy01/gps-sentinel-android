package com.gpsdefenseauditor.datasource

enum class SourceMode {
    LIVE_HARDWARE,
    SYNTHETIC_DATASET,
    ADVERSARIAL_GENERATOR
}

/**
 * One immutable telemetry observation.
 *
 * Unknown satellite counts should use -1. Sensor values that are unavailable
 * should be represented explicitly by the source contract, not silently treated
 * as confirmed zero values.
 */
data class TelemetrySnapshot(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float,
    val accuracyMeters: Float,
    val altitudeMeters: Double,
    val isMockFlagSet: Boolean,
    val satelliteCount: Int,
    val accelMagnitude: Float,
    val isVpnActive: Boolean,
    val sourceMode: SourceMode,
    val sequenceNumber: Long = 0L,
    val sessionId: Long = 0L
)

fun interface TelemetryListener {
    fun onTelemetryReceived(snapshot: TelemetrySnapshot)
}

interface LocationDataSource {
    val mode: SourceMode
    fun start()
    fun stop()
    fun setListener(listener: TelemetryListener?)
}
