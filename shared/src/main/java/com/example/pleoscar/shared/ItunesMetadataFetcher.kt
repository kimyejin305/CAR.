package com.example.pleoscar.shared

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ItunesMetadataFetcher {

    data class MediaAssets(
        val albumArtUrl: String,
        val previewUrl: String
    )

    suspend fun fetchTrackAssets(songTitle: String, artist: String): MediaAssets =
        withContext(Dispatchers.IO) {
            try {
                // 괄호 안 영문 표기 제거 후 깔끔한 검색어 생성 (예: "윤하 (YOUNHA)" -> "윤하")
                val cleanArtist = artist.replace(Regex("\\(.*?\\)"), "").trim()
                val query = URLEncoder.encode("$songTitle $cleanArtist", "UTF-8")
                val apiUrl =
                    "https://itunes.apple.com/search?term=$query&media=music&entity=song&limit=1"
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; SoundDrop)")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = connection.responseCode
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(responseText).getJSONArray("results")

                if (results.length() > 0) {
                    val firstItem = results.getJSONObject(0)
                    val rawArtUrl = firstItem.optString("artworkUrl100", "")
                    val highResArtUrl = rawArtUrl.replace("100x100bb", "600x600bb")
                    val previewUrl = firstItem.optString("previewUrl", "")

                    Log.d("PleoscarMedia", "iTunes 앨범 커버 확보 성공: $highResArtUrl")
                    return@withContext MediaAssets(
                        albumArtUrl = highResArtUrl,
                        previewUrl = previewUrl
                    )
                } else {
                    Log.w("PleoscarMedia", "iTunes 검색 결과 0건 (응답코드: $responseCode, 검색어: $songTitle $cleanArtist)")
                }
            } catch (e: Exception) {
                Log.w("PleoscarMedia", "iTunes 메타데이터 조회 실패: ${e.message}")
            }

            return@withContext MediaAssets(albumArtUrl = "", previewUrl = "")
        }
}