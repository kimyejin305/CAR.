package com.example.pleoscar.shared.repository

import android.util.Log
import com.example.pleoscar.shared.model.MusicPin
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class SoundDropRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {
    private val auth = Firebase.auth
    private val pinsCollection = db.collection("musicPins")

    suspend fun signInAnonymously(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Firebase 로그인에 실패했습니다.")
    }

    // 1. 음악 핀 등록 (지도에 공유 + 내 수집함에도 자동 기록)
    suspend fun saveMusicPin(
        songTitle: String,
        artist: String,
        hint: String,
        placeName: String = "현재 드라이브 위치",
        latitude: Double,
        longitude: Double,
        albumArtUrl: String = "",
        previewUrl: String = ""
    ): String {
        require(songTitle.isNotBlank()) { "노래 제목을 입력하세요." }
        require(artist.isNotBlank()) { "가수 이름을 입력하세요." }

        val userId = signInAnonymously()
        val document = pinsCollection.document()

        val pinData = hashMapOf<String, Any>(
            "id" to document.id,
            "songTitle" to songTitle.trim(),
            "artist" to artist.trim(),
            "hint" to hint.trim(),
            "placeName" to placeName.trim(), // 🔥 사용자가 선택/검색한 실제 장소 이름이 여기에 안전하게 저장됩니다!
            "latitude" to latitude,
            "longitude" to longitude,
            "albumArtUrl" to albumArtUrl.trim(),
            "previewUrl" to previewUrl.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "userId" to userId
        )

        // 1) 지도용 컬렉션(musicPins)에 저장
        document.set(pinData).await()

        // 2) 내가 등록한 노래니까 내 수집함(collected_pins)에도 같이 저장
        val collectedData = hashMapOf<String, Any>(
            "originalPinId" to document.id,
            "songTitle" to songTitle.trim(),
            "artist" to artist.trim(),
            "hint" to hint.trim(),
            "placeName" to placeName.trim(),
            "albumArtUrl" to albumArtUrl.trim(),
            "previewUrl" to previewUrl.trim(),
            "latitude" to latitude,
            "longitude" to longitude,
            "collectedAt" to com.google.firebase.Timestamp.now(),
            "userId" to userId
        )
        db.collection("collected_pins").add(collectedData).await()

        return document.id
    }

    // 2. 지도에 띄울 모든 유저의 핀 조회
    suspend fun getMusicPins(): List<MusicPin> {
        signInAnonymously()
        val snapshot = pinsCollection.get().await()
        return snapshot.documents.map { doc ->
            MusicPin(
                id = doc.id,
                songTitle = doc.getString("songTitle") ?: "",
                artist = doc.getString("artist") ?: "",
                hint = doc.getString("hint") ?: "",
                placeName = doc.getString("placeName") ?: "",
                latitude = doc.getDouble("latitude") ?: 0.0,
                longitude = doc.getDouble("longitude") ?: 0.0,
                albumArtUrl = doc.getString("albumArtUrl") ?: "",
                previewUrl = doc.getString("previewUrl") ?: "",
                createdAt = doc.get("createdAt")
            )
        }
    }

    // 3. 내가 꼽은(등록한) 노래 목록 조회 (마이페이지용)
    suspend fun getMyRegisteredPins(): List<MusicPin> {
        val uid = signInAnonymously()
        val snapshot = pinsCollection.whereEqualTo("userId", uid).get().await()
        return snapshot.documents.map { doc ->
            MusicPin(
                id = doc.id,
                songTitle = doc.getString("songTitle") ?: "",
                artist = doc.getString("artist") ?: "",
                hint = doc.getString("hint") ?: "",
                placeName = doc.getString("placeName") ?: "",
                latitude = doc.getDouble("latitude") ?: 0.0,
                longitude = doc.getDouble("longitude") ?: 0.0,
                albumArtUrl = doc.getString("albumArtUrl") ?: "",
                previewUrl = doc.getString("previewUrl") ?: "",
                createdAt = doc.get("createdAt")
            )
        }
    }

    // 4. 남이 꼽은 노래를 내 수집함에 저장
    suspend fun collectMusicPin(pin: MusicPin) {
        val userId = signInAnonymously()
        val collectedData = hashMapOf(
            "originalPinId" to pin.id,
            "songTitle" to pin.songTitle,
            "artist" to pin.artist,
            "hint" to pin.hint,
            "placeName" to pin.placeName,
            "albumArtUrl" to pin.albumArtUrl,
            "previewUrl" to pin.previewUrl,
            "latitude" to pin.latitude,
            "longitude" to pin.longitude,
            "collectedAt" to com.google.firebase.Timestamp.now(),
            "userId" to userId
        )
        db.collection("collected_pins")
            .add(collectedData)
            .await()
    }

    // 5. 내가 수집한 노래 목록 조회 (안전 버전)
    suspend fun getCollectedPins(): List<MusicPin> {
        return try {
            val snapshot = db.collection("collected_pins")
                .get()
                .await()

            snapshot.documents.map { doc ->
                MusicPin(
                    id = doc.id,
                    songTitle = doc.getString("songTitle") ?: "",
                    artist = doc.getString("artist") ?: "",
                    hint = doc.getString("hint") ?: "",
                    placeName = doc.getString("placeName") ?: "수집된 장소",
                    albumArtUrl = doc.getString("albumArtUrl") ?: "",
                    previewUrl = doc.getString("previewUrl") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    createdAt = doc.get("collectedAt")
                )
            }
        } catch (e: Exception) {
            Log.e("PleoscarCollected", "수집 목록 불러오기 실패", e)
            emptyList()
        }
    }

    // 6. 내가 등록한 핀 삭제
    suspend fun deletePin(pinId: String) {
        signInAnonymously()
        pinsCollection.document(pinId).delete().await()
    }

    // 7. 내가 수집한 핀 삭제
    suspend fun deleteCollectedPin(pinId: String) {
        signInAnonymously()
        db.collection("collected_pins").document(pinId).delete().await()
    }

    // 8. 샘플 핀 자동 생성
    // 8. 샘플 핀 자동 생성 (👉 이 기능을 잠시 꺼두거나 통째로 주석 처리!)
    suspend fun seedGwangalliSamplePinsIfNeeded() {
        // 앱 실행 시 강제로 샘플 핀이 생성되면서 장소명이 덮어씌워지는 것을 막기 위해 비워둡니다.
        return
    }
}