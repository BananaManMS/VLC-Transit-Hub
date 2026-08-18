package com.example.data.repository.renfe


import com.example.ui.cercanias.LiveVehicleInfo
import org.json.JSONObject

class GtfsParser {

    fun parseVehiclePositions(text: String, now: Long): Pair<Long, Map<String, LiveVehicleInfo>> {
        val map = mutableMapOf<String, LiveVehicleInfo>()
        val root = JSONObject(text)
        val headerObj = root.optJSONObject("header")
        val headerTimestamp = headerObj?.optLong("timestamp", 0L) ?: 0L

        val entityArray = root.optJSONArray("entity") ?: return Pair(headerTimestamp, map)
        for (i in 0 until entityArray.length()) {
            val entity = entityArray.optJSONObject(i) ?: continue
            val vehicleObj = entity.optJSONObject("vehicle") ?: continue
            val tripObj = vehicleObj.optJSONObject("trip") ?: continue

            val tripId = tripObj.optString("tripId", "")
            if (tripId.isEmpty()) continue

            val routeId = tripObj.optString("routeId", "")
            val posObj = vehicleObj.optJSONObject("position")
            val lat = posObj?.optDouble("latitude")
            val lon = posObj?.optDouble("longitude")
            val speed = posObj?.optDouble("speed")
            
            val currentStatus = vehicleObj.optString("currentStatus", "")
            val vehStopId = vehicleObj.optString("stopId", "")
            val innerVeh = vehicleObj.optJSONObject("vehicle")
            val label = innerVeh?.optString("label", "") ?: ""
            val platform = parsePlatformFromLabel(label)
            
            val timestampVal = vehicleObj.optLong("timestamp", 0L)
            
            val info = LiveVehicleInfo(
                tripId = tripId,
                routeId = routeId,
                latitude = lat,
                longitude = lon,
                status = currentStatus,
                platform = platform,
                speed = speed,
                currentStopId = vehStopId,
                timestamp = if (timestampVal > 0) timestampVal else now
            )
            map[tripId] = info
        }
        return Pair(headerTimestamp, map)
    }

    fun parseTripUpdates(text: String): Pair<Long, Map<String, GtfsRtTripUpdate>> {
        val map = mutableMapOf<String, GtfsRtTripUpdate>()
        val root = JSONObject(text)
        val headerObj = root.optJSONObject("header")
        val headerTimestamp = headerObj?.optLong("timestamp", 0L) ?: 0L

        val entityArray = root.optJSONArray("entity") ?: return Pair(headerTimestamp, map)
        for (i in 0 until entityArray.length()) {
            val entity = entityArray.optJSONObject(i) ?: continue
            val tripUpdate = entity.optJSONObject("tripUpdate") ?: continue
            val tripObj = tripUpdate.optJSONObject("trip") ?: continue
            
            val tripId = tripObj.optString("tripId", "")
            if (tripId.isEmpty()) continue
            
            val scheduleRelationship = tripObj.optString("scheduleRelationship", "SCHEDULED")
            var delay = tripUpdate.optInt("delay", 0)
            
            val stopDelays = mutableMapOf<String, Int>()
            val stopEstimatedTimes = mutableMapOf<String, Long>()
            val skippedStops = mutableSetOf<String>()
            val stopTimeUpdates = tripUpdate.optJSONArray("stopTimeUpdate")
            var firstActiveStopId = ""
            
            if (stopTimeUpdates != null) {
                for (j in 0 until stopTimeUpdates.length()) {
                    val stu = stopTimeUpdates.optJSONObject(j) ?: continue
                    val stuStopId = stu.optString("stopId", "")
                    val stuRel = stu.optString("scheduleRelationship", "")
                    
                    if (stuRel.equals("SKIPPED", ignoreCase = true) && stuStopId.isNotEmpty()) {
                        skippedStops.add(stuStopId)
                    }
                    if (firstActiveStopId.isEmpty() && !stuRel.equals("SKIPPED", ignoreCase = true) && stuStopId.isNotEmpty()) {
                        firstActiveStopId = stuStopId
                    }
                    
                    val depObj = stu.optJSONObject("departure") ?: stu.optJSONObject("arrival")
                    if (depObj != null) {
                        val dSec = depObj.optInt("delay", 0)
                        val epochTime = depObj.optLong("time", 0L)
                        if (stuStopId.isNotEmpty()) {
                            stopDelays[stuStopId] = dSec
                            if (epochTime > 0) stopEstimatedTimes[stuStopId] = epochTime
                        }
                        if (delay == 0 && dSec != 0) delay = dSec
                    }
                }
            }
            
            map[tripId] = GtfsRtTripUpdate(
                tripId = tripId,
                delaySeconds = delay,
                scheduleRelationship = scheduleRelationship,
                stopDelays = stopDelays,
                stopEstimatedTimes = stopEstimatedTimes,
                skippedStops = skippedStops,
                firstActiveStopId = firstActiveStopId
            )
        }
        return Pair(headerTimestamp, map)
    }

    private fun parsePlatformFromLabel(label: String): String {
        if (label.isBlank()) return ""
        val platfMatcher = Regex("""PLATF?\.\(([^)]+)\)""", RegexOption.IGNORE_CASE).find(label)
        if (platfMatcher != null) {
            return platfMatcher.groupValues[1].trim()
        }
        val viaMatcher = Regex("""V[íi]a\s*([0-9A-Za-z]+)""", RegexOption.IGNORE_CASE).find(label)
        if (viaMatcher != null) {
            return viaMatcher.groupValues[1].trim()
        }
        return ""
    }
}