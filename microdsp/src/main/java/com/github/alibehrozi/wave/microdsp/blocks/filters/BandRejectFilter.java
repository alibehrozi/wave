package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * Dedicated Finite Impulse Response (FIR) Band-Reject / Notch Filter block.
 */
public class BandRejectFilter extends FirFilter {

    private double gain;
    private double samplingFreq;
    private double lowCutoff;
    private double highCutoff;
    private double transitionWidth;
    private FilterDesign.WindowType windowType;

    /**
     * Create a band-reject FIR filter with custom parameters.
     * @param type            Data type (e.g., FLOAT32, FLOAT64)
     * @param gain            Overall filter gain
     * @param samplingFreq    Audio sample rate in Hz (e.g., 44100)
     * @param lowCutoff       Low cutoff frequency of the stopband in Hz
     * @param highCutoff      High cutoff frequency of the stopband in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @param windowType      Windowing function to use (e.g., HAMMING, HANN)
     * @param decimation      Decimation factor (rate reduction, 1 = no decimation)
     * @param interpolation   Interpolation factor (rate increase, 1 = no interpolation)
     * @param name            Block name
     */
    public BandRejectFilter(@NonNull DataType type,
                            double gain,
                            double samplingFreq,
                            double lowCutoff,
                            double highCutoff,
                            double transitionWidth,
                            FilterDesign.WindowType windowType,
                            int decimation,
                            int interpolation,
                            String name) {
        super(type,
                FilterDesign.bandReject(gain,
                        samplingFreq,
                        lowCutoff,
                        highCutoff,
                        transitionWidth,
                        windowType),
                decimation,
                interpolation,
                name);
        this.gain = gain;
        this.samplingFreq = samplingFreq;
        this.lowCutoff = lowCutoff;
        this.highCutoff = highCutoff;
        this.transitionWidth = transitionWidth;
        this.windowType = windowType;
    }

    /**
     * Create a band-reject FIR filter with default parameters.
     * @param type            Data type (e.g., FLOAT32, FLOAT64)
     * @param samplingFreq    Audio sample rate in Hz (e.g., 44100)
     * @param lowCutoff       Low cutoff frequency of the stopband in Hz
     * @param highCutoff      High cutoff frequency of the stopband in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0, default 0.05)
     */
    public BandRejectFilter(@NonNull DataType type,
                            double samplingFreq,
                            double lowCutoff,
                            double highCutoff,
                            double transitionWidth) {
        this(type,
                1.0,
                samplingFreq,
                lowCutoff,
                highCutoff,
                transitionWidth,
                FilterDesign.WindowType.HAMMING,
                1,
                1,
                "band_reject_filter");
    }

    /**
     * Create a band-reject FIR filter with default parameters.
     * @param type         Data type (e.g., FLOAT32, FLOAT64)
     * @param samplingFreq Audio sample rate in Hz (e.g., 44100)
     * @param lowCutoff    Low cutoff frequency of the stopband in Hz
     * @param highCutoff   High cutoff frequency of the stopband in Hz
     */
    public BandRejectFilter(@NonNull DataType type,
                            double samplingFreq,
                            double lowCutoff,
                            double highCutoff) {
        this(type, samplingFreq, lowCutoff, highCutoff, samplingFreq * 0.05);
    }

    /**
     * Update reject band frequencies dynamically at runtime.
     * @param lowCutoff  New low cutoff frequency in Hz
     * @param highCutoff New high cutoff frequency in Hz
     */
    public void setFrequencies(double lowCutoff, double highCutoff) {
        this.lowCutoff = lowCutoff;
        this.highCutoff = highCutoff;

        setTaps(FilterDesign.bandReject(this.gain,
                this.samplingFreq,
                this.lowCutoff,
                this.highCutoff,
                this.transitionWidth,
                this.windowType));
    }

    /**
     * Get the low cutoff frequency.
     * @return Low cutoff frequency in Hz
     */
    public double getLowCutoff() {
        return lowCutoff;
    }

    /**
     * Get the high cutoff frequency.
     * @return High cutoff frequency in Hz
     */
    public double getHighCutoff() {
        return highCutoff;
    }

    /**
     * Get the sampling frequency.
     * @return Audio sample rate in Hz
     */
    public double getSamplingFrequency() {
        return samplingFreq;
    }
}
