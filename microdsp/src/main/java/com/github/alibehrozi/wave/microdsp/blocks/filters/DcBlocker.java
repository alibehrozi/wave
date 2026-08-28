package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Dedicated DC Removal / DC Blocker filter block.
 * Strips DC offset (0 Hz bias) from real audio or complex IQ baseband streams using high-pass IIR biquad filtering.
 */
public class DcBlocker extends IirFilter {

    private double samplingFreq;
    private double cutoffFreq;

    /**
     * Create a DcBlocker.
     * @param type         Data type (e.g., FLOAT32, COMPLEX_FLOAT32)
     * @param samplingFreq Input sample rate in Hz
     * @param cutoffFreq   High-pass cutoff frequency in Hz
     * @param name         Block name
     */
    public DcBlocker(@NonNull DataType type, double samplingFreq, double cutoffFreq, String name) {
        super(type, FilterDesign.biquadHighPass(samplingFreq, cutoffFreq, 0.70710678), name);
        this.samplingFreq = samplingFreq;
        this.cutoffFreq = cutoffFreq;
    }

    /**
     * Create a DcBlocker with default cutoff frequency.
     * @param type         Data type
     * @param samplingFreq Input sample rate in Hz
     */
    public DcBlocker(@NonNull DataType type, double samplingFreq) {
        this(type, samplingFreq, 20.0, "dc_blocker");
    }

    /**
     * Create a DcBlocker with default parameters.
     * @param type Data type
     */
    public DcBlocker(@NonNull DataType type) {
        this(type, 48000.0, 20.0, "dc_blocker");
    }

    /**
     * Update the DC blocker cutoff frequency.
     * @param cutoffFreq New cutoff frequency in Hz
     */
    public void setCutoffFrequency(double cutoffFreq) {
        this.cutoffFreq = cutoffFreq;
        setCoefficients(FilterDesign.biquadHighPass(this.samplingFreq, this.cutoffFreq, 0.70710678));
    }

    /**
     * Get the current cutoff frequency.
     * @return Cutoff frequency in Hz
     */
    public double getCutoffFrequency() {
        return cutoffFreq;
    }

    /**
     * Get the sampling frequency.
     * @return Sampling frequency in Hz
     */
    public double getSamplingFrequency() {
        return samplingFreq;
    }
}
