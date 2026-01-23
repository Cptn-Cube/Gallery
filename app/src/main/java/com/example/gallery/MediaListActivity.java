package com.example.gallery;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MediaListActivity extends AppCompatActivity {
    private Button btnDelete;
        private static final int DELETE_REQUEST_CODE = 300;

    private static final String EXTRA_IS_VIDEO = "type";
    private RecyclerView recyclerView;
    private Button btnManageAccess;
    private boolean isVideo;
    private boolean isDialogShowing = false;
    private boolean isProcessingPermission = false;
    private ActivityResultLauncher<String[]> permissionLauncher;
    MediaAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_list);

        recyclerView = findViewById(R.id.recyclerView);
        btnManageAccess = findViewById(R.id.btnManageAccess);
        btnDelete = findViewById(R.id.btnDelete);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        isVideo = "video".equals(getIntent().getStringExtra(EXTRA_IS_VIDEO));

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    isProcessingPermission = false;
                    checkAndLoad();
                }
        );
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete media")
                    .setMessage("Delete selected items?")
                    .setPositiveButton("Delete", (d, w) -> deleteSelected())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // ✅ FIX: Filter the picker to only show the relevant media type
        btnManageAccess.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 34) {
                isProcessingPermission = true;
                // By only passing the specific permission (Image OR Video),
                // the system picker filters the media selection accordingly.
                permissionLauncher.launch(new String[]{
                        isVideo ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                });
            }
        });

        PermissionUtils.Status status = PermissionUtils.check(this, isVideo);
        if (status == PermissionUtils.Status.DENIED) {
            requestPermission();
        } else {
            checkAndLoad();
        }
    }

    private void checkAndLoad() {
        PermissionUtils.Status status = PermissionUtils.check(this, isVideo);

        if (status == PermissionUtils.Status.FULL) {
            isDialogShowing = false;
            btnManageAccess.setVisibility(View.GONE);
            loadMedia();
        } else if (status == PermissionUtils.Status.LIMITED) {
            isDialogShowing = false;
            btnManageAccess.setVisibility(View.VISIBLE);
            loadMedia();
        } else {
            if (!isDialogShowing && !isProcessingPermission) {
                if (!shouldShowRationale()) {
                    showPermissionDialog();
                } else {
                    requestPermission();
                }
            }
        }
    }
    private void deleteSelected() {
        ArrayList<Uri> uris = adapter.getSelectedUris();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PendingIntent pi =
                    MediaStore.createTrashRequest(getContentResolver(), uris, true);
            try {
                startIntentSenderForResult(
                        pi.getIntentSender(),
                        DELETE_REQUEST_CODE,
                        null, 0, 0, 0
                );
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            for (Uri u : uris) {
                getContentResolver().delete(u, null, null);
            }
            adapter.removeSelected();
            btnDelete.setVisibility(View.GONE);
        }
    }

    private void requestPermission() {
        if (isProcessingPermission) return;
        isProcessingPermission = true;

        if (Build.VERSION.SDK_INT >= 34) {
            // Request both + Visual so that "Allow All" works for both types globally
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            });
        } else if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == DELETE_REQUEST_CODE && res == RESULT_OK) {
            adapter.removeSelected();
            btnDelete.setVisibility(View.GONE);
        }
    }

    private boolean shouldShowRationale() {
        if (Build.VERSION.SDK_INT >= 33) {
            return shouldShowRequestPermissionRationale(isVideo ?
                    Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_MEDIA_IMAGES);
        }
        return shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void showPermissionDialog() {
        isDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Gallery access is required to show media.")
                .setCancelable(false)
                .setPositiveButton("Settings", (d, w) -> {
                    isDialogShowing = false;
                    openAppSettings();
                })
                .setNegativeButton("Exit", (d, w) -> {
                    isDialogShowing = false;
                    finish();
                })
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndLoad();
    }

    private void loadMedia() {
        new MediaFetcher(this, isVideo ? "video" : "image", list -> {

            // 🔥🔥🔥 THIS IS THE FIX 🔥🔥🔥
            MediaRepository.getInstance().setList(list);

            adapter = new MediaAdapter(list, count -> {
                btnDelete.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            });

            recyclerView.setAdapter(adapter);
        }).execute();
    }


    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isSelectionMode()) {
            adapter.clearSelection();
            btnDelete.setVisibility(View.GONE);
        } else {
            super.onBackPressed(); // closes activity
        }
    }

}