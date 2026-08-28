package com.github.alibehrozi.wave.microdsp.hardware.sdr.discovery;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.*;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.drivers.hackrf.*;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.alibehrozi.wave.microdsp.hardware.usb.core.UsbDeviceSpec;

import java.util.List;

/**
 * Singleton manager for simplified SDR discovery and lifecycle.
 *
 * <p>
 * This class provides a global entry point for the SDR functionality,
 * ensuring that common drivers are registered and the SdrManager is
 * properly initialized.
 * </p>
 */
public final class SdrDiscoveryManager {

    private static final String TAG = "SdrDiscoveryManager";

    /*
     * Load the native 'microdsp' library.
     */
    static {
        try {
            System.loadLibrary("microdsp");
            Log.i(TAG, "Native library 'microdsp' loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library 'microdsp'", e);
        }
    }

    private static SdrDiscoveryManager instance;

    private final SdrManager sdrManager;

    private SdrDiscoveryManager(@NonNull Context context) {
        this.sdrManager = new SdrManager(context);

        /*
         * Pre-register common SDR drivers.
         */
        sdrManager.registerDriver(new HackRfDriver());

        // TODO : add other sdr devices
        // sdrManager.registerDriver(new RtlSdrDriver());

        /*
         * Add common device specifications for auto-connect.
         */
        sdrManager.addDeviceSpec(new UsbDeviceSpec(0x1D50, 0x6089, "HackRF One"));
        sdrManager.addDeviceSpec(new UsbDeviceSpec(0x1D50, 0x604B, "HackRF Jawbreaker"));
        sdrManager.addDeviceSpec(new UsbDeviceSpec(0x1D50, 0xCC15, "rad1o"));
        sdrManager.addDeviceSpec(new UsbDeviceSpec(0x1FC9, 0x000C, "HackRF (DFU Mode)"));
        sdrManager.addDeviceSpec(new UsbDeviceSpec(0x0BDA, 0x2838, "RTL-SDR (RTL2838)"));
        sdrManager.addDeviceSpec(new UsbDeviceSpec(0x0BDA, 0x2832, "RTL-SDR (RTL2832U)"));

        Log.d(TAG, "SdrDiscoveryManager initialized");
    }

    /**
     * Initializes the SdrDiscoveryManager.
     * @param context Android context
     */
    public static synchronized void init(@NonNull Context context) {
        if (instance == null) {
            instance = new SdrDiscoveryManager(context);
        }
    }

    /**
     * Gets the singleton instance of SdrDiscoveryManager.
     * @return singleton instance
     * @throws IllegalStateException if init() has not been called
     */
    @NonNull
    public static synchronized SdrDiscoveryManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SdrDiscoveryManager not initialized. Call init(Context) first.");
        }
        return instance;
    }

    /**
     * Gets the underlying SdrManager instance.
     * @return SdrManager instance
     */
    @NonNull
    public SdrManager getSdrManager() {
        return sdrManager;
    }

    /**
     * Returns all physically connected USB devices that match the registered
     * SDR specifications.
     * @return list of available SDR devices
     */
    @NonNull
    public List<UsbDevice> getAvailableDevices() {
        return sdrManager.getAvailableUsbDevices();
    }

    /**
     * Returns all active SDR sessions.
     * @return list of active SDR sessions
     */
    @NonNull
    public List<SdrSession> getActiveSessions() {
        return sdrManager.getActiveSessions();
    }

    /**
     * Returns all connected SDR device instances.
     * @return list of connected SDR device instances
     */
    @NonNull
    public List<SdrDevice> getConnectedDevices() {
        return sdrManager.getConnectedDevices();
    }

    /**
     * Returns device information for all connected SDR devices.
     * @return list of device info objects
     */
    @NonNull
    public List<SdrDeviceInfo> getDeviceInfo() {
        return sdrManager.getDeviceInfo();
    }

    /**
     * Alias for {@link #getDeviceInfo()}, retrieving device information for all
     * connected SDR devices.
     * @return list of device info objects
     */
    @NonNull
    public List<SdrDeviceInfo> getDeviceInfos() {
        return sdrManager.getDeviceInfos();
    }

    /**
     * Gets the native handles for all currently connected SDR devices.
     * @return list of native handles
     */
    @NonNull
    public List<Long> getNativeHandle() {
        return sdrManager.getNativeHandle();
    }

    /**
     * Alias for {@link #getNativeHandle()}, retrieving native handles for all
     * connected SDR devices.
     * @return list of native handles
     */
    @NonNull
    public List<Long> getNativeHandles() {
        return sdrManager.getNativeHandles();
    }

    /**
     * Gets the SDR device assigned for reception (RX).
     * @return RX SDR device, or {@code null}
     */
    @Nullable
    public SdrDevice getRxDevice() {
        return sdrManager.getRxDevice();
    }

    /**
     * Sets the SDR device assigned for reception (RX).
     * @param sdr RX SDR device
     */
    public void setRxDevice(@Nullable SdrDevice sdr) {
        sdrManager.setRxDevice(sdr);
    }

    /**
     * Gets the SDR device assigned for transmission (TX).
     * @return TX SDR device, or {@code null}
     */
    @Nullable
    public SdrDevice getTxDevice() {
        return sdrManager.getTxDevice();
    }

    /**
     * Sets the SDR device assigned for transmission (TX).
     * @param sdr TX SDR device
     */
    public void setTxDevice(@Nullable SdrDevice sdr) {
        sdrManager.setTxDevice(sdr);
    }

    /**
     * Starts automatic SDR discovery.
     */
    public void startAutoDiscovery() {
        Log.i(TAG, "Starting automatic SDR discovery");
        sdrManager.setAutoConnectEnabled(true);
    }

    /**
     * Stops automatic SDR discovery.
     */
    public void stopAutoDiscovery() {
        Log.i(TAG, "Stopping automatic SDR discovery");
        sdrManager.setAutoConnectEnabled(false);
    }

    /**
     * Adds a listener for SDR events.
     * @param listener listener to add
     */
    public void addListener(@NonNull SdrManager.Listener listener) {
        sdrManager.addListener(listener);
    }

    /**
     * Removes a listener for SDR events.
     * @param listener listener to remove
     */
    public void removeListener(@NonNull SdrManager.Listener listener) {
        sdrManager.removeListener(listener);
    }
}
