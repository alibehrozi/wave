package com.github.alibehrozi.wave.microdsp.hardware.usb.permission;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Handles USB permission requests and management.
 */
public class UsbPermissionHelper {

    private static final String TAG = "UsbPermissionHelper";

    private final Context context;
    private final UsbManager usbManager;

    // Permission request tracking
    private PermissionCallback pendingCallback;
    private UsbDevice pendingDevice;
    private final Object permissionLock = new Object();

    // Broadcast receiver for permission results
    private BroadcastReceiver permissionReceiver;
    private boolean isReceiverRegistered = false;

    /**
     * Callback interface for USB permission requests.
     */
    public interface PermissionCallback {
        /**
         * Called when USB permission is granted.
         * @param device The USB device
         */
        void onPermissionGranted(@NonNull UsbDevice device);

        /**
         * Called when USB permission is denied.
         * @param device The USB device
         */
        void onPermissionDenied(@NonNull UsbDevice device);
    }

    /**
     * Creates a new USB permission helper.
     * @param context Application context
     */
    public UsbPermissionHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Log.d(TAG, "UsbPermissionHelper created");
    }

    /**
     * Checks if permission is already granted for a device.
     * @param device The USB device
     * @return true if permission is granted, false otherwise
     */
    public boolean hasPermission(@NonNull UsbDevice device) {
        return usbManager.hasPermission(device);
    }

    /**
     * Requests permission for a USB device.
     * @param device   The USB device
     * @param callback Callback for permission result
     */
    public void requestPermission(@NonNull UsbDevice device, @NonNull PermissionCallback callback) {

        // Check if permission already granted
        if (hasPermission(device)) {
            Log.d(TAG, "Permission already granted for device");
            callback.onPermissionGranted(device);
            return;
        }

        // Store pending request
        synchronized (permissionLock) {
            pendingCallback = callback;
            pendingDevice = device;
        }

        // Ensure receiver is registered
        if (!isReceiverRegistered) {
            registerReceiver();
        }

        // Request permission
        Intent innerIntent = new Intent(UsbPermissionReceiver.ACTION_USB_PERMISSION);
        // setting the package name of the inner intent makes it explicit
        // From Android 14 it is required that mutable PendingIntents have explicit inner intents!
        innerIntent.setPackage(context.getPackageName());
        PendingIntent mPermissionIntent =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        innerIntent,
                        PendingIntent.FLAG_MUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT
                );

        usbManager.requestPermission(device, mPermissionIntent);
        Log.d(TAG, "Permission requested for device: " + device.getDeviceName());
    }

    /**
     * Requests permission for a device and waits for the result.
     * This is a blocking version - use with caution (off the main thread).
     * @param device    The USB device
     * @param timeoutMs Timeout in milliseconds
     * @return true if permission granted, false otherwise
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean requestPermissionBlocking(@NonNull UsbDevice device, long timeoutMs)
            throws InterruptedException {
        final Object lock = new Object();
        final boolean[] result = {false};

        requestPermission(device, new PermissionCallback() {
            @Override
            public void onPermissionGranted(UsbDevice grantedDevice) {
                synchronized (lock) {
                    result[0] = true;
                    lock.notify();
                }
            }

            @Override
            public void onPermissionDenied(UsbDevice deniedDevice) {
                synchronized (lock) {
                    result[0] = false;
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            lock.wait(timeoutMs);
        }

        return result[0];
    }

    /**
     * Handles the permission result from the broadcast receiver.
     * @param device  The USB device
     * @param granted true if permission was granted
     */
    void handlePermissionResult(UsbDevice device, boolean granted) {
        PermissionCallback callback;
        synchronized (permissionLock) {
            callback = pendingCallback;
            pendingCallback = null;
            pendingDevice = null;
        }

        if (callback == null) {
            Log.w(TAG, "No pending permission callback");
            return;
        }

        if (granted) {
            Log.i(TAG, "USB permission granted for device: " + device.getDeviceName());
            callback.onPermissionGranted(device);
        } else {
            Log.w(TAG, "USB permission denied for device: " + device.getDeviceName());
            callback.onPermissionDenied(device);
        }
    }


    /**
     * Registers the broadcast receiver for permission results.
     */
    private void registerReceiver() {
        if (isReceiverRegistered) {
            Log.d(TAG, "USB broadcast receiver is already registered.");
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbPermissionReceiver.ACTION_USB_PERMISSION);

        permissionReceiver = new UsbPermissionReceiver(this);
        ContextCompat.registerReceiver(context, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        isReceiverRegistered = true;
        Log.d(TAG, "Permission receiver registered");
    }

    /**
     * Unregisters the broadcast receiver.
     */
    private void unregisterReceiver() {
        if (permissionReceiver != null && isReceiverRegistered) {
            try {
                // Attempt to unregister the receiver.
                context.unregisterReceiver(permissionReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "Permission receiver unregistered");
            } catch (IllegalArgumentException e) {
                // This catch block handles the rare case where the receiver state was 'true' but it was
                // somehow already unregistered by another part of the system, preventing a crash.
                Log.w(TAG, "Receiver already unregistered");
            }
            permissionReceiver = null;
        }
    }

    /**
     * Cleans up resources and unregisters receivers.
     * Should be called when no longer needed.
     */
    public void cleanup() {
        unregisterReceiver();
        synchronized (permissionLock) {
            pendingCallback = null;
            pendingDevice = null;
        }

        Log.d(TAG, "Cleanup complete");
    }
}