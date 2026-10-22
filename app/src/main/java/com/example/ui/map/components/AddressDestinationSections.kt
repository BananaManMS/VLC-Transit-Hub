package com.example.ui.map.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.NominatimResult
import com.example.data.model.PlaceCategory
import com.example.ui.dashboard.AppLanguage

@Composable
fun AddressDestinationDetailSections(
    address: NominatimResult,
    title: String,
    category: PlaceCategory,
    subcategoryLabel: String?,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    context: Context,
    textSecondaryColor: Color
) {
    val isValencian = appLanguage == AppLanguage.CA

    // Wikipedia / Wikimedia Hero Image & Summary Card (only if OSM explicitly provides wikipedia or wikidata tags)
    val hasExplicitWiki = !address.wikipedia.isNullOrBlank() || !address.wikidata.isNullOrBlank()

    if (hasExplicitWiki) {
        Spacer(modifier = Modifier.height(14.dp))
        PlaceWikiHeroCard(
            wikipediaTag = address.wikipedia,
            wikidataTag = address.wikidata,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode
        )
    }

    // Opening Hours Section
    if (!address.openingHours.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        ExpandableOpeningHoursCard(
            rawOpeningHours = address.openingHours,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode
        )
    }

    // Fee & Pricing Section (if available)
    if (!address.charge.isNullOrBlank() || !address.fee.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        FeePricingCard(
            fee = address.fee,
            charge = address.charge,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode
        )
    }

    // Historical & Architectural Details (if monument or historic building)
    val hasHistoricData = !address.startDate.isNullOrBlank() ||
            !address.historicType.isNullOrBlank() ||
            (!address.operator.isNullOrBlank() && category == PlaceCategory.ATTRACTION_CULTURE)

    if (hasHistoricData) {
        Spacer(modifier = Modifier.height(10.dp))
        HistoricalHeritageCard(
            startDate = address.startDate,
            historicType = address.historicType,
            operator = address.operator,
            appLanguage = appLanguage,
            isDarkMode = isDarkMode
        )
    }

    // Metadata & Contact Actions (Phone, Email, Website, Brand, Subcategory)
    val hasDetails = !address.phone.isNullOrBlank() ||
            !address.email.isNullOrBlank() ||
            !address.website.isNullOrBlank() ||
            (!address.brand.isNullOrBlank() && !address.brand.equals(title, ignoreCase = true)) ||
            (!address.operator.isNullOrBlank() && !hasHistoricData && !address.operator.equals(title, ignoreCase = true)) ||
            subcategoryLabel != null

    if (hasDetails) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!address.phone.isNullOrBlank()) {
                PlaceActionRow(
                    icon = Icons.Default.Phone,
                    title = address.phone,
                    subtitle = if (isValencian) "Trucar al local" else "Llamar al establecimiento",
                    isDarkMode = isDarkMode,
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${address.phone.trim()}"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }

            if (!address.email.isNullOrBlank()) {
                PlaceActionRow(
                    icon = Icons.Default.Email,
                    title = address.email,
                    subtitle = if (isValencian) "Enviar correu electrònic" else "Enviar correo electrónico",
                    isDarkMode = isDarkMode,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${address.email.trim()}"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }

            if (!address.website.isNullOrBlank()) {
                val displayWeb = address.website.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
                PlaceActionRow(
                    icon = Icons.Default.Language,
                    title = displayWeb,
                    subtitle = if (isValencian) "Lloc web oficial" else "Sitio web oficial",
                    isDarkMode = isDarkMode,
                    onClick = {
                        val webUrl = if (address.website.startsWith("http://") || address.website.startsWith("https://")) {
                            address.website
                        } else {
                            "https://${address.website}"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }

            if (!address.brand.isNullOrBlank() && !address.brand.equals(title, ignoreCase = true)) {
                PlaceActionRow(
                    icon = Icons.Default.Business,
                    title = address.brand,
                    subtitle = if (isValencian) "Cadena / Marca" else "Cadena / Marca",
                    isDarkMode = isDarkMode
                )
            }

            if (!address.operator.isNullOrBlank() && !hasHistoricData && !address.operator.equals(title, ignoreCase = true)) {
                PlaceActionRow(
                    icon = Icons.Default.Business,
                    title = address.operator,
                    subtitle = if (isValencian) "Entitat gestora / Operador" else "Entidad gestora / Operador",
                    isDarkMode = isDarkMode
                )
            }

            if (subcategoryLabel != null && !subcategoryLabel.equals(category.name, ignoreCase = true)) {
                PlaceActionRow(
                    icon = Icons.Default.Category,
                    title = subcategoryLabel,
                    subtitle = if (isValencian) "Tipus d'establiment" else "Tipo de establecimiento",
                    isDarkMode = isDarkMode
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Coordinates chip
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDarkMode) Color(0xFF262626) else Color(0xFFF1F5F9),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isValencian) "Punt al mapa" else "Punto en el mapa",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "%.4f, %.4f".format(address.latitude, address.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = textSecondaryColor
            )
        }
    }
}
