package com.example.data.model.routing

/**
 * Operational progression phase of a transit leg schedule.
 * Distinguishes between theoretical timetables awaiting active radar lock
 * and telemetry actively verified in real-time.
 */
enum class SchedulePhase {
    /** Timetable is theoretical or scheduled; system is waiting to acquire live radar/telemetry */
    THEORETICAL_AWAITING_RADAR,

    /** Active live telemetry acquired (vehicleId, live delay, or GPS trip update locked) */
    LIVE_ACQUIRED
}
