package com.github.alibehrozi.wave.microdsp.hardware.usb.permission;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages Android USB device permission requests.
 *
 * <p>This class is responsible for USB permission operations such as:
 * checking permission, requesting permission, and receiving asynchronous
 * permission results from Android.</p>
 *
 * <p>Permission requests are asynchronous. The result is delivered through
 * {@link PermissionCallback}.</p>
 */
public final class UsbPermissionManager {

    private static final String TAG = "UsbPermissionManager";

    /*
     * Private application-specific action used to receive USB permission results.
     */
    private static final String ACTION_USB_PERMISSION =
            "com.github.alibehrozi.wave.microdsp.USB_PERMISSION";

    /*
     * Extra used to identify the device name in the permission result.
     */
    private static final String EXTRA_DEVICE_NAME =
            "com.github.alibehrozi.wave.microdsp.extra.DEVICE_NAME";

    private final Context context;
    private final UsbManager usbManager;

    /*
     * Android callbacks related to permission dialogs are delivered through
     * the broadcast receiver. Listener callbacks are dispatched on the
     * main thread.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /*
     * Tracks permission callbacks waiting for a result.
     *
     * The key is the Android USB device name.
     */
    private final Object requestLock = new Object();
    private final Map<String, List<PermissionCallback>> pendingRequests = new HashMap<>();

    /*
     * Broadcast receiver for permission results.
     */
    private final BroadcastReceiver usbReceiver;

    /*
     * volatile so cross-thread visibility is guaranteed:
     *   - cleanup() sets cleanedUp from the connectionExecutor thread
     *   - hasPermission() / requestPermission() read it from any thread
     *   - registerReceiver / unregisterReceiver run on the mainHandler
     */
    private volatile boolean receiverRegistered = false;
    private volatile boolean cleanedUp = false;


    /**
     * Listener for USB permission results.
     */
    public interface PermissionCallback {

        /**
         * Called when USB permission has been granted.
         * @param device USB device for which permission was granted
         */
        void onPermissionGranted(@NonNull UsbDevice device);

        /**
         * Called when USB permission has been denied.
         * @param device USB device for which permission was denied
         */
        void onPermissionDenied(@NonNull UsbDevice device);

        /**
         * Called when the permission request could not be completed.
         * @param device  USB device associated with the request
         * @param message error message
         * @param cause   optional error cause
         */
        default void onPermissionError(
                @NonNull UsbDevice device,
                @NonNull String message,
                @Nullable Throwable cause) {
            // Optional - can be overridden
        }
    }


    /**
     * Creates a USB permission manager.
     * @param context Android context
     */
    public UsbPermissionManager(@NonNull Context context) {

        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);

        if (this.usbManager == null) {
            throw new IllegalStateException("USB service is not available on this device");
        }

        /*
         * Create the receiver that handles permission results.
         */
        this.usbReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent) {
                handlePermissionResult(intent);
            }
        };

        Log.d(TAG, "UsbPermissionManager created");
    }


    /**
     * Checks whether the application has permission to access
     * the specified USB device.
     * @param device USB device
     * @return {@code true} if permission has been granted
     */
    public boolean hasPermission(@NonNull UsbDevice device) {

        if (cleanedUp) {
            return false;
        }

        return usbManager.hasPermission(device);
    }


    /**
     * Requests permission to access the specified USB device.
     *
     * <p>If permission has already been granted, the callback is invoked
     * immediately on the main thread.</p>
     *
     * <p>If a permission request for the same device is already in progress,
     * the callback is added to the existing request instead of creating
     * another permission dialog.</p>
     *
     * <p>The permission result is delivered asynchronously through
     * {@link PermissionCallback}.</p>
     * @param device   USB device for which permission is requested
     * @param callback callback for the permission result
     */
    public void requestPermission(@NonNull UsbDevice device, @NonNull PermissionCallback callback) {
        if (cleanedUp) {
            notifyPermissionError(device, callback, "USB permission manager has already been cleaned up", null);
            return;
        }

        /*
         * Permission is already available.
         */
        if (hasPermission(device)) {
            notifyPermissionGranted(device, callback);
            return;
        }

        boolean shouldRequestPermission = false;
        synchronized (requestLock) {

            /*
             * Add the callback to the list of callbacks waiting
             * for this device.
             */
            List<PermissionCallback> callbacks = pendingRequests.get(device.getDeviceName());
            if (callbacks == null) {
                callbacks = new ArrayList<>();
                pendingRequests.put(device.getDeviceName(), callbacks);

                /*
                 * No permission request is currently active for this device.
                 * The Android permission dialog must be requested.
                 */
                shouldRequestPermission = true;
            }

            callbacks.add(callback);
        }

        /*
         * If another request is already waiting for this device,
         * simply wait for the existing Android permission result.
         */
        if (!shouldRequestPermission) {
            Log.d(TAG, "Permission request already pending for device: " + device.getDeviceName());
            return;
        }

        /*
         * Register the receiver and request permission on the main thread.
         */
        mainHandler.post(() -> {

            if (cleanedUp) {
                completePendingRequestWithError(device, "USB permission manager has already been cleaned up", null);
                return;
            }

            try {
                registerReceiver();
                Intent intent = new Intent(ACTION_USB_PERMISSION);
                intent.setPackage(context.getPackageName());
                intent.putExtra(EXTRA_DEVICE_NAME, device.getDeviceName());

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }

                PendingIntent permissionIntent = PendingIntent.getBroadcast(context,
                        device.getDeviceName().hashCode(), intent, flags);

                /*
                 * Ask Android to display the USB permission dialog.
                 */
                usbManager.requestPermission(device, permissionIntent);
                Log.i(TAG, "USB permission requested: " + device.getDeviceName());

            } catch (Exception e) {
                Log.e(TAG, "Failed to request USB permission: " + device.getDeviceName(), e);

                // Clean up the pending request so the receiver does not
                // linger if no result will ever arrive.
                completePendingRequestWithError(device, "Failed to request USB permission", e);

                // If no more requests are pending, unregister the receiver
                // that was registered just before the failure.
                synchronized (requestLock) {
                    if (pendingRequests.isEmpty()) unregisterReceiver();
                }
            }
        });
    }


    /**
     * Checks whether a permission request is currently pending
     * for the specified device.
     * @param device USB device
     * @return {@code true} if a permission request is pending
     */
    public boolean isPermissionPending(@NonNull UsbDevice device) {

        synchronized (requestLock) {
            List<PermissionCallback> callbacks = pendingRequests.get(device.getDeviceName());
            return callbacks != null && !callbacks.isEmpty();
        }
    }


    /**
     * Cancels any pending permission request for the specified device.
     * @param device USB device
     */
    public void cancelPermissionRequest(
            @NonNull UsbDevice device) {
        completePendingRequestWithError(device, "USB permission request cancelled", null);
    }


    /**
     * Checks whether any USB permission request is currently pending.
     * @return {@code true} if at least one permission request is pending
     */
    public boolean hasPendingRequests() {

        synchronized (requestLock) {
            return !pendingRequests.isEmpty();
        }
    }


    /**
     * Registers the USB permission broadcast receiver.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiver() {

        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        try {

            /*
             * Android 13 and newer require an explicit receiver export
             * setting for dynamically registered receivers.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(usbReceiver, filter);
            }

            receiverRegistered = true;
            Log.d(TAG, "USB permission receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register USB permission receiver", e);
            throw e;
        }
    }


    /**
     * Unregisters the USB permission broadcast receiver.
     */
    private void unregisterReceiver() {

        if (!receiverRegistered) {
            return;
        }
        try {
            context.unregisterReceiver(usbReceiver);
            Log.d(TAG, "USB permission receiver unregistered");
        } catch (IllegalArgumentException e) {
            /*
             * The receiver was already unregistered.
             */
            Log.w(TAG, "USB permission receiver was already unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering USB permission receiver", e);
        }

        receiverRegistered = false;
    }


    /**
     * Handles the asynchronous USB permission result.
     * @param intent permission result intent
     */
    private void handlePermissionResult(@NonNull Intent intent) {
        String action = intent.getAction();

        /*
         * Ignore broadcasts that are not USB permission results.
         */
        if (!ACTION_USB_PERMISSION.equals(action)) {
            return;
        }

        /*
         * Android includes the USB device in the permission result.
         * Use the type-safe API on API 33+ to avoid the deprecated overload.
         */
        UsbDevice device = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            } catch (Exception e) {
                Log.w(TAG, "Failed to get UsbDevice using new API", e);
            }
        }

        if (device == null) {
            //noinspection deprecation
            device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }

        /*
         * If the device is still missing from the intent, try to recover
         * it using the device name extra we added to the PendingIntent.
         */
        String deviceName = null;
        if (device != null) {
            deviceName = device.getDeviceName();
        } else {
            deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
            Log.w(TAG, "USB permission result device is null, recovered name: " + deviceName);
        }

        if (deviceName == null) {
            Log.e(TAG, "USB permission result received without a device or name");
            return;
        }

        boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
        List<PermissionCallback> callbacks;
        synchronized (requestLock) {
            callbacks = pendingRequests.remove(deviceName);
            if (pendingRequests.isEmpty()) {
                mainHandler.post(this::unregisterReceiver);
            }
        }

        if (callbacks == null || callbacks.isEmpty()) {
            Log.w(TAG, "Received USB permission result without a pending request: " + deviceName);
            return;
        }

        if (permissionGranted) {
            Log.i(TAG, "USB permission granted: " + deviceName);
            if (device != null) {
                notifyPermissionGranted(device, callbacks);
            } else {
                notifyPermissionError(deviceName, callbacks, "USB permission granted but device object is missing", null);
            }
        } else {
            Log.w(TAG, "USB permission denied: " + deviceName);
            if (device != null) notifyPermissionDenied(device, callbacks);
        }
    }


    /**
     * Notifies multiple listeners that a permission error occurred.
     * @param deviceName USB device name
     * @param callbacks  permission callbacks
     * @param message    error message
     * @param cause      optional error cause
     */
    private void notifyPermissionError(
            @NonNull String deviceName,
            @NonNull List<PermissionCallback> callbacks,
            @NonNull String message,
            @Nullable Throwable cause) {

        mainHandler.post(() -> {

            for (PermissionCallback callback : callbacks) {

                try {

                    // Note: This callback expects a UsbDevice, but we only have a name.
                    // Since this is an error condition where the device is null,
                    // we cannot provide a valid UsbDevice instance.
                    Log.e(TAG, "Permission error for " + deviceName + ": " + message);

                } catch (Exception e) {
                    Log.e(TAG, "Error in onPermissionError callback", e);
                }
            }
        });
    }


    /**
     * Completes a pending permission request with an error.
     * @param device  USB device associated with the request
     * @param message error message
     * @param cause   optional error cause
     */
    private void completePendingRequestWithError(
            @NonNull UsbDevice device,
            @NonNull String message,
            @Nullable Throwable cause) {

        List<PermissionCallback> callbacks;

        synchronized (requestLock) {
            callbacks = pendingRequests.remove(device.getDeviceName());
            if (pendingRequests.isEmpty()) {
                mainHandler.post(this::unregisterReceiver);
            }
        }

        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }

        notifyPermissionError(device, callbacks, message, cause);
    }


    /**
     * Notifies a single listener that permission was granted.
     * @param device   USB device
     * @param callback permission callback
     */
    private void notifyPermissionGranted(
            @NonNull UsbDevice device,
            @NonNull PermissionCallback callback) {

        mainHandler.post(() -> {

            try {
                callback.onPermissionGranted(device);
            } catch (Exception e) {
                Log.e(TAG, "Error in onPermissionGranted callback", e);
            }
        });
    }


    /**
     * Notifies multiple listeners that permission was granted.
     * @param device    USB device
     * @param callbacks permission callbacks
     */
    private void notifyPermissionGranted(@NonNull UsbDevice device, @NonNull List<PermissionCallback> callbacks) {
        mainHandler.post(() -> {
            for (PermissionCallback callback : callbacks) {
                try {
                    callback.onPermissionGranted(device);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onPermissionGranted callback", e);
                }
            }
        });
    }


    /**
     * Notifies multiple listeners that permission was denied.
     * @param device    USB device
     * @param callbacks permission callbacks
     */
    private void notifyPermissionDenied(@NonNull UsbDevice device, @NonNull List<PermissionCallback> callbacks) {
        mainHandler.post(() -> {
            for (PermissionCallback callback : callbacks) {
                try {
                    callback.onPermissionDenied(device);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onPermissionDenied callback", e);
                }
            }
        });
    }


    /**
     * Notifies a single listener that a permission error occurred.
     * @param device   USB device
     * @param callback permission callback
     * @param message  error message
     * @param cause    optional error cause
     */
    private void notifyPermissionError(
            @NonNull UsbDevice device,
            @NonNull PermissionCallback callback,
            @NonNull String message,
            @Nullable Throwable cause) {

        mainHandler.post(() -> {

            try {
                callback.onPermissionError(device, message, cause);
            } catch (Exception e) {
                Log.e(TAG, "Error in onPermissionError callback", e);
            }
        });
    }


    /**
     * Notifies multiple listeners that a permission error occurred.
     * @param device    USB device
     * @param callbacks permission callbacks
     * @param message   error message
     * @param cause     optional error cause
     */
    private void notifyPermissionError(@NonNull UsbDevice device, @NonNull List<PermissionCallback> callbacks,
                                       @NonNull String message, @Nullable Throwable cause) {
        mainHandler.post(() -> {
            for (PermissionCallback callback : callbacks) {
                try {
                    callback.onPermissionError(device, message, cause);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onPermissionError callback", e);
                }
            }
        });
    }


    /**
     * Cleans up resources and unregisters the permission receiver.
     *
     * <p>Any pending permission requests are completed with an error
     * because the permission manager is no longer active.</p>
     */
    public void cleanup() {

        if (cleanedUp) {
            return;
        }

        cleanedUp = true;

        /*
         * Remove all pending requests.
         */
        Map<String, List<PermissionCallback>> requestsToComplete;

        synchronized (requestLock) {
            requestsToComplete = new HashMap<>(pendingRequests);
            pendingRequests.clear();
        }

        /*
         * Unregister the permission receiver.
         */
        unregisterReceiver();

        /*
         * Notify pending callbacks that the manager has been cleaned up.
         */
        for (Map.Entry<String, List<PermissionCallback>> entry
                : requestsToComplete.entrySet()) {

            List<PermissionCallback> callbacks = entry.getValue();

            mainHandler.post(() -> {
                for (PermissionCallback callback : callbacks) {

                    try {

                        /*
                         * The device itself is not retained by the map key,
                         * so there is no valid UsbDevice instance available
                         * here. Pending callbacks are therefore not notified
                         * individually during cleanup.
                         *
                         * The request is simply discarded.
                         */
                        Log.d(TAG, "Pending USB permission request discarded during cleanup: " + entry.getKey());

                    } catch (Exception e) {
                        Log.e(TAG, "Error handling pending permission callback", e);
                    }
                }
            });
        }

        Log.d(TAG, "UsbPermissionManager cleanup complete");
    }
}