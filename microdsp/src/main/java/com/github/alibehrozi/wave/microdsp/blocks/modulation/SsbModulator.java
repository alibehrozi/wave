package com.github.alibehrozi.wave.microdsp.blocks.modulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Single Sideband (SSB) modulation block.
 * <p>
 * Takes a {@code FLOAT} audio signal and produces a {@code COMPLEX_FLOAT} IQ
 * analytic signal
 * representing either the Upper Sideband (USB) or Lower Sideband (LSB) using a
 * Hilbert transform FIR filter.
 */
public class SsbModulator extends Block {

    /**
     * Sideband selection for SSB modulation.
     */
    public enum Sideband {
        /**
         * Lower Sideband
         */
        LSB,
        /**
         * Upper Sideband
         */
        USB
    }

    /**
     * Create an SsbModulator.
     * @param sideband Sideband type ({@link Sideband#USB} or {@link Sideband#LSB})
     * @param ntaps    Number of taps for the Hilbert filter (must be odd)
     * @param name     Block name
     */
    public SsbModulator(Sideband sideband, int ntaps, String name) {
        super(name, nativeCreateSsbModulator(sideband.ordinal(), ntaps, name));
    }

    /**
     * Create an SsbModulator with default filter taps (65) and default name.
     * @param sideband Sideband type ({@link Sideband#USB} or {@link Sideband#LSB})
     */
    public SsbModulator(Sideband sideband) {
        this(sideband, 65, "ssb_mod");
    }

    private static native long nativeCreateSsbModulator(int sideband, int ntaps, String name);
}
