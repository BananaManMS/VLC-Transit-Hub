package com.example.ui.dashboard

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// WEATHER CARD PANEL WITH LIVE SEARCH
@Composable
fun WeatherCard(
    data: WeatherData?,
    isFahrenheit: Boolean,
    isDarkMode: Boolean,
    appLanguage: AppLanguage = AppLanguage.ES,
    isOnline: Boolean = true
) {
    val context = LocalContext.current

    val cardBg = MaterialTheme.colorScheme.surface
    val cardTextColor = MaterialTheme.colorScheme.onSurface
    val cardTextSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    val onWeatherClick = {
        val rawCity = data?.cityName?.trim() ?: ""
        val query = if (rawCity.isNotBlank() &&
            !rawCity.equals("Ubicación GPS", ignoreCase = true) &&
            !rawCity.equals("GPS", ignoreCase = true)
        ) {
            "tiempo $rawCity"
        } else {
            "tiempo"
        }
        val webUrl = "https://www.google.com/search?q=" + Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val chooser = Intent.createChooser(intent, "Abrir con").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e2: Exception) {
                val toastMsg = if (appLanguage == AppLanguage.CA) "No s'ha pogut obrir el cercador de l'oratge." else "No se pudo abrir el buscador del tiempo."
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        onClick = onWeatherClick,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("weather_widget_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            // Location Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = cardTextSecondaryColor,
                        modifier = Modifier.size(13.dp)
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
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = cardTextSecondaryColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (data == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isOnline) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = cardTextSecondaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.weather_offline),
                            fontSize = 14.sp,
                            color = cardTextSecondaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.weather_loading),
                            fontSize = 14.sp,
                            color = cardTextSecondaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                val isNight = currentHour >= 21 || currentHour < 6
                 val conditionText = when (data.condition) {
                    WeatherCondition.SUNNY -> if (isNight) {
                        if (appLanguage == AppLanguage.CA) "Clar" else "Despejado"
                    } else {
                        if (appLanguage == AppLanguage.CA) "Solejat" else "Soleado"
                    }
                    WeatherCondition.PARTLY_CLOUDY -> if (appLanguage == AppLanguage.CA) "Parcialment ennuvolat" else "Parcialmente nublado"
                    WeatherCondition.CLOUDY -> if (appLanguage == AppLanguage.CA) "Ennuvolat" else "Nublado"
                    WeatherCondition.DRIZZLE -> if (appLanguage == AppLanguage.CA) "Boirim" else "Llovizna"
                    WeatherCondition.LIGHT_RAIN -> if (appLanguage == AppLanguage.CA) "Pluja lleugera" else "Lluvia ligera"
                    WeatherCondition.MODERATE_RAIN -> if (appLanguage == AppLanguage.CA) "Pluja moderada" else "Lluvia moderada"
                    WeatherCondition.HEAVY_RAIN -> if (appLanguage == AppLanguage.CA) "Pluja forta" else "Lluvia fuerte"
                    WeatherCondition.RAINY -> if (appLanguage == AppLanguage.CA) "Pluja" else "Lluvia"
                    WeatherCondition.STORMY -> if (appLanguage == AppLanguage.CA) "Tempestuós" else "Tormentoso"
                    WeatherCondition.WINDY -> if (appLanguage == AppLanguage.CA) "Ventós" else "Ventoso"
                    WeatherCondition.SNOWY -> if (appLanguage == AppLanguage.CA) "Nevant" else "Nieve"
                    WeatherCondition.FOGGY -> if (appLanguage == AppLanguage.CA) "Boira" else "Niebla"
                }

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
                        WeatherConditionIcon(
                            condition = data.condition,
                            isNight = isNight,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )

                        val tempText = if (isFahrenheit) "${data.currentTempFahrenheit.toInt()}°" else "${data.currentTempCelsius.toInt()}°"
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
                        val minText = if (isFahrenheit) "${data.minTempFahrenheit.toInt()}°F" else "${data.minTempCelsius.toInt()}°"
                        val maxText = if (isFahrenheit) "${data.maxTempFahrenheit.toInt()}°F" else "${data.maxTempCelsius.toInt()}°"
                        
                        val maxMinPrefix = androidx.compose.ui.res.stringResource(com.example.R.string.weather_max_prefix)
                        Text(
                            text = "$maxMinPrefix $maxText · Mín $minText",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )

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
                        val madridCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid"))
                        val currentMadridHour = madridCal.get(java.util.Calendar.HOUR_OF_DAY)
                        val firstHour = data.hourlyForecast.firstOrNull()?.hour ?: -1
                        if (firstHour != -1 && firstHour != currentMadridHour) {
                            val matchIdx = data.hourlyForecast.indexOfFirst { it.hour == currentMadridHour }
                            if (matchIdx > 0) {
                                data.hourlyForecast.drop(matchIdx)
                            } else {
                                data.hourlyForecast
                            }
                        } else {
                            data.hourlyForecast
                        }
                    } else {
                        val baseTemp = data.currentTempCelsius
                        val currentHourVal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid")).get(java.util.Calendar.HOUR_OF_DAY)
                        (0 until 24).map { i ->
                            val h = (currentHourVal + i) % 24
                            val timeLabel = if (i == 0) (if (appLanguage == AppLanguage.CA) "Ara" else "Ahora") else String.format(java.util.Locale.getDefault(), "%02d:00", h)
                            ForecastHour(time = timeLabel, tempCelsius = baseTemp.toDouble(), condition = data.condition)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val nowLabel = if (appLanguage == AppLanguage.CA) "Ara" else "Ahora"

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hourly_weather_forecast")
                ) {
                    items(
                        count = hourlyList.size,
                        key = { index -> "${hourlyList[index].time}_$index" }
                    ) { index ->
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
                                .clickable { onWeatherClick() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayTime,
                                fontSize = 11.sp,
                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                color = if (isNow) MaterialTheme.colorScheme.primary else cardTextSecondaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                             WeatherConditionIcon(
                                condition = item.condition,
                                isNight = itemIsNight,
                                tint = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
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

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    color = cardTextSecondaryColor.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(6.dp))

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
    
    return when {
        diffDays < 0 -> "(Pasado)"
        diffDays == 0 -> "(Hoy)"
        diffDays == 1 -> "(Mañana)"
        diffDays == 2 -> "(En 2 días)"
        diffDays == 3 -> "(En 3 días)"
        diffDays == 4 -> "(En 4 días)"
        diffDays == 5 -> "(En 5 días)"
        diffDays == 6 -> "(En 6 días)"
        diffDays == 7 -> "(En una semana)"
        diffDays in 8..30 -> "(En $diffDays días)"
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
        val hex = event.colorHex ?: "#3B82F6"
        try {
            Color(hex.toColorInt())
        } catch (e: Exception) {
            Color(0xFF3B82F6)
        }
    }
    val (startTime, endTime) = remember(event.startMillis, event.endMillis) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Europe/Madrid")
        }
        val start = event.startMillis?.let { formatter.format(Date(it)) } ?: "--:--"
        val end = event.endMillis?.let { formatter.format(Date(it)) } ?: "--:--"
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
        val s = event.startMillis
        val e = event.endMillis
        if (event.isAllDay || s == null || e == null || s == e) {
            false
        } else {
            val duration = e - s
            if (duration % 86400000L == 0L) {
                false
            } else {
                val startCal = java.util.Calendar.getInstance().apply { timeInMillis = s }
                val endCal = java.util.Calendar.getInstance().apply { timeInMillis = e }
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
        onClick = {
            var launched = false
            val calEventId = event.calendarEventId

            // 1. Si es un evento coordinado de calendario del sistema con ID válido, abrir el evento nativo
            if (calEventId != null && calEventId > 0) {
                try {
                    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calEventId)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = eventUri
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        event.startMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                        event.endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                    }
                    context.startActivity(intent)
                    launched = true
                } catch (e: Exception) {
                    Log.w("EventCard", "Failed to open event directly by ID: $calEventId", e)
                }
            }

            // 2. Si no o si falla, abrir la vista de fecha/hora en el calendario nativo
            if (!launched) {
                try {
                    val targetTime = event.startMillis ?: System.currentTimeMillis()
                    val timeUri = CalendarContract.CONTENT_URI.buildUpon()
                        .appendPath("time")
                        .let { ContentUris.appendId(it, targetTime).build() }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = timeUri
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        event.startMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                        event.endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                    }
                    context.startActivity(intent)
                    launched = true
                } catch (e: Exception) {
                    Log.w("EventCard", "Failed to open calendar time view", e)
                }
            }

            // 3. Fallback: Abrir la aplicación de calendario nativo
            if (!launched) {
                try {
                    val calendarAppIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_CALENDAR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(calendarAppIntent)
                    launched = true
                } catch (e: Exception) {
                    Log.w("EventCard", "Failed to open calendar app by category", e)
                }
            }

            // 4. Fallback: Intentar abrir paquete de Google Calendar
            if (!launched) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.calendar")?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        launched = true
                    }
                } catch (e: Exception) {
                    Log.w("EventCard", "Failed to open Google Calendar package", e)
                }
            }

            if (!launched) {
                Toast.makeText(
                    context,
                    context.getString(com.example.R.string.toast_cannot_open_calendar),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
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

@Composable
fun DashboardClockWidget(
    currentTimeFlow: kotlinx.coroutines.flow.StateFlow<String>,
    appLanguage: AppLanguage,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val currentTimeState = currentTimeFlow.collectAsState()
    val currentTime: String = currentTimeState.value

    Column(
        modifier = modifier.testTag("clock_widget")
    ) {
        val (hoursMinutes, seconds) = remember(currentTime) {
            if (currentTime.length >= 8 && currentTime[2] == ':' && currentTime[5] == ':') {
                Pair(currentTime.substring(0, 5), currentTime.substring(6))
            } else if (currentTime.count { it == ':' } == 2) {
                val parts = currentTime.split(":")
                Pair("${parts[0]}:${parts[1]}", parts[2])
            } else {
                val clean = if (currentTime.isEmpty()) "09:12" else currentTime
                Pair(clean, "00")
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = hoursMinutes,
                fontSize = if (isTablet) 54.sp else 44.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = com.example.ui.theme.SpaceGroteskFontFamily,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-1.5).sp,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = seconds,
                fontSize = if (isTablet) 24.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = com.example.ui.theme.SpaceGroteskFontFamily,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.alignByBaseline()
            )
        }

        val isMidnightCross = currentTime.startsWith("00:00")
        val formattedDate = remember(appLanguage, isMidnightCross) {
            try {
                val locale = if (appLanguage == AppLanguage.CA) java.util.Locale.forLanguageTag("ca-ES") else java.util.Locale.forLanguageTag("es-ES")
                java.text.SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", locale).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Europe/Madrid")
                }.format(java.util.Date())
            } catch (e: Exception) {
                if (appLanguage == AppLanguage.CA) "diumenge, 19 de juliol de 2026" else "domingo, 19 de julio de 2026"
            }
        }
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun WeatherConditionIcon(
    condition: WeatherCondition,
    isNight: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = condition.getIconUrl(isNight),
        contentDescription = condition.description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
    )
}

