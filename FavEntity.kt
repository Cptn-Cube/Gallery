package com.example.musicplayer


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fav_music")
data class FavEntity(

    @PrimaryKey
    val id: Long,

    val title: String,
    val artist: String,
    val uri: String,
    val albumId: Long
)
