package com.example.ui.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    isDarkMode: Boolean
) {
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val cards by metroViewModel.transitCardsFlow.collectAsState()
    val isRefreshingCards by metroViewModel.isRefreshingCards.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDetailCard by remember { mutableStateOf<TransitCardUiModel?>(null) }

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
                    val sortedNormal = cards.filter { !it.isFaded }.sortedWith(compareBy({ CardCategory.valueOf(it.category).orderIndex }, { it.cardNumber }))
                    val sortedFaded = cards.filter { it.isFaded }.sortedWith(compareBy({ CardCategory.valueOf(it.category).orderIndex }, { it.cardNumber }))
                    sortedNormal + sortedFaded
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable { showAddDialog = true }
                                .testTag("add_card_button"),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
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
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = texts.addCardLabel,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    items(finalSortedCards.size, key = { index -> finalSortedCards[index].cardNumber }) { index ->
                        val card = finalSortedCards[index]
                        val category = CardCategory.valueOf(card.category)
                        val isFaded = card.isFaded
                        
                        val (bgColor, contentColor, badgeBgColor) = getCardColors(category = category, isFaded = isFaded, isDarkMode = isDarkMode)

                        Card(
                            border = appCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .clickable { selectedDetailCard = card }
                                .testTag("card_item_${card.cardNumber}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (category == CardCategory.TUIN || category == CardCategory.MOBILIS) {
                                                    Icons.Default.CreditCard
                                                } else {
                                                    Icons.Default.Subway
                                                },
                                                contentDescription = null,
                                                tint = contentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = card.assignedName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = contentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(6.dp))
                                        
                                        Text(
                                            text = if (isFaded) "${category.label} (Inactiva)" else category.label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .background(badgeBgColor, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column {
                                            Text(
                                                text = formatCardNumber(card.cardNumber),
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = contentColor.copy(alpha = 0.7f),
                                                letterSpacing = 1.sp
                                            )
                                        }
                                        
                                        Column(
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = card.remainingValue,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor
                                            )
                                            if (isFaded) {
                                                Text(
                                                    text = "Agotada/Inactiva",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = contentColor.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCardDialog(
            appLanguage = appLanguage,
            metroViewModel = metroViewModel,
            onDismiss = { showAddDialog = false }
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
}
