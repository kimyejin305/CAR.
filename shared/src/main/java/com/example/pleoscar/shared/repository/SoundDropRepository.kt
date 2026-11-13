package com.example.pleoscar.shared.repository

import com.example.pleoscar.shared.model.MusicPin
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class SoundDropRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {

    private val auth = Firebase.auth

    private val pinsCollection = db.collection("musicPins")

    // 1. Firebase 익명 로그인
    suspend fun signInAnonymously(): String {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            return currentUser.uid
        }

        val result = auth.signInAnonymously().await()

        return result.user?.uid
            ?: error("Firebase 로그인에 실패했습니다.")
    }

    // 2. 음악 핀 저장
    suspend fun saveMusicPin(
        songTitle: String,
        artist: String,
        hint: String,
        latitude: Double,
        longitude: Double,
        albumArtUrl: String = "",
        previewUrl: String = ""
    ): String {

        require(songTitle.isNotBlank()) {
            "노래 제목을 입력하세요."
        }

        require(artist.isNotBlank()) {
            "가수 이름을 입력하세요."
        }

        require(latitude in -90.0..90.0) {
            "위도가 올바르지 않습니다."
        }

        require(longitude in -180.0..180.0) {
            "경도가 올바르지 않습니다."
        }

        val userId = signInAnonymously()

        val document = pinsCollection.document()

        val pinData = hashMapOf<String, Any>(
            "id" to document.id,
            "songTitle" to songTitle.trim(),
            "artist" to artist.trim(),
            "hint" to hint.trim(),
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

    // 3. 음악 핀 전체 조회
    suspend fun getMusicPins(): List<MusicPin> {

        signInAnonymously()

        val snapshot = pinsCollection.get().await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(MusicPin::class.java)
        }
    }

    // 4. 음악 핀 실시간 조회
    fun observeMusicPins(): Flow<List<MusicPin>> = callbackFlow {

        if (auth.currentUser == null) {
            close(
                IllegalStateException(
                    "먼저 Firebase 익명 로그인을 실행하세요."
                )
            )
        } else {

            val listener =
                pinsCollection.addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    val pins = snapshot?.documents?.mapNotNull {
                        it.toObject(MusicPin::class.java)
                    } ?: emptyList()

                    trySend(pins)
                }

            awaitClose {
                listener.remove()
            }
        }
    }
    /**
     * 반경 이내에서 재생 버튼을 눌러 발견한 음악 핀을 내 수집 목록(collected_pins)에 저장합니다.
     */
    suspend fun collectMusicPin(pin: MusicPin) {
        val collectedData = hashMapOf(
            "originalPinId" to pin.id,
            "songTitle" to pin.songTitle,
            "artist" to pin.artist,
            "albumArtUrl" to pin.albumArtUrl,
            "previewUrl" to pin.previewUrl,
            "latitude" to pin.latitude,
            "longitude" to pin.longitude,
            "collectedAt" to com.google.firebase.Timestamp.now()
        )
        db.collection("collected_pins")
            .add(collectedData)
            .await()
    }
    /**
     * 내 수집 목록(collected_pins)을 최신순으로 불러옵니다.
     */
    suspend fun getCollectedPins(): List<MusicPin> {
        val snapshot = db.collection("collected_pins")
            .orderBy("collectedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            MusicPin(
                id = doc.id,
                songTitle = doc.getString("songTitle") ?: "",
                artist = doc.getString("artist") ?: "",
                albumArtUrl = doc.getString("albumArtUrl") ?: "",
                previewUrl = doc.getString("previewUrl") ?: "",
                latitude = doc.getDouble("latitude") ?: 0.0,
                longitude = doc.getDouble("longitude") ?: 0.0
            )
        }
    }
    /**
     * 시연 및 테스트용으로 부산 광안리 일대에 샘플 음악 핀 3개가 없으면 1회 자동 등록합니다.
     */
    suspend fun seedGwangalliSamplePinsIfNeeded() {
        val existing = getMusicPins()
        val hasGwangalliPin = existing.any { it.latitude in 35.145..35.165 && it.longitude in 129.110..129.135 }
        if (hasGwangalliPin) return

        val sampleTracks = listOf(
            Triple("밤편지", "아이유", Pair(35.1532, 129.1189)),
            Triple("Dynamite", "BTS", Pair(35.1558, 129.1245)),
            Triple("Hype Boy", "NewJeans", Pair(35.1495, 129.1142))
        )

        for ((title, artist, coords) in sampleTracks) {
            val assets = com.example.pleoscar.shared.ItunesMetadataFetcher.fetchTrackAssets(title, artist)
            saveMusicPin(
                songTitle = title,
                artist = artist,
                hint = "광안리 드라이브 코스 추천곡 🌊",
                latitude = coords.first,
                longitude = coords.second,
                albumArtUrl = assets.albumArtUrl,
                previewUrl = assets.previewUrl
            )
        }
    }
}