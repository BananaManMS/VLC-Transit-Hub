package com.example.data.model

enum class WeatherCondition {
    SUNNY,
    CLEAR,
    CLOUDY,
    PARTLY_CLOUDY,
    DRIZZLE,
    LIGHT_RAIN,
    MODERATE_RAIN,
    HEAVY_RAIN,
    RAINY,
    STORMY,
    SNOWY,
    FOGGY,
    WINDY
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
        return WeatherData()
    }

    companion object {
        suspend fun getWeatherData(city: String): WeatherData {
            return WeatherData(cityName = city)
        }

        suspend fun getWeatherDataByCoords(lat: Double, lon: Double, city: String = "Valencia"): WeatherData {
            return WeatherData(cityName = city)
        }
    }
}
