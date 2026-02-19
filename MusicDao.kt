package com.example.musicplayer

import androidx.room.*

@Dao
interface MusicDao {

    // ---------------- LAST PLAYED ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLastPlayed(song: LastPlayedEntity)

    @Query("SELECT * FROM last_played LIMIT 1")
    suspend fun getLastPlayed(): LastPlayedEntity?

    @Query("DELETE FROM last_played")
    suspend fun clearLastPlayed()


    // ---------------- FAVORITES ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFav(song: FavEntity)

    @Delete
    suspend fun removeFav(song: FavEntity)

    @Query("SELECT * FROM fav_music")
    suspend fun getAllFav(): List<FavEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM fav_music WHERE id=:songId)")
    suspend fun isFav(songId: Long): Boolean
}
