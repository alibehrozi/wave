package com.github.alibehrozi.wave.microdsp.hardware.sdr.manager;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;

import android.hardware.usb.UsbDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents an active, open SDR device session managed by the SDR subsystem.
 *
 * <p>Each session encapsulates the underlying USB device, the matching SDR driver,
 * the active SDR hardware interface instance, static device information, and the
 * assigned logical role (e.g. RX, TX, Spectrum Monitor).</p>
 */
public final class SdrSession {

    private final UsbDevice usbDevice;
    private final SdrDriver driver;
    private final SdrDevice device;
    private final SdrDeviceInfo deviceInfo;
    private volatile SdrRole role;

    /**
     * Creates a new SDR session.
     * @param usbDevice  physical USB device
     * @param driver     SDR driver that initialized the device
     * @param device     connected SDR device instance
     * @param deviceInfo device hardware capability information, or {@code null}
     * @param role       assigned logical role
     */
    public SdrSession(
            @NonNull UsbDevice usbDevice,
            @NonNull SdrDriver driver,
            @NonNull SdrDevice device,
            @Nullable SdrDeviceInfo deviceInfo,
            @NonNull SdrRole role) {

        this.usbDevice = usbDevice;
        this.driver = driver;
        this.device = device;
        this.deviceInfo = deviceInfo;
        this.role = role;
    }

    /**
     * Gets the USB device associated with this session.
     * @return USB device
     */
    @NonNull
    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    /**
     * Gets the SDR driver that initialized this session.
     * @return SDR driver
     */
    @NonNull
    public SdrDriver getDriver() {
        return driver;
    }

    /**
     * Gets the active SDR device instance.
     * @return SDR device instance
     */
    @NonNull
    public SdrDevice getDevice() {
        return device;
    }

    /**
     * Gets static device information and hardware capabilities.
     * @return device information, or {@code null}
     */
    @Nullable
    public SdrDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    /**
     * Gets the currently assigned logical role for this session.
     * @return SDR role
     */
    @NonNull
    public SdrRole getRole() {
        return role;
    }

    /**
     * Updates the assigned logical role for this session.
     * @param role new SDR role
     */
    public void setRole(@NonNull SdrRole role) {
        this.role = role;
    }

    @NonNull
    @Override
    public String toString() {
        return "SdrSession{" +
                "device=" + usbDevice.getDeviceName() +
                ", driver=" + driver.getClass().getSimpleName() +
                ", role=" + role +
                '}';
    }
}
