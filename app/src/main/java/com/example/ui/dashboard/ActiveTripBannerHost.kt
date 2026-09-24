package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.trip.UnifiedActiveTripSnapshot
import com.example.data.repository.ActiveTripState
import com.example.ui.routing.components.ActiveTripOverlay
import com.example.util.RealTimeTripStatus

@Composable
fun ActiveTripBannerHost(
    activeTrip: ActiveTripState?,
    isPlannerVisible: Boolean,
    isViewingActiveRouteOnMap: Boolean,
    appLanguage: AppLanguage,
    realTimeTripStatus: RealTimeTripStatus,
    isRecalculatingTransfer: Boolean,
    recalculateError: String?,
    onExpandDetails: () -> Unit,
    onCancelTrip: () -> Unit,
    onAdvanceLeg: (Int) -> Unit,
    onRecalculateTransfer: () -> Unit,
    onDismissRecalculateError: () -> Unit,
    onOpenRouteOnMap: () -> Unit,
    onHeightChanged: (Dp) -> Unit,
    unifiedTripSnapshot: UnifiedActiveTripSnapshot? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeTrip != null && !isPlannerVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
            .widthIn(max = 600.dp)
            .padding(bottom = 8.dp)
    ) {
        activeTrip?.let { currentActiveTrip ->
            ActiveTripOverlay(
                activeTrip = currentActiveTrip,
                onExpandDetails = onExpandDetails,
                onCancelTrip = onCancelTrip,
                onAdvanceLeg = onAdvanceLeg,
                onRecalculateTransfer = onRecalculateTransfer,
                isRecalculating = isRecalculatingTransfer,
                recalculateError = recalculateError,
                onDismissRecalculateError = onDismissRecalculateError,
                realTimeStatus = realTimeTripStatus,
                appLanguage = appLanguage,
                isNavigationMapView = isViewingActiveRouteOnMap,
                onOpenRouteOnMap = onOpenRouteOnMap,
                onHeightChanged = onHeightChanged,
                unifiedSnapshot = unifiedTripSnapshot
            )
        }
    }
}
