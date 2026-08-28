package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;

/**
 * Device-specific implementation used by {@link SdrManager} to identify,
 * connect, and create {@link SdrDevice} instances for a specific SDR family.
 *
 * <p>Each supported SDR family should provide one driver implementation.
 * For example:</p>
 *
 * <ul>
 *     <li>{@code HackRfDriver}</li>
 *     <li>{@code RtlSdrDriver}</li>
 *     <li>{@code PlutoSdrDriver}</li>
 * </ul>
 *
 * <p>The driver is responsible for device-specific initialization.
 * Generic USB discovery, permission handling, and connection lifecycle
 * remain the responsibility of {@link SdrManager} and the USB layer.</p>
 */
public interface SdrDriver {

    /**
     * Determines whether this driver supports the specified USB device.
     *
     * <p>This method should only inspect the USB device identity and should
     * not open the device or request permissions.</p>
     * @param device USB device to check
     * @return {@code true} if this driver can handle the device
     */
    boolean supports(@NonNull UsbDevice device);

    /**
     * Creates and initializes an SDR instance using an already opened
     * USB connection.
     *
     * <p>The USB permission must already have been granted and the
     * connection must be valid before this method is called.</p>
     * @param device     USB device
     * @param connection opened USB connection
     * @return initialized SDR instance, or {@code null} if initialization fails
     */
    @Nullable
    SdrDevice create(
            @NonNull UsbDevice device,
            @NonNull UsbDeviceConnection connection
    );
}