package com.example.pleoscar.shared

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LocationSearchScreen(
    carContext: CarContext,
    private val onPlaceSelected: (SearchedPlace) -> Unit
) : Screen(carContext) {

    private var searchResults: List<SearchedPlace> = emptyList()
    private var isSearching: Boolean = false
    private var submittedQuery: String = ""

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(
                if (submittedQuery.isBlank()) {
                    "검색어를 입력한 뒤 키보드의 검색(엔터)을 눌러주세요."
                } else {
                    "'$submittedQuery'에 대한 검색 결과가 없습니다."
                }
            )

        // 차량 화면 안전 규격에 맞춰 최대 5개 결과만 표시
        searchResults.take(5).forEach { place ->
            val safeTitle = place.name.ifBlank { "검색된 장소" }.take(30)
            val safeAddress = place.address.ifBlank { "위치 좌표 확인됨" }.take(50)

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(safeTitle)
                    .addText(safeAddress)
                    .setOnClickListener {
                        CarToast.makeText(
                            carContext,
                            "📍 '$safeTitle' 주변 지도로 이동합니다.",
                            CarToast.LENGTH_SHORT
                        ).show()
                        onPlaceSelected(place)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        val builder = SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    // 타이핑 도중에는 템플릿 갱신(invalidate)을 하지 않아 주행 안전 제한 크래시 방지
                }

                override fun onSearchSubmitted(searchText: String) {
                    val trimmed = searchText.trim()
                    if (trimmed.isBlank()) return
                    submittedQuery = trimmed
                    performSearch(trimmed)
                }
            }
        )
            .setHeaderAction(Action.BACK)
            .setSearchHint("지역 또는 카페명 입력 후 엔터")
            .setShowKeyboardByDefault(true)
            .setLoading(isSearching)

        // 로딩 중에는 ItemList를 동시에 넣지 않아야 Car App Library 크래시가 발생하지 않음
        if (!isSearching) {
            builder.setItemList(listBuilder.build())
        }

        return builder.build()
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            try {
                isSearching = true
                invalidate()

                val results = LocationSearchHelper.searchPlaces(carContext, query)
                searchResults = results
            } catch (e: Exception) {
                Log.e("LocationSearchScreen", "장소 검색 중 오류 발생", e)
                searchResults = emptyList()
                CarToast.makeText(
                    carContext,
                    "검색 중 오류가 발생했습니다. 다시 시도해 주세요.",
                    CarToast.LENGTH_SHORT
                ).show()
            } finally {
                isSearching = false
                invalidate()
            }
        }
    }
}