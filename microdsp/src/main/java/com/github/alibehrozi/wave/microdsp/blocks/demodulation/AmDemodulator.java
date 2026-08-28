package com.github.alibehrozi.wave.microdsp.blocks.demodulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Amplitude Modulation (AM) demodulation block.
 * <p>
 * Performs envelope detection on an input {@code COMPLEX_FLOAT} IQ signal to recover
 * the original {@code FLOAT} baseband audio signal. Output is calculated as {@code abs(IQ)}.
 */
public class AmDemodulator extends Block {

    /**
     * Create an AmDemodulator with a custom name.
     * @param name Block name
     */
    public AmDemodulator(String name) {
        super(name, nativeCreateAmDemodulator(name));
    }

    /**
     * Create an AmDemodulator with default name.
     */
    public AmDemodulator() {
        this("am_demod");
    }

    private static native long nativeCreateAmDemodulator(String name);
}
