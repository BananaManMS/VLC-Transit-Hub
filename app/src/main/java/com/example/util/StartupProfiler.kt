package com.example.util

import android.util.Log

object StartupProfiler {
    val appStartTime = System.currentTimeMillis()

    fun log(tag: String, message: String) {
        val elapsed = System.currentTimeMillis() - appStartTime
        val thread = Thread.currentThread().name
        Log.d("STARTUP_PERF", "[+${elapsed}ms][$thread][$tag] $message")
    }
}
