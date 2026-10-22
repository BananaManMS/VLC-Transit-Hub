package com.example.util

import com.example.data.model.trip.UnifiedActiveTripSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative state bus connecting [ActiveTripTrackingService] (single producer)
 * with UI consumers (ActiveTripOverlay, RouteDetailBottomSheet, MapScreen) across
 * foreground and background transitions.
 */
object UnifiedActiveTripStateTracker {

    private val _snapshot = MutableStateFlow<UnifiedActiveTripSnapshot?>(null)
    val snapshot: StateFlow<UnifiedActiveTripSnapshot?> = _snapshot.asStateFlow()

    private val _isAppForegrounded = MutableStateFlow(true)
    val isAppForegrounded: StateFlow<Boolean> = _isAppForegrounded.asStateFlow()

    // Immediate trigger channel to wake up the service reconciliation loop
    private val _forceReconcileTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forceReconcileTrigger: SharedFlow<Unit> = _forceReconcileTrigger.asSharedFlow()

    fun updateSnapshot(newSnapshot: UnifiedActiveTripSnapshot?) {
        _snapshot.value = newSnapshot
    }

    fun setAppForegrounded(foregrounded: Boolean) {
        val wasBackgrounded = !_isAppForegrounded.value
        _isAppForegrounded.value = foregrounded
        if (wasBackgrounded && foregrounded) {
            // App just came to foreground: wake up reconciliation loop immediately
            triggerImmediateReconcile()
        }
    }

    fun triggerImmediateReconcile() {
        _forceReconcileTrigger.tryEmit(Unit)
    }

    fun reset() {
        _snapshot.value = null
        _isAppForegrounded.value = true
    }
}
