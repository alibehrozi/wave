package com.github.alibehrozi.wave.microdsp.blocks.sources;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrDevice;
import com.github.alibehrozi.wave.microdsp.hardware.sdr.core.SdrStreamingState;
import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Java wrapper for SdrSource C++ block.
 * This block takes IQ samples from an SDR device and outputs them
 * to the signal processing chain.
 *
 * <p>
 * The SDR device should be fully configured (frequency, gain, sample rate)
 * before being passed to this block. Configuration is done directly through
 * the SdrDevice interface, not through this block.
 * </p>
 *
 * <p>
 * This block is passive - starting and stopping
 * the flow of samples from the device to the processing pipeline.
 * </p>
 */
public class SdrSource extends Block {

    /**
     * The underlying SDR device instance
     */
    private final SdrDevice device;

    /**
     * Native handle to the C++ SdrSource block
     */
    private final long nativeBlockHandle;

    /**
     * Constructs an SdrSource block.
     * The output port type is automatically determined from the SDR device.
     * @param device The SDR device to receive samples from (must already be
     *               configured)
     * @throws NullPointerException if device is null
     * @throws RuntimeException     if native block creation fails
     */
    public SdrSource(@NonNull SdrDevice device) {
        super("sdr_source_" + device.getDeviceInfo().getProduct());

        if (!device.supportsRx()) {
            throw new IllegalArgumentException("Device '" + device.getDeviceInfo().getProduct() +
                    "' does not support reception (role=" + device.getRole() + ")");
        }

        this.device = device;

        // Create the native block, passing the data type
        this.nativeBlockHandle = nativeCreateSdrSource(
                device.getNativeHandle(),
                getName());

        if (this.nativeBlockHandle == 0) {
            throw new RuntimeException("Failed to create native SdrSource block for type");
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
     * Checks if the device is currently receiving samples.
     * @return true if receiving, false otherwise
     */
    public boolean isReceiving() {
        return device.getStreamingState().equals(SdrStreamingState.RX) ||
                device.getStreamingState().equals(SdrStreamingState.RX_TX);
    }

    // Native methods
    private static native long nativeCreateSdrSource(long deviceHandle, String name);
    private static native void nativeDestroySdrSource(long handle);
}