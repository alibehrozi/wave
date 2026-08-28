package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

/**
 * Defines error types reported by the SDR management layer.
 */
public enum SdrErrorType {

    /**
     * No error.
     */
    NONE,

    /**
     * No registered SDR driver supports the connected USB device.
     */
    NO_DRIVER,

    /**
     * The SDR driver failed to initialize the hardware device.
     */
    DRIVER_INITIALIZATION_FAILED,

    /**
     * The USB connection layer reported an error.
     */
    USB_ERROR,

    /**
     * An operation was requested without a connected SDR device.
     */
    NOT_CONNECTED,

    /**
     * An SDR operation (e.g. tuning or streaming) failed.
     */
    OPERATION_FAILED,

    /**
     * Unknown or unclassified error.
     */
    UNKNOWN
}
