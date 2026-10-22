package com.example.ui.cercanias

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

class CercaniasAlertsPollingManager(
    private val viewModel: CercaniasViewModel,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private var lastFetchTime = 0L

    fun startPolling() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10.minutes)
                lastFetchTime = System.currentTimeMillis()
                viewModel.fetchCercaniasRealTimeAlerts(force = true)
            }
        }
    }

    fun onForeground() {
        val now = System.currentTimeMillis()
        if (lastFetchTime > 0L && (now - lastFetchTime) >= 10 * 60 * 1000L) {
            lastFetchTime = now
            viewModel.fetchCercaniasRealTimeAlerts(force = true)
        }
        startPolling()
    }

    fun stopPolling() {
        job?.cancel()
        job = null
    }
}
