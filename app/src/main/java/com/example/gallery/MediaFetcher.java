package com.example.gallery;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.function.Consumer;

public class MediaFetcher extends AsyncTask<Void, Void, ArrayList<MediaModel>> {

    private final Context context;
    private final String type;
    private final Consumer<ArrayList<MediaModel>> callback;

    public MediaFetcher(Context context, String type,
                        Consumer<ArrayList<MediaModel>> callback) {
        this.context = context.getApplicationContext();
        this.type = type;
        this.callback = callback;
    }

    @Override
    protected ArrayList<MediaModel> doInBackground(Void... voids) {

        ArrayList<MediaModel> list = new ArrayList<>();

        Uri uri = type.equals("video")
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.MediaColumns._ID
        };

        String order = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, null, null, order)) {

            if (cursor == null) return list;

            int idCol = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns._ID);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);

                Uri contentUri = ContentUris.withAppendedId(uri, id);

                list.add(new MediaModel(contentUri.toString()));
            }
        }

        return list;
    }

    @Override
    protected void onPostExecute(ArrayList<MediaModel> list) {
        callback.accept(list);
    }
}
