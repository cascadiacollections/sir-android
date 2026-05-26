package com.cascadiacollections.sir

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

@Serializable
data class RadioBrowserStation(
    @SerialName("stationuuid")
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val favicon: String? = null,
    val bitrate: Int = 0,
    val codec: String = "",
    @SerialName("countrycode")
    val countryCode: String = "",
    val tags: String = ""
) {
    val displayLabel: String
        get() = if (codec.isNotEmpty()) {
            "$name (${codec.uppercase()}, ${bitrate}kbps)"
        } else {
            name
        }
}

/**
 * HTTP client for radio-browser.info API.
 * No authentication required. Rate-limited to ~100 requests/minute.
 */
class RadioBrowserService {
    companion object {
        private const val TAG = "RadioBrowserService"
        private const val BASE_URL = "https://de1.api.radio-browser.info"
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    suspend fun searchStations(query: String, limit: Int = 30): Result<List<RadioBrowserStation>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val encodedQuery = query.trim()
                    .replace(" ", "+")
                    .replace("&", "%26")
                    .replace("\"", "%22")

                val url = "$BASE_URL/json/stations/search?name=$encodedQuery&limit=$limit&hidebroken=true"
                Log.d(TAG, "Searching: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "SIR-Android/1.0")
                    .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Search failed: ${resp.code}")
                        return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    }

                    val body = resp.body.string() ?: "[]"
                    Log.d(TAG, "Response body: $body")
                    
                    val stations = try {
                        val json = kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                        }
                        json.decodeFromString<List<RadioBrowserStation>>(body)
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON decode failed", e)
                        emptyList()
                    }

                    Log.d(TAG, "Parsed ${stations.size} stations")
                    Result.success(stations)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                Result.failure(e)
            }
        }
}
