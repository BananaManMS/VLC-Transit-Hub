package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VLC Transit", appName)
  }



  @Test
  fun `test renfe repository initialization`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    
    try {
      val repo = RenfeRepository(context, db)
      repo.initDatabaseFromAssetsIfNeeded()
      val stations = repo.getAllStations()
      println("DEBUG: Loaded stations count = ${stations.size}")
      
      // Get departures for Estació Del Nord (65000)
      val departuresNord = repo.getDeparturesForStation("65000")
      println("DEBUG: Estació Del Nord (65000) departures count = ${departuresNord.size}")
      departuresNord.take(15).forEach {
        println("  Line: ${it.routeId}, Dest: ${it.destination}, Time: ${it.departureTime}, MinsRem: ${it.minutesRemaining}")
      }
      
      // Let's check some other stations to see if they are empty
      val moixentDepartures = repo.getDeparturesForStation("64003")
      println("DEBUG: Moixent (64003) departures count = ${moixentDepartures.size}")
      moixentDepartures.take(5).forEach {
        println("  Line: ${it.routeId}, Dest: ${it.destination}, Time: ${it.departureTime}, MinsRem: ${it.minutesRemaining}")
      }
      
      assertTrue("Stations should not be empty", stations.isNotEmpty())
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    } finally {
      db.close()
    }
  }
}
