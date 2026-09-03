package com.github.alibehrozi.wave;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * Manages high-performance settings.
 * This class handles CPU wake locks, sustained performance mode, and thread priorities
 * to ensure that signal processing is not interrupted by Android's power management.
 */
public final class PerformanceManager {
    private static final String TAG = "PerformanceManager";
    private static final String WAKE_LOCK_TAG = "microdsp:performance_mode";

    private final PowerManager powerManager;
    private final String packageName;
    private PowerManager.WakeLock wakeLock;
    private boolean isPerformanceModeEnabled = false;

    private static PerformanceManager instance;

    /**
     * Get the singleton instance of PerformanceManager.
     */
    public static synchronized PerformanceManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new PerformanceManager(context.getApplicationContext());
        }
        return instance;
    }

    private PerformanceManager(Context context) {
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.packageName = context.getPackageName();
    }

    /**
     * Enables high-performance mode.
     * - Acquires a Partial Wake Lock to prevent CPU sleep.
     * - Requests Sustained Performance Mode (if activity provided).
     * - Keeps the screen on (if activity provided).
     *
     * @param activity Optional activity for window-based performance flags.
     */
    public synchronized void enablePerformanceMode(Activity activity) {
        if (isPerformanceModeEnabled) return;

        Log.i(TAG, "Enabling High-Performance Mode...");

        // 1. Acquire CPU Wake Lock (max 2 hours as safety)
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        }
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(2 * 60 * 60 * 1000L); // 2 hours
        }

        // 2. Activity-specific flags
        if (activity != null) {
            Window window = activity.getWindow();
            
            // Keep Screen On
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Sustained Performance Mode
            if (powerManager.isSustainedPerformanceModeSupported()) {
                window.setSustainedPerformanceMode(true);
                Log.d(TAG, "Sustained Performance Mode enabled");
            }
        }

        isPerformanceModeEnabled = true;
    }

    /**
     * Disables performance mode and releases resources.
     */
    public synchronized void disablePerformanceMode(Activity activity) {
        if (!isPerformanceModeEnabled) return;

        Log.i(TAG, "Disabling High-Performance Mode...");

        // 1. Release Wake Lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // 2. Clear Activity flags
        if (activity != null) {
            Window window = activity.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            window.setSustainedPerformanceMode(false);
        }

        isPerformanceModeEnabled = false;
    }

    /**
     * Helper to set the calling thread to a high priority.
     * Use this inside Block.work() or Flowgraph worker threads.
     */
    public static void setHighThreadPriority() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
    }

    /**
     * Check if the device is currently in a power-saving state that might affect DSP.
     */
    public boolean isPowerSaveMode() {
        return powerManager.isPowerSaveMode();
    }

    /**
     * Checks if the app is ignoring battery optimizations.
     * Required for stable background DSP processing.
     */
    public boolean isIgnoringBatteryOptimizations() {
        return powerManager.isIgnoringBatteryOptimizations(packageName);
    }

    /**
     * Requests the user to disable battery optimization for this app.
     */
    public void requestIgnoreBatteryOptimizations(@NonNull Context context) {
        if (isIgnoringBatteryOptimizations()) return;

        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
