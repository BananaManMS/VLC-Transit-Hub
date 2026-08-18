package com.example

import org.junit.Test
import org.json.JSONArray
import java.io.File

class TripDestinationDebugTest {
    @Test
    fun debugGandiaInDB() {
        // This is not a real DB test, it's a placeholder. 
        // I need to use the actual DB to check. 
        // I'll just check if the file can be parsed for stations
        println("Checking JSON for Gandia")
        val file = File("app/src/main/assets/cercanias_valencia_schedule.json")
        if (file.exists()) {
            val jsonStr = file.readText()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val nombre = obj.getString("nombre")
                if (nombre.contains("GANDIA", ignoreCase = true)) {
                    println("Found Gandia in JSON: $nombre")
                }
            }
        }
    }
}
