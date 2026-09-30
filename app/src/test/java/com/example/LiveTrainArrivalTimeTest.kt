package com.example

import com.example.ui.cercanias.formatEstimatedArrivalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTrainArrivalTimeTest {

    @Test
    fun testIsoWithDotsIsFormattedToHourAndMinute() {
        // Renfe sometimes outputs "2026-09-30T11.26:45"
        val input = "2026-09-30T11.26:45"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testIsoWithColonsIsFormattedToHourAndMinute() {
        val input = "2026-09-30T11:26:45"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testShortTimeWithDots() {
        val input = "11.26"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testStandardShortTimeWithColons() {
        val input = "11:26"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("11:26", formatted)
    }

    @Test
    fun testSingleDigitHourPadded() {
        val input = "2026-09-30T9.05:00"
        val formatted = formatEstimatedArrivalTime(input)
        assertEquals("09:05", formatted)
    }

    @Test
    fun testEmptyInputReturnsBlank() {
        val formatted = formatEstimatedArrivalTime("")
        assertEquals("", formatted)
    }
}
