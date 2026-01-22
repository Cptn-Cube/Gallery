package com.example.gallery;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MediaListActivity extends AppCompatActivity {

    private static final String EXTRA_IS_VIDEO = "type";
    private RecyclerView recyclerView;
    private Button btnManageAccess;
    private boolean isVideo;
    private boolean isDialogShowing = false;
    private boolean isProcessingPermission = false;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_list);

        recyclerView = findViewById(R.id.recyclerView);
        btnManageAccess = findViewById(R.id.btnManageAccess);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        isVideo = "video".equals(getIntent().getStringExtra(EXTRA_IS_VIDEO));

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    isProcessingPermission = false;
                    checkAndLoad();
                }
        );

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

    // ... rest of your methods (shouldShowRationale, showPermissionDialog, loadMedia)

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
            recyclerView.setAdapter(new MediaAdapter(list, pos -> { }));
        }).execute();
    }
}