package com.example.musicplayer

import java.io.Serializable

data class CategoryModel(
    val name: String,
    val songCount: Int,
    val type: CategoryType,
    val id: String = "" ,// Used for IDs like AlbumID or GenreID
    val fullPath: String = ""
): Serializable

enum class CategoryType { ALL, FOLDER, ALBUM, ARTIST, GENRE, PLAYLIST }