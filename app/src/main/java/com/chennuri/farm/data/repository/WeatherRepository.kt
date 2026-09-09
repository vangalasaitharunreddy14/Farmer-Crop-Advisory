package com.chennuri.farm.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WeatherSnapshot(
    val temperatureCelsius: Double,
    val rainProbabilityNext24hPercent: Int,
    val description: String
)

class WeatherRepository(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Fetches current temperature from the /weather endpoint and derives a
     * 24h rain probability from the /forecast endpoint (3-hourly buckets,
     * we take the max pop% across the next 8 buckets = ~24h).
     */
    suspend fun fetchCurrentWeather(lat: Double, lon: Double): Result<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val currentUrl =
                    "https://api.openweathermap.org/data/2.5/weather" +
                            "?lat=$lat&lon=$lon&units=metric&appid=$apiKey"

                val forecastUrl =
                    "https://api.openweathermap.org/data/2.5/forecast" +
                            "?lat=$lat&lon=$lon&units=metric&appid=$apiKey&cnt=8"

                val currentJson = executeGet(currentUrl)
                val forecastJson = executeGet(forecastUrl)

                val temp = currentJson
                    .getJSONObject("main")
                    .getDouble("temp")

                val description = currentJson
                    .getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("description")

                var maxPop = 0.0
                val list = forecastJson.getJSONArray("list")
                for (i in 0 until list.length()) {
                    val pop = list.getJSONObject(i).optDouble("pop", 0.0)
                    if (pop > maxPop) maxPop = pop
                }

                Result.success(
                    WeatherSnapshot(
                        temperatureCelsius = temp,
                        rainProbabilityNext24hPercent = (maxPop * 100).toInt(),
                        description = description
                    )
                )
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun executeGet(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("OpenWeather request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body from OpenWeather")
            return JSONObject(body)
        }
    }
}