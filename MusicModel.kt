package com.example.musicplayer

data class MusicModel(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val albumId: Long
)
