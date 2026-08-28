package com.github.alibehrozi.wave.microdsp.blocks.demodulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Frequency Modulation (FM) demodulation block.
 * <p>
 * Recovers a {@code FLOAT} baseband audio signal from a {@code COMPLEX_FLOAT} IQ signal
 * using quadrature differentiation (polar discriminator).
 */
public class FmDemodulator extends Block {

    /**
     * Create an FmDemodulator with custom gain and name.
     * @param gain Demodulator gain (scaling factor)
     * @param name Block name
     */
    public FmDemodulator(float gain, String name) {
        super(name, nativeCreateFmDemodulator(gain, name));
    }

    /**
     * Create an FmDemodulator with default name.
     * @param gain Demodulator gain (scaling factor)
     */
    public FmDemodulator(float gain) {
        this(gain, "fm_demod");
    }

    /**
     * Set the demodulator gain.
     * @param gain New gain value
     */
    public void setGain(float gain) {
        nativeSetGain(nativeHandle, gain);
    }

    private static native long nativeCreateFmDemodulator(float gain, String name);
    private native void nativeSetGain(long handle, float gain);
}
