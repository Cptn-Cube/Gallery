package com.example.musicplayer

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shabinder.jaudiotagger.audio.AudioFileIO
import com.shabinder.jaudiotagger.tag.FieldKey
import com.shabinder.jaudiotagger.tag.images.ArtworkFactory
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity2 : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var db: AppDatabase
    private lateinit var adapter: MusicAdapter
    private val musicList = ArrayList<MusicModel>()
    private var service: MusicService? = null

    private var selectedImageUri: Uri? = null
    private var dialogImageView: ImageView? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            dialogImageView?.setImageURI(it)
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as MusicService.LocalBinder).getService()
            service?.setPlaylist(musicList)
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        checkAndRequestPermissions()

        recycler = findViewById(R.id.recyclerMusic)
        findViewById<Button>(R.id.favSong).setOnClickListener{
            startActivity(Intent(this@MainActivity2, MainActivity4::class.java))
        }
        db = AppDatabase.getDatabase(this)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = MusicAdapter(musicList,
            { pos -> service?.play(pos) },
            { song -> showOptionsDialog(song) },
            db, false)

        recycler.adapter = adapter

        loadMusic()
        bindService(Intent(this, MusicService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val listToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (listToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listToRequest.toTypedArray(), 100)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun showOptionsDialog(song: MusicModel) {
        val options = arrayOf("Play", "Edit Info & Rename", "Details Info", "Set as Ringtone")
        AlertDialog.Builder(this)
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { val idx = musicList.indexOf(song); service?.play(idx) }
                    1 -> showEditDialog(song)
                    2 -> showDetailsDialog(song)
                    3 -> setAsRingtone(song.id, song.title)
                }
            }
            .show()
    }

    private fun setAsRingtone(songId: Long, songTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_RINGTONE, true) }
                contentResolver.update(uri, values, null, null)
                withContext(Dispatchers.Main) {
                    RingtoneManager.setActualDefaultRingtoneUri(this@MainActivity2, RingtoneManager.TYPE_RINGTONE, uri)
                    Toast.makeText(this@MainActivity2, "Ringtone set!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun editFullMetadata(songId: Long, nTitle: String, nArtist: String, nAlbum: String, nGenre: String, isTagSupported: Boolean, newImgUri: Uri?) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val TAG = "MusicEdit"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oldPath = getFilePathFromUri(uri) ?: return@launch
                val oldFile = File(oldPath)
                val ext = if (oldPath.contains(".")) oldPath.substring(oldPath.lastIndexOf(".")) else ".mp3"

                // Sanitize filename to prevent system naming conflicts
                val safeTitle = nTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                val newFileName = "$safeTitle$ext"
                val newPath = "${oldFile.parent}/$newFileName"
                val newFile = File(newPath)

                // 1. UPDATE PHYSICAL TAGS FIRST (Stay on old path to avoid EPERM)
                if (isTagSupported && oldFile.exists()) {
                    try {
                        val audioFile = AudioFileIO.read(oldFile)
                        val tag = audioFile.tagOrCreateAndSetDefault
                        tag.setField(FieldKey.TITLE, nTitle)
                        tag.setField(FieldKey.ARTIST, nArtist)
                        tag.setField(FieldKey.ALBUM, nAlbum)
                        tag.setField(FieldKey.GENRE, nGenre)

                        newImgUri?.let { imgUri ->
                            val bitmap = decodeBitmapSafely(imgUri)
                            bitmap?.let {
                                val tempArt = File(cacheDir, "art_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(tempArt).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                                tag.deleteArtworkField()
                                tag.setField(ArtworkFactory.createArtworkFromFile(tempArt))
                                tempArt.delete()
                                it.recycle()
                            }
                        }
                        audioFile.commit()
                        Log.d(TAG, "Physical tags committed.")
                    } catch (e: Exception) { Log.e(TAG, "Tag Error: ${e.message}") }
                }

                // 2. DATABASE RENAME AND METADATA UPDATE
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, nTitle)
                    put(MediaStore.Audio.Media.ARTIST, nArtist)
                    put(MediaStore.Audio.Media.ALBUM, nAlbum)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ (Q): Use IS_PENDING to lock the record
                    values.put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName)
                    values.put(MediaStore.Audio.Media.IS_PENDING, 1)
                    contentResolver.update(uri, values, null, null)

                    // Finalize the update and unlock
                    val unlockValues = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                    contentResolver.update(uri, unlockValues, null, null)
                    Log.d(TAG, "MediaStore Update Successful (Android 10+)")
                } else {
                    // Android 8-9 (Legacy): Manual Rename
                    if (oldPath != newPath && oldFile.renameTo(newFile)) {
                        values.put(MediaStore.Audio.Media.DATA, newPath)
                        Log.d(TAG, "Legacy File Rename Success")
                    }
                    contentResolver.update(uri, values, null, null)
                    if (newImgUri != null) deleteAlbumArtCache(songId)
                }

                // 3. FORCE SCAN (Removes stale entries and fixes Genre/Artist linkage)
                MediaScannerConnection.scanFile(this@MainActivity2, arrayOf(newPath, oldPath), null) { _, _ ->
                    Handler(Looper.getMainLooper()).post {
                        loadMusic()
                        Toast.makeText(this@MainActivity2, "Update Successful!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }

    private fun deleteAlbumArtCache(songId: Long) {
        try {
            val cursor = contentResolver.query(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId), arrayOf(MediaStore.Audio.Media.ALBUM_ID), null, null, null)
            val albumId = cursor?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return
            val albumArtUri = Uri.parse("content://media/external/audio/albumart")
            contentResolver.delete(ContentUris.withAppendedId(albumArtUri, albumId), null, null)

            val thumbDir = File(Environment.getExternalStorageDirectory(), ".thumbnails")
            if (thumbDir.exists()) thumbDir.listFiles()?.filter { it.name.contains(albumId.toString()) }?.forEach { it.delete() }
        } catch (e: Exception) { Log.w("MusicEdit", "Cache cleanup failed: ${e.message}") }
    }

    private fun decodeBitmapSafely(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            } else {
                @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) { null }
    }

    private fun showEditDialog(song: MusicModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }
        selectedImageUri = null
        val view = layoutInflater.inflate(R.layout.dialog_edit_song, null)
        val editTitle = view.findViewById<EditText>(R.id.editTitle)
        val editArtist = view.findViewById<EditText>(R.id.editArtist)
        val editAlbum = view.findViewById<EditText>(R.id.editAlbum)
        val editGenre = view.findViewById<EditText>(R.id.editGenre)
        val btnPickImg = view.findViewById<Button>(R.id.btnPickImage)
        dialogImageView = view.findViewById(R.id.selImage)

        editTitle.setText(song.title)
        editArtist.setText(song.artist)
        dialogImageView?.setImageURI(Uri.parse("content://media/external/audio/albumart/${song.albumId}"))

        val path = getFilePathFromUri(Uri.parse(song.uri)) ?: ""
        var isTagSupported = true
        try {
            val af = AudioFileIO.read(File(path))
            editAlbum.setText(af.tag?.getFirst(FieldKey.ALBUM) ?: "")
            editGenre.setText(af.tag?.getFirst(FieldKey.GENRE) ?: "")
        } catch (e: Exception) { isTagSupported = false }

        if (!isTagSupported || path.contains("WhatsApp", true)) {
            isTagSupported = false
            btnPickImg.isEnabled = false
            editArtist.isEnabled = false; editAlbum.isEnabled = false; editGenre.isEnabled = false
        }

        btnPickImg.setOnClickListener { imagePickerLauncher.launch("image/*") }

        AlertDialog.Builder(this)
            .setTitle(if (isTagSupported) "Edit Details" else "Rename Only")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                editFullMetadata(song.id, editTitle.text.toString(), editArtist.text.toString(),
                    editAlbum.text.toString(), editGenre.text.toString(), isTagSupported, selectedImageUri)
            }
            .setNegativeButton("Cancel") { _, _ -> dialogImageView = null }
            .show()
    }

    private fun showDetailsDialog(song: MusicModel) {
        val uri = Uri.parse(song.uri)
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                val dur = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val info = "📌 Title: ${song.title}\n👤 Artist: $artist\n💿 Album: $album\n📂 Folder: ${File(path).parentFile?.name}\n📏 Size: ${Formatter.formatFileSize(this, size)}\n🕒 Duration: ${formatDuration(dur)}"
                AlertDialog.Builder(this).setTitle("Details").setMessage(info).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun formatDuration(duration: Long): String {
        val min = (duration / 1000) / 60
        val sec = (duration / 1000) % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun loadMusic() {
        musicList.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID)
        contentResolver.query(uri, proj, null, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val t = c.getString(1) ?: "Unknown"
                val a = c.getString(2) ?: "Unknown"
                val albId = c.getLong(3)
                musicList.add(MusicModel(id, t, a, ContentUris.withAppendedId(uri, id).toString(), albId))
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        try { unbindService(conn) } catch (e: Exception) {}
        super.onDestroy()
    }
}