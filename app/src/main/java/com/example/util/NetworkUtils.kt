package com.example.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun <T> retryIO(
    times: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 3000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("NetworkRetry", "Attempt ${attempt + 1}/$times failed: ${e.localizedMessage}", e)
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
    }
    return block() // Last attempt. If it fails, the exception is thrown.
}
