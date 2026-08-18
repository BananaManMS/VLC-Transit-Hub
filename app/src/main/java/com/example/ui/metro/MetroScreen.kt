package com.example.ui.metro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import com.example.ui.theme.ScreenHeader
import com.example.ui.theme.UnifiedTabRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroScreen(
    modifier: Modifier = Modifier,
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    initialPage: Int = 0,
    onBackClick: (() -> Unit)? = null,
    onBackGesture: (() -> Unit)? = null
) {
    val backHandlerAction = onBackClick ?: onBackGesture
    if (backHandlerAction != null) {
        BackHandler(enabled = true) {
            backHandlerAction()
        }
    }
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val favoriteStations by metroViewModel.sortedFavoriteStations.collectAsState()
    val selectedStationId by metroViewModel.realTimeSelectedStationId.collectAsState()
    val departures by metroViewModel.realTimeDepartures.collectAsState()
    val isLoading by metroViewModel.realTimeLoading.collectAsState()
    val error by metroViewModel.realTimeError.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, metroViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                metroViewModel.startRealTimeRefreshTicker()
            } else if (event == Lifecycle.Event.ON_STOP) {
                metroViewModel.stopRealTimeRefreshTicker()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            metroViewModel.stopRealTimeRefreshTicker()
        }
    }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("metro_screen_container")
    ) {
        ScreenHeader(
            title = texts.headerMetroTitle,
            subtitle = texts.headerMetroSubtitle,
            onBackClick = onBackClick
        )

        UnifiedTabRow(
            selectedTabIndex = pagerState.currentPage,
            tabs = if (appLanguage == AppLanguage.CA) listOf("Eixides", "Avisos", "Targetes") else listOf("Salidas", "Avisos", "Tarjetas"),
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    MetroDeparturesTab(
                        appLanguage = appLanguage,
                        metroViewModel = metroViewModel,
                        favoriteStations = favoriteStations,
                        selectedStationId = selectedStationId ?: "15",
                        departures = departures,
                        isLoading = isLoading,
                        error = error,
                        isDarkMode = isDarkMode
                    )
                }
                1 -> {
                    AvisosTab(
                        appLanguage = appLanguage,
                        metroViewModel = metroViewModel,
                        isDarkMode = isDarkMode,
                        onNavigateToMetroStation = { stationId ->
                            metroViewModel.selectRealTimeStation(stationId)
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        }
                    )
                }
                2 -> {
                    TarjetasTab(
                        appLanguage = appLanguage,
                        metroViewModel = metroViewModel,
                        isDarkMode = isDarkMode
                    )
                }
            }
        }
    }
}
