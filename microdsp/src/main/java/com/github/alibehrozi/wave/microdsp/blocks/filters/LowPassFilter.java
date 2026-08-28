package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Dedicated Finite Impulse Response (FIR) Low-Pass Filter block.
 */
public class LowPassFilter extends FirFilter {

    private double gain;
    private double samplingFreq;
    private double cutoffFreq;
    private double transitionWidth;
    private FilterDesign.WindowType windowType;

    /**
     * Create a low-pass FIR filter with custom parameters.
     * @param type            Data type (e.g., FLOAT32, FLOAT64)
     * @param gain            Overall filter gain
     * @param samplingFreq    Audio sample rate in Hz (e.g., 44100)
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @param windowType      Windowing function to use (e.g., HAMMING, HANN)
     * @param decimation      Decimation factor (rate reduction, 1 = no decimation)
     * @param interpolation   Interpolation factor (rate increase, 1 = no interpolation)
     * @param name            Block name
     */
    public LowPassFilter(@NonNull DataType type,
                         double gain,
                         double samplingFreq,
                         double cutoffFreq,
                         double transitionWidth,
                         FilterDesign.WindowType windowType,
                         int decimation,
                         int interpolation,
                         String name) {
        super(type,
                FilterDesign.lowPass(gain, samplingFreq, cutoffFreq, transitionWidth, windowType),
                decimation,
                interpolation,
                name);
        this.gain = gain;
        this.samplingFreq = samplingFreq;
        this.cutoffFreq = cutoffFreq;
        this.transitionWidth = transitionWidth;
        this.windowType = windowType;
    }

    /**
     * Create a low-pass FIR filter with default parameters.
     * @param type            Data type (e.g., FLOAT32, FLOAT64)
     * @param samplingFreq    Audio sample rate in Hz (e.g., 44100)
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0, default 0.05)
     */
    public LowPassFilter(@NonNull DataType type,
                         double samplingFreq,
                         double cutoffFreq,
                         double transitionWidth) {
        this(type, 1.0,
                samplingFreq,
                cutoffFreq,
                transitionWidth,
                FilterDesign.WindowType.HAMMING,
                1,
                1,
                "low_pass_filter");
    }

    /**
     * Create a low-pass FIR filter with default parameters.
     * @param type         Data type (e.g., FLOAT32, FLOAT64)
     * @param samplingFreq Audio sample rate in Hz (e.g., 44100)
     * @param cutoffFreq   Cutoff frequency in Hz
     */
    public LowPassFilter(@NonNull DataType type, double samplingFreq, double cutoffFreq) {
        this(type, samplingFreq, cutoffFreq, samplingFreq * 0.05);
    }

    /**
     * Update the cutoff frequency dynamically at runtime.
     * @param newCutoffFreq New cutoff frequency in Hz
     */
    public void setCutoffFrequency(double newCutoffFreq) {
        this.cutoffFreq = newCutoffFreq;
        setTaps(FilterDesign.lowPass(this.gain,
                this.samplingFreq,
                this.cutoffFreq,
                this.transitionWidth,
                this.windowType));
    }

    /**
     * Update filter design parameters dynamically at runtime.
     * @param cutoffFreq      New cutoff frequency in Hz
     * @param transitionWidth New relative transition width
     */
    public void setParameters(double cutoffFreq, double transitionWidth) {
        this.cutoffFreq = cutoffFreq;
        this.transitionWidth = transitionWidth;
        setTaps(FilterDesign.lowPass(this.gain,
                this.samplingFreq,
                this.cutoffFreq,
                this.transitionWidth,
                this.windowType));
    }

    /**
     * Get the cutoff frequency.
     * @return Cutoff frequency in Hz
     */
    public double getCutoffFrequency() {
        return cutoffFreq;
    }

    /**
     * Get the sampling frequency.
     * @return Audio sample rate in Hz
     */
    public double getSamplingFrequency() {
        return samplingFreq;
    }

    /**
     * Get the transition width.
     * @return Relative transition width
     */
    public double getTransitionWidth() {
        return transitionWidth;
    }
}
