package com.github.alibehrozi.wave.microdsp.blocks.modulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Binary Phase Shift Keying (BPSK) modulator block.
 * <p>
 * Takes a {@code FLOAT} stream of symbols (0.0 or 1.0) and produces a
 * {@code COMPLEX_FLOAT} IQ signal where 1.0 maps to 1 + 0j and 0.0 maps to -1 + 0j.
 */
public class BpskModulator extends Block {

    /**
     * Create a BpskModulator with a custom name.
     * @param name Block name
     */
    public BpskModulator(String name) {
        super(name, nativeCreateBpskModulator(name));
    }

    /**
     * Create a BpskModulator with default name.
     */
    public BpskModulator() {
        this("bpsk_mod");
    }

    private static native long nativeCreateBpskModulator(String name);
}
