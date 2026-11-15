package com.example.pleoscar.shared

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.example.pleoscar.shared.model.MusicPin
import com.example.pleoscar.shared.repository.SoundDropRepository
import kotlinx.coroutines.launch

class CollectedPinsScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = SoundDropRepository()
    private var collectedPins: List<MusicPin> = emptyList()
    private var isLoading: Boolean = true

    init {
        loadCollectedPins()
    }

    private fun loadCollectedPins() {
        isLoading = true
        invalidate()
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
            .setTitle("🎧 내가 수집한 음악함 (${collectedPins.size}곡)")
            .setHeaderAction(Action.BACK)

        if (isLoading) {
            return builder.setLoading(true).build()
        }

        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage("아직 수집한 음악 핀이 없습니다. 주행 중 음악 존에 진입해 곡을 수집해 보세요!")

        collectedPins.forEach { pin ->
            // 🗑️ 각 핀마다 개별 삭제 버튼(Action) 생성
            val deleteAction = Action.Builder()
                .setTitle("삭제")
                .setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            // 파이어베이스에서 해당 핀 삭제
                            repository.deleteCollectedPin(pin.id)
                            CarToast.makeText(carContext, "수집함에서 삭제되었습니다.", CarToast.LENGTH_SHORT).show()
                            loadCollectedPins() // 목록 즉시 새로고침
                        } catch (e: Exception) {
                            Log.e("PleoscarCollected", "삭제 실패", e)
                            CarToast.makeText(carContext, "삭제 실패: ${e.message}", CarToast.LENGTH_LONG).show()
                        }
                    }
                }
                .build()

            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("🎵 ${pin.songTitle}")
                    .addText("가수: ${pin.artist.ifBlank { "알 수 없음" }} | 장소: ${pin.placeName}")
                    .addAction(deleteAction) // 👈 리스트 오른쪽에 삭제 버튼 장착!
                    .setOnClickListener {
                        // 🗺️ 행(Row)을 누를 때: 상세 창을 거치지 않고 곧바로 메인 지도 화면으로 복귀 (원하시는 흐름!)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return builder
            .setSingleList(itemListBuilder.build())
            .build()
    }
}