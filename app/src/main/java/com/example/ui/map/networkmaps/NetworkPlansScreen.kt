package com.example.ui.map.networkmaps

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch

@Composable
fun NetworkPlansScreen(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val mapsManager = remember { NetworkMapsManager(context) }
    val isSyncing by mapsManager.isSyncing.collectAsState()
    val syncProgress by mapsManager.syncProgress.collectAsState()
    val statusText by mapsManager.statusText.collectAsState()
    val errorMessage by mapsManager.errorMessage.collectAsState()
    val mapsStatus by mapsManager.mapsStatus.collectAsState()

    var selectedMapToView by remember { mutableStateOf<MapPlanItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        mapsManager.checkAndSyncMaps(forceRefresh = false)
    }

    BackHandler(onBack = {
        if (selectedMapToView != null) {
            selectedMapToView = null
        } else {
            onBack()
        }
    })

    // Pairs for 2-column layout
    val twoColumnPairs = remember {
        listOf(
            InterchangeMapId.ANGEL_GUIMERA to InterchangeMapId.ALAMEDA,
            InterchangeMapId.BENIMACLET to InterchangeMapId.EMPALME,
            InterchangeMapId.MARITIM to InterchangeMapId.VALENCIA_SANT_ISIDRE
        )
    }

    val onOpenPlan: (MapPlanItem) -> Unit = { item ->
        val fileStatus = mapsStatus[item.id]
        if (fileStatus?.exists == true) {
            selectedMapToView = item
        } else {
            if (isSyncing) {
                Toast.makeText(
                    context,
                    context.getString(com.example.R.string.plans_downloading_single),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                coroutineScope.launch {
                    mapsManager.forceRefreshAll()
                }
            }
        }
    }

    // Clean background matching Inicio
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkMode) Color(0xFF171717) else Color(0xFFFAFAFA)
    ) {
        AnimatedContent(
            targetState = selectedMapToView,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))) togetherWith (
                        slideOutHorizontally(
                            targetOffsetX = { -it / 4 },
                            animationSpec = tween(280, easing = FastOutLinearInEasing)
                        ) + fadeOut(tween(180))
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))) togetherWith (
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(260, easing = FastOutLinearInEasing)
                        ) + fadeOut(tween(180))
                    )
                }
            },
            label = "network_map_transition"
        ) { currentMapItem ->
            if (currentMapItem != null) {
                val currentFile = mapsManager.getMapFile(currentMapItem)
                NetworkPdfViewerScreen(
                    mapItem = currentMapItem,
                    file = currentFile,
                    appLanguage = appLanguage,
                    isDarkMode = isDarkMode,
                    onBack = { selectedMapToView = null }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Full-width top pill header spanning left to right edge with rounded bottom corners
                    NetworkPlansTopPillHeader(
                        appLanguage = appLanguage,
                        isDarkMode = isDarkMode,
                        isSyncing = isSyncing,
                        onBack = onBack,
                        onRefresh = {
                            coroutineScope.launch {
                                mapsManager.forceRefreshAll()
                            }
                        }
                    )

                    // 2. Scrollable list of compact cards underneath
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Downloading / Syncing Progress Banner
                        if (isSyncing) {
                            item(key = "sync_progress") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDarkMode) Color(0xFF222222) else Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = if (statusText.isNotBlank()) statusText else androidx.compose.ui.res.stringResource(com.example.R.string.plans_downloading),
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = "${(syncProgress * 100).toInt()}%",
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { syncProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        } else if (errorMessage != null) {
                            item(key = "sync_error") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(com.example.R.string.plans_sync_error_title),
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = errorMessage ?: "",
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontSize = 11.5.sp,
                                                color = if (isDarkMode) Color(0xFFA3A3A3) else Color(0xFF64748B)
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    mapsManager.forceRefreshAll()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_retry),
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION 1: Network Maps (General network PDF maps)
                        items(NetworkMapId.values(), key = { it.id }) { mapItem ->
                            val fileStatus = mapsStatus[mapItem.id]
                            NetworkMapCard(
                                mapItem = mapItem,
                                fileStatus = fileStatus,
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                onClick = { onOpenPlan(mapItem) }
                            )
                        }

                        // SECTION 2 Header: Intercambiadores por albertguillaumes.cat
                        item(key = "section_interchanges_header") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        try {
                                            uriHandler.openUri("https://albertguillaumes.cat")
                                        } catch (_: Exception) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://albertguillaumes.cat")).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.plans_interchanges_by),
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "albertguillaumes.cat",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Obrir enllaç",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // 1. Single full-width chip: Xàtiva · Bailén · Alacant
                        item(key = InterchangeMapId.XATIVA_BAILEN_ALACANT.id) {
                            InterchangeMapChip(
                                mapItem = InterchangeMapId.XATIVA_BAILEN_ALACANT,
                                fileStatus = mapsStatus[InterchangeMapId.XATIVA_BAILEN_ALACANT.id],
                                isDarkMode = isDarkMode,
                                appLanguage = appLanguage,
                                onClick = { onOpenPlan(InterchangeMapId.XATIVA_BAILEN_ALACANT) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 2. Remaining 6 interchanges in 2-column chip pairs
                        items(twoColumnPairs, key = { "${it.first.id}_${it.second.id}" }) { (leftItem, rightItem) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                InterchangeMapChip(
                                    mapItem = leftItem,
                                    fileStatus = mapsStatus[leftItem.id],
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onClick = { onOpenPlan(leftItem) },
                                    modifier = Modifier.weight(1f)
                                )
                                InterchangeMapChip(
                                    mapItem = rightItem,
                                    fileStatus = mapsStatus[rightItem.id],
                                    isDarkMode = isDarkMode,
                                    appLanguage = appLanguage,
                                    onClick = { onOpenPlan(rightItem) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
