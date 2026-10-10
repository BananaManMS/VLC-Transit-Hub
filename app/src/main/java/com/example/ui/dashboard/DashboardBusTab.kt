package com.example.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.bus.BusViewModel
import com.example.ui.bus.EmtBusScreen
import com.example.ui.metro.MetroViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DashboardBusTab(
    viewModel: DashboardViewModel,
    metroViewModel: MetroViewModel,
    busViewModel: BusViewModel = viewModel(),
    isDarkMode: Boolean,
    activeTripBottomPadding: Dp,
    initialPage: Int = 0,
    modifier: Modifier = Modifier
) {
    EmtBusScreen(
        viewModel = viewModel,
        busViewModel = busViewModel,
        metroViewModel = metroViewModel,
        initialPage = initialPage,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        isDarkMode = isDarkMode,
        activeTripBottomPadding = activeTripBottomPadding
    )
}
