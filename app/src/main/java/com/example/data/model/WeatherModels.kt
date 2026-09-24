package com.example.data.model

enum class WeatherCondition(val description: String) {
    SUNNY("Soleado"),
    CLEAR("Despejado"),
    CLOUDY("Nublado"),
    PARTLY_CLOUDY("Parcialmente nublado"),
    DRIZZLE("Llovizna"),
    LIGHT_RAIN("Lluvia ligera"),
    MODERATE_RAIN("Lluvia moderada"),
    HEAVY_RAIN("Lluvia fuerte"),
    RAINY("Lluvia"),
    STORMY("Tormentoso"),
    SNOWY("Nieve"),
    FOGGY("Niebla"),
    WINDY("Ventoso");

    fun getIconUrl(isNight: Boolean = false): String {
        return ""
    }
}

data class WeatherData(
    val cityName: String = "Valencia",
    val currentTempCelsius: Double = 22.0,
    val currentTempFahrenheit: Double = currentTempCelsius * 9 / 5 + 32,
    val minTempCelsius: Double = 18.0,
    val minTempFahrenheit: Double = minTempCelsius * 9 / 5 + 32,
    val maxTempCelsius: Double = 26.0,
    val maxTempFahrenheit: Double = maxTempCelsius * 9 / 5 + 32,
    val condition: WeatherCondition = WeatherCondition.SUNNY,
    val description: String = "Soleado",
    val windKmh: Double = 12.0,
    val humidityPercent: Int = 50,
    val precipitationChancePercent: Int = 10,
    val hourlyForecast: List<ForecastHour> = emptyList()
) {
    val temperatureCelsius: Double get() = currentTempCelsius
    val humidityPercentage: Int get() = humidityPercent
    fun getIconUrl(): String = ""
}

class WeatherService {
    suspend fun getCurrentWeather(): WeatherData {
        return getWeatherDataByCoords(39.46975, -0.37639, "Valencia")
    }

    companion object {
        suspend fun getWeatherData(city: String): WeatherData {
            return getWeatherDataByCoords(39.46975, -0.37639, city)
        }

        suspend fun getWeatherDataByCoords(lat: Double, lon: Double, city: String = "Valencia"): WeatherData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&hourly=temperature_2m,relative_humidity_2m,weathercode,precipitation_probability&timezone=Europe%2FMadrid"
                val request = okhttp3.Request.Builder().url(url).build()
                val response = com.example.data.network.NetworkModule.okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotBlank()) {
                        val root = org.json.JSONObject(bodyStr)
                        val currentWeather = root.optJSONObject("current_weather")
                        if (currentWeather != null) {
                            val temp = currentWeather.optDouble("temperature", 22.0)
                            val windSpeed = currentWeather.optDouble("windspeed", 12.0)
                            val wCode = currentWeather.optInt("weathercode", 0)

                            val (cond, desc) = mapWmoCode(wCode)

                            val hourly = root.optJSONObject("hourly")
                            val times = hourly?.optJSONArray("time")
                            val temps = hourly?.optJSONArray("temperature_2m")
                            val hums = hourly?.optJSONArray("relative_humidity_2m")
                            val precipChances = hourly?.optJSONArray("precipitation_probability")

                            val hourlyList = mutableListOf<ForecastHour>()
                            var currentHum = 50
                            var currentPrecipChance = 10

                            if (times != null && temps != null) {
                                val cal = java.util.Calendar.getInstance()
                                val curHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                currentHum = hums?.optInt(curHour, 50) ?: 50
                                currentPrecipChance = precipChances?.optInt(curHour, 10) ?: 10

                                for (i in 0 until minOf(24, times.length())) {
                                    val tStr = times.optString(i, "")
                                    val hTemp = temps.optDouble(i, temp)
                                    val hourNum = tStr.substringAfter("T").substringBefore(":").toIntOrNull() ?: i
                                    val formattedTime = String.format(java.util.Locale.getDefault(), "%02d:00", hourNum)
                                    hourlyList.add(
                                        ForecastHour(
                                            hour = hourNum,
                                            occupancyPercentage = 30,
                                            time = formattedTime,
                                            tempCelsius = hTemp,
                                            condition = cond
                                        )
                                    )
                                }
                            }

                            val minT = hourlyList.minOfOrNull { it.tempCelsius } ?: (temp - 3)
                            val maxT = hourlyList.maxOfOrNull { it.tempCelsius } ?: (temp + 4)

                            return@withContext WeatherData(
                                cityName = city,
                                currentTempCelsius = temp,
                                minTempCelsius = minT,
                                maxTempCelsius = maxT,
                                condition = cond,
                                description = desc,
                                windKmh = windSpeed,
                                humidityPercent = currentHum,
                                precipitationChancePercent = currentPrecipChance,
                                hourlyForecast = hourlyList
                            )
                        }
                    }
                }
                response.close()
            } catch (e: Exception) {
                android.util.Log.w("WeatherService", "Error fetching weather data: ${e.message}")
            }

            WeatherData(cityName = city)
        }

        private fun mapWmoCode(code: Int): Pair<WeatherCondition, String> {
            return when (code) {
                0 -> Pair(WeatherCondition.SUNNY, "Soleado")
                1 -> Pair(WeatherCondition.CLEAR, "Despejado")
                2 -> Pair(WeatherCondition.PARTLY_CLOUDY, "Parcialmente nublado")
                3 -> Pair(WeatherCondition.CLOUDY, "Nublado")
                45, 48 -> Pair(WeatherCondition.FOGGY, "Niebla")
                51, 53, 55 -> Pair(WeatherCondition.DRIZZLE, "Llovizna")
                61, 63 -> Pair(WeatherCondition.LIGHT_RAIN, "Lluvia ligera")
                65 -> Pair(WeatherCondition.HEAVY_RAIN, "Lluvia fuerte")
                80, 81, 82 -> Pair(WeatherCondition.RAINY, "Chubascos")
                95, 96, 99 -> Pair(WeatherCondition.STORMY, "Tormenta")
                else -> Pair(WeatherCondition.SUNNY, "Soleado")
            }
        }
    }
}
