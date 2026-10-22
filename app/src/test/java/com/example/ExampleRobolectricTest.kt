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
@Config(sdk = [34])
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
      val departures = repo.getDeparturesForStation("65000")
      assertTrue("Stations should not be empty", stations.isNotEmpty())
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    } finally {
      db.close()
    }
  }
}
