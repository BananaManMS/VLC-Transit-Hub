package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.database.DatabaseBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        applicationScope.launch {
            DatabaseBackupManager.restoreIfNeeded(this@MainApplication, database)
        }
    }
}
