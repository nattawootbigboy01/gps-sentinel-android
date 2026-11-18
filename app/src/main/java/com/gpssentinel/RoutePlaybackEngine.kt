package com.gpssentinel

import org.osmdroid.util.GeoPoint

/**
 * Deterministic route playback math, kept outside Compose so movement can be
 * tested without Android UI or LocationManager side effects.
 */
data class RoutePlaybackFrame(
    val point: GeoPoint,
    val progressMeters: Double,
    val totalDistanceMeters: Double,
    val reachedEnd: Boolean,
    val looped: Boolean
)

object RoutePlaybackEngine {
    fun advance(
        route: List<GeoPoint>,
        currentProgressMeters: Double,
        speedKmh: Int,
        elapsedSeconds: Double,
        stopAtEnd: Boolean
    ): RoutePlaybackFrame? {
        if (route.isEmpty()) return null
        require(speedKmh >= 0) { "speedKmh must not be negative" }
        require(elapsedSeconds >= 0.0 && elapsedSeconds.isFinite()) {
            "elapsedSeconds must be finite and non-negative"
        }

        val total = route.zipWithNext().sumOf { (a, b) -> a.distanceToAsDouble(b) }
        if (total <= 0.0) {
            return RoutePlaybackFrame(route.first(), 0.0, 0.0, stopAtEnd, false)
        }

        val safeProgress = currentProgressMeters.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val rawNext = safeProgress + (speedKmh / 3.6) * elapsedSeconds
        val reachedEnd = stopAtEnd && rawNext >= total
        val looped = !stopAtEnd && rawNext >= total
        val progress = when {
            stopAtEnd -> rawNext.coerceAtMost(total)
            looped -> rawNext % total
            else -> rawNext
        }

        var remaining = progress
        var point = route.last()
        for ((start, end) in route.zipWithNext()) {
            val segment = start.distanceToAsDouble(end)
            if (segment <= 0.0) continue
            if (remaining <= segment) {
                val fraction = (remaining / segment).coerceIn(0.0, 1.0)
                point = GeoPoint(
                    start.latitude + (end.latitude - start.latitude) * fraction,
                    start.longitude + (end.longitude - start.longitude) * fraction
                )
                break
            }
            remaining -= segment
        }

        return RoutePlaybackFrame(point, progress, total, reachedEnd, looped)
    }
}
