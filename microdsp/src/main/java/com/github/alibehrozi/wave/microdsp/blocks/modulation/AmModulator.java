package com.github.alibehrozi.wave.microdsp.blocks.modulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Amplitude Modulation (AM) block.
 * <p>
 * Takes a {@code FLOAT} audio signal as input and produces a
 * {@code COMPLEX_FLOAT} IQ signal where IQ = (1.0 + audio) + 0j.
 */
public class AmModulator extends Block {

    /**
     * Create an AmModulator with a custom name.
     * @param name Block name
     */
    public AmModulator(String name) {
        super(name, nativeCreateAmModulator(name));
    }

    /**
     * Create an AmModulator with default name.
     */
    public AmModulator() {
        this("am_mod");
    }

    private static native long nativeCreateAmModulator(String name);
}
