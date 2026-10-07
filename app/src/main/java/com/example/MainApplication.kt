package com.example

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.database.AppDatabase
import com.example.data.repository.RealTimeTransitRepository
import com.example.worker.ScheduleUpdateWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.util.StartupProfiler.log("MainApp", "onCreate starting: AppDatabase.getDatabase & RealTimeTransitRepository.init")
        AppDatabase.getDatabase(this)
        RealTimeTransitRepository.init(this)
        
        com.example.util.StartupProfiler.log("MainApp", "scheduleDailyUpdateWorker")
        scheduleDailyUpdateWorker()
        
        // Trigger an initial async sync on startup
        applicationScope.launch {
            com.example.util.StartupProfiler.log("MainApp", "MetroScheduleRepository.syncScheduleFromRemoteIfNeeded() starting")
            try {
                com.example.data.repository.MetroScheduleRepository.getInstance(this@MainApplication)
                    .syncScheduleFromRemoteIfNeeded()
                com.example.util.StartupProfiler.log("MainApp", "MetroScheduleRepository.syncScheduleFromRemoteIfNeeded() completed")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleDailyUpdateWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ScheduleUpdateWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Throwable) {
            android.util.Log.w("MainApplication", "WorkManager schedule skipped: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}
