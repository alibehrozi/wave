package com.github.alibehrozi.wave.microdsp.blocks.sinks;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrStreamingState;
import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Java wrapper for SdrSink C++ block.
 * This block takes IQ samples from the signal processing chain and
 * transmits them through an SDR device.
 *
 * <p>The SDR device should be fully configured (frequency, gain, sample rate)
 * before being passed to this block. Configuration is done directly through
 * the SdrDevice interface, not through this block.</p>
 *
 * <p>This block is passive - starting and stopping
 * the flow of samples from the processing pipeline to the device.</p>
 */
public class SdrSink extends Block {

    /**
     * The underlying SDR device instance
     */
    private final SdrDevice device;

    /**
     * Native handle to the C++ SdrSink block
     */
    private final long nativeBlockHandle;

    /**
     * Constructs an SdrSink block.
     * @param device The SDR device to transmit samples through (must already be configured)
     * @throws NullPointerException if device is null
     * @throws RuntimeException     if native block creation fails
     */
    public SdrSink(@NonNull SdrDevice device) {
        super("sdr_sink_" + device.getDeviceInfo().getProduct());

        if (!device.supportsTx()) {
            throw new IllegalArgumentException("Device '" + device.getDeviceInfo().getProduct() +
                    "' does not support transmission (role=" + device.getRole() + ")");
        }

        this.device = device;

        this.nativeBlockHandle = nativeCreateSdrSink(
                device.getNativeHandle(),
                getName()
        );

        if (this.nativeBlockHandle == 0) {
            throw new RuntimeException("Failed to create native SdrSink block");
        }
    }


    /**
     * Returns the underlying SDR device.
     * Use this to configure frequency, gain, sample rate, etc.
     * @return The SDR device instance
     */
    public SdrDevice getDevice() {
        return device;
    }

    /**
     * Checks if the device is currently transmitting samples.
     * @return true if transmitting, false otherwise
     */
    public boolean isTransmitting() {
        return device.getStreamingState() == SdrStreamingState.TX ||
                device.getStreamingState() == SdrStreamingState.RX_TX;
    }

    // Native methods
    private static native long nativeCreateSdrSink(long deviceHandle, String name);
    private static native void nativeDestroySdrSink(long handle);
}