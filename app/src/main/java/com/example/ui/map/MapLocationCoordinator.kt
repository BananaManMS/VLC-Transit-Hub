package com.example.ui.map

import android.content.Context
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

    private val _cameraTarget = MutableStateFlow<GeoPoint>(MapConfig.VALENCIA_CENTER)
    val cameraTarget: StateFlow<GeoPoint> = _cameraTarget.asStateFlow()

    private val _cameraZoom = MutableStateFlow(MapConfig.DEFAULT_ZOOM)
    val cameraZoom: StateFlow<Double> = _cameraZoom.asStateFlow()

    private val _cameraAnimTrigger = MutableStateFlow(0)
    val cameraAnimTrigger: StateFlow<Int> = _cameraAnimTrigger.asStateFlow()

    private var hasInitiallyCenteredOnUser = false

    private val _isFollowingUser = MutableStateFlow(false)
    val isFollowingUser: StateFlow<Boolean> = _isFollowingUser.asStateFlow()

    private var locationTrackingJob: Job? = null

    fun startLocationTracking(trackingContext: Context = context) {
        if (locationTrackingJob?.isActive == true) return
        locationTrackingJob = scope.launch(Dispatchers.IO) {
            locationTracker.getLocationUpdates(
                context = trackingContext,
                intervalMs = 12000L,
                minDistanceMeters = 10.0f
            ).collectLatest { location ->
                withContext(Dispatchers.Main) {
                    val geo = GeoPoint(location.latitude, location.longitude)
                    _userLocation.value = geo
                    if (!hasInitiallyCenteredOnUser) {
                        hasInitiallyCenteredOnUser = true
                        _isFollowingUser.value = true
                        _cameraTarget.value = geo
                        _cameraZoom.value = 16.0
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    } else if (_isFollowingUser.value) {
                        _cameraTarget.value = geo
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    }
                }
            }
        }
    }

    fun stopLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null
    }

    fun updateLocation(lat: Double, lon: Double) {
        val geo = GeoPoint(lat, lon)
        _userLocation.value = geo
        if (!hasInitiallyCenteredOnUser) {
            hasInitiallyCenteredOnUser = true
            _isFollowingUser.value = true
            _cameraTarget.value = geo
            _cameraZoom.value = 16.0
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        } else if (_isFollowingUser.value) {
            _cameraTarget.value = geo
            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
        }
    }

    fun updateLocation() {
        scope.launch(Dispatchers.IO) {
            val location = locationTracker.getLastKnownLocation(context)
            if (location != null) {
                withContext(Dispatchers.Main) {
                    updateLocation(location.latitude, location.longitude)
                }
            }
        }
    }

    fun centerOnUser() {
        _isFollowingUser.value = true
        val currentLoc = _userLocation.value
        if (currentLoc != null) {
            _cameraTarget.value = currentLoc
            _cameraZoom.value = 16.0
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
                        _cameraZoom.value = 16.0
                        _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                    } else {
                        locationTracker.requestSingleLocation(context) { lat, lon ->
                            val geo = GeoPoint(lat, lon)
                            _userLocation.value = geo
                            _cameraTarget.value = geo
                            _cameraZoom.value = 16.0
                            _cameraAnimTrigger.value = _cameraAnimTrigger.value + 1
                        }
                    }
                }
            }
        }
    }

    fun disableFollowUser() {
        _isFollowingUser.value = false
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
