package com.example.pleoscar.shared

import android.app.Presentation
import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.pleoscar.shared.model.MusicPin
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.MarkerIcons
import kotlin.math.hypot
import kotlin.math.ln

class NaverMapSurfaceRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
    private val onPinClicked: (MusicPin) -> Unit
) : DefaultLifecycleObserver, OnMapReadyCallback {

    companion object {
        private const val TAG = "NaverMapRenderer"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var naverMap: NaverMap? = null

    private val activeMarkers = mutableMapOf<String, Marker>()
    private var targetPinKeys: Set<String> = emptySet()
    private var pendingPins: List<MusicPin> = emptyList()

    // 검색 화면에서 돌아왔을 때 이동할 예약 좌표 & 마지막 카메라 좌표 기억
    private var pendingPreviewCoord: LatLng? = null
    private var lastCameraCoord: LatLng? = null

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            val surface = surfaceContainer.surface ?: return
            val width = surfaceContainer.width
            val height = surfaceContainer.height
            val dpi = surfaceContainer.dpi
            if (width <= 0 || height <= 0) return

            releasePresentation()
            setupVirtualDisplayAndMap(surface, width, height, dpi)
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            naverMap?.setContentPadding(
                visibleArea.left,
                visibleArea.top,
                (mapView?.width ?: 0) - visibleArea.right,
                (mapView?.height ?: 0) - visibleArea.bottom
            )
        }

        override fun onStableAreaChanged(stableArea: Rect) {}

        override fun onScroll(distanceX: Float, distanceY: Float) {
            val map = naverMap ?: return
            map.moveCamera(CameraUpdate.scrollBy(PointF(-distanceX, -distanceY)))
            lastCameraCoord = map.cameraPosition.target
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            val map = naverMap ?: return
            if (scaleFactor <= 0f) return
            val zoomDelta = ln(scaleFactor.toDouble()) / ln(2.0)
            map.moveCamera(CameraUpdate.zoomBy(zoomDelta))
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            releasePresentation()
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releasePresentation()
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
    }

    private fun setupVirtualDisplayAndMap(surface: Surface, width: Int, height: Int, dpi: Int) {
        val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        virtualDisplay = displayManager.createVirtualDisplay(
            "NaverMapVirtualDisplay",
            width,
            height,
            dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )

        val display = virtualDisplay?.display ?: return
        presentation = object : Presentation(carContext, display) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val newMapView = MapView(context)
                setContentView(newMapView)
                newMapView.onCreate(savedInstanceState)
                newMapView.onStart()
                newMapView.onResume()
                newMapView.getMapAsync(this@NaverMapSurfaceRenderer)
                mapView = newMapView
            }
        }
        presentation?.show()
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map

        map.setOnMapClickListener { point, _ ->
            val touchedPin = findNearestPinAtScreenPoint(map, point)
            if (touchedPin != null) {
                onPinClicked(touchedPin)
            }
        }

        if (pendingPins.isNotEmpty()) {
            showMusicPins(pendingPins, targetPinKeys)
        }

        // 검색 화면 등에서 돌아왔을 때 예약된 장소 좌표가 있으면 즉시 그 위치로 이동!
        val previewTarget = pendingPreviewCoord
        if (previewTarget != null) {
            lastCameraCoord = previewTarget
            map.moveCamera(CameraUpdate.scrollAndZoomTo(previewTarget, 14.0))
            pendingPreviewCoord = null
        } else if (lastCameraCoord != null) {
            // 핀 상세 화면에서 돌아왔을 때도 직전 탐색 위치를 그대로 유지
            map.moveCamera(CameraUpdate.scrollAndZoomTo(lastCameraCoord!!, 14.0))
        }

        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isScaleBarEnabled = true
    }

    fun getPinKey(pin: MusicPin): String {
        return pin.id.ifBlank { "${pin.songTitle}_${pin.latitude}_${pin.longitude}" }
    }

    private fun findNearestPinAtScreenPoint(map: NaverMap, touchPoint: PointF): MusicPin? {
        var nearest: MusicPin? = null
        var minPixelDist = 90f

        for (pin in pendingPins) {
            if (pin.latitude == 0.0 && pin.longitude == 0.0) continue
            val screenPt = map.projection.toScreenLocation(LatLng(pin.latitude, pin.longitude))
            val dist = hypot(screenPt.x - touchPoint.x, screenPt.y - touchPoint.y)
            if (dist <= minPixelDist) {
                minPixelDist = dist
                nearest = pin
            }
        }
        return nearest
    }

    private fun releasePresentation() {
        activeMarkers.clear()
        mapView?.onPause()
        mapView?.onStop()
        mapView?.onDestroy()
        mapView = null
        naverMap = null

        presentation?.dismiss()
        presentation = null

        virtualDisplay?.release()
        virtualDisplay = null
    }

    /**
     * 내 차량 현재 위치로 카메라 이동 및 파란 점(현위치) 갱신
     */
    fun moveCameraToLocation(latitude: Double, longitude: Double) {
        pendingPreviewCoord = null
        val coord = LatLng(latitude, longitude)
        lastCameraCoord = coord
        val map = naverMap ?: return
        map.moveCamera(CameraUpdate.scrollTo(coord))
        map.locationOverlay.apply {
            isVisible = true
            position = coord
        }
    }

    /**
     * 장소 검색("부산 광안리" 등)으로 선택한 탐색 위치로 지도 카메라 이동 (Surface 재생성 대비 예약 포함)
     */
    fun moveCameraToPreviewLocation(latitude: Double, longitude: Double) {
        val coord = LatLng(latitude, longitude)
        pendingPreviewCoord = coord
        lastCameraCoord = coord
        val map = naverMap
        if (map != null) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(coord, 14.0))
            pendingPreviewCoord = null
        }
    }

    fun zoomIn() {
        naverMap?.moveCamera(CameraUpdate.zoomIn())
    }

    fun zoomOut() {
        naverMap?.moveCamera(CameraUpdate.zoomOut())
    }

    fun updateTargetPinKeys(newTargetKeys: Set<String>) {
        targetPinKeys = newTargetKeys
        activeMarkers.forEach { (key, marker) ->
            val pin = pendingPins.find { getPinKey(it) == key }
            val isTarget = targetPinKeys.contains(key)
            marker.icon = if (isTarget) MarkerIcons.BLUE else MarkerIcons.GRAY
            if (pin != null) {
                marker.captionText = if (isTarget) "🎯 ${pin.songTitle}" else "🎵 ${pin.songTitle}"
            }
        }
    }

    fun showMusicPins(pins: List<MusicPin>, currentTargets: Set<String> = targetPinKeys) {
        pendingPins = pins
        targetPinKeys = currentTargets
        val map = naverMap ?: return

        activeMarkers.values.forEach { it.map = null }
        activeMarkers.clear()

        pins.forEach { pin ->
            if (pin.latitude != 0.0 || pin.longitude != 0.0) {
                val key = getPinKey(pin)
                val isTarget = targetPinKeys.contains(key)
                val marker = Marker().apply {
                    position = LatLng(pin.latitude, pin.longitude)
                    icon = if (isTarget) MarkerIcons.BLUE else MarkerIcons.GRAY
                    captionText = if (isTarget) "🎯 ${pin.songTitle}" else "🎵 ${pin.songTitle}"
                    subCaptionText = pin.artist
                    setOnClickListener {
                        onPinClicked(pin)
                        true
                    }
                    this.map = map
                }
                activeMarkers[key] = marker
            }
        }
    }
}