package com.example.pleoscar.shared

import android.app.SearchManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.MediaStore
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.pleoscar.shared.model.MusicPin
import com.example.pleoscar.shared.repository.SoundDropRepository
import kotlinx.coroutines.launch

class CollectedPinsScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = SoundDropRepository()
    private var collectedPins: List<MusicPin> = emptyList()
    private var isLoading: Boolean = true
    private var mediaPlayer: MediaPlayer? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                mediaPlayer?.release()
                mediaPlayer = null
            }
        })
        loadCollectedPins()
    }

    private fun loadCollectedPins() {
        lifecycleScope.launch {
            try {
                collectedPins = repository.getCollectedPins()
            } catch (e: Exception) {
                Log.e("PleoscarCollected", "수집 목록 불러오기 실패", e)
            } finally {
                isLoading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("🎧 내 수집 음악함 (${collectedPins.size}곡)")
            .setHeaderAction(Action.BACK)

        if (isLoading) {
            return builder.setLoading(true).build()
        }

        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage("아직 수집한 음악 핀이 없습니다. 주행 중 음악 존에 진입해 곡을 수집해 보세요!")

        // 차량 주행 중 안전 규격에 맞춰 최대 6개 항목까지 표시
        collectedPins.take(6).forEach { pin ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("🎵 ${pin.songTitle}")
                    .addText(pin.artist.ifBlank { "알 수 없는 아티스트" })
                    .setOnClickListener {
                        playCollectedTrack(pin)
                    }
                    .build()
            )
        }

        return builder
            .setSingleList(itemListBuilder.build())
            .build()
    }

    private fun playCollectedTrack(pin: MusicPin) {
        if (pin.previewUrl.isNotBlank()) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(pin.previewUrl)
                    setOnPreparedListener { mp ->
                        mp.start()
                        CarToast.makeText(
                            carContext,
                            "▶ '${pin.songTitle} - ${pin.artist}' 재생 중",
                            CarToast.LENGTH_SHORT
                        ).show()
                    }
                    setOnCompletionListener { mp ->
                        mp.release()
                        if (mediaPlayer == mp) mediaPlayer = null
                    }
                    prepareAsync()
                }
                return
            } catch (e: Exception) {
                Log.e("PleoscarCollected", "미리듣기 재생 실패", e)
            }
        }

        try {
            val query = "${pin.songTitle} ${pin.artist}".trim()
            val musicIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                putExtra(MediaStore.EXTRA_MEDIA_TITLE, pin.songTitle)
                putExtra(MediaStore.EXTRA_MEDIA_ARTIST, pin.artist)
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            carContext.startActivity(musicIntent)
        } catch (e: Exception) {
            CarToast.makeText(
                carContext,
                "재생할 외부 음악 앱이 설치되어 있지 않습니다.",
                CarToast.LENGTH_SHORT
            ).show()
        }
    }
}