package com.example.pleoscar.shared

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class SearchedPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

object LocationSearchHelper {

    /**
     * 지역명("부산 광안리") 또는 구체적인 카페/가게명("광안리 스타벅스")을 검색해 좌표 목록을 반환합니다.
     */
    suspend fun searchPlaces(context: Context, query: String): List<SearchedPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val results = mutableListOf<SearchedPlace>()

        // 1순위: OpenStreetMap Nominatim API (구체적인 카페/상호명 + 지역명 지원)
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&addressdetails=1&limit=5&accept-language=ko"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PleoscarSoundDropApp/1.0")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(responseText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val displayName = obj.optString("display_name", "")
                    val namePart = obj.optString("name", "").ifBlank {
                        displayName.substringBefore(",").trim()
                    }
                    val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                    val lon = obj.optString("lon").toDoubleOrNull() ?: continue

                    results.add(
                        SearchedPlace(
                            name = namePart,
                            address = displayName,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w("LocationSearchHelper", "Nominatim 검색 실패, Geocoder로 전환: ${e.message}")
        }

        if (results.isNotEmpty()) return@withContext results

        // 2순위: Android 내장 Geocoder 백업 검색
        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.KOREA).getFromLocationName(trimmed, 5)
            addresses?.forEach { addr ->
                val fullAddr = addr.getAddressLine(0) ?: trimmed
                val shortName = addr.featureName ?: fullAddr.substringBefore(",")
                results.add(
                    SearchedPlace(
                        name = shortName,
                        address = fullAddr,
                        latitude = addr.latitude,
                        longitude = addr.longitude
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("LocationSearchHelper", "Geocoder 검색 실패", e)
        }

        return@withContext results
    }
}