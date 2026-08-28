package com.github.alibehrozi.wave.microdsp.blocks.demodulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Single Sideband (SSB) demodulation block.
 * <p>
 * Recovers a {@code FLOAT} baseband audio signal from a {@code COMPLEX_FLOAT}
 * IQ analytic signal
 * for either Upper Sideband (USB) or Lower Sideband (LSB).
 */
public class SsbDemodulator extends Block {

    /**
     * Sideband selection for SSB demodulation.
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
     * Create an SsbDemodulator with custom sideband and name.
     * @param sideband Sideband to demodulate ({@link Sideband#USB} or
     *                 {@link Sideband#LSB})
     * @param name     Block name
     */
    public SsbDemodulator(Sideband sideband, String name) {
        super(name, nativeCreateSsbDemodulator(sideband.ordinal(), name));
    }

    /**
     * Create an SsbDemodulator with default name.
     * @param sideband Sideband to demodulate ({@link Sideband#USB} or
     *                 {@link Sideband#LSB})
     */
    public SsbDemodulator(Sideband sideband) {
        this(sideband, "ssb_demod");
    }

    private static native long nativeCreateSsbDemodulator(int sideband, String name);
}
