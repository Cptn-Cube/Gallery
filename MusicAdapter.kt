package com.example.musicplayer

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MusicAdapter(
    private var list: MutableList<MusicModel>,
    private val onClick: (Int) -> Unit,
    private val db: AppDatabase,
    private val isFavScreen: Boolean = false
) : RecyclerView.Adapter<MusicAdapter.VH>() {

    private var playingIndex = -1

    fun setPlayingIndex(index: Int) {
        val old = playingIndex
        playingIndex = index
        if (old != -1) notifyItemChanged(old)
        if (index != -1) notifyItemChanged(index)
    }

    fun updateList(newList: MutableList<MusicModel>) {
        list = newList
        notifyDataSetChanged()
    }

    inner class VH(parent: ViewGroup) :
        RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.row_music, parent, false)
        ) {

        val cover: ImageView = itemView.findViewById(R.id.imgRowCover)
        val title: TextView = itemView.findViewById(R.id.txtRowTitle)
        val artist: TextView = itemView.findViewById(R.id.txtRowArtist)
        val fav: ImageView = itemView.findViewById(R.id.favImg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(parent)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {

        val song = list[position]

        holder.title.text = song.title
        holder.artist.text = song.artist
        holder.cover.setImageURI(
            Uri.parse("content://media/external/audio/albumart/${song.albumId}")
        )

        // ---------- PLAY CLICK ----------
        holder.itemView.setOnClickListener {
            onClick(holder.adapterPosition)
        }

        // ---------- HEART STATE ----------
        CoroutineScope(Dispatchers.IO).launch {
            val isFav = db.musicDao().isFav(song.id)

            withContext(Dispatchers.Main) {
                holder.fav.setImageResource(
                    if (isFav) R.drawable.fill_heart
                    else R.drawable.empty_heart
                )
            }
        }

        // ---------- HEART CLICK ----------
        holder.fav.setOnClickListener {

            val adapterPos = holder.adapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener

            CoroutineScope(Dispatchers.IO).launch {

                val dao = db.musicDao()
                val favEntity = FavEntity(
                    song.id,
                    song.title,
                    song.artist,
                    song.uri,
                    song.albumId
                )

                val alreadyFav = dao.isFav(song.id)

                if (alreadyFav) {
                    dao.removeFav(favEntity)
                } else {
                    dao.addFav(favEntity)
                }

                val newState = dao.isFav(song.id)

                withContext(Dispatchers.Main) {

                    holder.fav.setImageResource(
                        if (newState) R.drawable.fill_heart
                        else R.drawable.empty_heart
                    )

                    // ⭐ REMOVE FROM FAV SCREEN ONLY
                    if (!newState && isFavScreen) {
                        list.removeAt(adapterPos)
                        notifyItemRemoved(adapterPos)
                    }
                }
            }
        }

        // ---------- PLAYING HIGHLIGHT ----------
        if (position == playingIndex) {
            holder.title.setTextColor(0xFF00C853.toInt())
        } else {
            holder.title.setTextColor(Color.BLACK)
        }
    }
}
