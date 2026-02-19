package com.example.musicplayer

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder

import kotlinx.coroutines.*

import kotlinx.coroutines.Dispatchers

class MusicService : Service() {

    private val binder = LocalBinder()

    private var player: MediaPlayer? = null
    private var list: List<MusicModel> = emptyList()
    private var index = -1

    // ⭐ UI listeners
    var onSongChanged: ((Int) -> Unit)? = null
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService() = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun setPlaylist(l: List<MusicModel>) {
        list = l
    }

    fun getCurrentIndex() = index
    fun isPlaying() = player?.isPlaying == true
    fun getDuration() = player?.duration ?: 0
    fun getPosition() = player?.currentPosition ?: 0

    // ---------------- PLAY ----------------
    fun play(i: Int) {

        if (i < 0 || i >= list.size) return

        index = i

        try {
            player?.release()
            player = MediaPlayer()

            player?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            player?.setDataSource(applicationContext, Uri.parse(list[i].uri))
            player?.prepareAsync()

            player?.setOnPreparedListener {
                it.start()

                onSongChanged?.invoke(index)
                onPlayStateChanged?.invoke(true)

                // ⭐ SAVE TO ROOM
                saveLastPlayed(list[i])
            }

            player?.setOnCompletionListener {
                next()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun saveLastPlayed(song: MusicModel) {

        val db = AppDatabase.getDatabase(applicationContext)

        val entity = LastPlayedEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            uri = song.uri,
            albumId = song.albumId
        )

        CoroutineScope(Dispatchers.IO).launch {
            db.musicDao().clearLastPlayed()
            db.musicDao().insertLastPlayed(entity)
        }
    }


    fun toggle() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                onPlayStateChanged?.invoke(false)
            } else {
                it.start()
                onPlayStateChanged?.invoke(true)
            }
        }
    }

    fun next() {
        if (list.isEmpty()) return
        val newIndex = (index + 1) % list.size
        play(newIndex)
    }

    fun previous() {
        if (list.isEmpty()) return
        val newIndex = if (index - 1 < 0) list.size - 1 else index - 1
        play(newIndex)
    }
}
