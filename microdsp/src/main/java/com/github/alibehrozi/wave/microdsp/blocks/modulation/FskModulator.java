package com.github.alibehrozi.wave.microdsp.blocks.modulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Frequency Shift Keying (FSK) modulator block.
 * <p>
 * Takes a {@code FLOAT} stream of symbols (0.0 or 1.0) and produces a
 * {@code COMPLEX_FLOAT} IQ signal alternating between Mark (1.0) and Space (0.0) frequencies.
 */
public class FskModulator extends Block {

    /**
     * Create an FskModulator with custom frequencies and name.
     * @param sampleRate Sample rate in Hz
     * @param freqMark   Frequency in Hz for symbol 1.0 (Mark)
     * @param freqSpace  Frequency in Hz for symbol 0.0 (Space)
     * @param name       Block name
     */
    public FskModulator(double sampleRate, double freqMark, double freqSpace, String name) {
        super(name, nativeCreateFskModulator(sampleRate, freqMark, freqSpace, name));
    }

    /**
     * Create an FskModulator with default name.
     * @param sampleRate Sample rate in Hz
     * @param freqMark   Frequency in Hz for symbol 1.0 (Mark)
     * @param freqSpace  Frequency in Hz for symbol 0.0 (Space)
     */
    public FskModulator(double sampleRate, double freqMark, double freqSpace) {
        this(sampleRate, freqMark, freqSpace, "fsk_mod");
    }

    private static native long nativeCreateFskModulator(double sampleRate, double freqMark, double freqSpace,
                                                        String name);
}
