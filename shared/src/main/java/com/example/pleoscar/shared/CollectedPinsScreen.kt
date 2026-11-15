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
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("🎵 ${pin.songTitle}")
                    .addText("가수: ${pin.artist.ifBlank { "알 수 없음" }} | 장소: ${pin.placeName}")
                    .setOnClickListener {
                        val detailScreen = PinDetailScreen(
                            carContext = carContext,
                            pin = pin,
                            currentLat = 0.0,
                            currentLng = 0.0,
                            isCurrentlyTarget = false,
                            totalTargetCount = 0,
                            onToggleTarget = { },
                            onClearAllTargets = { },

                            // 1️⃣ 삭제를 누를 때: 상세 창만 닫고 수집 목록 페이지로 돌아와서 목록 갱신
                            onDeletePin = { targetPin ->
                                screenManager.pop() // 상세 창 닫기 (목록 페이지로 복귀)

                                lifecycleScope.launch {
                                    try {
                                        repository.deleteCollectedPin(targetPin.id)
                                        CarToast.makeText(carContext, "수집함에서 삭제되었습니다.", CarToast.LENGTH_SHORT).show()
                                        loadCollectedPins() // 목록 즉시 새로고침
                                    } catch (e: Exception) {
                                        Log.e("PleoscarCollected", "삭제 실패", e)
                                        CarToast.makeText(carContext, "삭제 실패: ${e.message}", CarToast.LENGTH_LONG).show()
                                    }
                                }
                            },

                            // 2️⃣ 위치 이동을 누를 때: 상세 창과 목록 창을 모두 닫고 곧바로 메인 지도 화면으로 복귀
                            onViewLocation = { lat, lng ->
                                screenManager.pop() // 상세 창 닫기
                                screenManager.pop() // 수집 목록 창 닫기 -> 메인 지도 도달
                            }
                        )
                        screenManager.push(detailScreen)
                    }
                    .build()
            )
        }

        return builder
            .setSingleList(itemListBuilder.build())
            .build()
    }
}