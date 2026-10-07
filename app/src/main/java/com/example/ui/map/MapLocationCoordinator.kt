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
            var lastGpsLat = 0.0
            var lastGpsLon = 0.0
            var gpsStaleRepetitions = 0

            locationTracker.getLocationUpdates(
                context = trackingContext,
                intervalMs = 12000L,
                minDistanceMeters = 10.0f
            ).collectLatest { location ->
                withContext(Dispatchers.Main) {
                    val isNetworkProvider = location.provider == LocationManager.NETWORK_PROVIDER
                    val isGpsProvider = location.provider == LocationManager.GPS_PROVIDER
                    val progressInfo = ActiveTripProgressTracker.progressState.value
                    val isInTunnelOrDeadReckoning = progressInfo.isDeadReckoning && progressInfo.isBoarded

                    if (isGpsProvider) {
                        val isSameLocation = Math.abs(location.latitude - lastGpsLat) < 0.00005 &&
                                Math.abs(location.longitude - lastGpsLon) < 0.00005
                        if (isSameLocation && lastGpsLat != 0.0) {
                            gpsStaleRepetitions++
                        } else {
                            gpsStaleRepetitions = 0
                            lastGpsLat = location.latitude
                            lastGpsLon = location.longitude
                        }
                    }

                    val isGpsStale = gpsStaleRepetitions >= 2 || (location.hasAccuracy() && location.accuracy > 70f)

                    // If GPS sends repeated stale locations or we are in a tunnel/dead-reckoning leg,
                    // prioritize network cellular updates and reject stale GPS rollbacks.
                    val shouldIgnoreStaleGps = isGpsProvider && (isInTunnelOrDeadReckoning || isGpsStale) && _isCellTowerLocation.value
                    val isCellTower = isNetworkProvider || (isInTunnelOrDeadReckoning && !isGpsProvider)

                    if (!shouldIgnoreStaleGps) {
                        val geo = GeoPoint(location.latitude, location.longitude)
                        _userLocation.value = geo
                        _isCellTowerLocation.value = isCellTower

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
                }
            }
        }
    }

    fun stopLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null
    }

    fun updateLocation(lat: Double, lon: Double, isCellTower: Boolean = false) {
        val geo = GeoPoint(lat, lon)
        _userLocation.value = geo
        _isCellTowerLocation.value = isCellTower
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
