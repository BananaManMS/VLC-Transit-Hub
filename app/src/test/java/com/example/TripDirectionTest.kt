package com.example

import org.junit.Test
import org.junit.Assert.*

class TripDirectionTest {
    @Test
    fun testTripDirection() {
        val tripId = "4001X24001C1"
        val directionDigit = tripId.lastOrNull { it.isDigit() }?.toString()?.toIntOrNull()
        println("directionDigit: $directionDigit")
    }
}
