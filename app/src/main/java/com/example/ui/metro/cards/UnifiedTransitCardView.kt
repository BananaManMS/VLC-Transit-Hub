package com.example.ui.metro.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.metro.CardCategory
import com.example.ui.metro.formatCardNumber
import com.example.ui.theme.SpaceGroteskFontFamily

enum class CardDisplayFormat {
    HERO,     // Large card used in Onboarding & Detail Dialog
    LIST,     // Full width list item used in MetroCardsTab
    COMPACT   // Carousel item used in DashboardHomeTab
}

data class CardStyleDefinition(
    val backgroundColors: List<Color>,
    val badgeLabel: String,
    val badgeBgColor: Color,
    val badgeTextColor: Color,
    val sheenColor: Color
)

fun getStyleForCategory(category: CardCategory): CardStyleDefinition {
    return when (category) {
        CardCategory.SUMA_SENCILLO,
        CardCategory.SUMA_MENSUAL,
        CardCategory.SUMA_MENSUAL_JOVE,
        CardCategory.SUMA_TSERIES -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            badgeLabel = "SUMA",
            badgeBgColor = Color(0xFFEF4444),
            badgeTextColor = Color.White,
            sheenColor = Color(0x30FFFFFF)
        )
        CardCategory.MOBILIS -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF0A2540), Color(0xFF00529B), Color(0xFF0072CE)),
            badgeLabel = "Móbilis",
            badgeBgColor = Color(0xFF00A8E8),
            badgeTextColor = Color.White,
            sheenColor = Color(0x38E0F7FA)
        )
        CardCategory.TUIN,
        CardCategory.TUIN_JOVE -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF3D1200), Color(0xFFB33600), Color(0xFFFF5500)),
            badgeLabel = "TuiN",
            badgeBgColor = Color(0xFFFF6B00),
            badgeTextColor = Color.White,
            sheenColor = Color(0x38FFE0B2)
        )
        CardCategory.OTHER -> CardStyleDefinition(
            backgroundColors = listOf(Color(0xFF1C1600), Color(0xFF66520E), Color(0xFFA88B24)),
            badgeLabel = "Móbilis",
            badgeBgColor = Color(0xFFD4AF37),
            badgeTextColor = Color(0xFF1C1600),
            sheenColor = Color(0x30FFF8E1)
        )
    }
}

/**
 * 100% Static, ultra-high performance Card View (Zero sensors, Zero animation/CPU overhead).
 */
@Composable
fun UnifiedTransitCardView(
    card: TransitCardUiModel,
    appLanguage: AppLanguage = AppLanguage.ES,
    format: CardDisplayFormat = CardDisplayFormat.LIST,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val category = remember(card.category) {
        try {
            CardCategory.valueOf(card.category)
        } catch (_: Exception) {
            CardCategory.OTHER
        }
    }
    val style = remember(category) { getStyleForCategory(category) }
    val isFaded = card.isFaded

    val backgroundBrush = remember(style.backgroundColors, isFaded) {
        Brush.linearGradient(
            colors = if (isFaded) {
                listOf(Color(0xFF2B2D33), Color(0xFF383B42), Color(0xFF1F2126))
            } else {
                style.backgroundColors
            }
        )
    }

    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(cardShape)
                        .clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .border(
                width = 1.dp,
                color = if (isFaded) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.22f),
                shape = cardShape
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (format == CardDisplayFormat.HERO) 4.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when (format) {
                        CardDisplayFormat.HERO -> Modifier.height(165.dp)
                        CardDisplayFormat.LIST -> Modifier.height(115.dp)
                        CardDisplayFormat.COMPACT -> Modifier.width(185.dp).height(115.dp)
                    }
                )
                .background(backgroundBrush)
                .padding(if (format == CardDisplayFormat.HERO) 16.dp else 12.dp)
        ) {
            when (format) {
                CardDisplayFormat.HERO -> {
                    HeroCardContent(card = card, category = category, style = style, isFaded = isFaded)
                }
                CardDisplayFormat.LIST -> {
                    ListCardContent(card = card, category = category, style = style, isFaded = isFaded, appLanguage = appLanguage)
                }
                CardDisplayFormat.COMPACT -> {
                    CompactCardContent(card = card, category = category, style = style, isFaded = isFaded, appLanguage = appLanguage)
                }
            }
        }
    }
}

@Composable
private fun HeroCardContent(
    card: TransitCardUiModel,
    category: CardCategory,
    style: CardStyleDefinition,
    isFaded: Boolean
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isFaded) Color.Gray else style.badgeBgColor
                ) {
                    Text(
                        text = style.badgeLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = style.badgeTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.label,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFE5C158), Color(0xFFBE972C), Color(0xFFF3DF95))
                        )
                    )
                    .border(0.5.dp, Color(0xFF8B6B1B), RoundedCornerShape(4.dp))
            )

            Text(
                text = formatCardNumber(card.cardNumber),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 1.5.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.assignedName.ifBlank { card.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.zonas.isNotBlank()) {
                    Text(
                        text = card.zonas,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Text(
                text = if (isFaded) androidx.compose.ui.res.stringResource(com.example.R.string.card_status_inactive) else card.remainingValue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = if (isFaded) Color.White.copy(alpha = 0.5f) else Color.White
            )
        }
    }
}

@Composable
private fun ListCardContent(
    card: TransitCardUiModel,
    category: CardCategory,
    style: CardStyleDefinition,
    isFaded: Boolean,
    appLanguage: AppLanguage
) {
    val inactiveStr = androidx.compose.ui.res.stringResource(com.example.R.string.card_status_inactive)
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
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isFaded) Color.Gray else style.badgeBgColor
                ) {
                    Text(
                        text = style.badgeLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        color = style.badgeTextColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = card.assignedName.ifBlank { card.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Text(
                    text = if (isFaded) "${category.label} ($inactiveStr)" else category.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
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
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                if (card.zonas.isNotBlank()) {
                    Text(
                        text = card.zonas,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val cardAlert = remember(card, appLanguage) {
                    TransitCardAlertManager.evaluateCardAlert(card, appLanguage)
                }
                if (cardAlert != null && !isFaded) {
                    Text(
                        text = cardAlert.titleText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (cardAlert.isCritical) Color(0xFFFF8A80) else Color(0xFFFFD180),
                        modifier = Modifier
                            .background(
                                color = if (cardAlert.isCritical) Color(0xFF8C1D18) else Color(0xFF6B4700),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = if (isFaded) inactiveStr else card.remainingValue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isFaded) Color.White.copy(alpha = 0.5f) else Color.White
                )
            }
        }
    }
}

@Composable
private fun CompactCardContent(
    card: TransitCardUiModel,
    category: CardCategory,
    style: CardStyleDefinition,
    isFaded: Boolean,
    appLanguage: AppLanguage
) {
    val inactiveStr = androidx.compose.ui.res.stringResource(com.example.R.string.card_status_inactive)
    val checkBalanceStr = androidx.compose.ui.res.stringResource(com.example.R.string.cards_summary_check_balance)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isFaded) Color.Gray else style.badgeBgColor
            ) {
                Text(
                    text = style.badgeLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = style.badgeTextColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }

        Column {
            Text(
                text = card.assignedName.ifBlank { card.title },
                style = MaterialTheme.typography.titleSmall,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (isFaded) inactiveStr else card.remainingValue.ifBlank { checkBalanceStr },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFaded) Color.White.copy(alpha = 0.5f) else Color.White,
                maxLines = 1
            )

            if (card.zonas.isNotBlank()) {
                Text(
                    text = card.zonas,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}
