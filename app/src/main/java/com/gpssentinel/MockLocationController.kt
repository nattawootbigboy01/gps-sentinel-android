package com.gpssentinel

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

/**
 * Android test-location provider for authorized testing of apps owned by the developer.
 * The provider is system-wide while enabled; always stop it when the test ends.
 */
class MockLocationController(private val locationManager: LocationManager) {
    private var registered = false
    private var enabled = false

    fun start() {
        if (!registered) {
            try {
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, true, false, false,
                    true, true, true,
                    Criteria.POWER_HIGH,
                    Criteria.ACCURACY_FINE
                )
            } catch (_: IllegalArgumentException) {
                // The GPS test provider may already be registered by this app process.
            }
            registered = true
        }
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        enabled = true
    }

    fun update(latitude: Double, longitude: Double, speedKmh: Float, accuracyMeters: Float) {
        check(enabled) { "Mock Location provider is not enabled" }
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = accuracyMeters.coerceAtLeast(1f)
            speed = (speedKmh.coerceAtLeast(0f) / 3.6f)
            bearing = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix)
    }

    fun stop() {
        if (!registered) return
        try {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
        } catch (_: Exception) { }
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { }
        enabled = false
        registered = false
    }
}
