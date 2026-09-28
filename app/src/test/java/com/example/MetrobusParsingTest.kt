package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.MetrobusRepository
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MetrobusParsingTest {

    @Test
    fun testParseMetrobusStopsJson() {
        val json = """
            {
              "stops": [
                {
                  "lines": ["112A", "112B", "115"],
                  "stop_id": "157",
                  "stop_lat": 39.477254,
                  "stop_lon": -0.357659,
                  "stop_name": "Blasco Ibáñez - Gascó Oliag"
                },
                {
                  "lines": ["111", "119N"],
                  "stop_id": "180",
                  "stop_lat": 39.472000,
                  "stop_lon": -0.365000,
                  "stop_name": "Plaça d'Espanya"
                }
              ]
            }
        """.trimIndent()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val client = OkHttpClient()
        val repo = MetrobusRepository(db, client, context)

        val stops = repo.parseStopsJson(json)
        assertEquals(2, stops.size)

        val first = stops[0]
        assertEquals("157", first.id)
        assertEquals("Blasco Ibáñez - Gascó Oliag", first.denominacion)
        assertEquals(39.477254, first.latitud, 0.0001)
        assertEquals(-0.357659, first.longitud, 0.0001)
        assertEquals("112A,112B,115", first.lineas)
    }
}
