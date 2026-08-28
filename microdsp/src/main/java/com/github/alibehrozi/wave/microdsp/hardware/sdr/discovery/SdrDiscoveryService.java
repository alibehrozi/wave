package com.github.alibehrozi.wave.microdsp.hardware.sdr.discovery;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.github.alibehrozi.wave.microdsp.R;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDeviceInfo;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrErrorType;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;

import java.util.List;

/**
 * Foreground service that manages the SDR discovery lifecycle.
 */
public final class SdrDiscoveryService extends Service {

    private static final String TAG = "SdrDiscoveryService";
    public static final String ACTION_STOP_SERVICE = "com.github.alibehrozi.wave.microdsp.ACTION_STOP_SERVICE";
    public static final String ACTION_DISCONNECT_SDR = "com.github.alibehrozi.wave.microdsp.ACTION_DISCONNECT_SDR";

    private static final String CHANNEL_ID = "sdr_discovery_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int REQUEST_CODE_CONTENT = 100;
    private static final int REQUEST_CODE_STOP = 101;
    private static final int REQUEST_CODE_DISCONNECT = 102;

    private SdrDiscoveryManager discoveryManager;
    private NotificationManager notificationManager;
    private boolean isDeviceConnected = false;

    private final SdrManager.Listener sdrListener = new SdrManager.Listener() {
        @Override
        public void onSdrConnected(@NonNull SdrDevice sdr, @NonNull UsbDevice device) {
            String name = device.getProductName();
            if (name == null || name.trim().isEmpty()) {
                name = "Unknown Device";
            }
            isDeviceConnected = true;
            updateNotification("SDR Connected: " + name, true);
        }

        @Override
        public void onSdrDisconnected(@NonNull UsbDevice device, @Nullable SdrDeviceInfo deviceInfo) {
            isDeviceConnected = false;
            updateNotification("Waiting for device...", false);
        }

        @Override
        public void onError(@NonNull SdrErrorType errorType, @Nullable String message) {
            isDeviceConnected = false;
            updateNotification("SDR Error: " + (message != null ? message : errorType.name()), false);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        SdrDiscoveryManager.init(getApplicationContext());
        discoveryManager = SdrDiscoveryManager.getInstance();
        discoveryManager.addListener(sdrListener);

        List<SdrDevice> connected = discoveryManager.getConnectedDevices();
        String initialText = "Starting SDR Discovery...";
        if (!connected.isEmpty()) {
            isDeviceConnected = true;
            initialText = "SDR Connected";
        }

        Notification notification = createNotification(initialText, isDeviceConnected);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
        }

        discoveryManager.startAutoDiscovery();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVICE.equals(action)) {
                Log.i(TAG, "Received ACTION_STOP_SERVICE. Terminating service.");
                stopForegroundService();
                return START_NOT_STICKY;
            } else if (ACTION_DISCONNECT_SDR.equals(action)) {
                Log.i(TAG, "Received ACTION_DISCONNECT_SDR. Disconnecting SDR devices.");
                if (discoveryManager != null) {
                    discoveryManager.getSdrManager().disconnectAll();
                }
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    private void stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void updateNotification(String text, boolean deviceConnected) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        if (notificationManager != null) {
            Notification notification = createNotification(text, deviceConnected);
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification createNotification(String text, boolean deviceConnected) {
        PendingIntent contentPendingIntent = createContentPendingIntent();
        PendingIntent stopPendingIntent = createStopPendingIntent();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.sdr_app_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (deviceConnected) {
            PendingIntent disconnectPendingIntent = createDisconnectPendingIntent();
            builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.sdr_action_disconnect),
                    disconnectPendingIntent
            );
        }

        builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.sdr_action_stop),
                stopPendingIntent
        );

        return builder.build();
    }

    private PendingIntent createContentPendingIntent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            intent = new Intent();
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;

        return PendingIntent.getActivity(this, REQUEST_CODE_CONTENT, intent, flags);
    }

    private PendingIntent createStopPendingIntent() {
        Intent stopIntent = new Intent(this, SdrDiscoveryService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;

        return PendingIntent.getService(this, REQUEST_CODE_STOP, stopIntent, flags);
    }

    private PendingIntent createDisconnectPendingIntent() {
        Intent disconnectIntent = new Intent(this, SdrDiscoveryService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT_SDR);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= PendingIntent.FLAG_IMMUTABLE;

        return PendingIntent.getService(this, REQUEST_CODE_DISCONNECT, disconnectIntent, flags);
    }

    @Override
    public void onDestroy() {
        if (discoveryManager != null) {
            discoveryManager.removeListener(sdrListener);
            discoveryManager.stopAutoDiscovery();
        }
        super.onDestroy();
        Log.d(TAG, "SdrDiscoveryService destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sdr_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.sdr_channel_desc));
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
