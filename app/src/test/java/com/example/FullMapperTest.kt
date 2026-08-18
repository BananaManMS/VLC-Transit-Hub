package com.example

import com.example.data.mapper.CercaniasDepartureMapper
import org.junit.Test
import org.junit.Assert.*

class FullMapperTest {
    @Test
    fun testNormalize() {
        println(CercaniasDepartureMapper.normalizeStationName("Estació Del Nord"))
        println(CercaniasDepartureMapper.normalizeStationName("Valencia Nord"))
        println(CercaniasDepartureMapper.normalizeStationName("València Nord"))
        println(CercaniasDepartureMapper.normalizeStationName("Gandia"))
        println(CercaniasDepartureMapper.normalizeStationName("Gandía"))
        println(CercaniasDepartureMapper.normalizeStationName("Castelló de la Plana"))
        println(CercaniasDepartureMapper.normalizeStationName("Xàtiva"))
    }
}
