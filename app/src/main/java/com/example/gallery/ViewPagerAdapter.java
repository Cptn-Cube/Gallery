package com.example.gallery;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
public class ViewPagerAdapter extends RecyclerView.Adapter<ViewPagerAdapter.VH> {

    private final ArrayList<MediaModel> list;
    private final ExoPlayer player;
    private int current = -1;

    public ViewPagerAdapter(ArrayList<MediaModel> list, ExoPlayer player) {
        this.list = list;
        this.player = player;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_viewpager_media, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MediaModel model = list.get(position);

        // reset views
        holder.imageView.setVisibility(View.GONE);
        holder.playerView.setVisibility(View.GONE);
        holder.playerView.setPlayer(null);

        if (model.isVideo()) {
            holder.playerView.setVisibility(View.VISIBLE);
        } else {
            holder.imageView.setVisibility(View.VISIBLE);
            Glide.with(holder.imageView.getContext())
                    .load(Uri.parse(model.getUri()))
                    .fitCenter()
                    .into(holder.imageView);
        }
    }

    public void attachPlayer(int position,RecyclerView recyclerView) {
        if (position < 0 || position >= list.size()) return;

        MediaModel model = list.get(position);
        if (!model.isVideo()) return;
        stop();
        current = position;
        RecyclerView.ViewHolder vh =
                recyclerView.findViewHolderForAdapterPosition(position);
        if (!(vh instanceof VH)) return;
        VH holder = (VH) vh;

        holder.playerView.setPlayer(null);
        player.clearMediaItems();

        holder.playerView.setPlayer(player);

        player.setMediaItem(MediaItem.fromUri(model.getUri()));
        player.prepare();
        player.seekTo(model.playbackPosition);
        player.setPlayWhenReady(true);
    }
    /** ⏸ Stop and save position */
    public void stop() {
        if (current != -1 && current < list.size()) {
            MediaModel model = list.get(current);
            model.playbackPosition = player.getCurrentPosition();

            player.setPlayWhenReady(false);
            player.stop();
            player.clearMediaItems();

            current = -1;
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imageView;
        PlayerView playerView;

        VH(View v) {
            super(v);
            imageView = v.findViewById(R.id.fullImage);
            playerView = v.findViewById(R.id.playerView);
        }
    }
}
