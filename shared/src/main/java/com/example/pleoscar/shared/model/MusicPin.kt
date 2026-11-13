package com.example.pleoscar.shared.model

data class MusicPin(
    val id: String = "",
    val songTitle: String = "",
    val artist: String = "",
    val hint: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val albumArtUrl: String = "",
    val previewUrl: String = "",
    val createdAt: Any? = null
)