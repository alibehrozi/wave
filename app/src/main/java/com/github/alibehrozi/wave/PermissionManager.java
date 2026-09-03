package com.github.alibehrozi.wave;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Handles application permissions such as notification and microphone permissions.
 */
public final class PermissionManager {
    private static final String TAG = "PermissionManager";
    public static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;
    public static final int REQUEST_CODE_RECORD_AUDIO = 1002;
    public static final int REQUEST_CODE_APP_PERMISSIONS = 1003;

    private static PermissionManager instance;

    /**
     * Get the singleton instance of PermissionManager.
     */
    public static synchronized PermissionManager getInstance() {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }

    private PermissionManager() {}

    /**
     * Checks if notification permission is granted.
     *
     * @param context Application or Activity context.
     * @return true if permission is granted or if running on Android < 13.
     */
    public boolean hasNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Checks if microphone (RECORD_AUDIO) permission is granted.
     *
     * @param context Application or Activity context.
     * @return true if RECORD_AUDIO permission is granted.
     */
    public boolean hasRecordAudioPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests microphone (RECORD_AUDIO) permission if not already granted.
     *
     * @param activity Calling activity to display permission prompt.
     */
    public void requestRecordAudioPermission(@NonNull Activity activity) {
        if (!hasRecordAudioPermission(activity)) {
            Log.i(TAG, "Requesting RECORD_AUDIO permission");
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_CODE_RECORD_AUDIO
            );
        }
    }

    /**
     * Requests notification permission on Android 13+ (API 33+) if not already granted.
     *
     * @param activity Calling activity to display permission prompt.
     */
    public void requestNotificationPermission(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                Log.i(TAG, "Requesting POST_NOTIFICATIONS permission");
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIFICATIONS
                );
            }
        }
    }
}
