package com.example.ui.metro

import com.example.util.LocationUtils
import com.example.util.isBilingualTokenMatch
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.util.isSubsequence
import com.example.util.levenshteinDistance
import com.example.util.normalizeForSearch
import com.example.ui.dashboard.AppLanguage
import java.util.Locale

fun computeMetroSearchScore(station: MetroStation, query: String): Double {
    val normalizedQuery = query.normalizeForSearch()
    val normalizedName = station.name.normalizeForSearch()
    val normalizedId = station.id.normalizeForSearch()

    // 1. Direct ID matches
    if (normalizedId == normalizedQuery) return 1000.0
    if (normalizedId.startsWith(normalizedQuery)) return 900.0 + (normalizedQuery.length.toDouble() / normalizedId.length)
    if (normalizedId.contains(normalizedQuery)) return 800.0

    // 2. Direct name matches
    if (normalizedName == normalizedQuery) return 500.0
    if (normalizedName.startsWith(normalizedQuery)) return 400.0 + (normalizedQuery.length.toDouble() / normalizedName.length)
    if (normalizedName.contains(normalizedQuery)) return 300.0

    // 2.5 Line matches
    for (line in station.lines) {
        val normalizedLine = line.normalizeForSearch()
        if (normalizedLine == normalizedQuery) return 250.0
        if (normalizedLine.contains(normalizedQuery)) return 200.0
    }

    // 3. Token-based word match (e.g. "colon" in "Plaza de Colon")
    val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val nameTokens = normalizedName.split(Regex("\\s+")).filter { it.isNotEmpty() }

    if (queryTokens.isNotEmpty()) {
        var matchedTokensCount = 0
        var totalTokenScore = 0.0
        for (qToken in queryTokens) {
            var bestTokenScore = 0.0
            for (nToken in nameTokens) {
                if (isBilingualTokenMatch(qToken, nToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 100.0)
                } else if (nToken.startsWith(qToken) || qToken.startsWith(nToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 80.0 * minOf(qToken.length, nToken.length) / maxOf(qToken.length, nToken.length))
                } else if (nToken.contains(qToken)) {
                    bestTokenScore = maxOf(bestTokenScore, 60.0 * qToken.length / nToken.length)
                } else {
                    val dist = levenshteinDistance(qToken, nToken)
                    val maxLength = maxOf(qToken.length, nToken.length)
                    if (maxLength > 0) {
                        val similarity = 1.0 - (dist.toDouble() / maxLength)
                        if (similarity >= 0.6) {
                            bestTokenScore = maxOf(bestTokenScore, similarity * 50.0)
                        }
                    }
                }
            }
            if (bestTokenScore > 0) {
                matchedTokensCount++
                totalTokenScore += bestTokenScore
            }
        }
        if (matchedTokensCount > 0) {
            val completenessBonus = if (matchedTokensCount == queryTokens.size) 50.0 else 0.0
            return (totalTokenScore / queryTokens.size) + completenessBonus
        }
    }

    // 4. Character subsequence matching (fuzzy search)
    if (isSubsequence(normalizedQuery, normalizedName)) {
        return 10.0 + (normalizedQuery.length.toDouble() / normalizedName.length) * 10.0
    }

    // 5. Global Levenshtein distance for the entire string
    val globalDist = levenshteinDistance(normalizedQuery, normalizedName)
    val maxGlobalLength = maxOf(normalizedQuery.length, normalizedName.length)
    if (maxGlobalLength > 0) {
        val globalSimilarity = 1.0 - (globalDist.toDouble() / maxGlobalLength)
        if (globalSimilarity >= 0.5) {
            return globalSimilarity * 10.0
        }
    }

    return 0.0
}

@Composable
fun MetroStationSelectionDialog(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    metroViewModel: MetroViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStations by remember(favoriteStations) { mutableStateOf(favoriteStations) }

    val dialogListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    val initialFavorites = remember { favoriteStations }
    val filteredStations = remember(searchQuery, allNetworkStations) {
        val baseList = if (searchQuery.isBlank()) {
            allNetworkStations.map { Pair(it, 0.0) }
        } else {
            allNetworkStations.map { station ->
                Pair(station, computeMetroSearchScore(station, searchQuery))
            }.filter { it.second > 0.0 }
        }
        val (favs, nonFavs) = baseList.partition { initialFavorites.contains(it.first.id) }
        if (searchQuery.isBlank()) {
            favs.sortedBy { it.first.name.normalizeForSearch() }.map { it.first } + nonFavs.sortedBy { it.first.name.normalizeForSearch() }.map { it.first }
        } else {
            favs.sortedByDescending { it.second }.map { it.first } + nonFavs.sortedByDescending { it.second }.map { it.first }
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF171D2C) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val defaultBorderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = if (isDarkMode) Color(0xFF131824) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .testTag("station_selection_page")
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Seleccionar Estacions" else "Seleccionar Estaciones",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Selecciona fins a 10 estacions favorites de Metrovalencia" else "Selecciona hasta 10 estaciones favoritas de Metrovalencia",
                            fontSize = 12.sp,
                            color = subtextColor
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_back_to_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Tancar" else "Cerrar",
                            tint = subtextColor
                        )
                    }
                }

                // Barra de búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (appLanguage == AppLanguage.CA) "Cercar estació..." else "Buscar estación...", fontSize = 14.sp, color = subtextColor) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Cercar" else "Search",
                            tint = subtextColor
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (appLanguage == AppLanguage.CA) "Netejar" else "Clear",
                                    tint = subtextColor
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("station_search_input_page"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = defaultBorderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    )
                )

                // List of stations
                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredStations, key = { it.id }) { station ->
                        val isChecked = selectedStations.contains(station.id)
                        val bgCol = if (isChecked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                        val bord = if (isChecked) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, defaultBorderColor)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedStations = selectedStations - station.id
                                    } else {
                                        if (selectedStations.size >= 10) {
                                            Toast.makeText(context, "Sólo puedes seleccionar un máximo de 10 estaciones.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            selectedStations = selectedStations + station.id
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = bgCol),
                            border = bord
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Metro Line Badges
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        station.lines.forEach { line ->
                                            val normalizedLineId = if (line.startsWith("L")) line else "L$line"
                                            val lineObj = ValenciaMetroData.lines.find { it.id == normalizedLineId }
                                            val colorHex = lineObj?.colorHex ?: "#7F8C8D"
                                            val lineColor = try {
                                                Color(android.graphics.Color.parseColor(colorHex))
                                            } catch (e: Exception) {
                                                Color.Gray
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(lineColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = line,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (selectedStations.size >= 10) {
                                                Toast.makeText(context, "Sólo puedes seleccionar un máximo de 10 estaciones.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                selectedStations = selectedStations + station.id
                                            }
                                        } else {
                                            selectedStations = selectedStations - station.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = accentColor
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Seleccionades: ${selectedStations.size} de 10" else "Seleccionadas: ${selectedStations.size} de 10",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedStations.isNotEmpty() && selectedStations.size <= 10) accentColor else MaterialTheme.colorScheme.error
                    )

                    Button(
                        onClick = {
                            if (selectedStations.isEmpty() || selectedStations.size > 10) {
                                val errToast = if (appLanguage == AppLanguage.CA) "Per favor, selecciona entre 1 i 10 estacions." else "Por favor, selecciona entre 1 y 10 estaciones."
                                Toast.makeText(context, errToast, Toast.LENGTH_SHORT).show()
                            } else {
                                metroViewModel.updateFavoriteStations(selectedStations)
                                val successToast = if (appLanguage == AppLanguage.CA) "Estacions favorites actualitzades." else "Estaciones favoritas actualizadas."
                                Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        enabled = selectedStations.isNotEmpty() && selectedStations.size <= 10,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (appLanguage == AppLanguage.CA) "Guardar canvis" else "Guardar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MetroQuickStationPickerDialog(
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    metroViewModel: MetroViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val favoriteStations by metroViewModel.favoriteStations.collectAsState()
    val allNetworkStations by metroViewModel.allNetworkStations.collectAsState()
    val selectedStationId by metroViewModel.selectedStationId.collectAsState()
    val lastLocation by metroViewModel.lastLocation.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val dialogListState = rememberLazyListState()

    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    val (closestStations, alphabeticalStations, searchResults) = remember(searchQuery, allNetworkStations, favoriteStations, lastLocation) {
        if (searchQuery.isBlank()) {
            val refLat = lastLocation?.latitude ?: 39.4699
            val refLon = lastLocation?.longitude ?: -0.37739

            val sortedByDist = allNetworkStations.sortedBy { station ->
                LocationUtils.calculateDistanceMeters(refLat, refLon, station.latitude, station.longitude)
            }

            val top3Closest = sortedByDist.take(3)
            val remainingSortedAlphabetically = sortedByDist.drop(3)
                .sortedBy { it.name.normalizeForSearch() }

            Triple(top3Closest, remainingSortedAlphabetically, emptyList<MetroStation>())
        } else {
            val filtered = allNetworkStations.map { station ->
                Pair(station, computeMetroSearchScore(station, searchQuery))
            }.filter { it.second > 0.0 }
             .sortedByDescending { it.second }
             .map { it.first }
            Triple(emptyList<MetroStation>(), emptyList<MetroStation>(), filtered)
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF171D2C) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val defaultBorderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = if (isDarkMode) Color(0xFF131824) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .testTag("quick_station_picker_page")
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Consultar Estació" else "Consultar Estación",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Veure eixides en temps real sense modificar favorites" else "Ver salidas en tiempo real sin modificar tus favoritas",
                            fontSize = 12.sp,
                            color = subtextColor
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_quick_picker")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Tancar" else "Cerrar",
                            tint = subtextColor
                        )
                    }
                }

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (appLanguage == AppLanguage.CA) "Cercar estació..." else "Buscar estación...", fontSize = 14.sp, color = subtextColor) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = if (appLanguage == AppLanguage.CA) "Cercar" else "Buscar",
                            tint = subtextColor
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (appLanguage == AppLanguage.CA) "Netejar" else "Limpiar",
                                    tint = subtextColor
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("quick_station_search_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = defaultBorderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    )
                )

                // List of stations
                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val renderStation = @Composable { station: MetroStation ->
                        val isCurrentSelected = station.id == selectedStationId
                        val isFav = favoriteStations.contains(station.id)
                        val bgCol = if (isCurrentSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            cardBg
                        }
                        val bord = if (isCurrentSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, defaultBorderColor)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    metroViewModel.selectRealTimeStation(station.id)
                                    val msg = if (appLanguage == AppLanguage.CA) "Mostrant eixides de ${station.name}" else "Mostrando salidas de ${station.name}"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .testTag("quick_picker_station_${station.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = bgCol),
                            border = bord
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = station.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        val distText = metroViewModel.getStationDistanceText(station)
                                        if (distText != null) {
                                            Text(
                                                text = distText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor,
                                                modifier = Modifier.padding(start = 6.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Line Badges
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        station.lines.forEach { line ->
                                            val normalizedLineId = if (line.startsWith("L")) line else "L$line"
                                            val lineObj = ValenciaMetroData.lines.find { it.id == normalizedLineId }
                                            val colorHex = lineObj?.colorHex ?: "#7F8C8D"
                                            val lineColor = try {
                                                Color(android.graphics.Color.parseColor(colorHex))
                                            } catch (e: Exception) {
                                                Color.Gray
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(lineColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = line,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isFav) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Favorita",
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (searchQuery.isBlank()) {
                        if (closestStations.isNotEmpty()) {
                            item(key = "header_closest_metro") {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "ESTACIONS MÉS PRÒXIMES" else "ESTACIONES MÁS CERCANAS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(closestStations, key = { "closest_${it.id}" }) { station ->
                                renderStation(station)
                            }
                        }

                        if (alphabeticalStations.isNotEmpty()) {
                            item(key = "header_alphabetical_metro") {
                                Text(
                                    text = if (appLanguage == AppLanguage.CA) "TOTES LES ESTACIONS (A-Z)" else "TODAS LAS ESTACIONES (A-Z)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(alphabeticalStations, key = { "other_${it.id}" }) { station ->
                                renderStation(station)
                            }
                        }
                    } else {
                        items(searchResults, key = { it.id }) { station ->
                            renderStation(station)
                        }
                    }
                }
            }
        }
    }
}
