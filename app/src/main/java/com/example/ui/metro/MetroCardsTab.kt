package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import com.example.ui.metro.cards.AddTransitCardWizardDialog
import com.example.ui.metro.cards.CardDisplayFormat
import com.example.ui.metro.cards.TransitCardAlert
import com.example.ui.metro.cards.TransitCardAlertManager
import com.example.ui.metro.cards.TransitCardAlertPopup
import com.example.ui.metro.cards.UnifiedTransitCardView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.theme.appCardBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetasTab(
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    activeTripBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val cards by metroViewModel.transitCardsFlow.collectAsState()
    val isRefreshingCards by metroViewModel.isRefreshingCards.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showManageCardsDialog by remember { mutableStateOf(false) }
    var selectedDetailCard by remember { mutableStateOf<TransitCardUiModel?>(null) }
    var pendingAlerts by remember { mutableStateOf<List<TransitCardAlert>>(emptyList()) }

    LaunchedEffect(Unit) {
        metroViewModel.autoRefreshCardsIfNeeded()
    }

    LaunchedEffect(cards) {
        if (cards.isNotEmpty()) {
            val alerts = TransitCardAlertManager.getAlertsForPopup(context, cards, appLanguage)
            if (alerts.isNotEmpty()) {
                pendingAlerts = alerts
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshingCards,
        onRefresh = { metroViewModel.refreshTransitCards() },
        modifier = Modifier.fillMaxSize().testTag("tarjetas_pull_to_refresh")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (cards.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = texts.noCardsSaved,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = texts.cardsSavedDesc,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(texts.addCardLabel)
                    }
                }
            } else {
                val finalSortedCards = remember(cards) {
                    cards.sortedWith(
                        compareBy<TransitCardUiModel> { it.customOrder }
                            .thenBy { try { CardCategory.valueOf(it.category).orderIndex } catch (_: Exception) { 99 } }
                            .thenBy { it.cardNumber }
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + activeTripBottomPadding),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                onClick = { showAddDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .testTag("add_card_button"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF232633) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = texts.addCardLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Card(
                                onClick = { showManageCardsDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .testTag("manage_cards_button"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkMode) Color(0xFF232633) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.R.string.cards_organize_btn),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(finalSortedCards.size, key = { index -> finalSortedCards[index].cardNumber }) { index ->
                        val card = finalSortedCards[index]
                        UnifiedTransitCardView(
                            card = card,
                            appLanguage = appLanguage,
                            format = CardDisplayFormat.LIST,
                            onClick = { selectedDetailCard = card },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_item_${card.cardNumber}")
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTransitCardWizardDialog(
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            onDismiss = { showAddDialog = false },
            onCardAdded = { newCard ->
                selectedDetailCard = newCard
            }
        )
    }

    if (selectedDetailCard != null) {
        CardDetailDialog(
            card = selectedDetailCard!!,
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            isDarkMode = isDarkMode,
            onDismiss = { selectedDetailCard = null }
        )
    }

    if (pendingAlerts.isNotEmpty()) {
        TransitCardAlertPopup(
            alerts = pendingAlerts,
            appLanguage = appLanguage,
            onDismiss = { pendingAlerts = emptyList() },
            onSelectCard = { cardNumber ->
                val target = cards.find { it.cardNumber == cardNumber }
                if (target != null) {
                    selectedDetailCard = target
                }
            }
        )
    }

    if (showManageCardsDialog) {
        ManageCardsDialog(
            cards = cards,
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            isDarkMode = isDarkMode,
            onDismiss = { showManageCardsDialog = false }
        )
    }
}

@Composable
fun ManageCardsDialog(
    cards: List<TransitCardUiModel>,
    appLanguage: AppLanguage,
    metroViewModel: MetroViewModel,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    // Remember cardList ONLY ONCE when dialog opens to prevent recomposition glitches during drag
    val cardList = remember {
        cards.sortedWith(
            compareBy<TransitCardUiModel> { it.customOrder }
                .thenBy { try { CardCategory.valueOf(it.category).orderIndex } catch (_: Exception) { 99 } }
                .thenBy { it.cardNumber }
        ).toMutableStateList()
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 68.dp.toPx() }

    AlertDialog(
        onDismissRequest = {
            metroViewModel.updateCardsOrder(cardList.map { it.cardNumber })
            onDismiss()
        },
        containerColor = if (isDarkMode) Color(0xFF0F172A) else Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cards_organize_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isDarkMode) Color.White else Color.Black
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.cards_organize_instruction),
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp), // Continuous list row styling
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    itemsIndexed(cardList, key = { _, c -> c.cardNumber }) { index, card ->
                        val isBeingDragged = draggingIndex == index

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isBeingDragged) 10f else 0f)
                                .graphicsLayer {
                                    if (isBeingDragged) {
                                        translationY = dragOffsetY
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .background(
                                    color = if (isBeingDragged) {
                                        if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Drag handle icon
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.drag_reorder_desc),
                                    tint = if (isBeingDragged) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                                    },
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(24.dp)
                                        .pointerInput(cardList) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingIndex = index
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    val currIdx = draggingIndex ?: return@detectDragGestures
                                                    val threshold = itemHeightPx * 0.5f
                                                    if (dragOffsetY > threshold && currIdx < cardList.size - 1) {
                                                        val item = cardList.removeAt(currIdx)
                                                        cardList.add(currIdx + 1, item)
                                                        draggingIndex = currIdx + 1
                                                        dragOffsetY -= itemHeightPx
                                                    } else if (dragOffsetY < -threshold && currIdx > 0) {
                                                        val item = cardList.removeAt(currIdx)
                                                        cardList.add(currIdx - 1, item)
                                                        draggingIndex = currIdx - 1
                                                        dragOffsetY += itemHeightPx
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingIndex = null
                                                    dragOffsetY = 0f
                                                    metroViewModel.updateCardsOrder(cardList.map { it.cardNumber })
                                                },
                                                onDragCancel = {
                                                    draggingIndex = null
                                                    dragOffsetY = 0f
                                                    metroViewModel.updateCardsOrder(cardList.map { it.cardNumber })
                                                }
                                            )
                                        }
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.assignedName.ifBlank { card.title },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (card.assignedName.isNotBlank()) "${card.title} • ${card.remainingValue}" else card.remainingValue,
                                        fontSize = 11.sp,
                                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {

                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.example.R.string.cards_show_on_home_label),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                    Switch(
                                        checked = card.showOnHome,
                                        onCheckedChange = { isChecked ->
                                            cardList[index] = card.copy(showOnHome = isChecked)
                                            metroViewModel.updateCardHomeVisibility(card.cardNumber, isChecked)
                                        },
                                        modifier = Modifier
                                            .testTag("manage_home_switch_${card.cardNumber}")
                                            .graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
                                    )
                                }
                            }

                            // iOS-style fine horizontal divider under the item (unless it's being dragged or the last item)
                            if (!isBeingDragged && index < cardList.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(
                                            if (isDarkMode) Color(0xFF334155).copy(alpha = 0.4f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
                                        )
                                        .padding(start = 36.dp) // Offsets to align perfectly with the title text
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    metroViewModel.updateCardsOrder(cardList.map { it.cardNumber })
                    onDismiss()
                }
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.btn_done),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
