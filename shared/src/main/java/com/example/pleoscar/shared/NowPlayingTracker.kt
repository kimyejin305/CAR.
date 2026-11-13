package com.example.pleoscar.shared

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * 차량 인포테인먼트 시스템에서 현재 재생 중인 미디어(노래 제목, 가수)를 읽어오는 클래스
 */
class CarMediaNotificationListener : NotificationListenerService()

object NowPlayingTracker {

    data class TrackInfo(
        val title: String,
        val artist: String,
        val isLiveDetected: Boolean
    )

    // 시연 중 백그라운드 음악 앱이 꺼져 있을 때를 대비한 예비 트랙 순환 목록
    private val fallbackTracks = listOf(
        TrackInfo("Magic Shop", "BTS", false),
        TrackInfo("오르트구름", "윤하 (YOUNHA)", false),
        TrackInfo("밤편지", "아이유 (IU)", false),
        TrackInfo("Hype Boy", "NewJeans", false)
    )
    private var fallbackIndex = 0

    fun getCurrentPlayingTrack(context: Context): TrackInfo {
        try {
            val mediaSessionManager =
                context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listenerComponent =
                ComponentName(context, CarMediaNotificationListener::class.java)

            val controllers = mediaSessionManager.getActiveSessions(listenerComponent)

            // 현재 재생 중(PLAYING)이거나 일시정지된 미디어 컨트롤러 우선 탐색
            val activeController = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()

            val metadata = activeController?.metadata
            if (metadata != null) {
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: "알 수 없는 아티스트"

                if (!title.isNullOrBlank()) {
                    Log.d("PleoscarMedia", "실시간 재생 곡 감지 성공: $title - $artist")
                    return TrackInfo(title = title, artist = artist, isLiveDetected = true)
                }
            }
        } catch (e: SecurityException) {
            Log.w("PleoscarMedia", "알림 접근 권한 미허용 상태 -> 시연용 폴백 트랙 사용")
        } catch (e: Exception) {
            Log.e("PleoscarMedia", "미디어 세션 조회 오류", e)
        }

        // 재생 중인 미디어가 없거나 권한 전일 때 시연용 트랙 반환
        val track = fallbackTracks[fallbackIndex % fallbackTracks.size]
        fallbackIndex++
        Log.d("PleoscarMedia", "폴백 트랙 반환: ${track.title} - ${track.artist}")
        return track
    }
}