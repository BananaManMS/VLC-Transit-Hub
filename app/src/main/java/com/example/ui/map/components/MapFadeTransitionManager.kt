package com.example.ui.map.components

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.lang.ref.WeakReference

/**
 * Manages buttery-smooth, hardware-friendly fade-in and fade-out transitions
 * for map transit layers and marker icons when zooming, moving, or filtering.
 *
 * Uses a single centralized ValueAnimator per MapView to ensure zero CPU/GPU overhead,
 * preventing any conflicts with specific feature transparency modifiers (e.g. EMT/Metro highlights).
 */
object MapFadeTransitionManager {

    enum class Layer {
        BUS,
        METROBUS,
        VALENBISI,
        METRO,
        CERCANIAS,
        CUSTOM_PLACES
    }

    private class LayerState {
        var currentAlpha: Float = 1.0f
        var startAlpha: Float = 1.0f
        var targetAlpha: Float = 1.0f
        var isInitialized: Boolean = false
    }

    private val layerStates = mapOf(
        Layer.BUS to LayerState(),
        Layer.METROBUS to LayerState(),
        Layer.VALENBISI to LayerState(),
        Layer.METRO to LayerState(),
        Layer.CERCANIAS to LayerState(),
        Layer.CUSTOM_PLACES to LayerState()
    )

    private var activeAnimator: ValueAnimator? = null
    private var currentMapViewRef: WeakReference<MapView>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Active marker holders with their base functional alphas for frame-by-frame updates
    private val activeLayerMarkers = mutableMapOf<Layer, MutableList<Pair<Marker, Float>>>()

    private const val FADE_DURATION_MS = 200L
    private val interpolator = DecelerateInterpolator(1.5f)

    /**
     * Gets the current animated alpha multiplier for a layer [0.0f .. 1.0f].
     */
    fun getLayerAlpha(layer: Layer): Float {
        return layerStates[layer]?.currentAlpha ?: 1.0f
    }

    /**
     * Registers active markers for a layer alongside their base functional alpha
     * (e.g., 0.25f when dimmed by highlight, 1.0f for selected/normal).
     */
    fun registerActiveMarkers(layer: Layer, markers: List<Pair<Marker, Float>>) {
        synchronized(activeLayerMarkers) {
            activeLayerMarkers[layer] = markers.toMutableList()
        }
    }

    /**
     * Checks targets for all layers and starts a smooth fade transition if any target has changed.
     */
    fun updateLayerVisibility(
        mapView: MapView,
        isBusVisible: Boolean,
        isMetrobusVisible: Boolean,
        isValenbisiVisible: Boolean,
        isMetroVisible: Boolean,
        isCercaniasVisible: Boolean,
        isCustomPlacesVisible: Boolean
    ) {
        currentMapViewRef = WeakReference(mapView)

        val targets = mapOf(
            Layer.BUS to if (isBusVisible) 1.0f else 0.0f,
            Layer.METROBUS to if (isMetrobusVisible) 1.0f else 0.0f,
            Layer.VALENBISI to if (isValenbisiVisible) 1.0f else 0.0f,
            Layer.METRO to if (isMetroVisible) 1.0f else 0.0f,
            Layer.CERCANIAS to if (isCercaniasVisible) 1.0f else 0.0f,
            Layer.CUSTOM_PLACES to if (isCustomPlacesVisible) 1.0f else 0.0f
        )

        var hasChanges = false
        for ((layer, target) in targets) {
            val state = layerStates[layer] ?: continue
            if (!state.isInitialized) {
                // First initialization sets the state instantly without animation
                state.currentAlpha = target
                state.targetAlpha = target
                state.startAlpha = target
                state.isInitialized = true
            } else if (state.targetAlpha != target) {
                state.startAlpha = state.currentAlpha
                state.targetAlpha = target
                hasChanges = true
            }
        }

        if (hasChanges) {
            startAnimation()
        }
    }

    private fun startAnimation() {
        mainHandler.post {
            activeAnimator?.cancel()

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = FADE_DURATION_MS
                this.interpolator = MapFadeTransitionManager.interpolator
                addUpdateListener { animation ->
                    val fraction = animation.animatedFraction
                    val mv = currentMapViewRef?.get() ?: return@addUpdateListener

                    synchronized(activeLayerMarkers) {
                        for ((layer, state) in layerStates) {
                            if (state.startAlpha != state.targetAlpha) {
                                state.currentAlpha = state.startAlpha + (state.targetAlpha - state.startAlpha) * fraction
                                val markers = activeLayerMarkers[layer] ?: continue
                                for ((marker, baseAlpha) in markers) {
                                    marker.alpha = baseAlpha * state.currentAlpha
                                }
                            }
                        }
                    }

                    try {
                        if (mv.isAttachedToWindow || mv.parent != null) {
                            mv.invalidate()
                        }
                    } catch (_: Exception) {}
                }
            }

            activeAnimator = animator
            animator.start()
        }
    }

    /**
     * Cancels any running animation and cleans up references.
     */
    fun reset() {
        mainHandler.post {
            activeAnimator?.cancel()
            activeAnimator = null
            currentMapViewRef = null
            synchronized(activeLayerMarkers) {
                activeLayerMarkers.clear()
            }
            for (state in layerStates.values) {
                state.isInitialized = false
                state.currentAlpha = 1.0f
                state.startAlpha = 1.0f
                state.targetAlpha = 1.0f
            }
        }
    }
}
