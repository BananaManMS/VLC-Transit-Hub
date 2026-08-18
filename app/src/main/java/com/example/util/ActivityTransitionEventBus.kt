package com.example.util

import com.google.android.gms.location.ActivityTransitionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event Bus desacoplado y thread-safe basado en MutableSharedFlow
 * para comunicar interrupciones de hardware desde el BroadcastReceiver
 * sin mantener referencias a Services o Contexts.
 */
object ActivityTransitionEventBus {
    
    private val _events = MutableSharedFlow<ActivityTransitionEvent>(
        extraBufferCapacity = 64
    )
    val events: SharedFlow<ActivityTransitionEvent> = _events.asSharedFlow()

    fun postEvent(event: ActivityTransitionEvent): Boolean {
        return _events.tryEmit(event)
    }
}
