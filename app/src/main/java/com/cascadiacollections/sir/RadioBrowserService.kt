package com.cascadiacollections.sir

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class RadioBrowserStation(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val favicon: String? = null,
    val bitrate: Int = 0,
    val codec: String = "",
    @SerialName("country_code")
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

    private val httpClient = OkHttpClient()

    suspend fun searchStations(query: String, limit: Int = 30): Result<List<RadioBrowserStation>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val encodedQuery = query.trim()
                    .replace(" ", "+")
                    .replace("&", "%26")
                    .replace("\"", "%22")

                val url = "$BASE_URL/json/stations/search?name=$encodedQuery&limit=$limit&hidebroken=true"
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
                    val stations = try {
                        kotlinx.serialization.json.Json.decodeFromString<List<RadioBrowserStation>>(body)
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON decode failed", e)
                        emptyList()
                    }

                    Result.success(stations)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                Result.failure(e)
            }
        }
}
