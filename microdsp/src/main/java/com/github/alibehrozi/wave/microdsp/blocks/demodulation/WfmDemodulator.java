package com.github.alibehrozi.wave.microdsp.blocks.demodulation;

import com.github.alibehrozi.wave.microdsp.core.Block;

/**
 * Wideband FM (WFM) demodulation block.
 * <p>
 * Performs FM demodulation followed by a de-emphasis filter to recover
 * high-fidelity {@code FLOAT} audio from {@code COMPLEX_FLOAT} broadcast FM signals.
 */
public class WfmDemodulator extends Block {

    /**
     * Create a WfmDemodulator with sample rate, de-emphasis time constant, and custom name.
     * @param sampleRate Input sample rate in Hz
     * @param tau        De-emphasis time constant in seconds (e.g., 75e-6 for US, 50e-6 for EU)
     * @param name       Block name
     */
    public WfmDemodulator(double sampleRate, double tau, String name) {
        super(name, nativeCreateWfmDemodulator(sampleRate, tau, name));
    }

    /**
     * Create a WfmDemodulator with default de-emphasis (75 µs) and default name.
     * @param sampleRate Input sample rate in Hz
     */
    public WfmDemodulator(double sampleRate) {
        this(sampleRate, 75e-6, "wfm_demod");
    }

    private static native long nativeCreateWfmDemodulator(double sampleRate, double tau, String name);
}
