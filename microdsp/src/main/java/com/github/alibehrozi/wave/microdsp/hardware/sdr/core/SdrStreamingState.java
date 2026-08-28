package com.github.alibehrozi.wave.microdsp.hardware.sdr.core;

/**
 * Represents the current streaming state of an SDR device.
 *
 * <p>An SDR may operate in receive (RX), transmit (TX), or
 * simultaneous receive and transmit (RX_TX) mode.</p>
 */
public enum SdrStreamingState {

    /**
     * The SDR is not currently receiving or transmitting.
     */
    IDLE,

    /**
     * The SDR is currently receiving IQ samples.
     */
    RX,

    /**
     * The SDR is currently transmitting IQ samples.
     */
    TX,

    /**
     * The SDR is simultaneously receiving and transmitting IQ samples.
     *
     * <p>This state represents full-duplex operation and is only
     * applicable to SDR devices that support simultaneous RX and TX.</p>
     */
    RX_TX
}