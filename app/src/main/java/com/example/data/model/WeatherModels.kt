package com.example.data.model

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

enum class WeatherCondition(val description: String, val iconCode: String) {
    SUNNY("Sunny", "01"),
    PARTLY_CLOUDY("Partly Cloudy", "02"),
    CLOUDY("Cloudy", "04"),
    DRIZZLE("Drizzle", "09"),
    LIGHT_RAIN("Light Rain", "10"),
    MODERATE_RAIN("Moderate Rain", "09"),
    HEAVY_RAIN("Heavy Rain", "09"),
    RAINY("Rainy", "10"),
    STORMY("Stormy", "11"),
    WINDY("Windy", "50"),
    SNOWY("Snowy", "13"),
    FOGGY("Foggy", "50");

    fun getIconUrl(isNight: Boolean = false): String {
        val suffix = if (isNight) "n" else "d"
        return "https://openweathermap.org/img/wn/$iconCode$suffix@4x.png"
    }
}

data class ForecastHour(
    val hour: Int = 12,
    val occupancyPercentage: Int = 30,
    val time: String = "$hour:00",
    val tempCelsius: Double = 22.0,
    val condition: WeatherCondition = WeatherCondition.SUNNY
) {
    constructor(time: String, tempCelsius: Int, condition: WeatherCondition = WeatherCondition.SUNNY) : this(
        hour = time.takeWhile { it.isDigit() }.toIntOrNull() ?: 12,
        occupancyPercentage = 30,
        time = time,
        tempCelsius = tempCelsius.toDouble(),
        condition = condition
    )

    constructor(time: String, tempCelsius: Double, condition: WeatherCondition = WeatherCondition.SUNNY) : this(
        hour = time.takeWhile { it.isDigit() }.toIntOrNull() ?: 12,
        occupancyPercentage = 30,
        time = time,
        tempCelsius = tempCelsius,
        condition = condition
    )
}

data class WeatherData(
    val cityName: String = "Valencia",
    val currentTempCelsius: Int = 22,
    val condition: WeatherCondition = WeatherCondition.SUNNY,
    val humidityPercent: Int = 50,
    val windKmh: Int = 12,
    val precipitationChancePercent: Int = 10,
    val hourlyForecast: List<ForecastHour> = emptyList(),
    val willRainTodayOrTomorrow: Boolean = false,
    val rainProbabilityToday: Int = 0,
    val rainProbabilityTomorrow: Int = 0,
    val minTempCustomCelsius: Int? = null,
    val maxTempCustomCelsius: Int? = null
) {
    fun currentTempFahrenheit(): Int = (currentTempCelsius * 9 / 5) + 32
    fun minTempCelsius(): Int = minTempCustomCelsius ?: (currentTempCelsius - 4)
    fun maxTempCelsius(): Int = maxTempCustomCelsius ?: (currentTempCelsius + 4)
    fun minTempFahrenheit(): Int = (minTempCelsius() * 9 / 5) + 32
    fun maxTempFahrenheit(): Int = (maxTempCelsius() * 9 / 5) + 32

    val currentTempFahrenheit: Double get() = currentTempFahrenheit().toDouble()
    val minTempCelsius: Double get() = minTempCelsius().toDouble()
    val maxTempCelsius: Double get() = maxTempCelsius().toDouble()
    val minTempFahrenheit: Double get() = minTempFahrenheit().toDouble()
    val maxTempFahrenheit: Double get() = maxTempFahrenheit().toDouble()
    val temperatureCelsius: Double get() = currentTempCelsius.toDouble()
    val humidityPercentage: Int get() = humidityPercent
    val description: String get() = condition.description
    fun getIconUrl(isNight: Boolean = false): String = condition.getIconUrl(isNight)
}

object WeatherService {
    private val cache = ConcurrentHashMap<String, Pair<Long, WeatherData>>()
    private const val CACHE_EXPIRATION_MS = 900_000L

    fun getCachedData(key: String, ignoreExpiry: Boolean = false): WeatherData? {
        val entry = cache[key] ?: return null
        if (!ignoreExpiry && System.currentTimeMillis() - entry.first > CACHE_EXPIRATION_MS) {
            return null
        }
        return entry.second
    }

    fun putCachedData(key: String, data: WeatherData) {
        cache[key] = Pair(System.currentTimeMillis(), data)
    }

    fun mapWmoToCondition(code: Int): WeatherCondition {
        return when (code) {
            0, 1 -> WeatherCondition.SUNNY
            2 -> WeatherCondition.PARTLY_CLOUDY
            3 -> WeatherCondition.CLOUDY
            45, 48 -> WeatherCondition.FOGGY
            51, 53, 55 -> WeatherCondition.DRIZZLE
            61, 63 -> WeatherCondition.LIGHT_RAIN
            65 -> WeatherCondition.HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY
            80, 81 -> WeatherCondition.MODERATE_RAIN
            82 -> WeatherCondition.HEAVY_RAIN
            95, 96, 99 -> WeatherCondition.STORMY
            else -> WeatherCondition.SUNNY
        }
    }

    suspend fun getWeatherData(city: String, seed: Long = 0L): WeatherData {
        return getWeatherDataByCoords(39.46975, -0.37639, city)
    }

    suspend fun getWeatherDataByCoords(
        latitude: Double,
        longitude: Double,
        cityName: String = "Valencia"
    ): WeatherData = withContext(Dispatchers.IO) {
        val cacheKey = "${latitude}_${longitude}"
        getCachedData(cacheKey)?.let { return@withContext it }

        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&hourly=temperature_2m,relative_humidity_2m,weathercode,precipitation_probability&timezone=Europe%2FMadrid"
            val request = Request.Builder().url(url).build()
            val response = com.example.data.network.NetworkModule.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                if (bodyStr.isNotBlank()) {
                    val json = JSONObject(bodyStr)
                    val current = json.getJSONObject("current_weather")
                    val currentTemp = current.getDouble("temperature").toInt()
                    val weatherCode = current.getInt("weathercode")
                    val windSpeed = current.getDouble("windspeed").toInt()
                    val condition = mapWmoToCondition(weatherCode)

                    val hourly = json.getJSONObject("hourly")
                    val times = hourly.getJSONArray("time")
                    val temps = hourly.getJSONArray("temperature_2m")
                    val codes = hourly.getJSONArray("weathercode")
                    val precips = hourly.optJSONArray("precipitation_probability")

                    val forecastList = mutableListOf<ForecastHour>()
                    val count = minOf(24, times.length())
                    var totalPrecip = 0
                    for (i in 0 until count) {
                        val timeStr = times.getString(i).substringAfter("T").take(5)
                        val temp = temps.getDouble(i).toInt()
                        val code = codes.getInt(i)
                        forecastList.add(ForecastHour(timeStr, temp, mapWmoToCondition(code)))
                        if (precips != null && i < precips.length()) {
                            totalPrecip += precips.getInt(i)
                        }
                    }
                    val avgPrecip = if (count > 0) totalPrecip / count else 10
                    val weatherData = WeatherData(
                        cityName = cityName,
                        currentTempCelsius = currentTemp,
                        condition = condition,
                        humidityPercent = 55,
                        windKmh = windSpeed,
                        precipitationChancePercent = avgPrecip,
                        hourlyForecast = forecastList,
                        willRainTodayOrTomorrow = avgPrecip > 40,
                        rainProbabilityToday = avgPrecip,
                        rainProbabilityTomorrow = (avgPrecip * 0.8).toInt()
                    )
                    putCachedData(cacheKey, weatherData)
                    return@withContext weatherData
                }
            }
        } catch (_: Exception) { }

        getCachedData(cacheKey, ignoreExpiry = true) ?: WeatherData(cityName = cityName)
    }

    suspend fun getCurrentWeather(): WeatherData {
        return getWeatherDataByCoords(39.46975, -0.37639, "Valencia")
    }
}
