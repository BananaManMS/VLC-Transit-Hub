package com.example

import com.example.data.repository.LineStationInfo
import com.example.ui.metro.MetroDirectionClassifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MetroDirectionTest {

    private val lineStationsMap = mutableMapOf<String, List<LineStationInfo>>()

    @Before
    fun setUp() {
        val file = File("src/main/assets/metro_line_stations.json")
        if (file.exists()) {
            val jsonObject = JSONObject(file.readText())
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val lineKey = keys.next()
                val arr = jsonObject.getJSONArray(lineKey)
                val list = mutableListOf<LineStationInfo>()
                for (i in 0 until arr.length()) {
                    val stObj = arr.getJSONObject(i)
                    list.add(
                        LineStationInfo(
                            id = stObj.optString("id", ""),
                            name = stObj.optString("name", ""),
                            zone = stObj.optString("zone", "A")
                        )
                    )
                }
                lineStationsMap[lineKey] = list
            }
        }
    }

    @Test
    fun testL1EmpalmeVsValenciaSudAtAngelGuimera() {
        val l1Stations = lineStationsMap["L1"] ?: emptyList()

        val dirEmpalme = MetroDirectionClassifier.classifyDepartureDirection(
            lineId = "L1",
            destination = "Empalme",
            currentStationId = "17", // Àngel Guimerà
            currentStationName = "Àngel Guimerà",
            lineStations = l1Stations
        )

        val dirValenciaSud = MetroDirectionClassifier.classifyDepartureDirection(
            lineId = "L1",
            destination = "València Sud",
            currentStationId = "17", // Àngel Guimerà
            currentStationName = "Àngel Guimerà",
            lineStations = l1Stations
        )

        val dirBetera = MetroDirectionClassifier.classifyDepartureDirection(
            lineId = "L1",
            destination = "Bétera",
            currentStationId = "17",
            currentStationName = "Àngel Guimerà",
            lineStations = l1Stations
        )

        val dirCastello = MetroDirectionClassifier.classifyDepartureDirection(
            lineId = "L1",
            destination = "Castelló",
            currentStationId = "17",
            currentStationName = "Àngel Guimerà",
            lineStations = l1Stations
        )

        // Empalme y Bétera van al Norte (Sentido 1)
        assertEquals(1, dirEmpalme)
        assertEquals(1, dirBetera)

        // València Sud y Castelló van al Sur (Sentido 0)
        assertEquals(0, dirValenciaSud)
        assertEquals(0, dirCastello)

        // Empalme y València Sud DEBEN tener direcciones distintas
        assertNotEquals(dirEmpalme, dirValenciaSud)
    }

    @Test
    fun testL3AndL5SharedTrunk() {
        val l3Stations = lineStationsMap["L3"] ?: emptyList()
        val l5Stations = lineStationsMap["L5"] ?: emptyList()

        // En Xàtiva (16):
        // Trenes hacia Aeroport (Oeste) -> Sentido 0
        val l3Aeroport = MetroDirectionClassifier.classifyDepartureDirection("L3", "Aeroport", "16", "Xàtiva", l3Stations)
        val l5Aeroport = MetroDirectionClassifier.classifyDepartureDirection("L5", "Aeroport", "16", "Xàtiva", l5Stations)

        assertEquals(0, l3Aeroport)
        assertEquals(0, l5Aeroport)

        // Trenes hacia el Este (Rafelbunyol / Marítim) -> Sentido 1
        val l3Rafelbunyol = MetroDirectionClassifier.classifyDepartureDirection("L3", "Rafelbunyol", "16", "Xàtiva", l3Stations)
        val l5Maritim = MetroDirectionClassifier.classifyDepartureDirection("L5", "Marítim", "16", "Xàtiva", l5Stations)

        assertEquals(1, l3Rafelbunyol)
        assertEquals(1, l5Maritim)
    }

    @Test
    fun testL7AndL2AtJesus() {
        val l7Stations = lineStationsMap["L7"] ?: emptyList()
        val l2Stations = lineStationsMap["L2"] ?: emptyList()

        // En Jesús (25):
        // Hacia Torrent Av. (Sur) -> Sentido 0
        val l7Torrent = MetroDirectionClassifier.classifyDepartureDirection("L7", "Torrent Avinguda", "25", "Jesús", l7Stations)
        val l2Torrent = MetroDirectionClassifier.classifyDepartureDirection("L2", "Torrent Avinguda", "25", "Jesús", l2Stations)
        assertEquals(0, l7Torrent)
        assertEquals(0, l2Torrent)

        // Hacia el Norte/Este (Marítim / Llíria) -> Sentido 1
        val l7Maritim = MetroDirectionClassifier.classifyDepartureDirection("L7", "Marítim", "25", "Jesús", l7Stations)
        val l2Lliria = MetroDirectionClassifier.classifyDepartureDirection("L2", "Llíria", "25", "Jesús", l2Stations)
        assertEquals(1, l7Maritim)
        assertEquals(1, l2Lliria)
    }
}
