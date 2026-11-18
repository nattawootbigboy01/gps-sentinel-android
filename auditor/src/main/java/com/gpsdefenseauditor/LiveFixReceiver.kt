package com.gpsdefenseauditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
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
    private const val PREFS = "maha_thep_live_feed"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 200
    private val _latest = MutableStateFlow<LiveGpsFix?>(null)
    private val _history = MutableStateFlow<List<LiveGpsFix>>(emptyList())
    private var restored = false
    val latest = _latest.asStateFlow()
    val history = _history.asStateFlow()

    @Synchronized
    fun restore(context: Context) {
        if (restored) return
        restored = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return
        val parsed = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                LiveGpsFix(
                    sequence = item.optLong("sequence", -1L),
                    latitude = item.optDouble("latitude", Double.NaN),
                    longitude = item.optDouble("longitude", Double.NaN),
                    speedKmh = item.optInt("speedKmh", -1),
                    accuracyMeters = item.optDouble("accuracyMeters", Double.NaN).toFloat(),
                    timestampMs = item.optLong("timestampMs", 0L),
                    source = item.optString("source", "").take(64)
                ).takeIf(::isValid)
            }.takeLast(MAX_HISTORY)
        }.getOrDefault(emptyList())
        _history.value = parsed
    }

    @Synchronized
    fun accept(context: Context, fix: LiveGpsFix): Boolean {
        restore(context)
        if (!isValid(fix)) return false
        val previous = _latest.value
        if (previous != null && fix.sequence <= previous.sequence) return false
        _latest.value = fix
        val updated = (_history.value + fix).takeLast(MAX_HISTORY)
        _history.value = updated
        val array = JSONArray()
        updated.forEach { item ->
            array.put(JSONObject().apply {
                put("sequence", item.sequence)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("speedKmh", item.speedKmh)
                put("accuracyMeters", item.accuracyMeters.toDouble())
                put("timestampMs", item.timestampMs)
                put("source", item.source)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, array.toString()).apply()
        return true
    }

    internal fun isValid(fix: LiveGpsFix): Boolean =
        fix.sequence >= 0L &&
            fix.latitude.isFinite() && fix.latitude in -90.0..90.0 &&
            fix.longitude.isFinite() && fix.longitude in -180.0..180.0 &&
            fix.speedKmh in 0..250 &&
            fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0f..10_000f &&
            fix.timestampMs > 0L &&
            fix.source.length <= 64
}

class LiveFixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.gpsdefenseauditor.action.LIVE_FIX") return
        val fix = LiveGpsFix(
            sequence = intent.getLongExtra("sequence", -1L),
            latitude = intent.getDoubleExtra("latitude", Double.NaN),
            longitude = intent.getDoubleExtra("longitude", Double.NaN),
            speedKmh = intent.getIntExtra("speed_kmh", -1),
            accuracyMeters = intent.getFloatExtra("accuracy_m", Float.NaN),
            timestampMs = intent.getLongExtra("timestamp_ms", 0L),
            source = intent.getStringExtra("source").orEmpty().take(64)
        )
        LiveFeedStore.accept(context, fix)
    }
}
