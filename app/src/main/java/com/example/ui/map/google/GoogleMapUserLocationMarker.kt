package com.example.ui.map.google

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import org.osmdroid.util.GeoPoint

/**
 * Modern, subtle User Location Marker on Google Maps matching OSMdroid UserLocationMarker:
 * 1. Smooth sliding animation (duration 450ms) between GPS updates.
 * 2. Hardware orientation sensor for real-time compass heading with anti-jitter smoothing.
 * 3. Directional pointer triangle in forward heading direction with white outline.
 * 4. Crisp white outer halo circle with vibrant inner core (blue for GNSS, emerald for cell tower).
 * 5. Subtle semi-transparent accuracy radius circle on the map.
 */
@Composable
internal fun GoogleMapUserLocationMarker(
    userLocation: GeoPoint,
    userBearing: Float?,
    isCellTowerLocation: Boolean,
    context: Context,
    cameraBearing: Float = 0f
) {
    // 1. Smooth sliding position animation (duration 450ms matching OSMdroid UserLocationMarker)
    val targetLatLng = remember(userLocation.latitude, userLocation.longitude) {
        LatLng(userLocation.latitude, userLocation.longitude)
    }
    var prevLatLng by remember { mutableStateOf(targetLatLng) }
    var currentTargetLatLng by remember { mutableStateOf(targetLatLng) }
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(targetLatLng) {
        if (targetLatLng != currentTargetLatLng) {
            prevLatLng = LatLng(
                prevLatLng.latitude + (currentTargetLatLng.latitude - prevLatLng.latitude) * animProgress.value,
                prevLatLng.longitude + (currentTargetLatLng.longitude - prevLatLng.longitude) * animProgress.value
            )
            currentTargetLatLng = targetLatLng
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
    }

    val animatedLatLng = remember(animProgress.value, prevLatLng, currentTargetLatLng) {
        val f = animProgress.value.toDouble()
        LatLng(
            prevLatLng.latitude + (currentTargetLatLng.latitude - prevLatLng.latitude) * f,
            prevLatLng.longitude + (currentTargetLatLng.longitude - prevLatLng.longitude) * f
        )
    }

    // 2. Hardware Orientation Sensor for real-time heading
    var sensorHeading by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuthRad = orientationAngles[0]
                    var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    if (azimuthDeg < 0f) azimuthDeg += 360f
                    sensorHeading = azimuthDeg
                } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                    var azimuthDeg = event.values[0]
                    if (azimuthDeg < 0f) azimuthDeg += 360f
                    sensorHeading = azimuthDeg
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        rotationSensor?.let {
            sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // 3. Smooth bearing interpolation to avoid jitter
    val rawTargetBearing = userBearing ?: sensorHeading
    var smoothedBearing by remember { mutableFloatStateOf(rawTargetBearing) }
    LaunchedEffect(rawTargetBearing) {
        val angleDiff = ((rawTargetBearing - smoothedBearing + 540f) % 360f) - 180f
        smoothedBearing = (smoothedBearing + angleDiff * 0.30f) % 360f
        if (smoothedBearing < 0f) smoothedBearing += 360f
    }

    // 4. Custom Icon from Cache
    val userIcon = remember(isCellTowerLocation) {
        GoogleMapsMarkerCache.getOrCreateUserLocationIcon(
            context = context,
            isCellTowerLocation = isCellTowerLocation,
            hasDirectionalArrow = !isCellTowerLocation
        )
    }

    // 5. Accuracy circle (meters) + Center Pin with Directional Arrow
    val circleFill = if (isCellTowerLocation) Color(0x18059669) else Color(0x181D4ED8)
    val circleStroke = if (isCellTowerLocation) Color(0x4010B981) else Color(0x403B82F6)

    Circle(
        center = animatedLatLng,
        radius = 20.0,
        fillColor = circleFill,
        strokeColor = circleStroke,
        strokeWidth = 1.5f,
        zIndex = 999f
    )

    val markerState = remember { MarkerState(position = animatedLatLng) }
    LaunchedEffect(animatedLatLng) {
        markerState.position = animatedLatLng
    }

    val screenBearing = if (isCellTowerLocation) 0f else (smoothedBearing - cameraBearing + 360f) % 360f

    // flat = false ensures billboard pass rendering with maximum priority (zIndex = 1000f) above all map elements
    Marker(
        state = markerState,
        icon = userIcon.descriptor,
        anchor = userIcon.anchor,
        flat = false,
        rotation = screenBearing,
        title = "Tu ubicación",
        zIndex = 1000f
    )
}
