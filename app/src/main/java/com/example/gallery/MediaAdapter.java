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

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    private final ArrayList<MediaModel> list;
    private final ArrayList<Integer> selectedPositions = new ArrayList<>();
    private boolean selectionMode = false;
    private final SelectionListener listener;

    public MediaAdapter(ArrayList<MediaModel> list, SelectionListener listener) {
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
    public void onBindViewHolder(@NonNull VH h, int pos) {
        MediaModel m = list.get(pos);
        Uri uri = Uri.parse(m.getUri());

        Glide.with(h.imageView.getContext())
                .load(uri)
                .centerCrop()
                .into(h.imageView);

        h.playIcon.setVisibility(m.isVideo() ? View.VISIBLE : View.GONE);

        // Selection UI
        if (selectionMode) {
            h.selectIcon.setVisibility(View.VISIBLE);
            h.selectIcon.setImageResource(
                    selectedPositions.contains(pos)
                            ? R.drawable.ic_circle_tick
                            : R.drawable.ic_circle_empty
            );
        } else {
            h.selectIcon.setVisibility(View.GONE);
        }

        h.itemView.setOnLongClickListener(v -> {
            if (!selectionMode) {
                selectionMode = true;
                toggle(pos);
            }
            return true;
        });

        h.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggle(pos);
            }
        });
    }

    private void toggle(int pos) {
        if (selectedPositions.contains(pos)) {
            selectedPositions.remove((Integer) pos);
        } else {
            selectedPositions.add(pos);
        }

        if (selectedPositions.isEmpty()) {
            selectionMode = false;
        }

        notifyDataSetChanged();
        listener.onSelectionChanged(selectedPositions.size());
    }
    public void clearSelection() {
        selectedPositions.clear();
        selectionMode = false;
        notifyDataSetChanged();
    }
    public boolean isSelectionMode() {
        return selectionMode;
    }

    public ArrayList<Uri> getSelectedUris() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (int pos : selectedPositions) {
            uris.add(Uri.parse(list.get(pos).getUri()));
        }
        return uris;
    }

    public void removeSelected() {
        selectedPositions.sort((a, b) -> b - a);
        for (int pos : selectedPositions) {
            list.remove(pos);
        }
        selectedPositions.clear();
        selectionMode = false;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imageView, playIcon, selectIcon;

        VH(@NonNull View v) {
            super(v);
            imageView = v.findViewById(R.id.imgMedia);
            playIcon = v.findViewById(R.id.imgPlay);
            selectIcon = v.findViewById(R.id.imgSelect);
        }
    }
}
