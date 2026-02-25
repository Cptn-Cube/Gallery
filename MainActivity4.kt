package com.example.musicplayer

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity4 : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var horizontalScroll: HorizontalScrollView
    private val musicList = ArrayList<MusicModel>()
    private val categoryList = ArrayList<CategoryModel>()

    // Original MusicAdapter from your reference code
    private lateinit var musicAdapter: MusicAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main4)

        recycler = findViewById(R.id.recyclerMusic)
        horizontalScroll = findViewById(R.id.scrollview)
        recycler.layoutManager = LinearLayoutManager(this)

        setupButtons()
        loadAllSongs() // Default view
    }

    private fun setupButtons() {
        val btnAll = findViewById<Button>(R.id.allsong)
        val btnFolder = findViewById<Button>(R.id.folder)
        val btnAlbum = findViewById<Button>(R.id.album)
        val btnGenre = findViewById<Button>(R.id.genre)
        val btnPlaylist = findViewById<Button>(R.id.playlist)

        val clickListener = android.view.View.OnClickListener { view ->
            scrollToButton(view)
            when (view.id) {
                R.id.allsong -> loadAllSongs()
                R.id.folder -> loadCategories(CategoryType.FOLDER)
                R.id.album -> loadCategories(CategoryType.ALBUM)
                R.id.genre -> loadCategories(CategoryType.GENRE)
                R.id.playlist -> loadCategories(CategoryType.PLAYLIST)
            }
        }

        btnAll.setOnClickListener(clickListener)
        btnFolder.setOnClickListener(clickListener)
        btnAlbum.setOnClickListener(clickListener)
        btnGenre.setOnClickListener(clickListener)
        btnPlaylist.setOnClickListener(clickListener)
    }

    private fun scrollToButton(view: android.view.View) {
        val scrollX = view.left - (horizontalScroll.width / 2) + (view.width / 2)
        horizontalScroll.smoothScrollTo(scrollX, 0)
    }

    private fun loadAllSongs() {
        musicList.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATA)

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1) ?: "Unknown"
                val artist = cursor.getString(2) ?: "Unknown"
                val albId = cursor.getLong(3)
                val path = cursor.getString(4)
                musicList.add(MusicModel(id, title, artist, ContentUris.withAppendedId(uri, id).toString(), albId))
            }
        }
        // Use your existing MusicAdapter
        musicAdapter = MusicAdapter(musicList, { /* play */ }, { /* options */ }, AppDatabase.getDatabase(this), false)
        recycler.adapter = musicAdapter
    }

    private fun loadCategories(type: CategoryType) {
        categoryList.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = when (type) {
            CategoryType.FOLDER -> arrayOf(MediaStore.Audio.Media.DATA)
            CategoryType.ALBUM -> arrayOf(MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID)
            CategoryType.GENRE -> arrayOf(MediaStore.Audio.Media._ID) // Genres need a different URI usually
            else -> arrayOf(MediaStore.Audio.Media.TITLE)
        }

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val map = mutableMapOf<String, Int>()
            while (cursor.moveToNext()) {
                val key = when (type) {
                    CategoryType.FOLDER -> File(cursor.getString(0)).parentFile?.name ?: "Root"
                    CategoryType.ALBUM -> cursor.getString(0) ?: "Unknown"
                    else -> "Unknown"
                }
                map[key] = map.getOrDefault(key, 0) + 1
            }

            map.forEach { (name, count) ->
                categoryList.add(CategoryModel(name, count, type))
            }
        }

        recycler.adapter = CategoryAdapter(categoryList) { category ->
            openCategoryDetail(category)
        }
    }

    private fun openCategoryDetail(category: CategoryModel) {
        // Here you would navigate to a new Activity or Fragment
        // passing the CategoryModel. In that screen, you run a query
        // with a WHERE clause based on the category name.
        val intent = Intent(this, CategoryDetailActivity::class.java)
        intent.putExtra("CATEGORY_DATA", category)
        startActivity(intent)
    }

    private fun filterMusicList(category: CategoryModel) {
        val filteredList = ArrayList<MusicModel>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val selection = when(category.type) {
            CategoryType.FOLDER -> "${MediaStore.Audio.Media.DATA} LIKE ?"
            CategoryType.ALBUM -> "${MediaStore.Audio.Media.ALBUM} = ?"
            else -> null
        }

        val selectionArgs = when(category.type) {
            CategoryType.FOLDER -> arrayOf("%/${category.name}/%")
            CategoryType.ALBUM -> arrayOf(category.name)
            else -> null
        }

        contentResolver.query(uri, null, selection, selectionArgs, null)?.use { cursor ->
            // ... Parse cursor into filteredList exactly like loadAllSongs ...
        }

        musicAdapter = MusicAdapter(filteredList, { /* play */ }, { /* options */ }, AppDatabase.getDatabase(this), false)
        recycler.adapter = musicAdapter
    }
}