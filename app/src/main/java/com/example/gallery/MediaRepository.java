package com.example.gallery;

import java.util.ArrayList;

public class MediaRepository {
    private static final MediaRepository INSTANCE = new MediaRepository();
    private final ArrayList<MediaModel> mediaList = new ArrayList<>();

    public static MediaRepository getInstance() {
        return INSTANCE;
    }

    public ArrayList<MediaModel> getList() {
        return mediaList;
    }

    public void setList(ArrayList<MediaModel> list) {
        mediaList.clear();
        mediaList.addAll(list);
    }
}
