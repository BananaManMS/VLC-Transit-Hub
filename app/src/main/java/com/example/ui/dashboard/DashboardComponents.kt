package com.example.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CalendarItemEntity
import com.example.ui.theme.appCardBorder
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherData
import com.example.data.model.ForecastHour
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// HELPER EXTENSION TO PARSE HEX COLOR STRING INTO COMPOSE COLOR DIRECTLY
fun String.toColorInt(): Int {
    if (startsWith("#")) {
        var color = substring(1)
        if (color.length == 6) {
            color = "FF$color"
        }
        return color.toLong(16).toInt()
    }
    return 0xFF3DDC84.toInt() // fallback Android Green
}

// WEATHER CARD PANEL WITH LIVE SEARCH
@Composable
fun WeatherCard(
    data: WeatherData?,
    isFahrenheit: Boolean,
    isDarkMode: Boolean,
    currentTime: String = "",
    appLanguage: AppLanguage = AppLanguage.ES
) {
    val context = LocalContext.current

    val cardBg = MaterialTheme.colorScheme.surface
    val cardTextColor = MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                appCardBorder(),
                RoundedCornerShape(16.dp)
            )
            .clickable {
                val cityName = data?.cityName ?: "Valencia"
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=tiempo+en+${cityName}")
                )
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val toastMsg = if (appLanguage == AppLanguage.CA) "No s'ha pogut obrir el cercador de l'oratge." else "No se pudo abrir el buscador del tiempo."
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
            .testTag("weather_widget_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // Location Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = cardTextSecondaryColor,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = (data?.cityName ?: "VALENCIA").uppercase(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = cardTextSecondaryColor,
                    letterSpacing = 1.sp
                )
            }

            if (data == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Carregant dades de l'oratge..." else "Cargando datos meteorológicos...",
                        fontSize = 14.sp,
                        color = cardTextSecondaryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Weather icon & temperature
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val currentHour = try {
                            if (currentTime.contains(":")) currentTime.substringBefore(":").toInt() else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        } catch (e: Exception) {
                            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        }
                        val isNight = currentHour >= 21 || currentHour < 6
                        val conditionText = when (data.condition) {
                            WeatherCondition.SUNNY -> if (isNight) {
                                if (appLanguage == AppLanguage.CA) "Clar" else "Despejado"
                            } else {
                                if (appLanguage == AppLanguage.CA) "Solejat" else "Soleado"
                            }
                            WeatherCondition.PARTLY_CLOUDY -> if (appLanguage == AppLanguage.CA) "Parcialment ennuvolat" else "Parcialmente nublado"
                            WeatherCondition.CLOUDY -> if (appLanguage == AppLanguage.CA) "Ennuvolat" else "Nublado"
                            WeatherCondition.RAINY -> if (appLanguage == AppLanguage.CA) "Pluja" else "Lluvia"
                            WeatherCondition.STORMY -> if (appLanguage == AppLanguage.CA) "Tempestuós" else "Tormentoso"
                            WeatherCondition.WINDY -> if (appLanguage == AppLanguage.CA) "Ventós" else "Ventoso"
                        }

                        Icon(
                            imageVector = when (data.condition) {
                                WeatherCondition.SUNNY, WeatherCondition.PARTLY_CLOUDY -> {
                                    if (isNight) Icons.Default.DarkMode else Icons.Default.WbSunny
                                }
                                else -> Icons.Default.Cloud
                            },
                            contentDescription = conditionText,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )

                        val tempText = if (isFahrenheit) "${data.currentTempFahrenheit()}°" else "${data.currentTempCelsius}°"
                        Text(
                            text = tempText,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            letterSpacing = (-1).sp
                        )
                    }

                    // Weather description Column
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        val minText = if (isFahrenheit) "${data.minTempFahrenheit()}°F" else "${data.minTempCelsius()}°"
                        val maxText = if (isFahrenheit) "${data.maxTempFahrenheit()}°F" else "${data.maxTempCelsius()}°"
                        
                        val maxMinPrefix = if (appLanguage == AppLanguage.CA) "Màx" else "Máx"
                        Text(
                            text = "$maxMinPrefix $maxText · Mín $minText",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )

                        val currentHour = try {
                            if (currentTime.contains(":")) currentTime.substringBefore(":").toInt() else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        } catch (e: Exception) {
                            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        }
                        val isNight = currentHour >= 21 || currentHour < 6
                        val conditionText = when (data.condition) {
                            WeatherCondition.SUNNY -> if (isNight) {
                                if (appLanguage == AppLanguage.CA) "Clar" else "Despejado"
                            } else {
                                if (appLanguage == AppLanguage.CA) "Solejat" else "Soleado"
                            }
                            WeatherCondition.PARTLY_CLOUDY -> if (appLanguage == AppLanguage.CA) "Parcialment ennuvolat" else "Parcialmente nublado"
                            WeatherCondition.CLOUDY -> if (appLanguage == AppLanguage.CA) "Ennuvolat" else "Nublado"
                            WeatherCondition.RAINY -> if (appLanguage == AppLanguage.CA) "Pluja" else "Lluvia"
                            WeatherCondition.STORMY -> if (appLanguage == AppLanguage.CA) "Tempestuós" else "Tormentoso"
                            WeatherCondition.WINDY -> if (appLanguage == AppLanguage.CA) "Ventós" else "Ventoso"
                        }
                        
                        Text(
                            text = conditionText,
                            fontSize = 12.sp,
                            color = cardTextSecondaryColor,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Hourly forecast LazyRow using actual data from data.hourlyForecast
                val hourlyList = remember(data.hourlyForecast, data.currentTempCelsius, data.condition) {
                    if (!data.hourlyForecast.isNullOrEmpty()) {
                        data.hourlyForecast
                    } else {
                        val baseTemp = data.currentTempCelsius
                        val currentHourVal = try {
                            if (currentTime.contains(":")) currentTime.substringBefore(":").toInt() else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        } catch (e: Exception) {
                            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        }
                        (0 until 24).map { i ->
                            val h = (currentHourVal + i) % 24
                            val timeLabel = if (i == 0) (if (appLanguage == AppLanguage.CA) "Ara" else "Ahora") else String.format(java.util.Locale.getDefault(), "%02d:00", h)
                            ForecastHour(timeLabel, baseTemp, data.condition)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val nowLabel = if (appLanguage == AppLanguage.CA) "Ara" else "Ahora"

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hourly_weather_forecast")
                ) {
                    items(hourlyList.size) { index ->
                        val item = hourlyList[index]
                        val isNow = index == 0
                        val displayTime = if (isNow) nowLabel else item.time
                        val displayTemp = if (isFahrenheit) (item.tempCelsius * 9 / 5) + 32 else item.tempCelsius
                        
                        val itemHour = try {
                            if (item.time.contains(":")) item.time.substringBefore(":").toInt() else 12
                        } catch (e: Exception) {
                            12
                        }
                        val itemIsNight = itemHour >= 21 || itemHour < 6
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayTime,
                                fontSize = 11.sp,
                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                color = if (isNow) MaterialTheme.colorScheme.primary else cardTextSecondaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Icon(
                                imageVector = when (item.condition) {
                                    WeatherCondition.SUNNY, WeatherCondition.PARTLY_CLOUDY -> if (itemIsNight) Icons.Default.DarkMode else Icons.Default.WbSunny
                                    WeatherCondition.RAINY, WeatherCondition.STORMY -> Icons.Default.Grain
                                    else -> Icons.Default.Cloud
                                },
                                contentDescription = null,
                                tint = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$displayTemp°",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = cardTextSecondaryColor.copy(alpha = 0.2f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Details Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Vent: ${data.windKmh} km/h" else "Viento: ${data.windKmh} km/h",
                        fontSize = 11.sp,
                        color = cardTextSecondaryColor
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Humitat: ${data.humidityPercent}%" else "Humedad: ${data.humidityPercent}%",
                        fontSize = 11.sp,
                        color = cardTextSecondaryColor
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.CA) "Pluja: ${data.precipitationChancePercent}%" else "Lluvia: ${data.precipitationChancePercent}%",
                        fontSize = 11.sp,
                        color = cardTextSecondaryColor
                    )
                }
            }
        }
    }
}

fun getRelativeDateLabel(startMillis: Long?): String {
    if (startMillis == null) return ""
    
    val todayCal = java.util.Calendar.getInstance()
    todayCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    todayCal.set(java.util.Calendar.MINUTE, 0)
    todayCal.set(java.util.Calendar.SECOND, 0)
    todayCal.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = todayCal.timeInMillis
    
    val oneDayMs = 24 * 60 * 60 * 1000L
    
    val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
    targetCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    targetCal.set(java.util.Calendar.MINUTE, 0)
    targetCal.set(java.util.Calendar.SECOND, 0)
    targetCal.set(java.util.Calendar.MILLISECOND, 0)
    val targetStart = targetCal.timeInMillis
    
    val diffDays = ((targetStart - todayStart) / oneDayMs).toInt()
    
    return when (diffDays) {
        0 -> "(Hoy)"
        1 -> "(Mañana)"
        2 -> "(En 2 días)"
        3 -> "(En 3 días)"
        4 -> "(En 4 días)"
        5 -> "(En 5 días)"
        6 -> "(En 6 días)"
        7 -> "(En una semana)"
        in 8..30 -> "(En $diffDays días)"
        else -> {
            val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            "(${sdf.format(java.util.Date(startMillis))})"
        }
    }
}

// GOOGLE CALENDAR EVENT CARD COMPONENT
@Composable
fun EventCard(
    event: CalendarItemEntity,
    onDelete: () -> Unit,
    isDarkMode: Boolean = true,
    appLanguage: AppLanguage = AppLanguage.ES
) {
    val context = LocalContext.current
    val eventColor = remember(event.colorHex) {
        Color(event.colorHex.toColorInt())
    }
    val (startTime, endTime) = remember(event.startMillis, event.endMillis) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = formatter.format(Date(event.startMillis ?: 0L))
        val end = formatter.format(Date(event.endMillis ?: 0L))
        start to end
    }
    val dateLabel = remember(event.startMillis) {
        getRelativeDateLabel(event.startMillis)
    }
    val isToday = remember(dateLabel) {
        val cleanLabel = dateLabel.replace("(", "").replace(")", "").trim()
        cleanLabel.equals("Hoy", ignoreCase = true) || cleanLabel.isEmpty()
    }
    val isTomorrow = remember(dateLabel) {
        val cleanLabel = dateLabel.replace("(", "").replace(")", "").trim()
        cleanLabel.equals("Mañana", ignoreCase = true)
    }
    val hasTime = remember(event.startMillis, event.endMillis, event.isAllDay) {
        if (event.isAllDay || event.startMillis == null || event.endMillis == null || event.startMillis == event.endMillis) {
            false
        } else {
            val duration = event.endMillis!! - event.startMillis!!
            if (duration % 86400000L == 0L) {
                false
            } else {
                val startCal = java.util.Calendar.getInstance().apply { timeInMillis = event.startMillis!! }
                val endCal = java.util.Calendar.getInstance().apply { timeInMillis = event.endMillis!! }
                val startHour = startCal.get(java.util.Calendar.HOUR_OF_DAY)
                val startMin = startCal.get(java.util.Calendar.MINUTE)
                val endHour = endCal.get(java.util.Calendar.HOUR_OF_DAY)
                val endMin = endCal.get(java.util.Calendar.MINUTE)
                !(startHour == 0 && startMin == 0 && (endHour == 0 && endMin == 0 || (endHour == 23 && endMin == 59)))
            }
        }
    }

    val cardBg = MaterialTheme.colorScheme.surface
    val cardTextColor = MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        border = appCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val eventId = event.calendarEventId ?: event.id.toLong()
                    val uri = android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, eventId)
                    val intent = Intent(Intent.ACTION_VIEW).setData(uri)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val builder = android.provider.CalendarContract.CONTENT_URI.buildUpon()
                        builder.appendPath("time")
                        android.content.ContentUris.appendId(builder, event.startMillis ?: System.currentTimeMillis())
                        val intent = Intent(Intent.ACTION_VIEW).setData(builder.build())
                        context.startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(context, if (appLanguage == AppLanguage.CA) "No s'ha pogut obrir el calendari" else "No se pudo abrir el calendario", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .testTag("event_card_${event.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Vertical accent bar on the left (neutra por defecto: white with 0.08 opacity or 0.14 white)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Time",
                            tint = if (isToday) cardTextSecondaryColor else if (isTomorrow) Color(0xFFE8A33D) else eventColor,
                            modifier = Modifier.size(11.dp)
                        )
                        
                        if (isTomorrow) {
                            Text(
                                text = "MAÑANA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8A33D)
                            )
                            Text(
                                text = "·",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardTextSecondaryColor
                            )
                        }
                        
                        val cleanLabel = dateLabel.replace("(", "").replace(")", "").trim()
                        val timeDisplay = if (!hasTime) {
                            if (isToday) "HOY · TODO EL DÍA" else if (isTomorrow) "TODO EL DÍA" else "${cleanLabel.uppercase()} • TODO EL DÍA"
                        } else {
                            if (isToday || isTomorrow) {
                                "$startTime - $endTime"
                            } else {
                                "${cleanLabel.uppercase()}  •  $startTime - $endTime"
                            }
                        }
                        
                        Text(
                            text = timeDisplay,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = cardTextSecondaryColor
                        )
                    }

                    // Delete Event with accessible touch target & reduced opacity (0.35)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_event_${event.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = cardTextSecondaryColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = event.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (event.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.description,
                        fontSize = 12.sp,
                        color = cardTextSecondaryColor,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// GOOGLE CALENDAR TASK CHECKBOX ROW COMPONENT
@Composable
fun TaskRow(
    task: CalendarItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val taskColor = remember(task.colorHex) {
        Color(task.colorHex.toColorInt())
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = appCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_task_${task.id}")
                ) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle completion",
                        tint = if (task.isCompleted) taskColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("delete_task_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
