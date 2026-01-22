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
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MediaListActivity extends AppCompatActivity {

    private String EXTRA_IS_VIDEO = "type";
    private Button btnManageAccess;
    private boolean isVideo;
    private RecyclerView rv;
    private MediaAdapter adapter;

    private boolean isDialogShowing = false;
    private boolean isProcessingPermission = false;

    private ActivityResultLauncher<String[]> permissionLauncher;

    private ActivityResultLauncher<PickVisualMediaRequest> pickerLauncher;
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_media_list);

        btnManageAccess = findViewById(R.id.btnManageAccess);
        rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new GridLayoutManager(this, 3));

        String typeExtra = getIntent().getStringExtra(EXTRA_IS_VIDEO);
        isVideo = "video".equals(typeExtra);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    isProcessingPermission = false;
                    // Force a UI refresh after user interacts with system picker/dialog
                    checkAndLoad();
                }
        );
         pickerLauncher = registerForActivityResult(
                        new ActivityResultContracts.PickMultipleVisualMedia(),
                        uris -> {
                            if (uris != null && !uris.isEmpty()) {
                                // Android automatically extends LIMITED access
                                checkAndLoad();
                            }
                        }
                );


        btnManageAccess.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 34 &&
                    PermissionUtils.check(this, isVideo) == PermissionUtils.Status.LIMITED) {

                pickerLauncher.launch(
                        new PickVisualMediaRequest.Builder()
                                .setMediaType(
                                        isVideo
                                                ? ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE
                                                : ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE
                                )
                                .build()
                );
            }
        });



        // Trigger request immediately if denied, otherwise check and load
        PermissionUtils.Status currentStatus = PermissionUtils.check(this, isVideo);
        if (currentStatus == PermissionUtils.Status.DENIED) {
            requestPermission();
        } else {
            checkAndLoad();
        }
    }

    private void checkAndLoad() {
        // Always check the LATEST status from the system
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
            // State: DENIED
            if (!isDialogShowing && !isProcessingPermission) {
                // Check if user has permanently denied (shouldShowRationale = false)
                if (!shouldShowRationale()) {
                    showCustomRationaleDialog();
                } else {
                    requestPermission();
                }
            }
        }
    }

    private boolean shouldShowRationale() {
        if (Build.VERSION.SDK_INT >= 33) {
            return shouldShowRequestPermissionRationale(isVideo ?
                    Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            return shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void showCustomRationaleDialog() {
        if (isDialogShowing) return;
        isDialogShowing = true;

        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Please provide access to media in Settings to continue using the gallery.")
                .setCancelable(false)
                .setPositiveButton("Settings", (dialog, which) -> {
                    isDialogShowing = false;
                    openAppSettings();
                })
                .setNegativeButton("Exit", (dialog, which) -> {
                    isDialogShowing = false;
                    finish();
                })
                .show();
    }

    private void requestPermission() {
        if (isProcessingPermission) return;
        isProcessingPermission = true;

        if (Build.VERSION.SDK_INT >= 33) {
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


    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This captures changes if user minimizes app, goes to settings, and comes back
        checkAndLoad();
    }

    private void loadMedia() {
        new MediaFetcher(this, isVideo ? "video" : "image", list -> {
            MediaRepository.getInstance().setList(list);
            adapter = new MediaAdapter(list, pos -> {
                // Viewer intent logic...
            });
            rv.setAdapter(adapter);
        }).execute();
    }
}