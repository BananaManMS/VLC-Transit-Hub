package com.example.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/**
 * Unified, battery-optimized central time pulse for the entire application.
 *
 * Automatically pauses (zero CPU wakeups) whenever no UI components are observing it
 * via [SharingStarted.WhileSubscribed].
 */
object AppTimeTicker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Exact 1-second pulse for Metro departures and Dashboard live clock.
     * Emits current epoch timestamp in milliseconds.
     */
    val secondPulse: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
        replay = 1
    )
}
