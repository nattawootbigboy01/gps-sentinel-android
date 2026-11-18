package com.gpssentinel

import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RoadRouteResult(
    val points: List<GeoPoint>,
    val distanceMeters: Double
)

object RoadRoutePlanner {
    private const val MAX_ROUTE_METERS = 1_000.0
    private const val MAX_RADIUS_METERS = 1_000.0

    suspend fun createNearbyRoute(origin: GeoPoint): RoadRouteResult =
        withContext(Dispatchers.IO) {
            var lastError = "ไม่พบถนนที่เหมาะสมในระยะ 1 กิโลเมตร"
            repeat(8) {
                val bearing = Random.nextDouble(0.0, 360.0)
                val offsetMeters = Random.nextDouble(180.0, 420.0)
                val destination = offsetPoint(origin, offsetMeters, bearing)
                try {
                    val route = requestDrivingRoute(origin, destination)
                    if (route.distanceMeters in 80.0..MAX_ROUTE_METERS &&
                        route.points.all { origin.distanceToAsDouble(it) <= MAX_RADIUS_METERS }
                    ) {
                        return@withContext route
                    }
                    lastError = "เส้นทางที่ได้ยาวเกินขอบเขตหรืออยู่นอกพื้นที่ทดสอบ"
                } catch (error: Exception) {
                    lastError = error.message ?: lastError
                }
            }
            throw IllegalStateException(lastError)
        }

    private fun requestDrivingRoute(start: GeoPoint, end: GeoPoint): RoadRouteResult {
        val coordinates = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        val url = URL(
            "https://router.project-osrm.org/route/v1/driving/$coordinates?overview=full&geometries=geojson&steps=false"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "GPS-Sentinel-LAB/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("บริการหาเส้นทางตอบกลับ HTTP $code")
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                throw IllegalStateException("บริการหาเส้นทางไม่พบเส้นทางขับขี่")
            }
            val route = json.getJSONArray("routes").getJSONObject(0)
            val distance = route.getDouble("distance")
            if (distance > MAX_ROUTE_METERS) {
                return RoadRouteResult(emptyList(), distance)
            }
            val coordinatesArray = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = buildList {
                for (index in 0 until coordinatesArray.length()) {
                    val coordinate = coordinatesArray.getJSONArray(index)
                    add(GeoPoint(coordinate.getDouble(1), coordinate.getDouble(0)))
                }
            }
            if (points.size < 2) throw IllegalStateException("ข้อมูลเส้นทางไม่ครบ")
            return RoadRouteResult(points, distance)
        } finally {
            connection.disconnect()
        }
    }

    private fun offsetPoint(origin: GeoPoint, distanceMeters: Double, bearingDegrees: Double): GeoPoint {
        val bearing = Math.toRadians(bearingDegrees)
        val north = cos(bearing) * distanceMeters
        val east = sin(bearing) * distanceMeters
        val latitude = origin.latitude + north / 111_320.0
        val longitudeScale = 111_320.0 * cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.01)
        val longitude = origin.longitude + east / longitudeScale
        return GeoPoint(latitude, longitude)
    }
}
