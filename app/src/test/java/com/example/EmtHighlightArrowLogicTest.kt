package com.example

import com.example.ui.map.components.EmtStationHighlightManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmtHighlightArrowLogicTest {

    @Test
    fun testArrowsHiddenWhenAllLinesSelected() {
        val state = EmtStationHighlightManager.EmtHighlightState(
            isHighlighted = true,
            selectedStopId = "2277",
            allStopLines = setOf("7", "9", "10", "11", "28", "31"),
            isolatedLineFilters = emptySet()
        )

        assertFalse(
            "Directional arrows must be hidden when 'Todas' (multiple lines) are active",
            state.showDirectionalArrows
        )
    }

    @Test
    fun testArrowsVisibleWhenSingleLineIsolated() {
        val state = EmtStationHighlightManager.EmtHighlightState(
            isHighlighted = true,
            selectedStopId = "2277",
            allStopLines = setOf("7", "9", "10", "11", "28", "31"),
            isolatedLineFilters = setOf("7")
        )

        assertTrue(
            "Directional arrows must be shown when a specific line is isolated",
            state.showDirectionalArrows
        )
    }

    @Test
    fun testArrowsVisibleWhenStopHasOnlyOneLineNaturally() {
        val state = EmtStationHighlightManager.EmtHighlightState(
            isHighlighted = true,
            selectedStopId = "100",
            allStopLines = setOf("19"),
            isolatedLineFilters = emptySet()
        )

        assertTrue(
            "Directional arrows must be shown when the stop only serves a single line",
            state.showDirectionalArrows
        )
    }
}
