package com.example.ui.map

import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import java.io.File

object MapConfig {
    const val DEFAULT_LATITUDE = 39.4699
    const val DEFAULT_LONGITUDE = -0.3763
    const val DEFAULT_ZOOM = 12.5
    const val MIN_ZOOM = 9.0
    const val MAX_ZOOM = 19.0

    val VALENCIA_CENTER = GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)

    // Limits for Comunitat Valenciana
    val VALENCIA_BOUNDS = BoundingBox(40.85, 0.85, 37.80, -1.65)

    // Viewbox for Valencia Province Transit Area (West, North, East, South) for Nominatim
    // Covers Metrovalencia, MetroBus, and Renfe Cercanías network (Utiel/Requena, Xàtiva, Gandia, Sagunto)
    const val VALENCIA_PROVINCE_VIEWBOX = "-1.40,40.15,0.30,38.70"

    private const val CARTO_API_KEY = "cb1_25ac_1_a7acaac60b8d4556306ddcb2"

    // CartoDB Voyager tile source (higher contrast, detailed streets and parks)
    val CARTO_LIGHT_SOURCE = XYTileSource(
        "CartoDB_Voyager_v2",
        0, 19, 256, ".png?key=$CARTO_API_KEY",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    )

    val CARTO_DARK_SOURCE = XYTileSource(
        "CartoDB_DarkMatter_v2",
        0, 19, 256, ".png?key=$CARTO_API_KEY",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
            "https://d.basemaps.cartocdn.com/dark_all/"
        )
    )

    // High-resolution ESRI World Imagery satellite tile source
    val ESRI_SATELLITE_SOURCE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery_v1",
        0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return "$baseUrl${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}${imageFilenameEnding()}"
        }
    }

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        val config = Configuration.getInstance()
        config.load(context, prefs)
        config.userAgentValue = com.example.data.network.NetworkModule.USER_AGENT

        // Purge old unauthenticated tile cache if not cleared yet
        if (!prefs.getBoolean("carto_v2_cache_cleared", false)) {
            clearMapCache(context)
            prefs.edit().putBoolean("carto_v2_cache_cleared", true).apply()
        }

        // Optimize disk cache size (200 MB) to prevent excessive storage use
        config.tileFileSystemCacheMaxBytes = 200L * 1024L * 1024L
        config.tileFileSystemCacheTrimBytes = 150L * 1024L * 1024L

        // Increase RAM tile memory cache so off-screen tiles stay loaded when panning
        config.cacheMapTileCount = 1000.toShort()

        // Pre-fetch extra tile rings (8 tiles in every direction) beyond visible screen bounds to eliminate gray gaps
        config.cacheMapTileOvershoot = 8.toShort()

        // Multi-threaded parallel tile fetching and disk cache reading
        config.tileDownloadThreads = 16.toShort()
        config.tileDownloadMaxQueueSize = 300.toShort()
        config.tileFileSystemThreads = 16.toShort()
        config.tileFileSystemMaxQueueSize = 300.toShort()
    }

    fun clearMapCache(context: Context) {
        try {
            val osmdroidBasePath = File(context.cacheDir, "osmdroid")
            if (osmdroidBasePath.exists()) {
                val oldLightDir = File(osmdroidBasePath, "CartoDB_Voyager")
                if (oldLightDir.exists()) oldLightDir.deleteRecursively()
                val oldDarkDir = File(osmdroidBasePath, "CartoDB_DarkMatter")
                if (oldDarkDir.exists()) oldDarkDir.deleteRecursively()
                val tilesDir = File(osmdroidBasePath, "tiles")
                if (tilesDir.exists()) tilesDir.deleteRecursively()
                osmdroidBasePath.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w("MapConfig", "Failed to delete osmdroid disk cache: ${e.message}")
        }
        try {
            SqlTileWriter().purgeCache()
        } catch (e: Exception) {
            Log.w("MapConfig", "Failed to purge SqlTileWriter cache: ${e.message}")
        }
    }
}

