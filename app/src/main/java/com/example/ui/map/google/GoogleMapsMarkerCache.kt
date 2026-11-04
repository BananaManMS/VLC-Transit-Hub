package com.example.ui.map.google

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.example.ui.map.components.CustomPlaceType
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.MapStyleOptions

data class CachedMarkerIcon(
    val descriptor: BitmapDescriptor,
    val anchor: Offset
)

/**
 * BitmapDescriptor Cache Facade ensuring instant 60-90 FPS rendering on Google Maps.
 * Internally delegates to domain-isolated caches (Rail, Bus, Special) to eliminate cache thrashing.
 * Preserves 100% backward compatibility for all callers and signatures.
 */
object GoogleMapsMarkerCache {

    // --- Rail (Metro & Cercanías) ---
    fun getOrCreateMetroStationIcon(
        context: Context,
        stationName: String,
        lines: List<String>,
        isDarkMode: Boolean,
        showPill: Boolean = true,
        isFavorite: Boolean = false
    ): CachedMarkerIcon = GoogleMapRailIconHelper.getOrCreateMetroStationIcon(
        context, stationName, lines, isDarkMode, showPill, isFavorite
    )

    fun getOrCreateMetroWhiteDotIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon =
        GoogleMapRailIconHelper.getOrCreateMetroWhiteDotIcon(context, isDarkMode)

    fun getOrCreateCercaniasStationIcon(
        context: Context,
        stationName: String,
        lines: List<String>,
        isDarkMode: Boolean,
        showPill: Boolean = true,
        isFavorite: Boolean = false
    ): CachedMarkerIcon = GoogleMapRailIconHelper.getOrCreateCercaniasStationIcon(
        context, stationName, lines, isDarkMode, showPill, isFavorite
    )

    fun getOrCreateCercaniasRedDotIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon =
        GoogleMapRailIconHelper.getOrCreateCercaniasRedDotIcon(context, isDarkMode)

    fun getOrCreateCercaniasLogoSmallIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon =
        GoogleMapRailIconHelper.getOrCreateCercaniasLogoSmallIcon(context, isDarkMode)

    // --- Bus (EMT & Metrobús) ---
    fun getOrCreateBusStopIcon(
        context: Context,
        stopName: String,
        linesString: String?,
        isDarkMode: Boolean,
        isMetrobus: Boolean,
        alias: String? = null,
        isFavorite: Boolean = false
    ): CachedMarkerIcon = GoogleMapBusIconHelper.getOrCreateBusStopIcon(
        context, stopName, linesString, isDarkMode, isMetrobus, alias, isFavorite
    )

    fun getOrCreateBusIconBadge(
        context: Context,
        isMetrobus: Boolean
    ): CachedMarkerIcon = GoogleMapBusIconHelper.getOrCreateBusIconBadge(context, isMetrobus)

    fun getOrCreateMergedCompactIconBadge(
        context: Context,
        isDarkMode: Boolean
    ): CachedMarkerIcon = GoogleMapBusIconHelper.getOrCreateMergedCompactIconBadge(context, isDarkMode)

    fun getOrCreateMergedStopIcon(
        context: Context,
        stopName: String,
        emtLinesString: String?,
        metrobusLinesString: String?,
        isDarkMode: Boolean,
        alias: String? = null,
        isFavorite: Boolean = false
    ): CachedMarkerIcon = GoogleMapBusIconHelper.getOrCreateMergedStopIcon(
        context, stopName, emtLinesString, metrobusLinesString, isDarkMode, alias, isFavorite
    )

    fun getOrCreateCompactDotIcon(
        context: Context,
        colorInt: Int
    ): CachedMarkerIcon = GoogleMapBusIconHelper.getOrCreateCompactDotIcon(context, colorInt)

    fun getOrCreateMergedDotIcon(context: Context): CachedMarkerIcon =
        GoogleMapBusIconHelper.getOrCreateMergedDotIcon(context)

    fun getOrCreateClusterIcon(
        context: Context,
        count: Int,
        colorInt: Int
    ): CachedMarkerIcon = GoogleMapBusIconHelper.getOrCreateClusterIcon(context, count, colorInt)

    // --- Special: Access, Itinerary, Places, Valenbisi, Live Trains, User Location, Chevrons ---
    fun getOrCreateAccessIcon(
        context: Context,
        typeCas: String
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateAccessIcon(context, typeCas)

    fun getOrCreateOriginMarkerIcon(context: Context): CachedMarkerIcon =
        GoogleMapSpecialMarkerHelper.getOrCreateOriginMarkerIcon(context)

    fun getOrCreateDestinationMarkerIcon(context: Context, isDarkMode: Boolean): CachedMarkerIcon =
        GoogleMapSpecialMarkerHelper.getOrCreateDestinationMarkerIcon(context, isDarkMode)

    fun getOrCreateTransferMarkerIcon(context: Context, colorHex: String, label: String): CachedMarkerIcon =
        GoogleMapSpecialMarkerHelper.getOrCreateTransferMarkerIcon(context, colorHex, label)

    fun getOrCreateStopDotWithTextIcon(context: Context, stopName: String, zoom: Double, isDarkMode: Boolean): CachedMarkerIcon =
        GoogleMapSpecialMarkerHelper.getOrCreateStopDotWithTextIcon(context, stopName, zoom, isDarkMode)

    fun getOrCreateTransferLabelIcon(context: Context, labelText: String, zoom: Double, isDarkMode: Boolean): CachedMarkerIcon =
        GoogleMapSpecialMarkerHelper.getOrCreateTransferLabelIcon(context, labelText, zoom, isDarkMode)

    fun getOrCreateCustomPlaceMarkerIcon(
        context: Context,
        type: CustomPlaceType,
        currentZoom: Double = 16.0,
        isDarkMode: Boolean = false,
        customColorHex: String? = null
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateCustomPlaceMarkerIcon(
        context, type, currentZoom, isDarkMode, customColorHex
    )

    fun getOrCreateValenbisiMarkerIcon(
        context: Context,
        available: Int,
        free: Int,
        isDarkMode: Boolean,
        showPill: Boolean
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateValenbisiMarkerIcon(
        context, available, free, isDarkMode, showPill
    )

    fun getOrCreateValenbisiCompactDotIcon(
        context: Context,
        available: Int
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateValenbisiCompactDotIcon(context, available)

    fun getOrCreateLiveTrainIcon(
        context: Context,
        lineCode: String,
        lineColorHex: Int,
        delayMinutes: Int,
        bearing: Float,
        zoom: Double,
        showArrow: Boolean
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateLiveTrainIcon(
        context, lineCode, lineColorHex, delayMinutes, bearing, zoom, showArrow
    )

    fun getOrCreateUserLocationIcon(
        context: Context,
        isCellTowerLocation: Boolean = false,
        hasDirectionalArrow: Boolean = true
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateUserLocationIcon(
        context, isCellTowerLocation, hasDirectionalArrow
    )

    fun getOrCreateChevronArrowIcon(
        context: Context,
        borderColor: Int
    ): CachedMarkerIcon = GoogleMapSpecialMarkerHelper.getOrCreateChevronArrowIcon(context, borderColor)

    fun clear() {
        GoogleMapRailIconHelper.clear()
        GoogleMapBusIconHelper.clear()
        GoogleMapSpecialMarkerHelper.clear()
    }
}

/**
 * Thread-safe Singleton cache for Google Maps MapStyleOptions JSON resources.
 * Prevents reading and parsing raw JSON style files repeatedly.
 */
object GoogleMapStyleCache {
    @Volatile
    private var lightStyle: MapStyleOptions? = null
    @Volatile
    private var darkStyle: MapStyleOptions? = null

    fun getLightStyle(context: Context): MapStyleOptions? {
        if (lightStyle == null) {
            synchronized(this) {
                if (lightStyle == null) {
                    try {
                        lightStyle = MapStyleOptions.loadRawResourceStyle(
                            context.applicationContext,
                            com.example.R.raw.map_style_clean_light
                        )
                    } catch (_: Exception) {}
                }
            }
        }
        return lightStyle
    }

    fun getDarkStyle(context: Context): MapStyleOptions? {
        if (darkStyle == null) {
            synchronized(this) {
                if (darkStyle == null) {
                    try {
                        darkStyle = MapStyleOptions.loadRawResourceStyle(
                            context.applicationContext,
                            com.example.R.raw.map_style_clean_dark
                        )
                    } catch (_: Exception) {}
                }
            }
        }
        return darkStyle
    }
}
