package com.example.gallery;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.VH> {

    public interface OnItemClick {
        void onClick(int position);
    }

    private final ArrayList<MediaModel> list;
    private final OnItemClick listener;

    public MediaAdapter(ArrayList<MediaModel> list, OnItemClick listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {

        MediaModel model = list.get(position);
        Uri uri = Uri.parse(model.getUri());

        Glide.with(holder.imageView.getContext())
                .load(uri)
                .centerCrop()
                .into(holder.imageView);

        holder.playIcon.setVisibility(
                model.isVideo() ? View.VISIBLE : View.GONE
        );

        holder.itemView.setOnClickListener(v ->
                listener.onClick(position)
        );
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        ImageView imageView;
        ImageView playIcon;

        VH(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgMedia);
            playIcon = itemView.findViewById(R.id.imgPlay);
        }
    }
}
