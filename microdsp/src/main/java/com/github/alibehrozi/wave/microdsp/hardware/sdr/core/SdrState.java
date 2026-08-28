package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

/**
 * Defines the possible states of an SDR device manager.
 */
public enum SdrState {

    /**
     * No SDR device is connected.
     */
    DISCONNECTED,

    /**
     * The USB layer is connecting to an SDR device.
     */
    CONNECTING,

    /**
     * The SDR driver is initializing the connected device.
     */
    INITIALIZING,

    /**
     * An SDR device is connected and ready for use.
     */
    CONNECTED,

    /**
     * The current SDR device is being disconnected.
     */
    DISCONNECTING,

    /**
     * An SDR or USB operation failed with an error.
     */
    ERROR,

    /**
     * The SDR manager has been permanently closed.
     */
    CLOSED
}
