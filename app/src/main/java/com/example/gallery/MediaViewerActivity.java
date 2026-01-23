package com.example.gallery;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.app.RemoteAction;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
public class MediaViewerActivity extends AppCompatActivity {

    private ViewPagerAdapter adapter;
    private ExoPlayer player;
    private int currentPage;
    private ArrayList<MediaModel> list;
    private ViewPager2 pager;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_media_viewer);

        list = MediaRepository.getInstance().getList();
        if (list == null || list.isEmpty()) {
            finish();
            return;
        }

        currentPage = getIntent().getIntExtra("pos", 0);

        player = new ExoPlayer.Builder(this).build();
        pager = findViewById(R.id.viewPager);

        adapter = new ViewPagerAdapter(list, player);
        pager.setAdapter(adapter);
        pager.setCurrentItem(currentPage, false);

        pager.post(() -> {
            RecyclerView rv = (RecyclerView) pager.getChildAt(0);
            adapter.attachPlayer(currentPage, rv);
        });

        pager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int pos) {
                        currentPage = pos;
                        RecyclerView rv = (RecyclerView) pager.getChildAt(0);
                        adapter.attachPlayer(pos, rv);
                    }
                }
        );

        findViewById(R.id.btnEdit).setOnClickListener(v -> edit(pager));
        findViewById(R.id.btnDelete).setOnClickListener(v -> del(pager));
    }

    @Override
    protected void onPause() {
        super.onPause();
        adapter.stop();   // ✅ stop cleanly
    }

    @Override
    protected void onResume() {
        super.onResume();
        pager.post(() -> {
            RecyclerView rv = (RecyclerView) pager.getChildAt(0);
            adapter.attachPlayer(currentPage, rv); // ✅ reattach
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }

    // edit & delete methods unchanged


    private void edit(ViewPager2 p) {
        int currentPos = p.getCurrentItem();
        String uriString = list.get(currentPos).getUri();
        Uri u = Uri.parse(uriString);

        Intent i = new Intent(Intent.ACTION_EDIT);

        // Dynamically set type based on the file content
        String mimeType = uriString.contains("video") ? "video/*" : "image/*";
        i.setDataAndType(u, mimeType);

        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(i, "Edit Media"));
        } catch (Exception e) {
            Toast.makeText(this, "No editing app found", Toast.LENGTH_SHORT).show();
        }
    }

    private static final int DELETE_REQUEST_CODE = 123;

    private void del(ViewPager2 p) {
        int pos = p.getCurrentItem();
        Uri uri = Uri.parse(list.get(pos).getUri());
        ArrayList<Uri> uriList = new ArrayList<>();
        uriList.add(uri);

        // ANDROID 11+ (API 30+) - Use Trash Request
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 'true' moves the item to Trash (Recently Deleted)
            PendingIntent pendingIntent = MediaStore.createTrashRequest(getContentResolver(), uriList, true);
            try {
                startIntentSenderForResult(
                        pendingIntent.getIntentSender(),
                        DELETE_REQUEST_CODE,
                        null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to open trash dialog", Toast.LENGTH_SHORT).show();
            }
        }
        // ANDROID 10 and BELOW - Fallback for older devices (Direct delete)
        else {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Media")
                    .setMessage("Are you sure you want to delete this permanently? Older Android versions do not support a trash folder.")
                    .setPositiveButton("Yes", (d, w) -> {
                        try {
                            int deletedRows = getContentResolver().delete(uri, null, null);
                            if (deletedRows > 0) {
                                onDeleteSuccess(pos);
                            } else {
                                Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                            }
                        } catch (SecurityException securityException) {
                            // Special case for Android 10 (API 29) RecoverableSecurityException
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && securityException instanceof RecoverableSecurityException) {
                                RecoverableSecurityException rse = (RecoverableSecurityException) securityException;
                                try {
                                    startIntentSenderForResult(
                                            rse.getUserAction().getActionIntent().getIntentSender(),
                                            DELETE_REQUEST_CODE,
                                            null, 0, 0, 0);
                                } catch (IntentSender.SendIntentException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    private void onDeleteSuccess(int pos) {
        // 1. Remove from the shared repository list immediately
        list.remove(pos);

        // 2. Update the ViewPager adapter locally for a smooth UI transition
        adapter.notifyItemRemoved(pos);
        adapter.notifyItemRangeChanged(pos, list.size());

        // 3. Prepare the data to send back to MediaListActivity
        Intent resultData = new Intent();
        resultData.putExtra("deleted_pos", pos); // Pass the index
        setResult(RESULT_OK, resultData);

        if (list.isEmpty()) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DELETE_REQUEST_CODE && resultCode == RESULT_OK) {
            // The user granted the system permission to delete the file
            onDeleteSuccess(pager.getCurrentItem());
        }
    }

}
