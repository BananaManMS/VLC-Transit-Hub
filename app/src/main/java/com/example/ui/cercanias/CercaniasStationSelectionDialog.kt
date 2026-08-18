package com.example.ui.cercanias

import com.example.util.LocationUtils
import com.example.util.normalizeForSearch
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.database.CercaniasStationEntity
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasStationSelectionDialog(
    viewModel: CercaniasViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsState()
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val cercaniasFavoriteStations by viewModel.cercaniasFavoriteStations.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStations by remember(cercaniasFavoriteStations) { mutableStateOf(cercaniasFavoriteStations.map { it.id }) }

    val dialogListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    var allCercaniasStations by remember { mutableStateOf<List<CercaniasStationEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        allCercaniasStations = viewModel.getAllCercaniasStations()
    }

    val initialFavorites = remember { cercaniasFavoriteStations.map { it.id } }
    val filteredStations = remember(searchQuery, allCercaniasStations) {
        val baseList = if (searchQuery.isBlank()) {
            allCercaniasStations.map { Pair(it, 0.0) }
        } else {
            allCercaniasStations.map { station ->
                Pair(station, computeCercaniasSearchScore(station, searchQuery))
            }.filter { it.second > 0.0 }
        }
        val (favs, nonFavs) = baseList.partition { initialFavorites.contains(it.first.id) }
        if (searchQuery.isBlank()) {
            favs.sortedBy { it.first.nombre.normalizeForSearch() }.map { it.first } + nonFavs.sortedBy { it.first.nombre.normalizeForSearch() }.map { it.first }
        } else {
            favs.sortedByDescending { it.second }.map { it.first } + nonFavs.sortedByDescending { it.second }.map { it.first }
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF171D2C) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

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
                            text = if (appLanguage == AppLanguage.CA) "Favorites de Rodalia" else "Favoritas Cercanías",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Selecciona fins a 10 estacions favorites de Rodalia Renfe" else "Selecciona hasta 10 estaciones favoritas de Cercanías Renfe",
                            fontSize = 12.sp,
                            color = subtextColor
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = texts.closeBtn,
                            tint = subtextColor
                        )
                    }
                }

                // Barra de búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text(texts.searchStationLabel, color = subtextColor) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = subtextColor) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    )
                )

                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredStations) { station ->
                        val isChecked = selectedStations.contains(station.id)
                        val bgCol = if (isChecked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                        val bord = if (isChecked) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, borderColor)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isChecked) {
                                        if (selectedStations.size >= 10) {
                                            val toastMsg = if (appLanguage == AppLanguage.CA) "Només pots seleccionar un màxim de 10 estacions." else "Sólo puedes seleccionar un máximo de 10 estaciones."
                                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            selectedStations = selectedStations + station.id
                                        }
                                    } else {
                                        selectedStations = selectedStations - station.id
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = bgCol),
                            shape = RoundedCornerShape(18.dp),
                            border = bord
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Cercanias Line Badges
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val linesList = station.lines.split(",").filter { it.isNotBlank() }
                                        linesList.forEach { line ->
                                            val colorHex = when (line) {
                                                "C1" -> "#00A3E0"
                                                "C2" -> "#FF6A00"
                                                "C3" -> "#7A287B"
                                                "C4" -> "#E52321"
                                                "C5" -> "#009639"
                                                "C6" -> "#002F6C"
                                                else -> "#7F8C8D"
                                            }
                                            val lineText = if (line.matches(Regex("C\\d"))) "C-${line.substring(1)}" else line
                                            Box(
                                                modifier = Modifier
                                                    .height(14.dp)
                                                    .widthIn(min = 24.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                                    .padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = lineText,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center,
                                                    style = TextStyle(
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = accentColor)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
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
                                val toastMsg = if (appLanguage == AppLanguage.CA) "Per favor, selecciona entre 1 i 10 estacions." else "Por favor, selecciona entre 1 y 10 estaciones."
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateCercaniasFavoriteStations(selectedStations)
                                val toastMsg = if (appLanguage == AppLanguage.CA) "Estacions favorites actualitzades." else "Estaciones favoritas actualizadas."
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
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
                        Text(if (appLanguage == AppLanguage.CA) "Guardar Canvis" else "Guardar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CercaniasQuickStationPickerDialog(
    viewModel: CercaniasViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val cercaniasFavoriteStations by viewModel.cercaniasFavoriteStations.collectAsState()
    val selectedStationId by viewModel.cercaniasSelectedStationId.collectAsState()
    val lastLocation by viewModel.lastLocation.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var allCercaniasStations by remember { mutableStateOf<List<CercaniasStationEntity>>(emptyList()) }
    val dialogListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        allCercaniasStations = viewModel.getAllCercaniasStations()
    }

    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    val favoriteIds = remember(cercaniasFavoriteStations) { cercaniasFavoriteStations.map { it.id }.toSet() }

    val (closestStations, alphabeticalStations, searchResults) = remember(searchQuery, allCercaniasStations, favoriteIds, lastLocation) {
        if (searchQuery.isBlank()) {
            val refLat = lastLocation?.first ?: 39.4699
            val refLon = lastLocation?.second ?: -0.37739

            val sortedByDist = allCercaniasStations.sortedBy { station ->
                LocationUtils.calculateDistanceMeters(refLat, refLon, station.latitud, station.longitud)
            }

            val top3Closest = sortedByDist.take(3)
            val remainingSortedAlphabetically = sortedByDist.drop(3)
                .sortedBy { it.nombre.normalizeForSearch() }

            Triple(top3Closest, remainingSortedAlphabetically, emptyList<CercaniasStationEntity>())
        } else {
            val filtered = allCercaniasStations.map { station ->
                Pair(station, computeCercaniasSearchScore(station, searchQuery))
            }.filter { it.second > 0.0 }
             .sortedByDescending { it.second }
             .map { it.first }
            Triple(emptyList<CercaniasStationEntity>(), emptyList<CercaniasStationEntity>(), filtered)
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF171D2C) else Color.White
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

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
                    .testTag("quick_cercanias_station_picker")
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (appLanguage == AppLanguage.CA) "Consultar Estació Rodalia" else "Consultar Estación Cercanías",
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
                        modifier = Modifier.testTag("btn_close_quick_cercanias_picker")
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
                        .testTag("quick_cercanias_search_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = borderColor,
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
                    val renderStation = @Composable { station: CercaniasStationEntity ->
                        val isCurrentSelected = station.id == selectedStationId
                        val isFav = favoriteIds.contains(station.id)
                        val bgCol = if (isCurrentSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            cardBg
                        }
                        val bord = if (isCurrentSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, borderColor)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCercaniasStation(station.id)
                                    val msg = if (appLanguage == AppLanguage.CA) "Mostrant eixides de ${station.displayName}" else "Mostrando salidas de ${station.displayName}"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .testTag("quick_picker_cercanias_station_${station.id}"),
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
                                            text = station.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        val distText = viewModel.getCercaniasStationDistanceText(station)
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
                                        val linesList = station.lines.split(",").filter { it.isNotBlank() }
                                        linesList.forEach { line ->
                                            val colorHex = when (line) {
                                                "C1" -> "#00A3E0"
                                                "C2" -> "#FF6A00"
                                                "C3" -> "#7A287B"
                                                "C4" -> "#E52321"
                                                "C5" -> "#009639"
                                                "C6" -> "#002F6C"
                                                else -> "#7F8C8D"
                                            }
                                            val lineText = if (line.matches(Regex("C\\d"))) "C-${line.substring(1)}" else line
                                            Box(
                                                modifier = Modifier
                                                    .height(14.dp)
                                                    .widthIn(min = 24.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                                    .padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = lineText,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center,
                                                    style = TextStyle(
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
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
                            item(key = "header_closest_cercanias") {
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
                            item(key = "header_alphabetical_cercanias") {
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
