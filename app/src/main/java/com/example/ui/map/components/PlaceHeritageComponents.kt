package com.example.ui.map.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.WikipediaSummary
import com.example.data.repository.WikipediaRepository
import com.example.ui.dashboard.AppLanguage

@Composable
fun PlaceWikiHeroCard(
    wikipediaTag: String?,
    wikidataTag: String?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (wikipediaTag.isNullOrBlank() && wikidataTag.isNullOrBlank()) return

    val context = LocalContext.current
    val preferredLang = if (appLanguage == AppLanguage.CA) "ca" else "es"
    val isValencian = appLanguage == AppLanguage.CA

    var wikiSummary by remember(wikipediaTag, wikidataTag, preferredLang) {
        mutableStateOf<WikipediaSummary?>(null)
    }
    var isLoading by remember(wikipediaTag, wikidataTag, preferredLang) {
        mutableStateOf(true)
    }

    LaunchedEffect(wikipediaTag, wikidataTag, preferredLang) {
        isLoading = true
        wikiSummary = WikipediaRepository.getPlaceSummary(wikipediaTag, wikidataTag, preferredLang)
        isLoading = false
    }

    val summary = wikiSummary ?: return

    val imageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .okHttpClient(WikipediaRepository.okHttpClient)
            .crossfade(true)
            .build()
    }

    val cardBg = if (isDarkMode) Color(0xFF262626) else Color(0xFFF8FAFC)
    val borderCol = if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo from Wikimedia Commons (if available)
            if (!summary.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFE2E8F0))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(summary.imageUrl)
                            .setHeader("User-Agent", WikipediaRepository.USER_AGENT)
                            .crossfade(300)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = summary.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )

                    // Attribution pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Wikimedia Commons",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Text content & Wikipedia citation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                if (!summary.description.isNullOrBlank()) {
                    Text(
                        text = summary.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!summary.extract.isNullOrBlank()) {
                    Text(
                        text = summary.extract,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Link to full Wikipedia article
                if (!summary.pageUrl.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(summary.pageUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = null,
                                tint = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF6366F1),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isValencian) "Més informació a Wikipedia" else "Más información en Wikipedia",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF6366F1)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF6366F1),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeePricingCard(
    fee: String?,
    charge: String?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val isValencian = appLanguage == AppLanguage.CA

    val (titleText, subtitleText, badgeColor) = when {
        !charge.isNullOrBlank() -> {
            Triple(
                charge,
                if (isValencian) "Tarifa d'entrada" else "Tarifa de entrada",
                if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
            )
        }
        fee?.equals("no", ignoreCase = true) == true || fee?.equals("free", ignoreCase = true) == true -> {
            Triple(
                if (isValencian) "Entrada gratuïta" else "Entrada gratuita",
                if (isValencian) "Sense cost d'accés" else "Acceso libre sin coste",
                Color(0xFF10B981)
            )
        }
        fee?.equals("yes", ignoreCase = true) == true -> {
            Triple(
                if (isValencian) "Entrada de pagament" else "Entrada de pago",
                if (isValencian) "Requereix tiquet o abonament" else "Requiere ticket de acceso",
                Color(0xFFF59E0B)
            )
        }
        else -> return
    }

    val cardBg = if (isDarkMode) Color(0xFF262626) else Color(0xFFF8FAFC)
    val borderCol = if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = textPrimary
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = textSecondary
                )
            }
        }
    }
}

@Composable
fun HistoricalHeritageCard(
    startDate: String?,
    historicType: String?,
    operator: String?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val isValencian = appLanguage == AppLanguage.CA

    val typeLabel = when (historicType?.lowercase()?.trim()) {
        "city_gate" -> if (isValencian) "Porta monumental de la muralla" else "Puerta monumental de la muralla"
        "monument" -> if (isValencian) "Monument històric" else "Monumento histórico"
        "castle" -> if (isValencian) "Castell / Fortalesa" else "Castillo / Fortaleza"
        "memorial" -> if (isValencian) "Memorial" else "Memorial"
        "ruins" -> if (isValencian) "Ruïnes històriques" else "Ruinas históricas"
        "archaeological_site" -> if (isValencian) "Jaciment arqueològic" else "Yacimiento arqueológico"
        "tower" -> if (isValencian) "Torre històrica" else "Torre histórica"
        "building" -> if (isValencian) "Edifici històric" else "Edificio histórico"
        "citywalls" -> if (isValencian) "Muralla històrica" else "Muralla histórica"
        else -> historicType?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
    }

    val dateLabel = if (!startDate.isNullOrBlank()) {
        val century = when {
            startDate.startsWith("13") -> if (isValencian) "Segle XIV" else "Siglo XIV"
            startDate.startsWith("14") -> if (isValencian) "Segle XV" else "Siglo XV"
            startDate.startsWith("15") -> if (isValencian) "Segle XVI" else "Siglo XVI"
            startDate.startsWith("16") -> if (isValencian) "Segle XVII" else "Siglo XVII"
            startDate.startsWith("17") -> if (isValencian) "Segle XVIII" else "Siglo XVIII"
            startDate.startsWith("18") -> if (isValencian) "Segle XIX" else "Siglo XIX"
            startDate.startsWith("19") -> if (isValencian) "Segle XX" else "Siglo XX"
            else -> null
        }
        if (century != null) "$startDate ($century)" else startDate
    } else null

    val cardBg = if (isDarkMode) Color(0xFF262626) else Color(0xFFF8FAFC)
    val borderCol = if (isDarkMode) Color(0xFF383838) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = if (isDarkMode) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                val mainTitle = typeLabel ?: (if (isValencian) "Patrimoni històric" else "Patrimonio histórico")
                Text(
                    text = mainTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = textPrimary
                )

                val details = listOfNotNull(
                    dateLabel?.let { if (isValencian) "Construcció: $it" else "Construcción: $it" },
                    operator?.let { if (isValencian) "Gestió: $it" else "Gestión: $it" }
                ).joinToString(" • ")

                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = textSecondary
                    )
                }
            }
        }
    }
}
