package com.github.alibehrozi.wave.microdsp.hardware.sdr.manager;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;

import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages SDR event listener registration and callback dispatching.
 *
 * <p>
 * All callbacks are safely delivered asynchronously on the Android Main thread.
 * </p>
 */
public final class SdrEventNotifier {

    private static final String TAG = "SdrEventNotifier";

    private final CopyOnWriteArrayList<SdrManager.Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Adds an SDR manager listener.
     * @param listener listener to add
     * @param isClosed atomic boolean indicating if manager is closed
     */
    public void addListener(@NonNull SdrManager.Listener listener, @NonNull AtomicBoolean isClosed) {
        if (isClosed.get()) {
            Log.w(TAG, "Cannot add listener after close");
            return;
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes an SDR manager listener.
     * @param listener listener to remove
     */
    public void removeListener(@NonNull SdrManager.Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Clears all SDR manager listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }

    /**
     * Notifies listeners that an SDR connected.
     * @param sdr      connected SDR
     * @param device   connected USB device
     * @param sdrLock  sdr lock for connection checking
     * @param isClosed atomic boolean checking if manager is closed
     */
    public void notifySdrConnected(
            @NonNull SdrDevice sdr,
            @NonNull UsbDevice device,
            @NonNull Object sdrLock,
            @NonNull AtomicBoolean isClosed) {

        for (SdrManager.Listener listener : listeners) {
            mainHandler.post(() -> {
                if (isClosed.get()) {
                    return;
                }
                synchronized (sdrLock) {
                    if (!sdr.isConnected()) {
                        return;
                    }
                }
                try {
                    listener.onSdrConnected(sdr, device);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onSdrConnected callback", e);
                }
            });
        }
    }

    /**
     * Notifies listeners that an SDR disconnected.
     * @param device     disconnected USB device
     * @param deviceInfo device info, or {@code null}
     * @param isClosed   atomic boolean checking if manager is closed
     */
    public void notifySdrDisconnected(
            @NonNull UsbDevice device,
            @Nullable SdrDeviceInfo deviceInfo,
            @NonNull AtomicBoolean isClosed) {

        for (SdrManager.Listener listener : listeners) {
            mainHandler.post(() -> {
                if (isClosed.get()) {
                    return;
                }
                try {
                    listener.onSdrDisconnected(device, deviceInfo);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onSdrDisconnected callback", e);
                }
            });
        }
    }

    /**
     * Notifies listeners that an error occurred.
     * @param errorType error type
     * @param message   error message
     * @param isClosed  atomic boolean checking if manager is closed
     */
    public void notifyError(
            @NonNull SdrErrorType errorType,
            @Nullable String message,
            @NonNull AtomicBoolean isClosed) {

        for (SdrManager.Listener listener : listeners) {
            mainHandler.post(() -> {
                if (isClosed.get()) {
                    return;
                }
                try {
                    listener.onError(errorType, message);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onError callback", e);
                }
            });
        }
    }
}
