package com.example.musicplayer

import android.animation.ValueAnimator
import android.content.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.*
import com.shabinder.jaudiotagger.audio.AudioFileIO
import com.shabinder.jaudiotagger.tag.FieldKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MainActivity3 : AppCompatActivity() {

    private lateinit var panelPlayer: View
    private lateinit var upArrow: ImageView

    private lateinit var panelTitle: TextView
    private lateinit var panelArtist: TextView
    private lateinit var panelPlay: ImageButton
    private lateinit var panelNext: ImageButton
    private lateinit var panelPrev: ImageButton
    private lateinit var panelProgress: ProgressBar

    private lateinit var panelCarousel: RecyclerView
    private lateinit var recycler: RecyclerView

    private lateinit var carouselAdapter: CoverCarouselAdapter
    private lateinit var listAdapter: MusicAdapter

    private val musicList = ArrayList<MusicModel>()
    private var service: MusicService? = null

    private lateinit var snapHelper: PagerSnapHelper

    private var isCollapsed = false
    private val progressHandler = Handler(Looper.getMainLooper())

    // ---------- SERVICE ----------
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {

            service = (binder as MusicService.LocalBinder).getService()
            service?.setPlaylist(musicList)

            val i = service?.getCurrentIndex() ?: 0
            if (i >= 0) syncUI(i)
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        panelPlayer = findViewById(R.id.panelPlayer)
        upArrow = findViewById(R.id.upPanelPlayer)

        panelTitle = findViewById(R.id.panelTitle)
        panelArtist = findViewById(R.id.panelArtist)
        panelPlay = findViewById(R.id.panelPlay)
        panelNext = findViewById(R.id.panelNext)
        panelPrev = findViewById(R.id.panelPrev)
        panelProgress = findViewById(R.id.panelProgress)

        panelCarousel = findViewById(R.id.panelCarousel)
        recycler = findViewById(R.id.recyclerMusic)

        // ---------- CAROUSEL ----------
        panelCarousel.layoutManager = CarouselZoomLayoutManager(this)
        snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(panelCarousel)

        recycler.layoutManager = LinearLayoutManager(this)

        loadMusic()

        val db = AppDatabase.getDatabase(this)

        listAdapter  = MusicAdapter(
            musicList,
            { pos -> service?.play(pos) },
            { song -> showEditDialog(song) },
            db,
            false
        )
        recycler.adapter = listAdapter

        carouselAdapter = CoverCarouselAdapter(musicList) { pos ->
            service?.play(pos)
        }
        panelCarousel.adapter = carouselAdapter

        panelPlay.setOnClickListener { service?.toggle() }
        panelNext.setOnClickListener { service?.next() }
        panelPrev.setOnClickListener { service?.previous() }

        // ⭐ PANEL SLIDE
        upArrow.setOnClickListener { togglePanel() }

        bindService(Intent(this, MusicService::class.java), conn, Context.BIND_AUTO_CREATE)
        startProgressUpdater()
    }

    // ---------- PANEL ANIMATION ----------

    fun Context.dpToPxF(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
    private fun reloadMusicLibrary() {
        musicList.clear()
        loadMusic()
        listAdapter.notifyDataSetChanged()
    }
    private fun showEditDialog(song: MusicModel) {

        val view = layoutInflater.inflate(R.layout.dialog_edit_song, null)

        val editTitle = view.findViewById<EditText>(R.id.editTitle)
        val editArtist = view.findViewById<EditText>(R.id.editArtist)

        editTitle.setText(song.title)
        editArtist.setText(song.artist)

        AlertDialog.Builder(this)
            .setTitle("Edit Song Info")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->

                val newTitle = editTitle.text.toString()
                val newArtist = editArtist.text.toString()

                editSongMetadata(song.uri, newTitle, newArtist)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun editSongMetadata(uri: String, newTitle: String, newArtist: String) {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val file = File(Uri.parse(uri).path!!)

                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, newTitle)
                tag.setField(FieldKey.ARTIST, newArtist)

                audioFile.commit()

                // Tell Android to rescan file
                MediaScannerConnection.scanFile(
                    this@MainActivity3,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity3, "Updated!", Toast.LENGTH_SHORT).show()
                    reloadMusicLibrary()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

//    private fun togglePanel() {
//
////        val moveDistance = panelPlayer.height - 140  // only seekbar visible
////        val playerHeight = resources.getDimensionPixelSize(R.dimen._260sdp)
//        val miniPlayerVisible = dpToPxF(80F) // 80dp visible mini player
//        val moveDistance = panelPlayer.height - miniPlayerVisible
//
//        if (!isCollapsed) {
//
//            panelPlayer.animate()
//                .translationY(-moveDistance.toFloat())
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            upArrow.animate()
//                .translationY(-moveDistance.toFloat())
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            recycler.animate()
//                .translationY(-moveDistance.toFloat())
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            panelCarousel.animate()
//                .translationY(-moveDistance.toFloat())
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//
//
//
//            upArrow.setImageResource(R.drawable.down_arrow)
//            isCollapsed = true
//
//        } else {
//
//            panelPlayer.animate()
//                .translationY(0f)
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            upArrow.animate()
//                .translationY(0F)
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            recycler.animate()
//                .translationY(0F)
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            panelCarousel.animate()
//                .translationY(0F)
//                .setDuration(350)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//                .start()
//            upArrow.setImageResource(R.drawable.up_arrow)
//            isCollapsed = false
//        }
//    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
//    private fun animatePanelHeight(from: Int, to: Int) {
//
//        val animator = ValueAnimator.ofInt(from, to)
//
//        animator.addUpdateListener {
//            val value = it.animatedValue as Int
//            val params = panelPlayer.layoutParams
//            params.height = value
//            panelPlayer.layoutParams = params
//            panelCarousel.layoutParams = params
//        }
//
//        animator.duration = 350
//        animator.interpolator = AccelerateDecelerateInterpolator()
//        animator.start()
//    }
//
//private fun togglePanel() {
//
//    val expandedHeight = dpToPx(360)
//    val collapsedHeight = dpToPx(80) // mini player size
//
//    val currentHeight = panelPlayer.height
//
//    if (!isCollapsed) {
//        animatePanelHeight(currentHeight, collapsedHeight)
//        upArrow.setImageResource(R.drawable.down_arrow)
//        isCollapsed = true
//    } else {
//        animatePanelHeight(currentHeight, expandedHeight)
//        upArrow.setImageResource(R.drawable.up_arrow)
//        isCollapsed = false
//    }
//}
private fun animatePanelHeight(from: Int, to: Int) {

    val animator = ValueAnimator.ofInt(from, to)

    animator.addUpdateListener { animation ->

        val value = animation.animatedValue as Int

        val params = panelPlayer.layoutParams as RelativeLayout.LayoutParams
        params.height = value
        panelPlayer.layoutParams = params
        panelCarousel.layoutParams = params

        // Force layout recalculation so RecyclerView expands
        panelPlayer.requestLayout()
    }

    animator.duration = 350
    animator.interpolator = AccelerateDecelerateInterpolator()
    animator.start()
}
    private var expandedHeight = 0


    private fun togglePanel() {

        if (expandedHeight == 0)
            expandedHeight = panelPlayer.height

        val collapsedHeight = dpToPx(80) // visible mini player

        if (!isCollapsed) {

            animatePanelHeight(panelPlayer.height, collapsedHeight)
            upArrow.setImageResource(R.drawable.down_arrow)
            isCollapsed = true

        } else {

            animatePanelHeight(panelPlayer.height, expandedHeight)
            upArrow.setImageResource(R.drawable.up_arrow)
            isCollapsed = false
        }
    }





    // ---------- PROGRESS ----------
    private fun startProgressUpdater() {
        progressHandler.post(object : Runnable {
            override fun run() {

                service?.let { s ->
                    val dur = s.getDuration()
                    val pos = s.getPosition()

                    if (dur > 0) {
                        panelProgress.max = dur
                        panelProgress.progress = pos
                    }

                    panelPlay.setImageResource(
                        if (s.isPlaying())
                            android.R.drawable.ic_media_pause
                        else
                            android.R.drawable.ic_media_play
                    )
                }

                progressHandler.postDelayed(this, 500)
            }
        })
    }

    // ---------- UI SYNC ----------
    private fun syncUI(index: Int) {

        if (index !in musicList.indices) return

        val m = musicList[index]

        panelTitle.text = m.title
        panelArtist.text = m.artist

        listAdapter.setPlayingIndex(index)
        carouselAdapter.setSelected(index)

        recycler.scrollToPosition(index)
        panelCarousel.scrollToPosition(index)
    }

    // ---------- LOAD SONGS ----------
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
        unbindService(conn)
        super.onDestroy()
    }
}
