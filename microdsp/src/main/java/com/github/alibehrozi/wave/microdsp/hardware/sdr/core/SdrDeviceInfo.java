package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Provides static identification, hardware capabilities, and information for an SDR device.
 *
 * <p>This class contains immutable information that describes the physical SDR device,
 * its front-end capabilities (e.g. duplex mode, RX/TX support), and its firmware.
 * Runtime state such as connection status, streaming state, frequency, sample rate,
 * and gain are provided by {@link SdrDevice} instead.</p>
 */
public final class SdrDeviceInfo {

    /**
     * Manufacturer or vendor of the SDR device.
     *
     * <p>Examples: "Great Scott Gadgets", "RTL-SDR", "Analog Devices".</p>
     */
    @NonNull
    private final String manufacturer;

    /**
     * Human-readable device model or product name.
     *
     * <p>Examples: "HackRF One", "RTL-SDR Blog V4", "ADALM-Pluto".</p>
     */
    @NonNull
    private final String product;

    /**
     * Hardware revision of the device, if available.
     */
    @Nullable
    private final String hardwareRevision;

    /**
     * Firmware version currently installed on the device, if available.
     */
    @Nullable
    private final String firmwareVersion;

    /**
     * Unique serial number assigned to the physical device, if available.
     */
    @Nullable
    private final String serialNumber;

    /**
     * USB vendor ID (VID), if the device is connected through USB.
     *
     * <p>Use -1 when not available or not applicable.</p>
     */
    private final int usbVendorId;

    /**
     * USB product ID (PID), if the device is connected through USB.
     *
     * <p>Use -1 when not available or not applicable.</p>
     */
    private final int usbProductId;

    /**
     * Role and duplex capability of the SDR.
     */
    @NonNull
    private final SdrRole role;

    /**
     * Constructs an {@link SdrDeviceInfo} instance with full capability information.
     * @param manufacturer     vendor name
     * @param product          product name
     * @param hardwareRevision hardware revision (nullable)
     * @param firmwareVersion  firmware version (nullable)
     * @param serialNumber     serial number (nullable)
     * @param usbVendorId      USB VID (-1 if not applicable)
     * @param usbProductId     USB PID (-1 if not applicable)
     * @param role             role/duplex capabilities (must not be null)
     */
    public SdrDeviceInfo(
            @NonNull String manufacturer,
            @NonNull String product,
            @Nullable String hardwareRevision,
            @Nullable String firmwareVersion,
            @Nullable String serialNumber,
            int usbVendorId,
            int usbProductId,
            @NonNull SdrRole role) {
        this.manufacturer = Objects.requireNonNull(manufacturer, "manufacturer must not be null");
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.hardwareRevision = hardwareRevision;
        this.firmwareVersion = firmwareVersion;
        this.serialNumber = serialNumber;
        this.usbVendorId = usbVendorId;
        this.usbProductId = usbProductId;
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    /**
     * Constructs an {@link SdrDeviceInfo} instance defaulting to {@link SdrRole#HALF_DUPLEX}.
     */
    public SdrDeviceInfo(
            @NonNull String manufacturer,
            @NonNull String product,
            @Nullable String hardwareRevision,
            @Nullable String firmwareVersion,
            @Nullable String serialNumber,
            int usbVendorId,
            int usbProductId) {
        this(manufacturer, product, hardwareRevision, firmwareVersion, serialNumber,
                usbVendorId, usbProductId, SdrRole.HALF_DUPLEX);
    }

    /**
     * Returns the manufacturer or vendor name.
     */
    @NonNull
    public String getManufacturer() {
        return manufacturer;
    }

    /**
     * Returns the human-readable product or model name.
     */
    @NonNull
    public String getProduct() {
        return product;
    }

    /**
     * Returns the hardware revision.
     * @return hardware revision, or null if unavailable
     */
    @Nullable
    public String getHardwareRevision() {
        return hardwareRevision;
    }

    /**
     * Returns the firmware version.
     * @return firmware version, or null if unavailable
     */
    @Nullable
    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    /**
     * Returns the unique serial number.
     * @return serial number, or null if unavailable
     */
    @Nullable
    public String getSerialNumber() {
        return serialNumber;
    }

    /**
     * Returns the USB vendor ID (VID).
     * @return USB vendor ID, or -1 if unavailable
     */
    public int getUsbVendorId() {
        return usbVendorId;
    }

    /**
     * Returns the USB product ID (PID).
     * @return USB product ID, or -1 if unavailable
     */
    public int getUsbProductId() {
        return usbProductId;
    }

    /**
     * Returns the logical role and duplex capability of this SDR device.
     * @return SDR role
     */
    @NonNull
    public SdrRole getRole() {
        return role;
    }

    /**
     * Checks if the device supports reception (RX).
     * @return {@code true} if reception is supported
     */
    public boolean supportsRx() {
        return role.supportsRx();
    }

    /**
     * Checks if the device supports transmission (TX).
     * @return {@code true} if transmission is supported
     */
    public boolean supportsTx() {
        return role.supportsTx();
    }

    /**
     * Checks if the device supports simultaneous receive and transmit (full duplex).
     * @return {@code true} if full duplex is supported
     */
    public boolean isFullDuplex() {
        return role.isFullDuplex();
    }

    /**
     * Checks if the device operates in half-duplex (RX or TX, but not simultaneously).
     * @return {@code true} if half duplex
     */
    public boolean isHalfDuplex() {
        return role.isHalfDuplex();
    }

    /**
     * Returns a human-readable representation of this device.
     */
    @Override
    public String toString() {
        return "SdrDeviceInfo{" +
                "manufacturer='" + manufacturer + '\'' +
                ", product='" + product + '\'' +
                ", hardwareRevision='" + hardwareRevision + '\'' +
                ", firmwareVersion='" + firmwareVersion + '\'' +
                ", serialNumber='" + serialNumber + '\'' +
                ", usbVendorId=" + usbVendorId +
                ", usbProductId=" + usbProductId +
                ", role=" + role +
                '}';
    }
}