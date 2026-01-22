package com.example.gallery;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

public class PermissionUtils {

    public enum Status {
        FULL,
        LIMITED,
        DENIED
    }

    public static Status check(Context c, boolean isVideo) {

        if (Build.VERSION.SDK_INT >= 34) {
            boolean img = ContextCompat.checkSelfPermission(
                    c, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;

            boolean vid = ContextCompat.checkSelfPermission(
                    c, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED;

            boolean limited = ContextCompat.checkSelfPermission(
                    c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED;

            if (img && vid) return Status.FULL;
            if (limited) return Status.LIMITED;
            return Status.DENIED;

        } else if (Build.VERSION.SDK_INT >= 33) {
            boolean granted = ContextCompat.checkSelfPermission(
                    c,
                    isVideo ? Manifest.permission.READ_MEDIA_VIDEO
                            : Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;

            return granted ? Status.FULL : Status.DENIED;

        } else {
            boolean granted = ContextCompat.checkSelfPermission(
                    c, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;

            return granted ? Status.FULL : Status.DENIED;
        }
    }
}
