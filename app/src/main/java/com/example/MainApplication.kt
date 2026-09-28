package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.RealTimeTransitRepository

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppDatabase.getDatabase(this)
        RealTimeTransitRepository.init(this)
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}

