package com.gpssentinel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class RoutePlaybackEngineTest {
    private val route = listOf(
        GeoPoint(16.46000, 102.82000),
        GeoPoint(16.46100, 102.82000)
    )

    @Test
    fun stopsExactlyAtDestinationWhenStopAtEndIsEnabled() {
        val total = route[0].distanceToAsDouble(route[1])
        val frame = RoutePlaybackEngine.advance(route, total - 2.0, 36, 1.0, true)
        assertNotNull(frame)
        assertEquals(total, frame!!.progressMeters, 0.001)
        assertEquals(route.last().latitude, frame.point.latitude, 0.000001)
        assertTrue(frame.reachedEnd)
        assertTrue(!frame.looped)
    }

    @Test
    fun wrapsProgressWhenLoopingIsEnabled() {
        val total = route[0].distanceToAsDouble(route[1])
        val frame = RoutePlaybackEngine.advance(route, total - 2.0, 36, 5.0, false)
        assertNotNull(frame)
        assertEquals(3.0, frame!!.progressMeters, 0.01)
        assertTrue(frame.looped)
        assertTrue(!frame.reachedEnd)
    }

    @Test
    fun skipsDuplicatePointsWithoutDividingByZero() {
        val duplicateRoute = listOf(route[0], route[0], route[1])
        val frame = RoutePlaybackEngine.advance(duplicateRoute, 0.0, 36, 1.0, true)
        assertNotNull(frame)
        assertTrue(frame!!.point.latitude > route[0].latitude)
    }

    @Test
    fun emptyRouteReturnsNoFrame() {
        assertEquals(null, RoutePlaybackEngine.advance(emptyList(), 0.0, 20, 1.0, true))
    }
}
