package com.example.data.model.routing

/**
 * Operational progression phase of a transit leg schedule.
 * Distinguishes between theoretical timetables awaiting active radar lock
 * and telemetry actively verified in real-time.
 */
enum class SchedulePhase {
    THEORETICAL,
    THEORETICAL_AWAITING_RADAR,
    LIVE_ACQUIRED
}
