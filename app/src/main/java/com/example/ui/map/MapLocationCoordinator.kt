package com.example.ui.map

import android.content.Context
import android.location.LocationManager
import com.example.util.ActiveTripProgressTracker
import com.example.util.LocationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

class MapLocationCoordinator(
    private val scope: CoroutineScope,
    private val context: Context,
    private val locationTracker: MapLocationTracker = MapLocationTracker()
) {
    private val _userLocation = MutableStateFlow<GeoPoint?>(null)
    val userLocation: StateFlow<GeoPoint?> = _userLocation.asStateFlow()

    private val _isCellTowerLocation = MutableStateFlow(false)
    val isCellTowerLocation: StateFlow<Boolean> = _isCellTowerLocation.asStateFlow()

    private val _cameraTarget: MutableStateFlow<GeoPoint>
    val cameraTarget: StateFlow<GeoPoint>

    private val _cameraZoom: MutableStateFlow<Double>
    val cameraZoom: StateFlow<Double>

    private val _cameraAnimTrigger = MutableStateFlow(0)
    val cameraAnimTrigger: StateFlow<Int> = _cameraAnimTrigger.asStateFlow()

    private var hasInitiallyCenteredOnUser = false

    private val _isFollowingUser = MutableStateFlow(false)
    val isFollowingUser: StateFlow<Boolean> = _isFollowingUser.asStateFlow()

    private var locationTrackingJob: Job? = null
    private var lastAccurateGpsTimeMs = 0L

    init {
        // Retrieve last known location synchronously from cache first, then LocationManager, then dashboard preferences
        val lastKnownSync = LocationUtils.lastKnownLocationCache
            ?: try { LocationUtils.getLocationFromLocationManager(context) } catch (e: Exception) { null }
        
        val initialGeo = if (lastKnownSync != null) {
            GeoPoint(lastKnownSync.latitude, lastKnownSync.longitude)
        } else {
            try {
                val prefs = context.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)
                val lat = prefs.getString("last_known_lat", "")?.toDoubleOrNull()
                    ?: prefs.getString("last_latitude", "")?.toDoubleOrNull()
                val lon = prefs.getString("last_known_lon", "")?.toDoubleOrNull()
                    ?: prefs.getString("last_longitude", "")?.toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            } catch (e: Exception) {
                null
            }
        }

        if (initialGeo != null) {
            _userLocation.value = initialGeo
            _cameraTarget = MutableStateFlow(initialGeo)
            _cameraZoom = MutableStateFlow(15.5) // Center immediately with the correct medium zoom level
            hasInitiallyCenteredOnUser = true
            _isFollowingUser.value = true
        } else {
            _cameraTarget = MutableStateFlow(MapConfig.VALENCIA_CENTER)
            _cameraZoom = MutableStateFlow(15.5)
        }
        cameraTarget = _cameraTarget.asStateFlow()
        cameraZoom = _cameraZoom.asStateFlow()

        scope.launch(Dispatchers.IO) {
            val lastLoc = try { com.example.util.LocationUtils.getBestLastLocation(context) } catch (e: Exception) { null }
            if (lastLoc != null) {
                val geo = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                withContext(Dispatchers.Main) {
                    _userLocation.value = geo
                    if (!hasInitiallyCenteredOnUser) {
                        _cameraTarget.value = geo
                        _cameraZoom.value = 15.5
                        hasInitiallyCenteredOnUser = true
                        _isFollowingUser.value = true
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    }
                }
            }
        }
    }

    fun startLocationTracking(trackingContext: Context = context) {
        if (locationTrackingJob?.isActive == true) return
        locationTrackingJob = scope.launch(Dispatchers.IO) {
            locationTracker.getLocationUpdates(
                context = trackingContext,
                intervalMs = 4000L,
                minDistanceMeters = 2.0f
            ).collectLatest { location ->
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    val isNetworkProvider = location.provider == LocationManager.NETWORK_PROVIDER
                    val isGpsProvider = location.provider == LocationManager.GPS_PROVIDER
                    val progressInfo = ActiveTripProgressTracker.progressState.value
                    val isInTunnelOrDeadReckoning = progressInfo.isDeadReckoning && progressInfo.isBoarded

                    var shouldIgnoreLocation = false

                    if (isGpsProvider) {
                        if (location.hasAccuracy() && location.accuracy <= 30f) {
                            lastAccurateGpsTimeMs = now
                        }
                        if (isInTunnelOrDeadReckoning && (location.hasAccuracy() && location.accuracy > 25f)) {
                            shouldIgnoreLocation = true
                        }
                        if (progressInfo.isBoarded && (location.hasAccuracy() && location.accuracy > 50f)) {
                            shouldIgnoreLocation = true
                        }
                    } else if (isNetworkProvider) {
                        val elapsedSinceGps = now - lastAccurateGpsTimeMs
                        if (progressInfo.isBoarded && elapsedSinceGps < 8000L) {
                            shouldIgnoreLocation = true
                        }
                    }

                    val isCellTower = isInTunnelOrDeadReckoning || isNetworkProvider

                    if (!shouldIgnoreLocation) {
                        val geo = GeoPoint(location.latitude, location.longitude)
                        _userLocation.value = geo
                        _isCellTowerLocation.value = isCellTower

                        val currentTarget = _cameraTarget.value
                        val distanceToTarget = currentTarget?.distanceToAsDouble(geo) ?: Double.MAX_VALUE

                        if (!hasInitiallyCenteredOnUser) {
                            hasInitiallyCenteredOnUser = true
                            _isFollowingUser.value = true
                            _cameraTarget.value = geo
                            _cameraZoom.value = 15.5
                            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                        } else if (_isFollowingUser.value && distanceToTarget > 3.0) {
                            _cameraTarget.value = geo
                            if (_cameraZoom.value < 13.0) {
                                _cameraZoom.value = 15.5
                            }
                            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                        }
                    } else {
                        _isCellTowerLocation.value = isCellTower
                    }
                }
            }
        }
    }

    fun stopLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null
    }

    fun updateLocation(lat: Double, lon: Double, isCellTower: Boolean? = null) {
        if (locationTrackingJob?.isActive == true) {
            // Ignore external raw/unfiltered updates when the map's dedicated tracker is active
            return
        }
        val progressInfo = ActiveTripProgressTracker.progressState.value
        val isInTunnelOrDeadReckoning = progressInfo.isDeadReckoning && progressInfo.isBoarded
        val resolvedCell = when {
            isInTunnelOrDeadReckoning -> true
            isCellTower != null -> isCellTower
            else -> _isCellTowerLocation.value
        }
        val geo = GeoPoint(lat, lon)
        _userLocation.value = geo
        _isCellTowerLocation.value = resolvedCell
        if (!hasInitiallyCenteredOnUser) {
            hasInitiallyCenteredOnUser = true
            _isFollowingUser.value = true
            _cameraTarget.value = geo
            _cameraZoom.value = 15.5
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        } else if (_isFollowingUser.value) {
            _cameraTarget.value = geo
            if (_cameraZoom.value < 13.0) {
                _cameraZoom.value = 15.5
            }
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        }
    }

    fun updateLocation() {
        scope.launch(Dispatchers.IO) {
            val location = locationTracker.getLastKnownLocation(context)
            if (location != null) {
                val isCell = location.provider == LocationManager.NETWORK_PROVIDER
                withContext(Dispatchers.Main) {
                    updateLocation(location.latitude, location.longitude, isCell)
                }
            }
        }
    }

    fun centerOnUser() {
        _isFollowingUser.value = true
        val currentLoc = _userLocation.value
        if (currentLoc != null) {
            _cameraTarget.value = currentLoc
            _cameraZoom.value = 15.5
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        } else {
            scope.launch(Dispatchers.IO) {
                val location = locationTracker.getLastKnownLocation(context)
                val target = if (location != null) {
                    GeoPoint(location.latitude, location.longitude).also {
                        withContext(Dispatchers.Main) { _userLocation.value = it }
                    }
                } else {
                    _userLocation.value
                }
                withContext(Dispatchers.Main) {
                    if (target != null) {
                        _cameraTarget.value = target
                        _cameraZoom.value = 15.5
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    } else {
                        locationTracker.requestSingleLocation(context) { lat, lon ->
                            val geo = GeoPoint(lat, lon)
                            _userLocation.value = geo
                            _cameraTarget.value = geo
                            _cameraZoom.value = 14.5
                            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                        }
                    }
                }
            }
        }
    }

    fun disableFollowUser() {
        _isFollowingUser.value = false
        hasInitiallyCenteredOnUser = true
    }

    fun prepareForItinerary() {
        _isFollowingUser.value = false
        hasInitiallyCenteredOnUser = true
    }

    fun updateCameraPosition(center: GeoPoint, zoom: Double) {
        if (!_isFollowingUser.value) {
            _cameraTarget.value = center
        }
        _cameraZoom.value = zoom
    }

    fun setCameraZoom(zoom: Double) {
        _cameraZoom.value = zoom
    }

    fun setCameraTarget(geoPoint: GeoPoint, zoom: Double = _cameraZoom.value) {
        _isFollowingUser.value = false
        _cameraTarget.value = geoPoint
        _cameraZoom.value = zoom
        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
    }
}
