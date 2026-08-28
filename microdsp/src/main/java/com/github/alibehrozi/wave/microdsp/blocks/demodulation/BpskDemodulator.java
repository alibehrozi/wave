package com.github.alibehrozi.wave.microdsp.blocks.demodulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Binary Phase Shift Keying (BPSK) demodulation block.
 * <p>
 * Takes {@code COMPLEX_FLOAT} IQ samples and produces {@code FLOAT} symbols (0.0 or 1.0)
 * using Costas loop carrier synchronization and decision slicing.
 */
public class BpskDemodulator extends Block {

    /**
     * Create a BpskDemodulator with a custom name.
     * @param name Block name
     */
    public BpskDemodulator(String name) {
        super(name, nativeCreateBpskDemodulator(name));
    }

    /**
     * Create a BpskDemodulator with default name.
     */
    public BpskDemodulator() {
        this("bpsk_demod");
    }

    private static native long nativeCreateBpskDemodulator(String name);
}
