package com.example.gallery;

import java.io.Serializable;

public class MediaModel {
    private final String uri;
    public long playbackPosition = 0;

    public MediaModel(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public boolean isVideo() {
        return uri.contains("video");
    }
}

