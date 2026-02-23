package com.example.musicplayer

import android.app.RecoverableSecurityException
import android.content.*
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shabinder.jaudiotagger.audio.AudioFileIO
import com.shabinder.jaudiotagger.tag.FieldKey
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

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

    private var pendingUpdate: Triple<Long, String, String>? = null
    private var isRingtonePending = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as MusicService.LocalBinder).getService()
            service?.setPlaylist(musicList)
            handler.post(updateUI)
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private val updateUI = object : Runnable {
        override fun run() {
            service?.let { s ->
                val index = s.getCurrentIndex()
                if (index != -1 && index < musicList.size) {
                    val m = musicList[index]
                    title.text = m.title
                    artist.text = m.artist
                    img.setImageURI(Uri.parse("content://media/external/audio/albumart/${m.albumId}"))
                    adapter.setPlayingIndex(index)
                    playPause.setImageResource(if (s.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

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

        adapter = MusicAdapter(musicList, { pos -> service?.play(pos) }, { song -> showEditDialog(song) }, db, false)
        recycler.adapter = adapter

        loadMusic()

        allSongBtn.setOnClickListener { showingFav = false; loadMusic() }
        favSongBtn.setOnClickListener { showingFav = true; loadFavorites() }
        playPause.setOnClickListener { service?.toggle() }
        next.setOnClickListener { service?.next() }

        bindService(Intent(this, MusicService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    // --- RINGTONE LOGIC ---
    private fun setAsRingtone(songId: Long, songTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "Allow Settings permission then try again", Toast.LENGTH_LONG).show()
                return
            }
        }

        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // IMPORTANT FOR ANDROID 9: Update flags explicitly
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
                    put(MediaStore.Audio.Media.IS_ALARM, false)
                    put(MediaStore.Audio.Media.IS_MUSIC, true)
                }

                contentResolver.update(uri, values, null, null)

                withContext(Dispatchers.Main) {
                    RingtoneManager.setActualDefaultRingtoneUri(this@MainActivity2, RingtoneManager.TYPE_RINGTONE, uri)
                    Toast.makeText(this@MainActivity2, "Ringtone changed to $songTitle", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                isRingtonePending = true
                pendingUpdate = Triple(songId, songTitle, "")
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        startIntentSenderForResult(e.userAction.actionIntent.intentSender, 1001, null, 0, 0, 0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- EDIT METADATA LOGIC ---
    private fun editSongMetadata(songId: Long, newTitle: String, newArtist: String) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val tempFile = File(cacheDir, "editing.mp3")
                        contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(tempFile).use { output -> input.copyTo(output) } }
                        val audioFile = AudioFileIO.read(tempFile)
                        audioFile.tagOrCreateAndSetDefault.apply {
                            setField(FieldKey.TITLE, newTitle)
                            setField(FieldKey.ARTIST, newArtist)
                        }
                        audioFile.commit()
                        FileOutputStream(pfd.fileDescriptor).use { output -> tempFile.inputStream().use { input -> input.copyTo(output) } }
                        tempFile.delete()
                    }
                } else {
                    getFilePathFromUri(uri)?.let { path ->
                        val file = File(path)
                        val audioFile = AudioFileIO.read(file)
                        audioFile.tagOrCreateAndSetDefault.apply {
                            setField(FieldKey.TITLE, newTitle)
                            setField(FieldKey.ARTIST, newArtist)
                        }
                        audioFile.commit()
                        MediaScannerConnection.scanFile(this@MainActivity2, arrayOf(file.absolutePath), null, null)
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                    put(MediaStore.Audio.Media.ARTIST, newArtist)
                }
                contentResolver.update(uri, values, null, null)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity2, "Updated!", Toast.LENGTH_SHORT).show()
                    loadMusic()
                }
            } catch (e: SecurityException) {
                isRingtonePending = false
                pendingUpdate = Triple(songId, newTitle, newArtist)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    startIntentSenderForResult(e.userAction.actionIntent.intentSender, 1001, null, 0, 0, 0)
                }
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        }
        return null
    }

    private fun showEditDialog(song: MusicModel) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_song, null)
        val editTitle = view.findViewById<EditText>(R.id.editTitle)
        val editArtist = view.findViewById<EditText>(R.id.editArtist)
        editTitle.setText(song.title)
        editArtist.setText(song.artist)

        AlertDialog.Builder(this)
            .setTitle("Options")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                editSongMetadata(song.id, editTitle.text.toString(), editArtist.text.toString())
            }
            .setNeutralButton("Set Ringtone") { _, _ ->
                setAsRingtone(song.id, song.title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            pendingUpdate?.let { (id, t, a) ->
                if (isRingtonePending) setAsRingtone(id, t) else editSongMetadata(id, t, a)
            }
            pendingUpdate = null
            isRingtonePending = false
        }
    }

    private fun loadMusic() {
        musicList.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID)
        contentResolver.query(uri, projection, null, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val albumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                musicList.add(MusicModel(id, title, artist, ContentUris.withAppendedId(uri, id).toString(), albumId))
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadFavorites() {
        CoroutineScope(Dispatchers.IO).launch {
            val favs = db.musicDao().getAllFav()
            withContext(Dispatchers.Main) {
                musicList.clear()
                favs.forEach { f -> musicList.add(MusicModel(f.id, f.title, f.artist, f.uri, f.albumId)) }
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateUI)
        unbindService(conn)
        super.onDestroy()
    }
}