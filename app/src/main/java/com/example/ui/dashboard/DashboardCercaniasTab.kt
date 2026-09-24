package com.example.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.cercanias.CercaniasScreen
import com.example.ui.cercanias.CercaniasViewModel

@Composable
fun DashboardCercaniasTab(
    cercaniasViewModel: CercaniasViewModel,
    isDarkMode: Boolean,
    initialPage: Int,
    activeTripBottomPadding: Dp,
    onBackClick: (() -> Unit)?,
    onBackGesture: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    CercaniasScreen(
        viewModel = cercaniasViewModel,
        isDarkMode = isDarkMode,
        initialPage = initialPage,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        activeTripBottomPadding = activeTripBottomPadding,
        onBackClick = onBackClick,
        onBackGesture = onBackGesture
    )
}
