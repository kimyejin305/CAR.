package com.example.pleoscar.shared.repository

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

    // 1. 음악 핀 등록 (지도에 공유)
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
            "placeName" to placeName.trim(),
            "latitude" to latitude,
            "longitude" to longitude,
            "albumArtUrl" to albumArtUrl.trim(),
            "previewUrl" to previewUrl.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "userId" to userId
        )

        document.set(pinData).await()
        return document.id
    }

    // 2. 지도에 띄울 모든 유저의 핀 조회 (모두 공유)
    suspend fun getMusicPins(): List<MusicPin> {
        signInAnonymously()
        val snapshot = pinsCollection.get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(MusicPin::class.java)
        }
    }

    // 3. 내가 꼽은(등록한) 노래 목록 조회 (마이페이지용)
    suspend fun getMyRegisteredPins(): List<MusicPin> {
        val uid = signInAnonymously()
        val snapshot = pinsCollection.whereEqualTo("userId", uid).get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(MusicPin::class.java)
        }
    }

    // 4. 남이 꼽은 노래를 내 수집함에 저장
    suspend fun collectMusicPin(pin: MusicPin) {
        val userId = signInAnonymously()
        val collectedData = hashMapOf(
            "originalPinId" to pin.id,
            "songTitle" to pin.songTitle,
            "artist" to pin.artist,
            "placeName" to pin.placeName,
            "albumArtUrl" to pin.albumArtUrl,
            "previewUrl" to pin.previewUrl,
            "latitude" to pin.latitude,
            "longitude" to pin.longitude,
            "collectedAt" to com.google.firebase.Timestamp.now(),
            "userId" to userId // 내 수집함으로 귀속
        )
        db.collection("collected_pins")
            .add(collectedData)
            .await()
    }

    // 5. 내가 수집한 노래 목록 조회 (마이페이지 수집함용)
    suspend fun getCollectedPins(): List<MusicPin> {
        val userId = signInAnonymously()
        val snapshot = db.collection("collected_pins")
            .whereEqualTo("userId", userId)
            .orderBy("collectedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            MusicPin(
                id = doc.id,
                songTitle = doc.getString("songTitle") ?: "",
                artist = doc.getString("artist") ?: "",
                placeName = doc.getString("placeName") ?: "수집된 장소",
                albumArtUrl = doc.getString("albumArtUrl") ?: "",
                previewUrl = doc.getString("previewUrl") ?: "",
                latitude = doc.getDouble("latitude") ?: 0.0,
                longitude = doc.getDouble("longitude") ?: 0.0,
                createdAt = doc.get("collectedAt")
            )
        }
    }

    // 6. 내가 등록한 핀 삭제 (인증 보장 추가)
    suspend fun deletePin(pinId: String) {
        signInAnonymously() // 🔥 필수: 삭제 전 익명 로그인 인증 상태 확실히 보장
        pinsCollection.document(pinId).delete().await()
    }

    // 7. 내가 수집한 핀 삭제 (인증 보장 추가)
    suspend fun deleteCollectedPin(pinId: String) {
        signInAnonymously() // 🔥 필수: 삭제 전 익명 로그인 인증 상태 확실히 보장
        db.collection("collected_pins").document(pinId).delete().await()
    }

    suspend fun seedGwangalliSamplePinsIfNeeded() {
        val existing = getMusicPins()
        val hasGwangalliPin = existing.any { it.latitude in 35.145..35.165 && it.longitude in 129.110..129.135 }
        if (hasGwangalliPin) return

        val sampleTracks = listOf(
            Triple("밤편지", "아이유", Triple("부산 수영구 광안해변로", 35.1532, 129.1189)),
            Triple("Dynamite", "BTS", Triple("부산 광안리 민락수변공원", 35.1558, 129.1245)),
            Triple("Hype Boy", "NewJeans", Triple("부산 남천동 해변길", 35.1495, 129.1142))
        )

        for ((title, artist, info) in sampleTracks) {
            val assets = com.example.pleoscar.shared.ItunesMetadataFetcher.fetchTrackAssets(title, artist)
            saveMusicPin(
                songTitle = title,
                artist = artist,
                hint = "광안리 드라이브 코스 추천곡 🌊",
                placeName = info.first,
                latitude = info.second,
                longitude = info.third,
                albumArtUrl = assets.albumArtUrl,
                previewUrl = assets.previewUrl
            )
        }
    }
}