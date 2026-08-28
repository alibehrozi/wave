package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

import androidx.annotation.NonNull;

/**
 * Defines the logical role and duplex capabilities of an SDR device.
 */
public enum SdrRole {

    /**
     * No specific role assigned or hardware capability unassigned.
     */
    UNASSIGNED,

    /**
     * Device dedicated or capable of receiving RF signals only (e.g. RTL-SDR).
     */
    RX_ONLY,

    /**
     * Device dedicated or capable of transmitting RF signals only.
     */
    TX_ONLY,

    /**
     * Device supports both RX and TX, but not simultaneously (e.g. HackRF One).
     */
    HALF_DUPLEX,

    /**
     * Device supports simultaneous RX and TX using independent RF chains (e.g. ADALM-Pluto).
     */
    FULL_DUPLEX;

    /**
     * Checks whether this role/mode supports receiving RF signals.
     * @return {@code true} if reception is supported
     */
    public boolean supportsRx() {
        return this == RX_ONLY || this == HALF_DUPLEX || this == FULL_DUPLEX;
    }

    /**
     * Checks whether this role/mode supports transmitting RF signals.
     * @return {@code true} if transmission is supported
     */
    public boolean supportsTx() {
        return this == TX_ONLY || this == HALF_DUPLEX || this == FULL_DUPLEX;
    }

    /**
     * Checks whether this role operates in full duplex (simultaneous RX and TX).
     * @return {@code true} if full duplex
     */
    public boolean isFullDuplex() {
        return this == FULL_DUPLEX;
    }

    /**
     * Checks whether this role operates in half duplex (RX or TX, not simultaneously).
     * @return {@code true} if half duplex
     */
    public boolean isHalfDuplex() {
        return this == HALF_DUPLEX;
    }

    /**
     * Returns a friendly display name suitable for UI presentation.
     * @return display name
     */
    @NonNull
    public String getDisplayName() {
        switch (this) {
            case RX_ONLY:
                return "Receiver (RX Only)";
            case TX_ONLY:
                return "Transmitter (TX Only)";
            case HALF_DUPLEX:
                return "Half-Duplex Transceiver (RX/TX)";
            case FULL_DUPLEX:
                return "Full-Duplex Transceiver (Simultaneous RX/TX)";
            case UNASSIGNED:
            default:
                return "Unassigned";
        }
    }
}
