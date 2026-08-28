package com.github.alibehrozi.wave.microdsp.hardware.sdr.drivers.hackrf;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.*;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.manager.SdrManager;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Device-specific driver used by
 * {@link SdrManager}
 * to identify and create HackRF SDR devices.
 *
 * <p>
 * This driver supports the HackRF USB device family, including:
 * HackRF One, HackRF Pro, Jawbreaker, and rad1o.
 * </p>
 *
 * <p>
 * USB vendor/product IDs are used to identify compatible HackRF USB
 * devices. The exact hardware model is identified later by
 * {@link HackRfDevice} through the native HackRF API.
 * </p>
 *
 * <p>
 * USB permission and connection management are handled outside this
 * driver by the USB connection layer.
 * </p>
 */
public final class HackRfDriver implements SdrDriver {

    private static final String TAG = "HackRfDriver";

    /**
     * HackRF USB vendor ID.
     */
    public static final int USB_VENDOR_ID = 0x1D50;

    /**
     * NXP LPC43xx DFU bootloader USB vendor ID (HackRF / rad1o in DFU mode).
     */
    public static final int USB_VENDOR_ID_DFU = 0x1FC9;

    /**
     * Jawbreaker USB product ID.
     */
    public static final int USB_PRODUCT_ID_JAWBREAKER = 0x604B;

    /**
     * HackRF One USB product ID.
     *
     * <p>
     * HackRF Pro uses the HackRF-compatible USB path and is identified
     * later by the native HackRF board ID.
     * </p>
     */
    public static final int USB_PRODUCT_ID_HACKRF_ONE = 0x6089;

    /**
     * rad1o USB product ID.
     */
    public static final int USB_PRODUCT_ID_RAD1O = 0xCC15;

    /**
     * HackRF DFU mode USB product ID (LPC43xx DFU).
     */
    public static final int USB_PRODUCT_ID_DFU = 0x000C;

    /**
     * Determines whether this driver supports the specified USB device.
     *
     * <p>
     * This method only checks the USB vendor and product IDs.
     * It does not request permission or open the device.
     * </p>
     * @param device USB device to check
     * @return {@code true} if the device is a supported HackRF-family device
     */
    @Override
    public boolean supports(@NonNull UsbDevice device) {
        int vendorId = device.getVendorId();
        int productId = device.getProductId();

        /*
         * Check standard HackRF USB vendor ID and product IDs.
         */
        if (vendorId == USB_VENDOR_ID) {
            return productId == USB_PRODUCT_ID_JAWBREAKER
                    || productId == USB_PRODUCT_ID_HACKRF_ONE
                    || productId == USB_PRODUCT_ID_RAD1O;
        }

        /*
         * Check DFU mode (NXP LPC43xx DFU bootloader).
         */
        if (vendorId == USB_VENDOR_ID_DFU) {
            return productId == USB_PRODUCT_ID_DFU;
        }

        return false;
    }

    /**
     * Creates and initializes a HackRF SDR instance using an already
     * opened USB connection.
     *
     * <p>
     * The USB permission must already have been granted and the
     * connection must be valid before this method is called.
     * </p>
     * @param device     USB HackRF-family device
     * @param connection opened USB connection
     * @return initialized HackRF SDR, or {@code null} if initialization fails
     */
    @Override
    @Nullable
    public SdrDevice create(
            @NonNull UsbDevice device,
            @NonNull UsbDeviceConnection connection) {

        /*
         * Verify that this driver supports the device.
         */
        if (!supports(device)) {
            Log.w(TAG, "Unsupported USB device passed to HackRF driver: "
                    + device.getDeviceName());
            return null;
        }

        /*
         * Create the HackRF SDR.
         *
         * The HackRfDevice implementation is responsible for:
         * - native initialization
         * - board identification
         * - board model detection
         * - device information creation
         */
        try {

            HackRfDevice sdr = HackRfDevice.create(device, connection);

            if (sdr == null) {
                Log.e(TAG, "Failed to initialize HackRF device: " + device.getDeviceName());
                return null;
            }

            Log.i(TAG, "HackRF device initialized: "
                    + sdr.getDeviceInfo().getProduct()
                    + " - "
                    + device.getDeviceName());

            return sdr;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing HackRF device: " + device.getDeviceName(), e);
            return null;
        }
    }
}