package com.gpsdefenseauditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveGpsFix(
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Int,
    val accuracyMeters: Float,
    val timestampMs: Long,
    val source: String
)

object LiveFeedStore {
    private val _latest = MutableStateFlow<LiveGpsFix?>(null)
    val latest = _latest.asStateFlow()

    fun accept(fix: LiveGpsFix) {
        val previous = _latest.value
        if (previous == null || fix.sequence > previous.sequence) _latest.value = fix
    }
}

class LiveFixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.gpsdefenseauditor.action.LIVE_FIX") return
        val lat = intent.getDoubleExtra("latitude", Double.NaN)
        val lon = intent.getDoubleExtra("longitude", Double.NaN)
        val sequence = intent.getLongExtra("sequence", -1L)
        val timestamp = intent.getLongExtra("timestamp_ms", 0L)
        if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0 ||
            sequence < 0L || timestamp <= 0L) return
        LiveFeedStore.accept(
            LiveGpsFix(
                sequence = sequence,
                latitude = lat,
                longitude = lon,
                speedKmh = intent.getIntExtra("speed_kmh", 0).coerceIn(0, 250),
                accuracyMeters = intent.getFloatExtra("accuracy_m", 999f).coerceAtLeast(0f),
                timestampMs = timestamp,
                source = intent.getStringExtra("source").orEmpty().take(64)
            )
        )
    }
}
