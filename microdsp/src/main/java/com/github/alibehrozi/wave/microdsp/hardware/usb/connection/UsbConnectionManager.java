package com.github.alibehrozi.wave.microdsp.hardware.usb.connection;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.alibehrozi.wave.microdsp.hardware.usb.core.UsbDeviceSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Manages Android USB device connections.
 *
 * <p>This class is responsible only for USB-level operations such as:
 * device discovery, permission checks, opening and closing USB
 * connections.</p>
 */
public final class UsbConnectionManager {

    private static final String TAG = "UsbConnectionManager";

    private final Context context;
    private final UsbManager usbManager;

    /**
     * Creates a USB connection manager.
     * @param context Android context
     */
    public UsbConnectionManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);

        if (this.usbManager == null) {
            throw new IllegalStateException("USB service is not available on this device");
        }
    }

    /**
     * Checks whether the device supports USB host mode.
     * @return {@code true} if USB host mode is supported
     */
    public boolean isUsbHostSupported() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    /**
     * Returns the currently connected USB devices.
     *
     * <p>The returned map is a snapshot of the devices available at the
     * time this method is called.</p>
     * @return map of connected USB devices
     */
    @NonNull
    public Map<String, UsbDevice> getDeviceList() {

        if (!isUsbHostSupported()) {
            return Collections.emptyMap();
        }

        return usbManager.getDeviceList();
    }

    /**
     * Returns all currently connected USB devices.
     * @return list of connected USB devices
     */
    @NonNull
    public List<UsbDevice> getConnectedDevices() {

        Map<String, UsbDevice> devices = getDeviceList();

        if (devices.isEmpty()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(devices.values());
    }

    /**
     * Finds a USB device by its device name.
     * @param deviceName USB device name
     * @return matching USB device, or {@code null} if not found
     */
    @Nullable
    public UsbDevice findDeviceByName(@NonNull String deviceName) {
        if (deviceName.isEmpty()) {
            return null;
        }
        return getDeviceList().get(deviceName);
    }

    /**
     * Checks whether a USB device is currently available.
     * @param device USB device
     * @return {@code true} if the device is currently connected
     */
    public boolean isDeviceAvailable(@NonNull UsbDevice device) {
        if (!isUsbHostSupported()) {
            return false;
        }
        UsbDevice currentDevice = usbManager.getDeviceList().get(device.getDeviceName());
        return currentDevice != null;
    }

    /**
     * Checks whether the application has permission to access
     * the specified USB device.
     *
     * <p>This method only checks the current permission state.
     * It does not request permission.</p>
     * @param device USB device
     * @return {@code true} if permission has been granted
     */
    public boolean hasPermission(@NonNull UsbDevice device) {
        if (!isUsbHostSupported()) {
            return false;
        }
        return usbManager.hasPermission(device);
    }

    /**
     * Finds all connected USB devices matching the specified
     * vendor and product IDs.
     * @param spec USB device specification
     * @return list of matching USB devices
     */
    @NonNull
    public List<UsbDevice> findDevices(@NonNull UsbDeviceSpec spec) {
        List<UsbDevice> matchedDevices = new ArrayList<>();
        for (UsbDevice device : getConnectedDevices()) {
            if (spec.matches(device.getVendorId(), device.getProductId())) {
                matchedDevices.add(device);
            }
        }
        return matchedDevices;
    }

    /**
     * Finds the first connected USB device matching the specified
     * vendor and product IDs.
     * @param spec USB device specification
     * @return first matching USB device, or {@code null} if not found
     */
    @Nullable
    public UsbDevice findFirstDevice(@NonNull UsbDeviceSpec spec) {
        for (UsbDevice device : getConnectedDevices()) {
            if (spec.matches(device.getVendorId(), device.getProductId())) {
                return device;
            }
        }
        return null;
    }

    /**
     * Finds all connected USB devices matching a vendor ID and
     * one of the specified product IDs.
     * @param vendorId   USB vendor ID
     * @param productIds USB product IDs
     * @return list of matching USB devices
     */
    @NonNull
    public List<UsbDevice> findDevices(int vendorId, @NonNull int... productIds) {
        if (productIds.length == 0) {
            return Collections.emptyList();
        }

        List<UsbDevice> matchedDevices = new ArrayList<>();
        for (UsbDevice device : getConnectedDevices()) {
            if (device.getVendorId() != vendorId) {
                continue;
            }

            for (int productId : productIds) {
                if (device.getProductId() == productId) {
                    matchedDevices.add(device);
                    break;
                }
            }
        }
        return matchedDevices;
    }

    /**
     * Finds the first connected USB device matching a vendor ID and
     * one of the specified product IDs.
     * @param vendorId   USB vendor ID
     * @param productIds USB product IDs
     * @return first matching USB device, or {@code null} if not found
     */
    @Nullable
    public UsbDevice findFirstDevice(int vendorId, @NonNull int... productIds) {
        if (productIds.length == 0) {
            return null;
        }

        for (UsbDevice device : getConnectedDevices()) {
            if (device.getVendorId() != vendorId) {
                continue;
            }

            for (int productId : productIds) {
                if (device.getProductId() == productId) {
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * Opens a USB connection to the specified device.
     *
     * <p>The caller must already have USB permission for the device.
     * This method does not request permission because permission requests
     * are asynchronous and should be handled by the USB permission layer.</p>
     *
     * <p>The device must also still be connected when this method is called.</p>
     *
     * <p>The returned {@link UsbDeviceConnection} is owned by the caller.
     * The caller is responsible for closing it when it is no longer needed.</p>
     * @param device USB device to open
     * @return opened USB connection, or {@code null} if the device cannot
     * be opened
     */
    @Nullable
    public UsbDeviceConnection open(@NonNull UsbDevice device) {
        if (!isDeviceAvailable(device)) {
            Log.w(TAG, "USB device is no longer available: " + device.getDeviceName());
            return null;
        }

        if (!hasPermission(device)) {
            Log.w(TAG, "USB permission not granted: " + device.getDeviceName());
            return null;
        }

        try {
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device: " + device.getDeviceName());
                return null;
            }

            Log.i(TAG, "USB device opened: " + device.getDeviceName());
            return connection;

        } catch (Exception e) {
            Log.e(TAG, "Error opening USB device: " + device.getDeviceName(), e);
            return null;
        }
    }

    /**
     * Finds the first connected USB device matching the specified
     * device specification and opens a connection to it.
     *
     * <p>The caller must already have USB permission for the device.</p>
     * @param spec USB device specification
     * @return opened USB connection, or {@code null} if no matching device
     * can be opened
     */
    @Nullable
    public UsbDeviceConnection open(@NonNull UsbDeviceSpec spec) {
        UsbDevice device = findFirstDevice(spec);
        if (device == null) {
            Log.w(TAG, "No matching USB device found for: " + spec);
            return null;
        }
        return open(device);
    }

    /**
     * Closes an opened USB connection.
     *
     * <p>Closing a connection releases the underlying USB resources.
     * Passing {@code null} has no effect.</p>
     * @param connection USB connection to close
     */
    public void close(@Nullable UsbDeviceConnection connection) {
        if (connection == null) return;
        try {
            connection.close();
            Log.i(TAG, "USB device connection closed");
        } catch (Exception e) {
            Log.e(TAG, "Error closing USB device connection", e);
        }
    }

    /**
     * Returns the Android USB manager.
     *
     * <p>This should generally only be used by the USB implementation
     * itself. Higher-level components should use the methods provided
     * by this class.</p>
     * @return Android USB manager
     */
    @NonNull
    public UsbManager getUsbManager() {
        return usbManager;
    }
}