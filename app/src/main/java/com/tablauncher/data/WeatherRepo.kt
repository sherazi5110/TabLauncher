package com.tablauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

data class HourForecast(val label: String, val tempC: Double, val code: Int, val isDay: Boolean)
data class DayForecast(val label: String, val maxC: Double, val minC: Double, val code: Int)

data class WeatherData(
    val city: String,
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val highC: Double,
    val lowC: Double,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
    val fetchedAt: Long,
)

data class CityResult(val name: String, val detail: String, val lat: Double, val lon: Double)

enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

/** Weather from Open-Meteo (free, no account needed). */
class WeatherRepo(private val context: Context, private val settings: Settings) {
    private val _state = MutableStateFlow<WeatherData?>(null)
    val state: StateFlow<WeatherData?> = _state
    val needsLocation = MutableStateFlow(false)
    private var lastKey = ""

    suspend fun refresh(force: Boolean = false) {
        val s = settings.current
        val now = System.currentTimeMillis()
        val cur = _state.value
        var lat = s.weatherLat
        var lon = s.weatherLon
        var city = s.weatherCity
        if (lat == null || lon == null) {
            val loc = lastLocation()
            if (loc == null) {
                needsLocation.value = true
                return
            }
            lat = loc.first; lon = loc.second
            city = cityName(lat, lon) ?: "Current location"
        }
        needsLocation.value = false
        val key = "%.2f,%.2f".format(Locale.US, lat, lon)
        if (!force && cur != null && key == lastKey && now - cur.fetchedAt < 30 * 60_000) return
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=5"
        val body = Net.get(url) ?: return
        try {
            val o = JSONObject(body)
            val current = o.getJSONObject("current")
            val hourly = o.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val curTime = current.getString("time").take(13)
            var start = 0
            for (i in 0 until times.length()) if (times.getString(i).take(13) == curTime) { start = i; break }
            val hours = (start until (start + 7).coerceAtMost(times.length())).map { i ->
                val t = times.getString(i)
                val h = t.substring(11, 13).toInt()
                HourForecast(
                    label = if (i == start) "Now" else hourLabel(h),
                    tempC = hourly.getJSONArray("temperature_2m").getDouble(i),
                    code = hourly.getJSONArray("weather_code").getInt(i),
                    isDay = hourly.getJSONArray("is_day").getInt(i) == 1,
                )
            }
            val daily = o.getJSONObject("daily")
            val dTimes = daily.getJSONArray("time")
            val days = (0 until dTimes.length()).map { i ->
                val date = java.time.LocalDate.parse(dTimes.getString(i))
                DayForecast(
                    label = if (i == 0) "Today" else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                    maxC = daily.getJSONArray("temperature_2m_max").getDouble(i),
                    minC = daily.getJSONArray("temperature_2m_min").getDouble(i),
                    code = daily.getJSONArray("weather_code").getInt(i),
                )
            }
            lastKey = key
            _state.value = WeatherData(
                city = city ?: "",
                tempC = current.getDouble("temperature_2m"),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
                highC = days.firstOrNull()?.maxC ?: 0.0,
                lowC = days.firstOrNull()?.minC ?: 0.0,
                hours = hours,
                days = days,
                fetchedAt = now,
            )
        } catch (e: Exception) {
            // Keep the previous data.
        }
    }

    private fun hourLabel(h: Int) = when {
        h == 0 -> "12AM"
        h < 12 -> "${h}AM"
        h == 12 -> "12PM"
        else -> "${h - 12}PM"
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return loc.latitude to loc.longitude
    }

    private suspend fun cityName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchCity(query: String): List<CityResult> {
        if (query.isBlank()) return emptyList()
        val body = Net.get("https://geocoding-api.open-meteo.com/v1/search?name=${Net.enc(query)}&count=8&language=en&format=json")
            ?: return emptyList()
        return try {
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                CityResult(
                    name = r.getString("name"),
                    detail = listOfNotNull(r.optString("admin1").takeIf { it.isNotBlank() }, r.optString("country").takeIf { it.isNotBlank() })
                        .joinToString(", "),
                    lat = r.getDouble("latitude"),
                    lon = r.getDouble("longitude"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun kind(code: Int): WeatherKind = when (code) {
            0 -> WeatherKind.CLEAR
            1, 2 -> WeatherKind.PARTLY
            3 -> WeatherKind.CLOUDY
            45, 48 -> WeatherKind.FOG
            in 51..67, in 80..82 -> WeatherKind.RAIN
            in 71..77, 85, 86 -> WeatherKind.SNOW
            in 95..99 -> WeatherKind.STORM
            else -> WeatherKind.CLOUDY
        }

        fun describe(code: Int): String = when (kind(code)) {
            WeatherKind.CLEAR -> "Clear"
            WeatherKind.PARTLY -> "Partly cloudy"
            WeatherKind.CLOUDY -> "Cloudy"
            WeatherKind.FOG -> "Fog"
            WeatherKind.RAIN -> if (code in 51..57) "Drizzle" else "Rain"
            WeatherKind.SNOW -> "Snow"
            WeatherKind.STORM -> "Thunderstorm"
        }
    }
}
