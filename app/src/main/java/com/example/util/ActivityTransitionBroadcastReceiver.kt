package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult

/**
 * Receiver de bajo nivel libre de estado (Stateless Receiver).
 * No contiene referencias estáticas ni llamadas a .getInstance().
 * Emite directamente al EventBus para evitar Memory Leaks.
 */
class ActivityTransitionBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        if (intent.action == PedestrianActivityTransitionManager.ACTION_PROCESS_ACTIVITY_TRANSITIONS) {
            if (ActivityTransitionResult.hasResult(intent)) {
                val result = ActivityTransitionResult.extractResult(intent) ?: return
                for (event in result.transitionEvents) {
                    ActivityTransitionEventBus.postEvent(event)
                }
            }
        }
    }
}
