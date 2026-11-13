package com.example.pleoscar.shared

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.example.pleoscar.shared.model.MusicPin

class PinDetailScreen(
    carContext: CarContext,
    private val pin: MusicPin,
    private val currentLat: Double,
    private val currentLng: Double,
    private val isCurrentlyTarget: Boolean,
    private val totalTargetCount: Int,
    private val onToggleTarget: (MusicPin) -> Unit,
    private val onClearAllTargets: () -> Unit
) : Screen(carContext) {

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
                    .addText("거리 및 감지 반경: $distanceText (사방 1.5km 진입 시 알림)")
                    .build()
            )

        if (pin.hint.isNotBlank()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("💬 남긴 한마디")
                    .addText(pin.hint)
                    .build()
            )
        }

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
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener {
                    onToggleTarget(pin)
                    screenManager.pop()
                }
                .build()
        }

        paneBuilder.addAction(toggleAction)

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