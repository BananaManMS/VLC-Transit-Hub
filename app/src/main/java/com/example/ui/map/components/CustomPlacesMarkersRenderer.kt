package com.example.ui.map.components

import android.content.Context
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Pure rendering delegate for Custom Places (Home, Work, Custom Favorites)
 * with zoom-level dependent styling (Pin, Badge, Dot) and marker recycling.
 */
object CustomPlacesMarkersRenderer {

    fun renderCustomPlaces(
        context: Context,
        mapView: MapView,
        homeLocation: RecentSearch?,
        workLocation: RecentSearch?,
        customFavorites: List<RecentSearch>,
        currentZoom: Double,
        isDarkMode: Boolean,
        recycledCustomFavoriteMarkers: MutableList<Marker>,
        onSelectItem: ((SelectedMapItem) -> Unit)? = null,
        onTapHandler: ((Context, MapView, GeoPoint) -> Boolean)? = null
    ): Int {
        val allCustomPlaces = mutableListOf<Pair<RecentSearch, CustomPlaceType>>()
        homeLocation?.let { home ->
            allCustomPlaces.add(Pair(home, CustomPlaceType.HOME))
        }
        workLocation?.let { work ->
            allCustomPlaces.add(Pair(work, CustomPlaceType.WORK))
        }
        customFavorites.forEach { fav ->
            if (!fav.showOnMap) return@forEach
            val placeType = when {
                fav.title.equals("Casa", ignoreCase = true) || fav.type.equals("home", ignoreCase = true) -> CustomPlaceType.HOME
                fav.title.equals("Trabajo", ignoreCase = true) || fav.type.equals("work", ignoreCase = true) -> CustomPlaceType.WORK
                else -> CustomPlaceType.FAVORITE
            }
            val alreadyAdded = allCustomPlaces.any { it.first.latitude == fav.latitude && it.first.longitude == fav.longitude }
            if (!alreadyAdded) {
                allCustomPlaces.add(Pair(fav, placeType))
            }
        }

        var activeCustomFavCount = 0
        allCustomPlaces.forEach { (place, placeType) ->
            val marker = if (activeCustomFavCount < recycledCustomFavoriteMarkers.size) {
                recycledCustomFavoriteMarkers[activeCustomFavCount]
            } else {
                Marker(mapView).also {
                    recycledCustomFavoriteMarkers.add(it)
                }
            }
            activeCustomFavCount++

            marker.infoWindow = null
            marker.closeInfoWindow()
            marker.position = GeoPoint(place.latitude, place.longitude)

            val iconResult = getCustomPlaceMarkerIcon(context, placeType, currentZoom, isDarkMode, place.colorHex)
            marker.icon = iconResult.drawable
            marker.setAnchor(iconResult.anchorU, iconResult.anchorV)

            marker.title = place.title
            marker.snippet = place.subtitle
            marker.isEnabled = true
            marker.setVisible(true)

            marker.setOnMarkerClickListener { m, _ ->
                if (onTapHandler != null) {
                    onTapHandler(context, mapView, m.position)
                } else {
                    val resolvedType = when (placeType) {
                        CustomPlaceType.HOME -> "home"
                        CustomPlaceType.WORK -> "work"
                        CustomPlaceType.FAVORITE -> "favorite"
                    }
                    val addrItem = SelectedMapItem.Address(
                        NominatimResult(
                            display_name = if (place.subtitle.isNotEmpty() && !place.subtitle.equals(place.title, ignoreCase = true)) place.title + ", " + place.subtitle else place.title,
                            lat = m.position.latitude.toString(),
                            lon = m.position.longitude.toString(),
                            type = resolvedType,
                            category = "favorite",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null,
                            placeCategory = PlaceCategory.FAVORITE,
                            placeName = place.title,
                            colorHex = place.colorHex,
                            road = place.road,
                            houseNumber = place.houseNumber,
                            suburb = place.suburb,
                            city = place.city,
                            postcode = place.postcode
                        )
                    )
                    onSelectItem?.invoke(addrItem)
                }
                true
            }
        }

        // Hide unused recycled custom favorite markers
        for (i in activeCustomFavCount until recycledCustomFavoriteMarkers.size) {
            recycledCustomFavoriteMarkers[i].apply {
                setVisible(false)
                isEnabled = false
                setOnMarkerClickListener(null)
            }
        }

        return activeCustomFavCount
    }
}
