package com.example.ui.map.google

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.collection.LruCache
import androidx.compose.ui.geometry.Offset
import com.example.ui.map.components.getBusDetailedMarkerIcon
import com.example.ui.map.components.getCompactDotIcon
import com.example.ui.map.components.getMarkerIcon
import com.example.ui.map.components.getMergedCompactMarkerIcon
import com.example.ui.map.components.getMergedDetailedMarkerIcon
import com.example.ui.map.components.getMergedDotMarkerIcon
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Dedicated cache and icon generator for EMT and Metrobús bus stops, clusters, and merged badges.
 */
internal object GoogleMapBusIconHelper {
    private val busCache = LruCache<String, CachedMarkerIcon>(512)

    fun getOrCreateBusStopIcon(
        context: Context,
        stopName: String,
        linesString: String?,
        isDarkMode: Boolean,
        isMetrobus: Boolean,
        alias: String? = null,
        isFavorite: Boolean = false
    ): CachedMarkerIcon {
        val baseName = if (!alias.isNullOrBlank()) "$alias ($stopName)" else stopName
        val displayName = if (isFavorite) "★ $baseName" else baseName
        val cleanKey = "BUS_DETAILED_${displayName}_${linesString.orEmpty()}_${isDarkMode}_$isMetrobus"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val busType = if (isMetrobus) "MB" else "BUS"
            val result = getBusDetailedMarkerIcon(
                context = context,
                stopName = displayName,
                linesString = linesString,
                isDarkMode = isDarkMode,
                busType = busType,
                labelPosition = "BELOW"
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(result.anchorU, result.anchorV)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(if (isMetrobus) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_RED),
                    anchor = Offset(0.5f, 1.0f)
                )
            }
        } catch (e: Throwable) {
            Log.e("GoogleMapBusIconHelper", "Error generating bus icon for $displayName", e)
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(if (isMetrobus) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 1.0f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateBusIconBadge(
        context: Context,
        isMetrobus: Boolean
    ): CachedMarkerIcon {
        val cleanKey = "BUS_BADGE_ONLY_$isMetrobus"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val label = if (isMetrobus) "MB" else "BUS"
            val colorInt = if (isMetrobus) AndroidColor.parseColor("#FFB300") else AndroidColor.parseColor("#E53935")
            val drawable = getMarkerIcon(context, label, colorInt)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(0.5f, 0.5f)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(if (isMetrobus) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_RED),
                    anchor = Offset(0.5f, 0.5f)
                )
            }
        } catch (e: Throwable) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(if (isMetrobus) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_RED),
                anchor = Offset(0.5f, 0.5f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateMergedCompactIconBadge(
        context: Context,
        isDarkMode: Boolean
    ): CachedMarkerIcon {
        val cleanKey = "MERGED_BADGE_ONLY_$isDarkMode"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val drawable = getMergedCompactMarkerIcon(context, isDarkMode)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(0.5f, 0.5f)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                    anchor = Offset(0.5f, 0.5f)
                )
            }
        } catch (e: Throwable) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                anchor = Offset(0.5f, 0.5f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateMergedStopIcon(
        context: Context,
        stopName: String,
        emtLinesString: String?,
        metrobusLinesString: String?,
        isDarkMode: Boolean,
        alias: String? = null,
        isFavorite: Boolean = false
    ): CachedMarkerIcon {
        val baseName = if (!alias.isNullOrBlank()) "$alias ($stopName)" else stopName
        val displayName = if (isFavorite) "★ $baseName" else baseName
        val cleanKey = "MERGED_DETAILED_${displayName}_${emtLinesString.orEmpty()}_${metrobusLinesString.orEmpty()}_$isDarkMode"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val result = getMergedDetailedMarkerIcon(
                context = context,
                stopName = displayName,
                emtLinesString = emtLinesString,
                metrobusLinesString = metrobusLinesString,
                isDarkMode = isDarkMode,
                labelPosition = "BELOW"
            )
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(result.anchorU, result.anchorV)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                    anchor = Offset(0.5f, 1.0f)
                )
            }
        } catch (e: Throwable) {
            Log.e("GoogleMapBusIconHelper", "Error generating merged icon for $displayName", e)
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                anchor = Offset(0.5f, 1.0f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateCompactDotIcon(
        context: Context,
        colorInt: Int
    ): CachedMarkerIcon {
        val cleanKey = "COMPACT_DOT_$colorInt"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val drawable = getCompactDotIcon(context, colorInt)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(0.5f, 0.5f)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(),
                    anchor = Offset(0.5f, 0.5f)
                )
            }
        } catch (e: Throwable) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(),
                anchor = Offset(0.5f, 0.5f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateMergedDotIcon(context: Context): CachedMarkerIcon {
        val cleanKey = "MERGED_DOT_ICON"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val drawable = getMergedDotMarkerIcon(context)
            val bitmap = drawable.bitmap
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset(0.5f, 0.5f)
            )
        } catch (e: Throwable) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                anchor = Offset(0.5f, 0.5f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun getOrCreateClusterIcon(
        context: Context,
        count: Int,
        colorInt: Int
    ): CachedMarkerIcon {
        val cleanKey = "CLUSTER_${count}_$colorInt"
        val cached = busCache.get(cleanKey)
        if (cached != null) return cached

        val icon = try {
            val drawable = com.example.ui.map.components.getClusterIcon(context, count, colorInt)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(0.5f, 0.5f)
                )
            } else {
                CachedMarkerIcon(
                    descriptor = BitmapDescriptorFactory.defaultMarker(),
                    anchor = Offset(0.5f, 0.5f)
                )
            }
        } catch (_: Throwable) {
            CachedMarkerIcon(
                descriptor = BitmapDescriptorFactory.defaultMarker(),
                anchor = Offset(0.5f, 0.5f)
            )
        }

        busCache.put(cleanKey, icon)
        return icon
    }

    fun clear() {
        busCache.evictAll()
    }
}
