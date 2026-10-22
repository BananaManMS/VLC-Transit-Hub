package com.example

import com.example.ui.bus.BusMapper
import com.example.ui.transit.toUnifiedDeparture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmtDivertedLineTest {

    @Test
    fun testParseDivertedLineWithCdataAndErrorTag() {
        val xml = """
            <estimacion parada="2243">
            <solo_parada>
            <bus>
            <linea>99</linea>
            <destino>
            <![CDATA[ P. Congressos ]]>
            </destino>
            <error>LÍNIA DESVIADA</error>
            </bus>
            <info/>
            </solo_parada>
            <parada_linea/>
            <info/>
            </estimacion>
        """.trimIndent()

        val result = BusMapper.parseEmtXml(xml)
        assertEquals(1, result.size)
        val item = result[0]
        assertEquals("99", item.linea)
        assertEquals("P. Congressos", item.destino)
        assertTrue("Expected item to be diverted", item.isDiverted)
        assertEquals("LÍNIA DESVIADA", item.divertedMessage)

        val unified = item.toUnifiedDeparture(0)
        assertEquals("99", unified.lineCode)
        assertEquals("P. Congressos", unified.destination)
        assertTrue("Expected unified departure to be diverted", unified.isDiverted)
        assertEquals("LÍNIA DESVIADA", unified.divertedMessage)
    }

    @Test
    fun testParseMixedDivertedAndLiveArrivals() {
        val xml = """
            <estimacion parada="2243">
            <solo_parada>
            <bus>
            <linea>99</linea>
            <destino><![CDATA[ P. Congressos ]]></destino>
            <error>LÍNIA DESVIADA</error>
            </bus>
            <bus>
            <linea>73</linea>
            <destino><![CDATA[ Tres Creus ]]></destino>
            <minutos>5</minutos>
            <horaLlegada>14:50</horaLlegada>
            </bus>
            </solo_parada>
            </estimacion>
        """.trimIndent()

        val result = BusMapper.parseEmtXml(xml)
        assertEquals(2, result.size)

        val diverted = result[0]
        assertEquals("99", diverted.linea)
        assertTrue(diverted.isDiverted)

        val live = result[1]
        assertEquals("73", live.linea)
        assertFalse(live.isDiverted)
        assertEquals("5", live.minutos)
        assertEquals("14:50", live.horaLlegada)
    }
}
