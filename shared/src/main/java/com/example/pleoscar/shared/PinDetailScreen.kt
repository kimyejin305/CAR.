package com.example.pleoscar.shared

import android.location.Location
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.example.pleoscar.shared.model.MusicPin
import com.example.pleoscar.shared.repository.SoundDropRepository
import kotlinx.coroutines.launch

class PinDetailScreen(
    carContext: CarContext,
    private val pin: MusicPin,
    private val currentLat: Double,
    private val currentLng: Double,
    private val isCurrentlyTarget: Boolean,
    private val totalTargetCount: Int,
    private val onToggleTarget: (MusicPin) -> Unit,
    private val onClearAllTargets: () -> Unit,
    private val onDeletePin: (MusicPin) -> Unit = {},
    private val onViewLocation: (Double, Double) -> Unit = { _, _ -> }
) : Screen(carContext) {

    private val repository = SoundDropRepository()

    override fun onGetTemplate(): Template {
        val distanceText = if (pin.latitude != 0.0 || pin.longitude != 0.0) {
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLng, pin.latitude, pin.longitude, results)
            val meters = results[0].toInt()
            if (meters >= 1000) {
                String.format("현재 위치에서 약 %.1fkm", meters / 1000f)
            } else {
                "현재 위치에서 약 ${meters}m"
            }
        } else {
            "위치 정보 없음"
        }

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("🎵 ${pin.songTitle}")
                    .addText("아티스트: ${pin.artist.ifBlank { "알 수 없음" }}")
                    .addText("거리 및 감지 반경: $distanceText (사방 2.0km 진입 시 알림)")
                    .build()
            )

        // 1. 내 수집함에 담기 버튼
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("📥 내 수집함에 담기")
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            repository.collectMusicPin(pin)
                            CarToast.makeText(carContext, "수집함에 성공적으로 담겼습니다!", CarToast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("PinDetail", "수집 실패", e)
                            CarToast.makeText(carContext, "수집 실패: ${e.message}", CarToast.LENGTH_LONG).show()
                        }
                    }
                }
                .build()
        )

        // 2. 수집 목표 설정/해제 버튼
        val toggleAction = if (isCurrentlyTarget) {
            Action.Builder()
                .setTitle("목표 선택 해제")
                .setOnClickListener {
                    onToggleTarget(pin)
                    screenManager.pop()
                }
                .build()
        } else {
            Action.Builder()
                .setTitle("🎯 수집 목표로 선택")
                .setOnClickListener {
                    onToggleTarget(pin)
                    screenManager.pop()
                }
                .build()
        }
        paneBuilder.addAction(toggleAction)

        // 3. 지도에서 위치 보기 버튼 (🔥 pop()을 1번만 호출하여 메인 지도로 안전하게 복귀)
        if (pin.latitude != 0.0 && pin.longitude != 0.0) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("📍 지도에서 위치 보기")
                    .setOnClickListener {
                        onViewLocation(pin.latitude, pin.longitude)
                        screenManager.pop() // 상세 창 닫기 (메인 지도로 복귀)
                    }
                    .build()
            )
        }

        // 4. 핀 삭제하기 버튼 (🔥 pop()을 1번만 호출하여 메인 지도로 안전하게 복귀)
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🗑️ 핀 삭제하기")
                .setBackgroundColor(CarColor.RED)
                .setOnClickListener {
                    onDeletePin(pin)
                    screenManager.pop() // 상세 창 닫기 (메인 지도로 복귀)
                }
                .build()
        )

        if (totalTargetCount > 0) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("모든 목표 초기화 (${totalTargetCount}곡)")
                    .setOnClickListener {
                        onClearAllTargets()
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("음악 핀 상세 정보")
            .setHeaderAction(Action.BACK)
            .build()
    }
}