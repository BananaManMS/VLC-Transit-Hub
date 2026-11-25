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
        if (!com.example.data.repository.renfe.RenfeAlertsRepository.hasValidCache()) {
            viewModel.fetchCercaniasRealTimeAlerts(force = false)
        }
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15.minutes)
                if (!com.example.data.repository.renfe.RenfeAlertsRepository.hasValidCache()) {
                    viewModel.fetchCercaniasRealTimeAlerts(force = false)
                }
            }
        }
    }

    fun onForeground() {
        startPolling()
    }

    fun stopPolling() {
        job?.cancel()
        job = null
    }
}
