package com.example

import android.app.Application
import com.example.data.database.AppDatabase

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppDatabase.getDatabase(this)
    }

    companion object {
        lateinit var instance: MainApplication
            private set
    }
}
