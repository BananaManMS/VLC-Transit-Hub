package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.dashboard.AppLanguage
import kotlinx.coroutines.delay

/**
 * Ensures Material 3 PullToRefreshBox always performs a clean false -> true -> false
 * transition when the user releases a pull-to-refresh gesture, even if the repository
 * serves cached data instantaneously without flipping its loading StateFlow.
 */
@Composable
fun rememberPullRefreshFeedback(
    isLoading: Boolean,
    minFeedbackMs: Long = 450L,
    onRefresh: () -> Unit
): Pair<Boolean, () -> Unit> {
    var refreshTriggerId by remember { mutableIntStateOf(0) }
    var isSyntheticRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTriggerId) {
        if (refreshTriggerId > 0) {
            isSyntheticRefreshing = true
            delay(minFeedbackMs)
            isSyntheticRefreshing = false
        }
    }

    val effectiveRefreshing = isLoading || isSyntheticRefreshing
    val triggerRefresh: () -> Unit = {
        isSyntheticRefreshing = true
        refreshTriggerId++
        onRefresh()
    }
    return effectiveRefreshing to triggerRefresh
}

/**
 * Custom Material 3 Pull-to-Refresh indicator providing clear, human-readable context
 * about which transit station and operator is actively being updated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.TransitPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    stationName: String = "",
    operatorName: String = "",
    appLanguage: AppLanguage,
    isInitialLoad: Boolean = false,
    refreshingText: String? = null,
    modifier: Modifier = Modifier
) {
    val distance = state.distanceFraction
    val isVisible = !isInitialLoad && (isRefreshing || distance > 0.05f)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
            .zIndex(20f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.testTag("transit_pull_refresh_indicator")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val rotation = (distance.coerceIn(0f, 1f) * 180f)
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                val statusText = when {
                    isRefreshing -> {
                        when {
                            !refreshingText.isNullOrBlank() -> refreshingText
                            stationName.isNotBlank() && isInitialLoad -> when (appLanguage) {
                                AppLanguage.CA -> "Consultant eixides de $stationName..."
                                else -> "Consultando salidas de $stationName..."
                            }
                            stationName.isNotBlank() -> when (appLanguage) {
                                AppLanguage.CA -> "Actualitzant eixides de $stationName..."
                                else -> "Actualizando salidas de $stationName..."
                            }
                            else -> when (appLanguage) {
                                AppLanguage.CA -> "Actualitzant..."
                                else -> "Actualizando..."
                            }
                        }
                    }
                    distance >= 1f -> {
                        when (appLanguage) {
                            AppLanguage.CA -> "Solta per a actualitzar"
                            else -> "Suelta para actualizar"
                        }
                    }
                    else -> {
                        when (appLanguage) {
                            AppLanguage.CA -> "Llisca per a actualitzar"
                            else -> "Desliza para actualizar"
                        }
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }
        }
    }
}
