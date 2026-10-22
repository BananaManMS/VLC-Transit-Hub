package com.example

import com.example.data.repository.ValenbisiRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ValenbisiSanitizationTest {

    @Test
    fun testSanitizeStationName() {
        assertEquals(
            "Avenida Pio XII",
            ValenbisiRepository.sanitizeStationName("_AVENIDA_PIO_XII")
        )
        assertEquals(
            "Guillem de Castro Lliria",
            ValenbisiRepository.sanitizeStationName("_001_GUILLEM_DE_CASTRO_LLIRIA")
        )
        assertEquals(
            "Plaza del Ayuntamiento",
            ValenbisiRepository.sanitizeStationName("_002_PLAZA_DEL_AYUNTAMIENTO")
        )
        assertEquals(
            "Calle Juan XXIII",
            ValenbisiRepository.sanitizeStationName("_CALLE_JUAN_XXIII")
        )
        assertEquals(
            "Cirilo Amoros",
            ValenbisiRepository.sanitizeStationName("_CIRILO AMOROS")
        )
    }
}
