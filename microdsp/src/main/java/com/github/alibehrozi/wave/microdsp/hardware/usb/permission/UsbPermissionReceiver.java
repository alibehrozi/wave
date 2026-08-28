package com.github.alibehrozi.wave.microdsp.hardware.usb.permission;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.IntentCompat;

import java.lang.ref.WeakReference;

/**
 * Internal broadcast receiver for USB permission results.
 * This is used by UsbPermissionHelper and should not be used directly.
 * <p>
 * The receiver handles USB permission grant/deny events and forwards
 * them to the helper for callback execution.
 */
class UsbPermissionReceiver extends BroadcastReceiver {

    private static final String TAG = "UsbPermissionReceiver";

    /**
     * Action string for USB permission requests.
     * Using a custom action avoids conflicts with system broadcasts.
     */
    static final String ACTION_USB_PERMISSION = "com.example.usb.USB_PERMISSION";
    private final WeakReference<UsbPermissionHelper> helperRef;

    /**
     * Creates a new permission receiver.
     *
     * @param helper The permission helper to notify
     */
    UsbPermissionReceiver(@NonNull UsbPermissionHelper helper) {
        this.helperRef = new WeakReference<>(helper);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // Ignore if not our permission action
        if (!ACTION_USB_PERMISSION.equals(action)) {
            return;
        }

        // Extract device and permission result
        UsbDevice device = IntentCompat.getParcelableExtra(
                intent, UsbManager.EXTRA_DEVICE, UsbDevice.class);

        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

        if (device == null) {
            Log.w(TAG, "Permission result received without device");
            return;
        }

        Log.d(TAG, "Permission result: " + (granted ? "GRANTED" : "DENIED") +
                " for device " + device.getDeviceName());

        UsbPermissionHelper helper = helperRef.get();
        if (helper != null) {
            helper.handlePermissionResult(device, granted);
        } else {
            Log.e(TAG, "Helper was garbage collected before permission result arrived");
        }
    }
}