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

    public static Status check(Context context, boolean isVideo) {

        if (Build.VERSION.SDK_INT >= 34) {
            boolean images = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;

            boolean videos = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED;

            boolean limited = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED;

            if (images && videos) return Status.FULL;
            if (limited) return Status.LIMITED;
            return Status.DENIED;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            boolean granted = ContextCompat.checkSelfPermission(
                    context,
                    isVideo
                            ? Manifest.permission.READ_MEDIA_VIDEO
                            : Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;

            return granted ? Status.FULL : Status.DENIED;
        }

        boolean granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;

        return granted ? Status.FULL : Status.DENIED;
    }
}
