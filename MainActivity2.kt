package com.example.musicplayer

import android.content.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity2 : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var img: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playPause: ImageButton
    private lateinit var next: ImageButton
    private lateinit var db: AppDatabase
    private lateinit var allSongBtn: Button
    private lateinit var favSongBtn: Button

    private lateinit var adapter: MusicAdapter
    private val musicList = ArrayList<MusicModel>()

    private var service: MusicService? = null
    private val handler = Handler(Looper.getMainLooper())

    private var showingFav = false

    // ---------- SERVICE CONNECTION ----------
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as MusicService.LocalBinder).getService()
            service?.setPlaylist(musicList)
            handler.post(updateUI)
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    // ---------- UI SYNC ----------
    private val updateUI = object : Runnable {
        override fun run() {

            service?.let { s ->

                val index = s.getCurrentIndex()

                if (index != -1 && index < musicList.size) {

                    val m = musicList[index]

                    title.text = m.title
                    artist.text = m.artist
                    img.setImageURI(
                        Uri.parse("content://media/external/audio/albumart/${m.albumId}")
                    )

                    adapter.setPlayingIndex(index)

                    playPause.setImageResource(
                        if (s.isPlaying())
                            android.R.drawable.ic_media_pause
                        else
                            android.R.drawable.ic_media_play
                    )
                }
            }

            handler.postDelayed(this, 500)
        }
    }

    // ---------- ACTIVITY ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        recycler = findViewById(R.id.recyclerMusic)
        img = findViewById(R.id.imgCover)
        title = findViewById(R.id.txtTitle)
        artist = findViewById(R.id.txtArtist)
        playPause = findViewById(R.id.btnPlayPause)
        next = findViewById(R.id.btnNext)
        allSongBtn = findViewById(R.id.allSong)
        favSongBtn = findViewById(R.id.favSong)

        db = AppDatabase.getDatabase(this)

        recycler.layoutManager = LinearLayoutManager(this)

        loadMusic()

        adapter = MusicAdapter(
            musicList,
            { pos -> service?.play(pos) },
            db,
            false
        )

        recycler.adapter = adapter

        // ---------- ALL SONGS ----------
        allSongBtn.setOnClickListener {
            showingFav = false
            adapter = MusicAdapter(
                musicList,
                { pos -> service?.play(pos) },
                db,
                false
            )
            recycler.adapter = adapter
            service?.setPlaylist(musicList)
        }

        // ---------- FAV SONGS ----------
        favSongBtn.setOnClickListener {

            showingFav = true

            CoroutineScope(Dispatchers.IO).launch {

                val favs = db.musicDao().getAllFav()
                val favMusic = mutableListOf<MusicModel>()

                for (f in favs) {
                    favMusic.add(
                        MusicModel(f.id, f.title, f.artist, f.uri, f.albumId)
                    )
                }

                withContext(Dispatchers.Main) {

                    adapter = MusicAdapter(
                        favMusic,
                        { pos -> service?.play(pos) },
                        db,
                        true
                    )

                    recycler.adapter = adapter
                    service?.setPlaylist(favMusic)
                }
            }
        }

        findViewById<LinearLayout>(R.id.playerBar).setOnClickListener {
            startActivity(Intent(this, MainActivity3::class.java))
        }

        playPause.setOnClickListener { service?.toggle() }
        next.setOnClickListener { service?.next() }

        bindService(Intent(this, MusicService::class.java), conn, Context.BIND_AUTO_CREATE)

        loadLastPlayedFromDb()
    }

    // ---------- RESTORE LAST PLAYED ----------
    private fun loadLastPlayedFromDb() {

        CoroutineScope(Dispatchers.IO).launch {

            val song = db.musicDao().getLastPlayed()

            song?.let {

                val index = musicList.indexOfFirst { m -> m.id == it.id }

                if (index != -1) {
                    withContext(Dispatchers.Main) {
                        service?.play(index)
                    }
                }
            }
        }
    }

    // ---------- LOAD MUSIC ----------
    private fun loadMusic() {

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val c = contentResolver.query(uri, null, null, null, null) ?: return

        while (c.moveToNext()) {

            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
            val artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
            val albumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))

            val contentUri = ContentUris.withAppendedId(uri, id)

            musicList.add(
                MusicModel(id, title, artist, contentUri.toString(), albumId)
            )
        }
        c.close()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateUI)
        unbindService(conn)
        super.onDestroy()
    }
}
