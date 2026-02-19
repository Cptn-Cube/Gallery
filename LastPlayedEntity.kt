package com.example.musicplayer



import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "last_played")
data class LastPlayedEntity(

    @PrimaryKey
    val id: Long,

    val title: String,
    val artist: String,
    val uri: String,
    val albumId: Long
)
