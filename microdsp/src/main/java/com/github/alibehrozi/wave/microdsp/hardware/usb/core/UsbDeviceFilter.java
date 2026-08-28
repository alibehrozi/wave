package com.github.alibehrozi.wave.microdsp.hardware.usb.core;

import android.hardware.usb.UsbDevice;

import androidx.annotation.NonNull;

/**
 * Filter for USB devices based on various criteria.
 * Supports combining multiple filters with AND/OR logic.
 * <p>
 * Usage examples:
 * <pre>
 * // Single device
 * UsbDeviceFilter hackrf = UsbDeviceFilter.byVendorAndProduct(0x1d50, 0x6089);
 *
 * // Multiple devices
 * UsbDeviceFilter allHackRF = UsbDeviceFilter.byVendorAndProduct(0x1d50, 0x6089, 0x604b);
 *
 * // Combined filters
 * UsbDeviceFilter filter = UsbDeviceFilter.any(
 *     UsbDeviceFilter.byVendorAndProduct(0x1d50, 0x6089),
 *     UsbDeviceFilter.byVendorAndProduct(0x0403, 0x6001)
 * );
 * </pre>
 */
@FunctionalInterface
public interface UsbDeviceFilter {

    /**
     * Checks if a USB device matches the filter.
     * @param device The USB device to check
     * @return true if the device matches, false otherwise
     */
    boolean matches(@NonNull UsbDevice device);

    /**
     * Combines this filter with another using AND logic.
     */
    @NonNull
    default UsbDeviceFilter and(@NonNull UsbDeviceFilter other) {
        return device -> matches(device) && other.matches(device);
    }

    /**
     * Combines this filter with another using OR logic.
     */
    @NonNull
    default UsbDeviceFilter or(@NonNull UsbDeviceFilter other) {
        return device -> matches(device) || other.matches(device);
    }

    /**
     * Negates this filter.
     */
    @NonNull
    default UsbDeviceFilter not() {
        return device -> !matches(device);
    }

    /**
     * Creates a filter that matches a specific vendor ID.
     */
    @NonNull
    static UsbDeviceFilter byVendor(int vendorId) {
        return device -> device.getVendorId() == vendorId;
    }

    /**
     * Creates a filter that matches a specific vendor and product ID.
     */
    @NonNull
    static UsbDeviceFilter byVendorAndProduct(int vendorId, int productId) {
        return device -> device.getVendorId() == vendorId && device.getProductId() == productId;
    }

    /**
     * Creates a filter that matches a vendor ID and any of the product IDs.
     */
    @NonNull
    static UsbDeviceFilter byVendorAndProduct(int vendorId, int... productIds) {
        return device -> {
            if (device.getVendorId() != vendorId) {
                return false;
            }
            for (int pid : productIds) {
                if (device.getProductId() == pid) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Creates a filter that matches any of the device specifications.
     */
    @NonNull
    static UsbDeviceFilter byDeviceSpecs(@NonNull UsbDeviceSpec... specs) {
        return device -> {
            for (UsbDeviceSpec spec : specs) {
                if (spec.matches(device.getVendorId(), device.getProductId())) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Creates a filter that matches a specific device class.
     */
    @NonNull
    static UsbDeviceFilter byDeviceClass(int deviceClass) {
        return device -> device.getDeviceClass() == deviceClass;
    }

    /**
     * Creates a filter that matches a specific device class and subclass.
     */
    @NonNull
    static UsbDeviceFilter byDeviceClass(int deviceClass, int subClass) {
        return device -> device.getDeviceClass() == deviceClass &&
                device.getDeviceSubclass() == subClass;
    }

    /**
     * Creates a filter that matches any of the given filters (OR logic).
     */
    @NonNull
    static UsbDeviceFilter any(@NonNull UsbDeviceFilter... filters) {
        return device -> {
            for (UsbDeviceFilter filter : filters) {
                if (filter.matches(device)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Creates a filter that matches all of the given filters (AND logic).
     */
    @NonNull
    static UsbDeviceFilter all(@NonNull UsbDeviceFilter... filters) {
        return device -> {
            for (UsbDeviceFilter filter : filters) {
                if (!filter.matches(device)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Creates a filter that matches devices with a specific manufacturer string.
     * Note: Requires reading the device descriptor.
     */
    @NonNull
    static UsbDeviceFilter byManufacturer(@NonNull String manufacturer) {
        return device -> {
            try {
                // This requires an open connection to read the descriptor
                // Implementation depends on your USB connection
                return false; // Placeholder
            } catch (Exception e) {
                return false;
            }
        };
    }

    /**
     * Always matches all devices.
     */
    @NonNull
    static UsbDeviceFilter all() {
        return device -> true;
    }

    /**
     * Never matches any device.
     */
    @NonNull
    static UsbDeviceFilter none() {
        return device -> false;
    }
}