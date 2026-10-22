package com.example.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.metro.MetroScreen
import com.example.ui.metro.MetroViewModel

@Composable
fun DashboardMetroTab(
    metroViewModel: MetroViewModel,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    initialPage: Int,
    activeTripBottomPadding: Dp,
    onBackClick: (() -> Unit)?,
    onBackGesture: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    MetroScreen(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        appLanguage = appLanguage,
        metroViewModel = metroViewModel,
        isDarkMode = isDarkMode,
        initialPage = initialPage,
        activeTripBottomPadding = activeTripBottomPadding,
        onBackClick = onBackClick,
        onBackGesture = onBackGesture
    )
}
