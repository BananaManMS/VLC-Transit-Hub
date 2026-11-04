package com.example.ui.map.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.map.RecentSearch
import com.example.ui.map.SelectedMapItem
import com.example.ui.map.components.CustomPlaceType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
internal fun GoogleMapPlacesLayer(
    destinationLocation: org.osmdroid.util.GeoPoint?,
    destinationTitle: String?,
    homeLocation: RecentSearch?,
    workLocation: RecentSearch?,
    customFavorites: List<RecentSearch>,
    isBusActive: Boolean,
    isMetroActive: Boolean,
    isCercaniasActive: Boolean,
    isDarkMode: Boolean,
    settledZoom: Float,
    context: Context,
    onItemClick: ((GoogleMapTransitItem) -> Unit)?
) {
    val isDimmedCustom = isBusActive || isMetroActive || isCercaniasActive
    val customAlpha = if (isDimmedCustom) 0.15f else 1.0f
    val customZIndex = if (isDimmedCustom) 2f else 15f

    destinationLocation?.let { dest ->
        key("DESTINATION_PIN_${dest.latitude}_${dest.longitude}") {
            val destIcon = remember(isDarkMode) {
                GoogleMapsMarkerCache.getOrCreateDestinationMarkerIcon(context, isDarkMode)
            }
            Marker(
                state = MarkerState(position = LatLng(dest.latitude, dest.longitude)),
                icon = destIcon.descriptor,
                anchor = destIcon.anchor,
                title = destinationTitle ?: "Destino",
                zIndex = 30f
            )
        }
    }

    if (homeLocation != null && homeLocation.showOnMap) {
        key("HOME_PIN_${homeLocation.latitude}_${homeLocation.longitude}") {
            val homeIcon = remember(isDarkMode, settledZoom) {
                GoogleMapsMarkerCache.getOrCreateCustomPlaceMarkerIcon(context, CustomPlaceType.HOME, settledZoom.toDouble(), isDarkMode, homeLocation.colorHex)
            }
            Marker(
                state = MarkerState(position = LatLng(homeLocation.latitude, homeLocation.longitude)),
                icon = homeIcon.descriptor,
                anchor = homeIcon.anchor,
                alpha = customAlpha,
                title = homeLocation.title.ifBlank { "Casa" },
                snippet = homeLocation.subtitle.ifBlank { null },
                zIndex = customZIndex,
                onClick = {
                    val addrItem = SelectedMapItem.Address(
                        NominatimResult(
                            display_name = if (homeLocation.subtitle.isNotEmpty() && !homeLocation.subtitle.equals(homeLocation.title, ignoreCase = true)) "${homeLocation.title}, ${homeLocation.subtitle}" else homeLocation.title,
                            lat = homeLocation.latitude.toString(),
                            lon = homeLocation.longitude.toString(),
                            type = "home",
                            category = "favorite",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null,
                            placeCategory = PlaceCategory.FAVORITE,
                            placeName = homeLocation.title,
                            colorHex = homeLocation.colorHex,
                            road = homeLocation.road,
                            houseNumber = homeLocation.houseNumber,
                            suburb = homeLocation.suburb,
                            city = homeLocation.city,
                            postcode = homeLocation.postcode
                        )
                    )
                    onItemClick?.invoke(GoogleMapTransitItem.CustomPlace(addrItem))
                    true
                }
            )
        }
    }

    if (workLocation != null && workLocation.showOnMap) {
        key("WORK_PIN_${workLocation.latitude}_${workLocation.longitude}") {
            val workIcon = remember(isDarkMode, settledZoom) {
                GoogleMapsMarkerCache.getOrCreateCustomPlaceMarkerIcon(context, CustomPlaceType.WORK, settledZoom.toDouble(), isDarkMode, workLocation.colorHex)
            }
            Marker(
                state = MarkerState(position = LatLng(workLocation.latitude, workLocation.longitude)),
                icon = workIcon.descriptor,
                anchor = workIcon.anchor,
                alpha = customAlpha,
                title = workLocation.title.ifBlank { "Trabajo" },
                snippet = workLocation.subtitle.ifBlank { null },
                zIndex = customZIndex,
                onClick = {
                    val addrItem = SelectedMapItem.Address(
                        NominatimResult(
                            display_name = if (workLocation.subtitle.isNotEmpty() && !workLocation.subtitle.equals(workLocation.title, ignoreCase = true)) "${workLocation.title}, ${workLocation.subtitle}" else workLocation.title,
                            lat = workLocation.latitude.toString(),
                            lon = workLocation.longitude.toString(),
                            type = "work",
                            category = "favorite",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null,
                            placeCategory = PlaceCategory.FAVORITE,
                            placeName = workLocation.title,
                            colorHex = workLocation.colorHex,
                            road = workLocation.road,
                            houseNumber = workLocation.houseNumber,
                            suburb = workLocation.suburb,
                            city = workLocation.city,
                            postcode = workLocation.postcode
                        )
                    )
                    onItemClick?.invoke(GoogleMapTransitItem.CustomPlace(addrItem))
                    true
                }
            )
        }
    }

    customFavorites.filter { it.showOnMap }.forEach { fav ->
        key("CUSTOM_FAV_${fav.id}_${fav.latitude}_${fav.longitude}") {
            val favIcon = remember(isDarkMode, settledZoom, fav.colorHex) {
                GoogleMapsMarkerCache.getOrCreateCustomPlaceMarkerIcon(context, CustomPlaceType.FAVORITE, settledZoom.toDouble(), isDarkMode, fav.colorHex)
            }
            Marker(
                state = MarkerState(position = LatLng(fav.latitude, fav.longitude)),
                icon = favIcon.descriptor,
                anchor = favIcon.anchor,
                alpha = customAlpha,
                title = fav.title,
                snippet = fav.subtitle.ifBlank { null },
                zIndex = customZIndex,
                onClick = {
                    val addrItem = SelectedMapItem.Address(
                        NominatimResult(
                            display_name = if (fav.subtitle.isNotEmpty() && !fav.subtitle.equals(fav.title, ignoreCase = true)) "${fav.title}, ${fav.subtitle}" else fav.title,
                            lat = fav.latitude.toString(),
                            lon = fav.longitude.toString(),
                            type = "favorite",
                            category = "favorite",
                            isLocalStop = false,
                            stopId = null,
                            stopType = null,
                            placeCategory = PlaceCategory.FAVORITE,
                            placeName = fav.title,
                            colorHex = fav.colorHex,
                            road = fav.road,
                            houseNumber = fav.houseNumber,
                            suburb = fav.suburb,
                            city = fav.city,
                            postcode = fav.postcode
                        )
                    )
                    onItemClick?.invoke(GoogleMapTransitItem.CustomPlace(addrItem))
                    true
                }
            )
        }
    }
}
