package com.github.alibehrozi.wave;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.discovery.SdrDiscoveryManager;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.discovery.SdrDiscoveryService;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;

/**
 * Main Application class for Wave.
 *
 * <p>Initializes the SDR discovery system and ensures the discovery service
 * is running to handle USB hotplug events.</p>
 */
public class WaveApplication extends Application {

    private static final String TAG = "WaveApplication";


    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Initialize the SDR Discovery Manager singleton.
         */
        SdrDiscoveryManager.init(this);

        /*
         * Start the SDR Discovery Service.
         * This ensures the app is ready to handle SDR devices immediately,
         * even if they were plugged in before the app started.
         */
        startSdrDiscoveryService();

        Log.i(TAG, "Wave Application initialized");
    }

    /**
     * Helper to get the SdrManager instance.
     * @return SdrManager instance
     */
    public static SdrManager getSdrManager() {
        return SdrDiscoveryManager.getInstance().getSdrManager();
    }

    private void startSdrDiscoveryService() {
        try {
            Intent serviceIntent = new Intent(this, SdrDiscoveryService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SDR Discovery Service", e);
        }
    }
}
