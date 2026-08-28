package com.github.alibehrozi.wave.microdsp.hardware.sdr.drivers.hackrf;

import androidx.annotation.NonNull;

/**
 * Java wrapper for the native HackRFInfo C++ class.
 * Provides detailed device information such as board ID, revision, firmware version,
 * part ID, platform compatibility, Opera Cake status, and more.
 *
 * <p>This class is used after a native HackRF handle has been successfully created.
 * It builds on the same native device pointer and does not own the underlying device handle;
 * the caller ({@link HackRfDevice}) is responsible for the lifetime of the native handle.</p>
 *
 * <p>All getters throw {@link IllegalStateException} if the object has been closed.</p>
 */
public final class HackRFInfo implements AutoCloseable {

    // Pointer to the native C++ HackRFInfo object (not the device handle)
    private long nativePtr;

    /**
     * Constructs a new HackRFInfo instance from an existing native device handle.
     *
     * <p>The native handle must be valid and was obtained from
     * {@link HackRfNative#create(int)}. This constructor creates a separate
     * native C++ wrapper that reads device information; it does not take ownership
     * of the device handle.</p>
     * @param nativeHandle valid native HackRF device handle (non-zero)
     * @throws IllegalArgumentException if the handle is zero
     * @throws RuntimeException         if native object creation fails
     */
    public HackRFInfo(long nativeHandle) {
        if (nativeHandle == 0) {
            throw new IllegalArgumentException("Native handle must not be zero");
        }
        nativePtr = nativeCreateHackRFInfo(nativeHandle);
        if (nativePtr == 0) {
            throw new RuntimeException("Failed to create native HackRFInfo object");
        }
    }

    /**
     * Gets the board ID.
     * @return The board ID as a byte (unsigned).
     * @throws IllegalStateException If the object has been closed.
     */
    public byte getBoardID() {
        checkNotClosed();
        return getBoardID(nativePtr);
    }

    /**
     * Gets the board ID name (e.g., "HackRF One").
     * @return The board ID name string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getBoardIDName() {
        checkNotClosed();
        return getBoardIDName(nativePtr);
    }

    /**
     * Gets the board revision.
     * @return The board revision as a byte (unsigned).
     * @throws IllegalStateException If the object has been closed.
     */
    public byte getBoardRevision() {
        checkNotClosed();
        return getBoardRevision(nativePtr);
    }

    /**
     * Gets the board revision name (e.g., "Rev 9").
     * @return The board revision name string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getBoardRevisionName() {
        checkNotClosed();
        return getBoardRevisionName(nativePtr);
    }

    /**
     * Gets the board manufacturer information.
     * @return The manufacturer information string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getBoardManufacturerInfo() {
        checkNotClosed();
        return getBoardManufacturerInfo(nativePtr);
    }

    /**
     * Gets the version string of the HackRF library.
     * @return The version string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getVersionString() {
        checkNotClosed();
        return getVersionString(nativePtr);
    }

    /**
     * Gets the USB API version.
     * @return The USB API version as short (unsigned).
     * @throws IllegalStateException If the object has been closed.
     */
    public short getUSBAPIVersion() {
        checkNotClosed();
        return getUSBAPIVersion(nativePtr);
    }

    /**
     * Gets the firmware version currently running on the device.
     * @return The firmware version string (e.g., "2021.03.1").
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getFirmwareVersion() {
        checkNotClosed();
        return getFirmwareVersion(nativePtr);
    }

    /**
     * Gets the part ID at the specified index (0 or 1).
     * @param index The index of the part ID (typically 0 or 1).
     * @return The part ID as int (unsigned).
     * @throws IllegalStateException If the object has been closed.
     */
    public int getPartID(int index) {
        checkNotClosed();
        return getPartID(nativePtr, index);
    }

    /**
     * Gets the concatenated part ID string, which is unique per device.
     * @return The part ID string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getPartIDString() {
        checkNotClosed();
        return getPartIDString(nativePtr);
    }

    /**
     * Gets the supported platform bitmask.
     * @return The supported platform as int (unsigned).
     * @throws IllegalStateException If the object has been closed.
     */
    public int getSupportedPlatform() {
        checkNotClosed();
        return getSupportedPlatform(nativePtr);
    }

    /**
     * Gets the names of supported platforms.
     * @return Array of supported platform names.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String[] getSupportedPlatformNames() {
        checkNotClosed();
        return getSupportedPlatformNames(nativePtr);
    }

    /**
     * Gets the platform compatibility information.
     * @return The platform compatibility string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getPlatformCompatibilityInfo() {
        checkNotClosed();
        return getPlatformCompatibilityInfo(nativePtr);
    }

    /**
     * Gets the Opera Cake boards present (if any).
     * @return Byte array of Opera Cake boards.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public byte[] getOperaCakeBoards() {
        checkNotClosed();
        return getOperaCakeBoards(nativePtr);
    }

    /**
     * Gets the Opera Cake information string.
     * @return The Opera Cake information.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getOperaCakeInfo() {
        checkNotClosed();
        return getOperaCakeInfo(nativePtr);
    }

    /**
     * Gets the CPLD checksum if supported.
     * @return The CPLD checksum as int (unsigned).
     * @throws IllegalStateException If the object has been closed or not supported.
     */
    public int getCPLDChecksum() {
        checkNotClosed();
        return getCPLDChecksum(nativePtr);
    }

    /**
     * Gets the complete device information dump (a multi-line string).
     * @return The device info string.
     * @throws IllegalStateException If the object has been closed.
     */
    @NonNull
    public String getDeviceInfo() {
        checkNotClosed();
        return getDeviceInfo(nativePtr);
    }

    /**
     * Checks if the device is valid.
     * @return True if the device is valid, false otherwise.
     * @throws IllegalStateException If the object has been closed.
     */
    public boolean isDeviceValid() {
        checkNotClosed();
        return isDeviceValid(nativePtr);
    }

    /**
     * Closes this resource, releasing the native HackRFInfo object.
     *
     * <p>This does not close the underlying device handle; it only frees
     * the info wrapper. After closing, all getters will throw an exception.</p>
     */
    @Override
    public void close() {
        if (nativePtr != 0) {
            nativeDestroyHackRFInfo(nativePtr);
            nativePtr = 0;
        }
    }

    private void checkNotClosed() {
        if (nativePtr == 0) {
            throw new IllegalStateException("HackRFInfo object is closed");
        }
    }

    /**
     * Creates a native HackRFInfo object from the given device handle.
     * @param devicePtr native device handle (HackRFDevice*)
     * @return pointer to a new HackRFInfo object, or 0 on failure
     */
    private static native long nativeCreateHackRFInfo(long devicePtr);
    private static native byte getBoardID(long nativePtr);
    private static native String getBoardIDName(long nativePtr);
    private static native byte getBoardRevision(long nativePtr);
    private static native String getBoardRevisionName(long nativePtr);
    private static native String getBoardManufacturerInfo(long nativePtr);
    private static native String getVersionString(long nativePtr);
    private static native short getUSBAPIVersion(long nativePtr);
    private static native String getFirmwareVersion(long nativePtr);
    private static native int getPartID(long nativePtr, int index);
    private static native String getPartIDString(long nativePtr);
    private static native int getSupportedPlatform(long nativePtr);
    private static native String[] getSupportedPlatformNames(long nativePtr);
    private static native String getPlatformCompatibilityInfo(long nativePtr);
    private static native byte[] getOperaCakeBoards(long nativePtr);
    private static native String getOperaCakeInfo(long nativePtr);
    private static native int getCPLDChecksum(long nativePtr);
    private static native String getDeviceInfo(long nativePtr);
    private static native boolean isDeviceValid(long nativePtr);
    private static native void nativeDestroyHackRFInfo(long nativePtr);
}