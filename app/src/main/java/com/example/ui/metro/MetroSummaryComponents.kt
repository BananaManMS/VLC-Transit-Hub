package com.example.ui.metro

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.data.model.Departure
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.util.normalizeForSearch
import java.util.Locale

@Composable
fun StationConfigDialog(
    allStations: List<MetroStation>,
    currentFavorites: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selectedStations by remember { mutableStateOf(currentFavorites) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val dialogListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        dialogListState.scrollToItem(0)
    }

    val initialFavorites = remember { currentFavorites }
    val filteredStations = remember(searchQuery, allStations) {
        val distinctStations = allStations.distinctBy { it.id }
        val baseList = if (searchQuery.isBlank()) {
            distinctStations.map { Pair(it, 0.0) }
        } else {
            distinctStations.map { station ->
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configurar Estaciones Favoritas",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Selecciona hasta 10 estaciones para tu panel rápido:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar estación...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("station_search_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                
                LazyColumn(
                    state = dialogListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStations, key = { it.id }) { station ->
                        val isChecked = selectedStations.contains(station.id)
                        val animatedCornerRadius by animateDpAsState(
                            targetValue = if (isChecked) 8.dp else 24.dp,
                            animationSpec = tween(durationMillis = 500),
                            label = "fav_dialog_station_corner"
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedStations = selectedStations - station.id
                                    } else {
                                        if (selectedStations.size >= 10) {
                                            android.widget.Toast.makeText(context, "Sólo puedes seleccionar un máximo de 10 estaciones.", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            selectedStations = selectedStations + station.id
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(animatedCornerRadius),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                }
                            ),
                            border = BorderStroke(
                                width = if (isChecked) 1.5.dp else 1.dp,
                                color = if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Líneas: ${station.lines.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (selectedStations.size >= 10) {
                                                android.widget.Toast.makeText(context, "Sólo puedes seleccionar un máximo de 10 estaciones.", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                selectedStations = selectedStations + station.id
                                            }
                                        } else {
                                            selectedStations = selectedStations - station.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = "Seleccionadas: ${selectedStations.size} de 10",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedStations.isNotEmpty() && selectedStations.size <= 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedStations.isEmpty() || selectedStations.size > 10) {
                        android.widget.Toast.makeText(context, "Por favor, selecciona entre 1 y 10 estaciones.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(selectedStations)
                    }
                },
                enabled = selectedStations.isNotEmpty() && selectedStations.size <= 10,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

