package com.example.util

import org.osmdroid.util.GeoPoint
import kotlin.math.pow

object PolylineDecoder {

    /**
     * Decodes an encoded polyline string (e.g. from Google Maps or MOTIS 2 / Transitous)
     * into a list of [GeoPoint] for osmdroid, filtering out invalid or out-of-bounds coordinates.
     *
     * @param encoded The polyline string
     * @param precision The precision factor (MOTIS 2 / Transitous uses 6, Google default uses 5)
     */
    fun decode(encoded: String, precision: Int = 6): List<GeoPoint> {
        val coordinates = decodeToCoordinates(encoded, precision)
        return coordinates
            .filter { (lat, lon) -> isValidValenciaCoordinate(lat, lon) }
            .map { (lat, lon) -> GeoPoint(lat, lon) }
    }

    /**
     * Checks if coordinates fall within reasonable bounds for Spain/Valencia metropolitan area.
     */
    fun isValidValenciaCoordinate(lat: Double, lon: Double): Boolean {
        // Broad bounds covering Valencia province & Community: Lat 38.0..41.0, Lon -2.0..1.0
        return lat in 38.0..41.0 && lon in -2.0..1.0
    }

    /**
     * Decodes an encoded polyline string into pairs of (latitude, longitude).
     */
    fun decodeToCoordinates(encoded: String, precision: Int = 6): List<Pair<Double, Double>> {
        if (encoded.isBlank()) return emptyList()

        val poly = ArrayList<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        val factor = 10.0.pow(precision.toDouble())

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat.toDouble() / factor
            val pLng = lng.toDouble() / factor
            poly.add(Pair(pLat, pLng))
        }

        return poly
    }
}
