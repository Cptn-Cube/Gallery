package com.example.musicplayer

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CategoryDetailActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var txtTitle: TextView
    private val musicList = ArrayList<MusicModel>()
    private lateinit var adapter: MusicAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_detail)

        recycler = findViewById(R.id.recyclerCategoryMusic)
        txtTitle = findViewById(R.id.txtCategoryTitle)
        recycler.layoutManager = LinearLayoutManager(this)

        // Get data from Intent
        val category = intent.getSerializableExtra("CATEGORY_DATA") as? CategoryModel

        if (category != null) {
            txtTitle.text = category.name
            loadFilteredMusic(category)
        }
    }

    private fun loadFilteredMusic(category: CategoryModel) {
        musicList.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Define selection (WHERE clause) based on type
        var selection: String? = null
        var selectionArgs: Array<String>? = null

        when (category.type) {
            CategoryType.FOLDER -> {
                selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
                // Matches files where the path contains the folder name
                selectionArgs = arrayOf("%/${category.name}/%")
            }
            CategoryType.ALBUM -> {
                selection = "${MediaStore.Audio.Media.ALBUM} = ?"
                selectionArgs = arrayOf(category.name)
            }
            CategoryType.ARTIST -> {
                selection = "${MediaStore.Audio.Media.ARTIST} = ?"
                selectionArgs = arrayOf(category.name)
            }
            // For Genre and Playlist, the logic is slightly different
            // but for this project, focus on Folder/Album/Artist first
            else -> {}
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID
        )

        contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1) ?: "Unknown"
                val artist = cursor.getString(2) ?: "Unknown"
                val albumId = cursor.getLong(3)

                musicList.add(MusicModel(
                    id, title, artist,
                    ContentUris.withAppendedId(uri, id).toString(),
                    albumId
                ))
            }
        }

        // Initialize your existing MusicAdapter
        adapter = MusicAdapter(musicList,
            { pos -> /* Handle Play */ },
            { song -> /* Handle Options like Rename/Edit */ },
            AppDatabase.getDatabase(this),
            false
        )
        recycler.adapter = adapter
    }
}