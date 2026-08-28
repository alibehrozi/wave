package com.github.alibehrozi.wave.microdsp.hardware.usb.connection;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.github.alibehrozi.wave.microdsp.hardware.usb.core.UsbDeviceFilter;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages USB hotplug events.
 *
 * <p>This class is responsible only for monitoring USB device attachment
 * and detachment events, filtering devices, and notifying listeners.</p>
 */
public final class UsbHotplugManager {

    private static final String TAG = "UsbHotplugManager";

    private static final int HANDLER_THREAD_PRIORITY =
            android.os.Process.THREAD_PRIORITY_BACKGROUND;

    private static final long EVENT_DEBOUNCE_MS = 100L;

    private final Context context;

    private final HandlerThread handlerThread;
    private final Handler backgroundHandler;

    private final AtomicBoolean isRunning =
            new AtomicBoolean(false);

    private final AtomicBoolean isReceiverRegistered =
            new AtomicBoolean(false);

    private final AtomicBoolean isCleanedUp =
            new AtomicBoolean(false);

    /*
     * Protects updates to the debounce timestamps.
     */
    private final Object debounceLock = new Object();

    /*
     * Tracks the last event time for each device and event type.
     */
    private final Map<String, Long> lastEventTimes = new ConcurrentHashMap<>();

    /*
     * Device filter.
     */
    private volatile UsbDeviceFilter deviceFilter =
            UsbDeviceFilter.all();

    /*
     * Hotplug listeners.
     */
    private final CopyOnWriteArrayList<HotplugListener> listeners =
            new CopyOnWriteArrayList<>();

    /*
     * USB broadcast receiver.
     */
    private final BroadcastReceiver usbReceiver;


    /**
     * Listener for USB hotplug events.
     */
    public interface HotplugListener {

        /**
         * Called when a matching USB device is attached.
         * @param device The attached USB device
         */
        void onDeviceAttached(@NonNull UsbDevice device);

        /**
         * Called when a matching USB device is detached.
         * @param device The detached USB device
         */
        void onDeviceDetached(@NonNull UsbDevice device);
    }


    /**
     * Creates a USB hotplug manager.
     * @param context Android application context
     */
    public UsbHotplugManager(@NonNull Context context) {
        this.context = context.getApplicationContext();

        /*
         * Create a dedicated background thread for USB hotplug processing.
         */
        this.handlerThread = new HandlerThread("UsbHotplugManager", HANDLER_THREAD_PRIORITY);
        this.handlerThread.start();
        this.backgroundHandler = new Handler(handlerThread.getLooper());

        /*
         * Create the USB broadcast receiver.
         */
        this.usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleUsbIntent(intent);
            }
        };

        Log.d(TAG, "UsbHotplugManager created");
    }


    /**
     * Sets the device filter for hotplug events.
     *
     * <p>Only devices matching this filter will trigger
     * attachment and detachment callbacks.</p>
     * @param filter device filter, or {@code null} to accept all devices
     */
    public void setDeviceFilter(@Nullable UsbDeviceFilter filter) {
        this.deviceFilter = filter != null ? filter : UsbDeviceFilter.all();
        Log.d(TAG, "Device filter updated: " + this.deviceFilter);
    }


    /**
     * Gets the current device filter.
     * @return current device filter
     */
    @NonNull
    public UsbDeviceFilter getDeviceFilter() {
        return deviceFilter;
    }


    /**
     * Adds a hotplug listener.
     * @param listener listener to add
     */
    public void addListener(@NonNull HotplugListener listener) {
        if (isCleanedUp.get()) {
            Log.w(TAG, "Cannot add listener after cleanup");
            return;
        }

        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "Listener added, total: " + listeners.size());
        }
    }


    /**
     * Removes a hotplug listener.
     * @param listener listener to remove
     */
    public void removeListener(@NonNull HotplugListener listener) {
        listeners.remove(listener);
        Log.d(TAG, "Listener removed, total: " + listeners.size());
    }


    /**
     * Clears all hotplug listeners.
     */
    public void clearListeners() {
        listeners.clear();
        Log.d(TAG, "All listeners cleared");
    }


    /**
     * Checks whether any hotplug listeners are registered.
     * @return {@code true} if at least one listener is registered
     */
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }


    /**
     * Starts monitoring USB hotplug events.
     *
     * <p>If monitoring is already running, this method does nothing.</p>
     */
    public void start() {

        /*
         * Do not start after cleanup.
         */
        if (isCleanedUp.get()) {
            Log.w(TAG, "Cannot start after cleanup");
            return;
        }

        /*
         * Prevent multiple start calls from registering the
         * receiver more than once.
         */
        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Hotplug monitoring already running");
            return;
        }

        /*
         * Register the receiver on the background thread.
         */
        backgroundHandler.post(this::registerReceiver);
    }


    /**
     * Stops monitoring USB hotplug events.
     *
     * <p>If monitoring is not running, this method does nothing.</p>
     */
    public void stop() {

        /*
         * Change the running state immediately so that queued events
         * are ignored as soon as stop is requested.
         */
        if (!isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Hotplug monitoring is not running");
            return;
        }

        /*
         * Unregister the receiver on the same background thread
         * used for hotplug processing.
         */
        backgroundHandler.post(this::unregisterReceiver);
    }


    /**
     * Checks whether hotplug monitoring is currently running.
     * @return {@code true} if monitoring is enabled
     */
    public boolean isRunning() {
        return isRunning.get();
    }


    /**
     * Registers the USB broadcast receiver.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiver() {

        /*
         * The manager may have been stopped or cleaned up while
         * registration was waiting in the background queue.
         */
        if (!isRunning.get() || isCleanedUp.get()) {
            return;
        }

        /*
         * Prevent duplicate receiver registration.
         */
        if (isReceiverRegistered.get()) {
            Log.d(TAG, "USB receiver already registered");
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        try {

            /*
             * Register the receiver with the background handler.
             *
             * On Android 13 and newer, explicitly mark the receiver
             * as not exported because it is only used internally
             * by this application.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                context.registerReceiver(
                        usbReceiver,
                        filter,
                        null,
                        backgroundHandler,
                        Context.RECEIVER_NOT_EXPORTED
                );

            } else {
                context.registerReceiver(usbReceiver, filter, null, backgroundHandler);
            }
            isReceiverRegistered.set(true);
            Log.i(TAG, "USB hotplug monitoring started");
        } catch (Exception e) {
            isReceiverRegistered.set(false);
            isRunning.set(false);
            Log.e(TAG, "Failed to register USB receiver", e);
        }
    }


    /**
     * Unregisters the USB broadcast receiver.
     */
    private void unregisterReceiver() {
        if (!isReceiverRegistered.compareAndSet(true, false)) return;
        try {
            context.unregisterReceiver(usbReceiver);
            Log.i(TAG, "USB hotplug monitoring stopped");
        } catch (IllegalArgumentException e) {

            /*
             * The receiver was already unregistered.
             */
            Log.w(TAG, "USB receiver was already unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering USB receiver", e);
        }
    }


    /**
     * Handles an incoming USB broadcast.
     *
     * <p>The receiver is registered with {@link #backgroundHandler},
     * so the event is already delivered on the manager's background
     * thread.</p>
     * @param intent USB broadcast intent
     */
    private void handleUsbIntent(
            @NonNull Intent intent) {

        /*
         * Ignore events after monitoring has been stopped.
         */
        if (!isRunning.get() || isCleanedUp.get()) {
            return;
        }

        String action = intent.getAction();

        /*
         * Only process USB attach and detach events.
         */
        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                && !UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            return;
        }

        UsbDevice device = getUsbDevice(intent);
        if (device == null) {
            Log.w(TAG, "Received USB hotplug event without a device");
            return;
        }

        processUsbEvent(action, device);
    }


    /**
     * Gets the USB device from a broadcast intent.
     * @param intent USB broadcast intent
     * @return USB device, or {@code null} if unavailable
     */
    @SuppressWarnings("deprecation")
    @Nullable
    private UsbDevice getUsbDevice(@NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        } else {
            return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }
    }


    /**
     * Processes a USB hotplug event.
     * @param action USB attach or detach action
     * @param device USB device associated with the event
     */
    private void processUsbEvent(@Nullable String action, @NonNull UsbDevice device) {
        UsbDeviceFilter currentFilter = deviceFilter;
        if (!currentFilter.matches(device)) {
            Log.v(TAG, "USB device filtered out: " + getDeviceInfo(device));
            return;
        }

        if (isEventDebounced(action, device)) {
            Log.v(TAG, "USB hotplug event debounced: " + getDeviceInfo(device));
            return;
        }

        try {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.i(TAG, "USB device attached: " + getDeviceInfo(device));
                notifyDeviceAttached(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.i(TAG, "USB device detached: " + getDeviceInfo(device));
                notifyDeviceDetached(device);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing USB hotplug event", e);
        }
    }

    /**
     * Checks whether a hotplug event should be ignored because
     * an identical event was received too recently.
     *
     * <p>Debouncing is tracked independently for each device and
     * event type, so an event from one USB device cannot suppress
     * an event from another device.</p>
     * @param action USB attach or detach action
     * @param device USB device
     * @return {@code true} if the event should be ignored
     */
    private boolean isEventDebounced(@Nullable String action, @NonNull UsbDevice device) {
        if (action == null) {
            return false;
        }

        String eventKey = action + ":" + device.getDeviceName();
        long now = System.currentTimeMillis();

        synchronized (debounceLock) {
            Long previousEventTime = lastEventTimes.get(eventKey);
            if (previousEventTime != null && now - previousEventTime < EVENT_DEBOUNCE_MS) {
                return true;
            }
            lastEventTimes.put(eventKey, now);
            return false;
        }
    }

    /**
     * Notifies all listeners that a USB device was attached.
     * @param device attached USB device
     */
    private void notifyDeviceAttached(
            @NonNull UsbDevice device) {
        for (HotplugListener listener : listeners) {
            try {
                listener.onDeviceAttached(device);
            } catch (Exception e) {
                Log.e(TAG, "Error in onDeviceAttached callback", e);
            }
        }
    }

    /**
     * Notifies all listeners that a USB device was detached.
     * @param device detached USB device
     */
    private void notifyDeviceDetached(
            @NonNull UsbDevice device) {
        for (HotplugListener listener : listeners) {
            try {
                listener.onDeviceDetached(device);
            } catch (Exception e) {
                Log.e(TAG, "Error in onDeviceDetached callback", e);
            }
        }
    }


    /**
     * Gets device information for logging.
     * @param device USB device
     * @return formatted device information
     */
    @NonNull
    private String getDeviceInfo(@NonNull UsbDevice device) {

        return String.format(
                Locale.US,
                "%s (VID=0x%04X, PID=0x%04X, Class=%d, SubClass=%d)",
                device.getDeviceName(),
                device.getVendorId(),
                device.getProductId(),
                device.getDeviceClass(),
                device.getDeviceSubclass()
        );
    }


    /**
     * Cleans up resources and stops monitoring.
     *
     * <p>After cleanup, this manager cannot be started again.</p>
     */
    public void cleanup() {

        /*
         * Make cleanup idempotent.
         */
        if (!isCleanedUp.compareAndSet(false, true)) {
            return;
        }

        /*
         * Disable event processing immediately.
         */
        isRunning.set(false);

        /*
         * Queue cleanup on the background thread so the receiver
         * is unregistered on the same thread used by this manager.
         */
        backgroundHandler.post(() -> {
            unregisterReceiver();

            /*
             * Remove any queued debounce information.
             */
            synchronized (debounceLock) {
                lastEventTimes.clear();
            }

            /*
             * Remove all listeners.
             */
            listeners.clear();
            deviceFilter = UsbDeviceFilter.all();
            handlerThread.getLooper().quitSafely();
            Log.d(TAG, "UsbHotplugManager cleanup complete");
        });
    }
}