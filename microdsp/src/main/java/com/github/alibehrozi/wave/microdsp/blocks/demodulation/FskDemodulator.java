package com.github.alibehrozi.wave.microdsp.blocks.demodulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Frequency Shift Keying (FSK) demodulation block.
 * <p>
 * Takes {@code COMPLEX_FLOAT} IQ samples and produces {@code FLOAT} symbols (0.0 or 1.0)
 * by estimating instantaneous frequency relative to Mark and Space center frequencies.
 */
public class FskDemodulator extends Block {

    /**
     * Create an FskDemodulator with custom frequencies and name.
     * @param sampleRate Input sample rate in Hz
     * @param freqMark   Mark frequency in Hz for symbol 1.0
     * @param freqSpace  Space frequency in Hz for symbol 0.0
     * @param name       Block name
     */
    public FskDemodulator(double sampleRate, double freqMark, double freqSpace, String name) {
        super(name, nativeCreateFskDemodulator(sampleRate, freqMark, freqSpace, name));
    }

    /**
     * Create an FskDemodulator with default name.
     * @param sampleRate Input sample rate in Hz
     * @param freqMark   Mark frequency in Hz for symbol 1.0
     * @param freqSpace  Space frequency in Hz for symbol 0.0
     */
    public FskDemodulator(double sampleRate, double freqMark, double freqSpace) {
        this(sampleRate, freqMark, freqSpace, "fsk_demod");
    }

    private static native long nativeCreateFskDemodulator(
            double sampleRate,
            double freqMark,
            double freqSpace,
            String name);
}
