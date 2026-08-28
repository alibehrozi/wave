package com.github.alibehrozi.wave.microdsp.hardware.usb.core;

import androidx.annotation.Nullable;

/**
 * Represents a USB device specification (vendor/product ID pair).
 * Used for device identification and filtering.
 */
public class UsbDeviceSpec {

    private final int vendorId;
    private final int productId;
    private final String name;

    /**
     * Creates a device specification.
     * @param vendorId  USB vendor ID
     * @param productId USB product ID
     */
    public UsbDeviceSpec(int vendorId, int productId) {
        this(vendorId, productId, null);
    }

    /**
     * Creates a device specification with a name.
     * @param vendorId  USB vendor ID
     * @param productId USB product ID
     * @param name      Human-readable device name
     */
    public UsbDeviceSpec(int vendorId, int productId, @Nullable String name) {
        this.vendorId = vendorId;
        this.productId = productId;
        this.name = name;
    }

    public int getVendorId() {
        return vendorId;
    }

    public int getProductId() {
        return productId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Checks if this spec matches a device.
     */
    public boolean matches(int vendorId, int productId) {
        return this.vendorId == vendorId && this.productId == productId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UsbDeviceSpec)) return false;
        UsbDeviceSpec other = (UsbDeviceSpec) obj;
        return vendorId == other.vendorId && productId == other.productId;
    }

    @Override
    public int hashCode() {
        return 31 * vendorId + productId;
    }

    @Override
    public String toString() {
        return name != null ? name :
                "VID:0x" + Integer.toHexString(vendorId) +
                " PID:0x" + Integer.toHexString(productId);
    }
}