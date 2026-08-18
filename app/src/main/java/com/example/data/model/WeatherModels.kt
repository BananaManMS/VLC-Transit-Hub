package com.example.data.model

import android.util.Log
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class WeatherCondition(val description: String, val iconName: String) {
    SUNNY("Sunny", "sunny"),
    PARTLY_CLOUDY("Partly Cloudy", "partly_cloudy"),
    CLOUDY("Cloudy", "cloudy"),
    RAINY("Rainy", "rainy"),
    STORMY("Stormy", "thunderstorm"),
    WINDY("Windy", "air")
}

data class ForecastHour(
    val time: String,
    val tempCelsius: Int,
    val condition: WeatherCondition
)

data class WeatherData(
    val cityName: String,
    val currentTempCelsius: Int,
    val condition: WeatherCondition,
    val humidityPercent: Int,
    val windKmh: Int,
    val precipitationChancePercent: Int,
    val hourlyForecast: List<ForecastHour>,
    val willRainTodayOrTomorrow: Boolean = false,
    val rainProbabilityToday: Int = 0,
    val rainProbabilityTomorrow: Int = 0,
    val minTempCustomCelsius: Int? = null,
    val maxTempCustomCelsius: Int? = null
) {
    fun currentTempFahrenheit(): Int = (currentTempCelsius * 9 / 5) + 32
    fun minTempCelsius(): Int = minTempCustomCelsius ?: (currentTempCelsius - 4)
    fun maxTempCelsius(): Int = maxTempCustomCelsius ?: (currentTempCelsius + 3)
    fun minTempFahrenheit(): Int = (minTempCelsius() * 9 / 5) + 32
    fun maxTempFahrenheit(): Int = (maxTempCelsius() * 9 / 5) + 32
}

object WeatherService {
    private const val TAG = "WeatherService"

    // Thread-safe Cache to prevent 429 Rate Limits from Open-Meteo
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, WeatherData>>()
    private const val CACHE_EXPIRATION_MS = 15 * 60 * 1000L // 15 minutes cache

    private fun getCachedData(key: String, ignoreExpiry: Boolean = false): WeatherData? {
        val cached = memoryCache[key.lowercase().trim()] ?: return null
        if (ignoreExpiry) {
            return cached.second
        }
        val age = System.currentTimeMillis() - cached.first
        if (age < CACHE_EXPIRATION_MS) {
            return cached.second
        }
        return null
    }

    private fun putCachedData(key: String, data: WeatherData) {
        val cleanKey = key.lowercase().trim()
        if (cleanKey.isNotEmpty()) {
            memoryCache[cleanKey] = Pair(System.currentTimeMillis(), data)
        }
    }

    private fun mapWmoToCondition(code: Int): WeatherCondition {
        return when (code) {
            0 -> WeatherCondition.SUNNY
            1, 2, 3 -> WeatherCondition.PARTLY_CLOUDY
            45, 48 -> WeatherCondition.CLOUDY
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherCondition.RAINY
            95, 96, 99 -> WeatherCondition.STORMY
            else -> WeatherCondition.CLOUDY
        }
    }

    private fun fetchUrl(urlString: String): String {
        val request = okhttp3.Request.Builder()
            .url(urlString)
            .build()
        
        val client = com.example.data.network.NetworkModule.okHttpClient.newBuilder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.string() ?: ""
            } else {
                throw java.io.IOException("HTTP Error: ${response.code}")
            }
        }
    }

    suspend fun getWeatherData(city: String, seed: Long = System.currentTimeMillis()): WeatherData? = withContext(Dispatchers.IO) {
        val cached = getCachedData(city)
        if (cached != null) {
            return@withContext cached
        }

        val cityLower = city.lowercase().trim()
        val predefinedCoords = when {
            cityLower.startsWith("valencia") -> Pair(39.46975, -0.37739)
            cityLower.startsWith("madrid") -> Pair(40.41678, -3.70379)
            cityLower.startsWith("barcelona") -> Pair(41.38879, 2.15899)
            cityLower.startsWith("sevilla") -> Pair(37.38283, -5.97317)
            cityLower.startsWith("málaga") || cityLower.startsWith("malaga") -> Pair(36.72016, -4.42034)
            cityLower.startsWith("bilbao") -> Pair(43.26271, -2.92528)
            cityLower.startsWith("zaragoza") -> Pair(41.65606, -0.87734)
            cityLower.startsWith("alicante") -> Pair(38.34517, -0.48149)
            cityLower.startsWith("vigo") -> Pair(42.23282, -8.72264)
            cityLower.startsWith("gijón") || cityLower.startsWith("gijon") -> Pair(43.53573, -5.66152)
            cityLower.startsWith("london") || cityLower.startsWith("londres") -> Pair(51.50853, -0.12574)
            cityLower.startsWith("paris") || cityLower.startsWith("parís") -> Pair(48.85341, 2.3488)
            cityLower.startsWith("new york") || cityLower.startsWith("nueva york") -> Pair(40.71427, -74.00597)
            else -> null
        }

        if (predefinedCoords != null) {
            val resolvedName = city.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            try {
                val realData = getWeatherDataByCoords(predefinedCoords.first, predefinedCoords.second, resolvedName)
                if (realData != null && (realData.currentTempCelsius != 0 || realData.hourlyForecast.isNotEmpty())) {
                    putCachedData(city, realData)
                    return@withContext realData
                }
            } catch (e: Exception) {
                if (e.message?.contains("429") == true) {
                    Log.w(TAG, "Rate limited (429) fetching predefined coords for $city. Using cache.")
                } else {
                    Log.w(TAG, "Error fetching live weather by predefined coords for $city: ${e.message}")
                }
            }
        }

        try {
            // Step 1: Geocode city using Open-Meteo Geocoding API
            val encCity = URLEncoder.encode(city, "UTF-8")
            val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encCity&count=1&language=es&format=json"
            val geoJsonStr = fetchUrl(geocodeUrl)
            val geoJson = JSONObject(geoJsonStr)
            val results = geoJson.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val firstResult = results.getJSONObject(0)
                val lat = firstResult.getDouble("latitude")
                val lon = firstResult.getDouble("longitude")
                val resolvedName = firstResult.optString("name", city)
                val realData = getWeatherDataByCoords(lat, lon, resolvedName)
                if (realData != null) {
                    putCachedData(city, realData)
                    putCachedData(resolvedName, realData)
                    return@withContext realData
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("429") == true) {
                Log.w(TAG, "Rate limited (429) geocoding/fetching for $city. Using cache.")
            } else {
                Log.w(TAG, "Error geocoding or fetching live weather for $city: ${e.message}")
            }
        }

        val fallback = getCachedData(city, ignoreExpiry = true)
        if (fallback != null) {
            return@withContext fallback
        }
        return@withContext null
    }

    suspend fun getWeatherDataByCoords(latitude: Double, longitude: Double, cityName: String): WeatherData? = withContext(Dispatchers.IO) {
        val cached = getCachedData(cityName)
        if (cached != null) {
            return@withContext cached
        }

        try {
            val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,weather_code&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=2&timezone=auto"
            val forecastJsonStr = fetchUrl(forecastUrl)
            val root = JSONObject(forecastJsonStr)
            
            val current = root.getJSONObject("current")
            val currentTemp = current.getDouble("temperature_2m").toInt()
            val humidity = current.getInt("relative_humidity_2m")
            val windSpeed = current.getDouble("wind_speed_10m").toInt()
            val precipitation = current.getDouble("precipitation")
            val wmoCode = current.getInt("weather_code")
            val currentTimeIso = current.optString("time", "")
            
            val condition = mapWmoToCondition(wmoCode)
            val precipitationChance = if (precipitation > 0) (precipitation * 100).toInt().coerceIn(10, 100) else 0
 
            val forecastList = mutableListOf<ForecastHour>()
            val hourlyObj = root.optJSONObject("hourly")
            if (hourlyObj != null) {
                val times = hourlyObj.optJSONArray("time")
                val temps = hourlyObj.optJSONArray("temperature_2m")
                val codes = hourlyObj.optJSONArray("weather_code")
                if (times != null && temps != null && codes != null) {
                    var startIdx = 0
                    if (currentTimeIso.isNotEmpty() && currentTimeIso.length >= 13) {
                        val currentHourPrefix = currentTimeIso.substring(0, 13)
                        for (i in 0 until times.length()) {
                            if (times.optString(i, "").startsWith(currentHourPrefix)) {
                                startIdx = i
                                break
                            }
                        }
                    }
                    val maxHours = Math.min(startIdx + 24, times.length())
                    for (i in startIdx until maxHours) {
                        val isoTime = times.optString(i, "")
                        val displayTime = if (isoTime.length >= 16) isoTime.substring(11, 16) else isoTime
                        val temp = temps.optDouble(i, currentTemp.toDouble()).toInt()
                        val code = codes.optInt(i, wmoCode)
                        forecastList.add(ForecastHour(displayTime, temp, mapWmoToCondition(code)))
                    }
                }
            }

            var willRainTodayOrTomorrow = false
            var rainProbabilityToday = 0
            var rainProbabilityTomorrow = 0
            var minTempCustom: Int? = null
            var maxTempCustom: Int? = null
            
            val dailyObj = root.optJSONObject("daily")
            if (dailyObj != null) {
                val dailyCodes = dailyObj.optJSONArray("weather_code")
                val dailyProbabilities = dailyObj.optJSONArray("precipitation_probability_max")
                val dailyMinTemps = dailyObj.optJSONArray("temperature_2m_min")
                val dailyMaxTemps = dailyObj.optJSONArray("temperature_2m_max")
                
                if (dailyCodes != null) {
                    for (i in 0 until dailyCodes.length()) {
                        val code = dailyCodes.optInt(i, 0)
                        val cond = mapWmoToCondition(code)
                        if (cond == WeatherCondition.RAINY || cond == WeatherCondition.STORMY) {
                            willRainTodayOrTomorrow = true
                        }
                    }
                }
                if (dailyProbabilities != null) {
                    rainProbabilityToday = dailyProbabilities.optInt(0, 0)
                    rainProbabilityTomorrow = dailyProbabilities.optInt(1, 0)
                    if (rainProbabilityToday >= 30 || rainProbabilityTomorrow >= 30) {
                        willRainTodayOrTomorrow = true
                    }
                }
                if (dailyMinTemps != null && dailyMinTemps.length() > 0) {
                    minTempCustom = dailyMinTemps.optDouble(0, currentTemp.toDouble() - 4.0).toInt()
                }
                if (dailyMaxTemps != null && dailyMaxTemps.length() > 0) {
                    maxTempCustom = dailyMaxTemps.optDouble(0, currentTemp.toDouble() + 3.0).toInt()
                }
            }

            val result = WeatherData(
                cityName = cityName,
                currentTempCelsius = currentTemp,
                condition = condition,
                humidityPercent = humidity,
                windKmh = windSpeed,
                precipitationChancePercent = precipitationChance,
                hourlyForecast = forecastList,
                willRainTodayOrTomorrow = willRainTodayOrTomorrow,
                rainProbabilityToday = rainProbabilityToday,
                rainProbabilityTomorrow = rainProbabilityTomorrow,
                minTempCustomCelsius = minTempCustom,
                maxTempCustomCelsius = maxTempCustom
            )
            putCachedData(cityName, result)
            return@withContext result
        } catch (e: Exception) {
            val is429 = e.message?.contains("429") == true
            if (is429) {
                Log.w(TAG, "Rate limited (429) fetching live weather by coords for $cityName. Using cache fallback.")
            } else {
                Log.w(TAG, "Error fetching live weather by coords for $cityName: ${e.message}")
            }
            val fallback = getCachedData(cityName, ignoreExpiry = true)
            if (fallback != null) {
                return@withContext fallback
            }
            return@withContext null
        }
    }

    private fun getSimulatedWeather(city: String, seed: Long = System.currentTimeMillis()): WeatherData {
        val cleanCity = city.trim()
        val cityLower = cleanCity.lowercase()
        val random = Random(cityLower.hashCode() + seed)

        val month = try {
            java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        } catch (e: Exception) { 7 } // Default to August (7)

        val seasonalFactor = Math.cos((month - 7) * Math.PI / 6.0)

        // August and January High/Low climatological normals for key cities
        val hAug: Double
        val lAug: Double
        val hJan: Double
        val lJan: Double
        when (cityLower) {
            "valencia" -> { hAug = 31.0; lAug = 22.0; hJan = 16.5; lJan = 7.0 }
            "madrid" -> { hAug = 33.0; lAug = 19.0; hJan = 10.0; lJan = 2.5 }
            "barcelona" -> { hAug = 29.0; lAug = 20.5; hJan = 14.0; lJan = 5.0 }
            "sevilla" -> { hAug = 36.0; lAug = 20.5; hJan = 16.0; lJan = 6.0 }
            "málaga", "malaga" -> { hAug = 31.0; lAug = 21.0; hJan = 17.0; lJan = 8.0 }
            "bilbao" -> { hAug = 26.0; lAug = 16.0; hJan = 13.0; lJan = 5.0 }
            "zaragoza" -> { hAug = 32.5; lAug = 18.5; hJan = 10.5; lJan = 2.5 }
            "alicante" -> { hAug = 31.5; lAug = 21.5; hJan = 17.0; lJan = 6.5 }
            "vigo" -> { hAug = 24.5; lAug = 15.0; hJan = 12.0; lJan = 6.0 }
            "gijón", "gijon" -> { hAug = 23.5; lAug = 16.0; hJan = 13.0; lJan = 7.5 }
            "london", "londres" -> { hAug = 23.0; lAug = 14.0; hJan = 8.0; lJan = 2.5 }
            "paris", "parís" -> { hAug = 25.5; lAug = 15.5; hJan = 7.5; lJan = 2.5 }
            "new york", "nueva york" -> { hAug = 29.0; lAug = 21.0; hJan = 4.0; lJan = -3.0 }
            else -> { hAug = 26.0; lAug = 16.0; hJan = 9.0; lJan = 2.0 }
        }

        val maxTemp = ( (hAug + hJan) / 2.0 + ((hAug - hJan) / 2.0) * seasonalFactor ).toInt()
        val minTemp = ( (lAug + lJan) / 2.0 + ((lAug - lJan) / 2.0) * seasonalFactor ).toInt()

        // Deterministic condition based on city and month
        val currentCondition = when {
            cityLower == "valencia" || cityLower == "alicante" || cityLower == "málaga" || cityLower == "malaga" -> {
                if (month in 5..8) WeatherCondition.SUNNY else if (random.nextBoolean()) WeatherCondition.SUNNY else WeatherCondition.PARTLY_CLOUDY
            }
            cityLower == "london" || cityLower == "londres" || cityLower == "gijón" || cityLower == "gijon" -> {
                if (random.nextInt(100) < 40) WeatherCondition.RAINY else if (random.nextBoolean()) WeatherCondition.CLOUDY else WeatherCondition.PARTLY_CLOUDY
            }
            else -> {
                if (month in 5..8) WeatherCondition.SUNNY else if (random.nextInt(100) < 25) WeatherCondition.RAINY else WeatherCondition.PARTLY_CLOUDY
            }
        }

        val currentHour = try {
            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        } catch (e: Exception) { 12 }

        val currentSinVal = Math.sin((currentHour.toDouble() - 10.5) * Math.PI / 12.0)
        val currentTemp = ( (maxTemp + minTemp) / 2.0 + ((maxTemp - minTemp) / 2.0) * currentSinVal ).toInt()
        val currentHumidity = ( 75.0 - 20.0 * currentSinVal ).toInt().coerceIn(10, 100)

        val forecastList = mutableListOf<ForecastHour>()
        for (i in 0 until 24) {
            val hour = (currentHour + i) % 24
            val hourStr = String.format("%02d:00", hour)
            
            // Calculate temperature using the perfect diurnal sine curve
            val hourSinVal = Math.sin((hour.toDouble() - 10.5) * Math.PI / 12.0)
            val hourlyTemp = ( (maxTemp + minTemp) / 2.0 + ((maxTemp - minTemp) / 2.0) * hourSinVal ).toInt()
            
            val hourCondition = if (i == 0) {
                currentCondition
            } else {
                // Keep the condition mostly stable, change occasionally
                if (random.nextInt(100) < 80) currentCondition else {
                    val secondaryConds = when (currentCondition) {
                        WeatherCondition.SUNNY -> listOf(WeatherCondition.SUNNY, WeatherCondition.PARTLY_CLOUDY)
                        WeatherCondition.RAINY -> listOf(WeatherCondition.RAINY, WeatherCondition.CLOUDY)
                        WeatherCondition.STORMY -> listOf(WeatherCondition.STORMY, WeatherCondition.RAINY)
                        else -> listOf(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY)
                    }
                    secondaryConds[random.nextInt(secondaryConds.size)]
                }
            }

            forecastList.add(
                ForecastHour(
                    time = hourStr,
                    tempCelsius = hourlyTemp,
                    condition = hourCondition
                )
            )
        }

        val rainProbabilityToday = when (currentCondition) {
            WeatherCondition.RAINY -> 80 + random.nextInt(15)
            WeatherCondition.STORMY -> 90 + random.nextInt(10)
            WeatherCondition.CLOUDY -> 20 + random.nextInt(20)
            WeatherCondition.PARTLY_CLOUDY -> 10 + random.nextInt(15)
            else -> random.nextInt(10)
        }
        val rainProbabilityTomorrow = (rainProbabilityToday + random.nextInt(-20, 20)).coerceIn(0, 100)
        val willRainTodayOrTomorrow = rainProbabilityToday >= 30 || rainProbabilityTomorrow >= 30

        return WeatherData(
            cityName = cleanCity.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            currentTempCelsius = currentTemp,
            condition = currentCondition,
            humidityPercent = currentHumidity,
            windKmh = if (currentCondition == WeatherCondition.STORMY) 35 + random.nextInt(20) else 10 + random.nextInt(15),
            precipitationChancePercent = rainProbabilityToday,
            hourlyForecast = forecastList,
            willRainTodayOrTomorrow = willRainTodayOrTomorrow,
            rainProbabilityToday = rainProbabilityToday,
            rainProbabilityTomorrow = rainProbabilityTomorrow,
            minTempCustomCelsius = minTemp,
            maxTempCustomCelsius = maxTemp
        )
    }
}
