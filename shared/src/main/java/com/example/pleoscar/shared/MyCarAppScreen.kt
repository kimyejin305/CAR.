package com.example.pleoscar.shared

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.provider.MediaStore
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.pleoscar.shared.model.MusicPin
import com.example.pleoscar.shared.repository.SoundDropRepository
import kotlinx.coroutines.launch

class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = SoundDropRepository()
    private var lastLatitude: Double = 35.8881
    private var lastLongitude: Double = 128.6104
    private var currentSpeedKmh: Float = 0f

    // 전체 음악 핀 & 주행 전 사용자가 선택한 목표 핀 목록
    private var cachedPins: List<MusicPin> = emptyList()
    private val targetPinKeys = mutableSetOf<String>()
    private var nearbyPin: MusicPin? = null
    private val alertedPinIds = mutableSetOf<String>()
    private var mediaPlayer: MediaPlayer? = null

    // 장소 검색으로 다른 지역을 미리 둘러보는 중인지 여부
    private var isExploringRemoteArea: Boolean = false

    private val mapRenderer: NaverMapSurfaceRenderer =
        NaverMapSurfaceRenderer(carContext, lifecycle) { clickedPin ->
            openPinDetailScreen(clickedPin)
        }

    private val locationManager =
        carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val locationListener = LocationListener { location ->
        currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        onLocationUpdated(location.latitude, location.longitude)
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                locationManager.removeUpdates(locationListener)
                mediaPlayer?.release()
                mediaPlayer = null
            }
        })
        checkPermissionAndStartTracking()
        loadMusicPinsFromFirestore()
    }

    private fun openPinDetailScreen(pin: MusicPin) {
        val key = mapRenderer.getPinKey(pin)
        val isTarget = targetPinKeys.contains(key)

        screenManager.push(
            PinDetailScreen(
                carContext = carContext,
                pin = pin,
                currentLat = lastLatitude,
                currentLng = lastLongitude,
                isCurrentlyTarget = isTarget,
                totalTargetCount = targetPinKeys.size,
                onToggleTarget = { toggledPin ->
                    val targetKey = mapRenderer.getPinKey(toggledPin)
                    if (targetPinKeys.contains(targetKey)) {
                        targetPinKeys.remove(targetKey)
                        CarToast.makeText(
                            carContext,
                            "목표 해제: '${toggledPin.songTitle}' (남은 목표 ${targetPinKeys.size}곡)",
                            CarToast.LENGTH_SHORT
                        ).show()
                    } else {
                        targetPinKeys.add(targetKey)
                        CarToast.makeText(
                            carContext,
                            "🎯 목표 선택 완료: '${toggledPin.songTitle}' (총 ${targetPinKeys.size}곡)",
                            CarToast.LENGTH_SHORT
                        ).show()
                    }
                    mapRenderer.updateTargetPinKeys(targetPinKeys.toSet())
                    checkNearbyMusicPins(lastLatitude, lastLongitude)
                    invalidate()
                },
                onClearAllTargets = {
                    targetPinKeys.clear()
                    alertedPinIds.clear()
                    mapRenderer.updateTargetPinKeys(emptySet())
                    CarToast.makeText(
                        carContext,
                        "모든 수집 목표가 초기화되었습니다. (자유 발견 모드)",
                        CarToast.LENGTH_SHORT
                    ).show()
                    checkNearbyMusicPins(lastLatitude, lastLongitude)
                    invalidate()
                }
            )
        )
    }

    override fun onGetTemplate(): Template {
        val locationIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_mylocation)
        ).build()

        val addIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_input_add)
        ).build()

        val playIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_media_play)
        ).build()

        val searchIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_search)
        ).build()

        val listIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_sort_by_size)
        ).build()

        // 상단 액션 바: 주행 규격(텍스트 버튼 1개 + 아이콘 버튼) 준수
        val actionStripBuilder = ActionStrip.Builder()

        val activePin = nearbyPin
        if (activePin != null) {
            val shortTitle = if (activePin.songTitle.length > 10) {
                activePin.songTitle.take(10) + "…"
            } else {
                activePin.songTitle
            }
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(playIcon)
                    .setTitle("$shortTitle 재생")
                    .setOnClickListener {
                        playPinInMusicApp(activePin)
                    }
                    .build()
            )
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(addIcon)
                    .setOnClickListener {
                        dropCurrentPlayingTrackHere()
                    }
                    .build()
            )
        } else {
            val idleTitle = if (targetPinKeys.isNotEmpty()) {
                "핀 등록 (목표 ${targetPinKeys.size})"
            } else {
                "핀 등록"
            }
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(addIcon)
                    .setTitle(idleTitle)
                    .setOnClickListener {
                        dropCurrentPlayingTrackHere()
                    }
                    .build()
            )
        }

        // 장소/카페 검색 아이콘 버튼
        actionStripBuilder.addAction(
            Action.Builder()
                .setIcon(searchIcon)
                .setOnClickListener {
                    screenManager.push(
                        LocationSearchScreen(carContext) { selectedPlace ->
                            isExploringRemoteArea = true
                            mapRenderer.moveCameraToPreviewLocation(
                                selectedPlace.latitude,
                                selectedPlace.longitude
                            )
                        }
                    )
                }
                .build()
        )

        // 내 수집 음악함(마이페이지) 아이콘 버튼 — 시속 10km 이상 주행 중일 때는 안전 잠금
        actionStripBuilder.addAction(
            Action.Builder()
                .setIcon(listIcon)
                .setOnClickListener {
                    if (currentSpeedKmh >= 10f) {
                        CarToast.makeText(
                            carContext,
                            "🛡️ 안전 운전을 위해 정차 후 수집함을 확인할 수 있습니다.",
                            CarToast.LENGTH_LONG
                        ).show()
                    } else {
                        screenManager.push(CollectedPinsScreen(carContext))
                    }
                }
                .build()
        )

        // 지도 전용 액션 바
        val zoomInIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_input_add)
        ).build()

        val zoomOutIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_revert)
        ).build()

        val mapActionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setIcon(zoomInIcon)
                    .setOnClickListener {
                        mapRenderer.zoomIn()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(zoomOutIcon)
                    .setOnClickListener {
                        mapRenderer.zoomOut()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(locationIcon)
                    .setOnClickListener {
                        isExploringRemoteArea = false
                        mapRenderer.moveCameraToLocation(lastLatitude, lastLongitude)
                        checkPermissionAndStartTracking()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStripBuilder.build())
            .setMapActionStrip(mapActionStrip)
            .build()
    }

    private fun loadMusicPinsFromFirestore() {
        lifecycleScope.launch {
            try {
                repository.seedGwangalliSamplePinsIfNeeded()
                val pins = repository.getMusicPins()
                cachedPins = pins
                Log.d("PleoscarFirestore", "불러온 음악 핀 개수: ${pins.size}")
                mapRenderer.showMusicPins(pins, targetPinKeys.toSet())
                checkNearbyMusicPins(lastLatitude, lastLongitude)
            } catch (e: Exception) {
                Log.e("PleoscarFirestore", "음악 핀 불러오기 실패", e)
            }
        }
    }

    private fun dropCurrentPlayingTrackHere() {
        val currentTrack = NowPlayingTracker.getCurrentPlayingTrack(carContext)

        lifecycleScope.launch {
            try {
                val pinLat = lastLatitude + 0.0003
                val pinLng = lastLongitude + 0.0003

                val assets = ItunesMetadataFetcher.fetchTrackAssets(
                    songTitle = currentTrack.title,
                    artist = currentTrack.artist
                )

                repository.saveMusicPin(
                    songTitle = currentTrack.title,
                    artist = currentTrack.artist,
                    hint = "",
                    latitude = pinLat,
                    longitude = pinLng,
                    albumArtUrl = assets.albumArtUrl,
                    previewUrl = assets.previewUrl
                )

                CarToast.makeText(
                    carContext,
                    "🎵 '${currentTrack.title} - ${currentTrack.artist}' 핀 등록 완료!",
                    CarToast.LENGTH_LONG
                ).show()

                Log.d("PleoscarFirestore", "현재 재생 곡 핀 저장 완료: ${currentTrack.title}")
                loadMusicPinsFromFirestore()
            } catch (e: Exception) {
                Log.e("PleoscarFirestore", "음악 핀 저장 실패", e)
                CarToast.makeText(
                    carContext,
                    "핀 등록에 실패했습니다.",
                    CarToast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onLocationUpdated(latitude: Double, longitude: Double) {
        lastLatitude = latitude
        lastLongitude = longitude

        if (!isExploringRemoteArea) {
            mapRenderer.moveCameraToLocation(latitude, longitude)
        }
        checkNearbyMusicPins(latitude, longitude)
    }

    private fun checkNearbyMusicPins(currentLat: Double, currentLng: Double) {
        if (cachedPins.isEmpty()) return

        val discoveryRadiusMeters = 1500f
        val isTargetOnlyMode = targetPinKeys.isNotEmpty()
        var closestPin: MusicPin? = null
        var minDistance = Float.MAX_VALUE
        val results = FloatArray(1)

        for (pin in cachedPins) {
            if (pin.latitude == 0.0 && pin.longitude == 0.0) continue
            val key = mapRenderer.getPinKey(pin)

            if (isTargetOnlyMode && !targetPinKeys.contains(key)) continue

            Location.distanceBetween(currentLat, currentLng, pin.latitude, pin.longitude, results)
            val distance = results[0]
            if (distance <= discoveryRadiusMeters && distance < minDistance) {
                minDistance = distance
                closestPin = pin
            }
        }

        val previousPin = nearbyPin
        nearbyPin = closestPin

        if (closestPin != null) {
            val pinKey = mapRenderer.getPinKey(closestPin)
            if (!alertedPinIds.contains(pinKey)) {
                alertedPinIds.add(pinKey)
                playDiscoveryChime()
                val badge = if (isTargetOnlyMode) "🎯 목표 음악 잠금 해제" else "🔓 주변 음악 발견"
                CarToast.makeText(
                    carContext,
                    "$badge (${minDistance.toInt()}m): ${closestPin.songTitle} - ${closestPin.artist}",
                    CarToast.LENGTH_LONG
                ).show()
            }
        } else if (previousPin != null) {
            val prevKey = mapRenderer.getPinKey(previousPin)
            alertedPinIds.remove(prevKey)
        }

        if (previousPin?.id != closestPin?.id) {
            invalidate()
        }
    }

    private fun playDiscoveryChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        } catch (e: Exception) {
            Log.w("PleoscarProximity", "알림음 재생 실패: ${e.message}")
        }
    }

    private fun playPinInMusicApp(pin: MusicPin) {
        val query = "${pin.songTitle} ${pin.artist}".trim()

        lifecycleScope.launch {
            try {
                repository.collectMusicPin(pin)
                Log.d("PleoscarFirestore", "음악 핀 수집 저장 완료: ${pin.songTitle}")
            } catch (e: Exception) {
                Log.e("PleoscarFirestore", "음악 핀 수집 저장 실패", e)
            }
        }

        if (pin.previewUrl.isNotBlank()) {
            playPreviewAudio(pin)
            return
        }

        try {
            val musicIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                putExtra(MediaStore.EXTRA_MEDIA_TITLE, pin.songTitle)
                putExtra(MediaStore.EXTRA_MEDIA_ARTIST, pin.artist)
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            carContext.startActivity(musicIntent)
            CarToast.makeText(
                carContext,
                "🎧 '${pin.songTitle}' 수집 완료 & 음악 앱으로 이동합니다.",
                CarToast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.w("PleoscarProximity", "설치된 외부 음악 앱 없음: ${e.message}")
            CarToast.makeText(
                carContext,
                "🎧 '${pin.songTitle} - ${pin.artist}' 수집 완료! (재생할 음악 앱이 설치되어 있지 않습니다)",
                CarToast.LENGTH_LONG
            ).show()
        }
    }

    private fun playPreviewAudio(pin: MusicPin) {
        val previewUrl = pin.previewUrl
        if (previewUrl.isBlank()) {
            CarToast.makeText(
                carContext,
                "🎧 '${pin.songTitle} - ${pin.artist}' 수집 완료! (미리듣기 음원 없음)",
                CarToast.LENGTH_LONG
            ).show()
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(previewUrl)
                setOnPreparedListener { mp ->
                    mp.start()
                    CarToast.makeText(
                        carContext,
                        "🎵 '${pin.songTitle} - ${pin.artist}' 수집 완료 & 미리듣기 재생!",
                        CarToast.LENGTH_LONG
                    ).show()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer == mp) mediaPlayer = null
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("PleoscarProximity", "미리듣기 재생 오류", e)
            CarToast.makeText(
                carContext,
                "🎧 '${pin.songTitle}' 수집 완료!",
                CarToast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkPermissionAndStartTracking() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            startLocationUpdates()
        } else {
            carContext.requestPermissions(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) { grantedPermissions, _ ->
                if (grantedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    startLocationUpdates()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val lastKnown: Location? =
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (lastKnown != null) {
            onLocationUpdated(lastKnown.latitude, lastKnown.longitude)
        }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                5f,
                locationListener
            )
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                5f,
                locationListener
            )
        }
    }
}