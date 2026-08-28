package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs Finite Impulse Response (FIR) filtering,
 * multirate polyphase decimation, and interpolation.
 */
public class FirFilter extends Block {

    private float[] currentTaps;

    /**
     * Create a FIR filter with custom taps, decimation, interpolation, and name.
     * @param type          Data type to process (e.g., FLOAT or COMPLEX_FLOAT)
     * @param taps          Array of FIR filter coefficients
     * @param decimation    Decimation factor (downsampling rate, 1 = no decimation)
     * @param interpolation Interpolation factor (upsampling rate, 1 = no interpolation)
     * @param name          Block name
     */
    public FirFilter(@NonNull DataType type, float[] taps, int decimation, int interpolation, String name) {
        super(name, nativeCreateFirFilter(type.ordinal(), taps, decimation, interpolation, name));
        this.currentTaps = taps != null ? taps.clone() : new float[0];
    }

    /**
     * Create a single-rate FIR filter with default name.
     * @param type Data type to process
     * @param taps Array of FIR filter coefficients
     */
    public FirFilter(@NonNull DataType type, float[] taps) {
        this(type, taps, 1, 1, "fir_filter");
    }

    /**
     * Update filter taps dynamically at runtime.
     * @param taps New array of filter coefficients
     */
    public void setTaps(float[] taps) {
        this.currentTaps = taps != null ? taps.clone() : new float[0];
        nativeSetTaps(nativeHandle, currentTaps);
    }

    /**
     * Get a copy of the current filter taps.
     * @return Copy of the current tap coefficients
     */
    public float[] getTaps() {
        return currentTaps != null ? currentTaps.clone() : new float[0];
    }

    /**
     * Create a low-pass FIR filter using windowed sinc design.
     * @param type            Data type to process
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Configured FirFilter instance
     */
    public static FirFilter createLowPass(@NonNull DataType type, double samplingFreq, double cutoffFreq,
                                          double transitionWidth) {
        float[] taps = FilterDesign.lowPass(samplingFreq, cutoffFreq, transitionWidth);
        return new FirFilter(type, taps, 1, 1, "lpf_fir");
    }

    /**
     * Create a high-pass FIR filter using windowed sinc design.
     * @param type            Data type to process
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Configured FirFilter instance
     */
    public static FirFilter createHighPass(@NonNull DataType type, double samplingFreq, double cutoffFreq,
                                           double transitionWidth) {
        float[] taps = FilterDesign.highPass(samplingFreq, cutoffFreq, transitionWidth);
        return new FirFilter(type, taps, 1, 1, "hpf_fir");
    }

    /**
     * Create a band-pass FIR filter using windowed sinc design.
     * @param type            Data type to process
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param lowCutoff       Low cutoff frequency in Hz
     * @param highCutoff      High cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Configured FirFilter instance
     */
    public static FirFilter createBandPass(@NonNull DataType type, double samplingFreq, double lowCutoff,
                                           double highCutoff, double transitionWidth) {
        float[] taps = FilterDesign.bandPass(samplingFreq, lowCutoff, highCutoff, transitionWidth);
        return new FirFilter(type, taps, 1, 1, "bpf_fir");
    }

    /**
     * Create a band-reject (notch) FIR filter using windowed sinc design.
     * @param type            Data type to process
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param lowCutoff       Low cutoff frequency of the stopband in Hz
     * @param highCutoff      High cutoff frequency of the stopband in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Configured FirFilter instance
     */
    public static FirFilter createBandReject(@NonNull DataType type, double samplingFreq, double lowCutoff,
                                             double highCutoff, double transitionWidth) {
        float[] taps = FilterDesign.bandReject(samplingFreq, lowCutoff, highCutoff, transitionWidth);
        return new FirFilter(type, taps, 1, 1, "brf_fir");
    }

    /**
     * Create a Root Raised Cosine (RRC) pulse shaping FIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sampling frequency in Hz
     * @param symbolRate   Symbol rate in symbols/sec
     * @param excessBw     Excess bandwidth (rolloff factor, e.g., 0.35)
     * @param ntaps        Number of filter taps
     * @return Configured FirFilter instance
     */
    public static FirFilter createRootRaisedCosine(@NonNull DataType type, double samplingFreq, double symbolRate,
                                                   double excessBw, int ntaps) {
        float[] taps = FilterDesign.rootRaisedCosine(samplingFreq, symbolRate, excessBw, ntaps);
        return new FirFilter(type, taps, 1, 1, "rrc_fir");
    }

    /**
     * Create a fluent builder for configuring and constructing a {@link FirFilter}.
     * @param type Data type to process
     * @return Builder instance
     */
    public static Builder builder(@NonNull DataType type) {
        return new Builder(type);
    }

    /**
     * Fluent Builder for {@link FirFilter}.
     */
    public static class Builder {
        private final DataType type;
        private float[] taps;
        private int decimation = 1;
        private int interpolation = 1;
        private String name = "fir_filter";
        private double gain = 1.0;
        private FilterDesign.WindowType windowType = FilterDesign.WindowType.HAMMING;

        /**
         * Create a new Builder.
         * @param type Data type to process
         */
        public Builder(@NonNull DataType type) {
            this.type = type;
        }

        /**
         * Set custom filter taps directly.
         * @param taps Array of FIR coefficients
         * @return This builder for chaining
         */
        public Builder taps(float[] taps) {
            this.taps = taps;
            return this;
        }

        /**
         * Set decimation factor.
         * @param decimation Downsampling factor (&ge; 1)
         * @return This builder for chaining
         */
        public Builder decimation(int decimation) {
            this.decimation = Math.max(1, decimation);
            return this;
        }

        /**
         * Set interpolation factor.
         * @param interpolation Upsampling factor (&ge; 1)
         * @return This builder for chaining
         */
        public Builder interpolation(int interpolation) {
            this.interpolation = Math.max(1, interpolation);
            return this;
        }

        /**
         * Set custom block name.
         * @param name Block name
         * @return This builder for chaining
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set filter gain.
         * @param gain Overall filter gain
         * @return This builder for chaining
         */
        public Builder gain(double gain) {
            this.gain = gain;
            return this;
        }

        /**
         * Set windowing function to use for filter design.
         * @param windowType Window type (e.g. HAMMING, BLACKMAN)
         * @return This builder for chaining
         */
        public Builder window(FilterDesign.WindowType windowType) {
            this.windowType = windowType;
            return this;
        }

        /**
         * Design low-pass FIR filter taps.
         * @param samplingFreq    Sample rate in Hz
         * @param cutoffFreq      Cutoff frequency in Hz
         * @param transitionWidth Relative transition width
         * @return This builder for chaining
         */
        public Builder lowPass(double samplingFreq, double cutoffFreq, double transitionWidth) {
            this.taps = FilterDesign.lowPass(this.gain, samplingFreq, cutoffFreq, transitionWidth, this.windowType);
            return this;
        }

        /**
         * Design high-pass FIR filter taps.
         * @param samplingFreq    Sample rate in Hz
         * @param cutoffFreq      Cutoff frequency in Hz
         * @param transitionWidth Relative transition width
         * @return This builder for chaining
         */
        public Builder highPass(double samplingFreq, double cutoffFreq, double transitionWidth) {
            this.taps = FilterDesign.highPass(this.gain, samplingFreq, cutoffFreq, transitionWidth, this.windowType);
            return this;
        }

        /**
         * Design band-pass FIR filter taps.
         * @param samplingFreq    Sample rate in Hz
         * @param lowCutoff       Low cutoff frequency in Hz
         * @param highCutoff      High cutoff frequency in Hz
         * @param transitionWidth Relative transition width
         * @return This builder for chaining
         */
        public Builder bandPass(double samplingFreq, double lowCutoff, double highCutoff, double transitionWidth) {
            this.taps = FilterDesign.bandPass(this.gain, samplingFreq, lowCutoff, highCutoff, transitionWidth,
                    this.windowType);
            return this;
        }

        /**
         * Design band-reject (notch) FIR filter taps.
         * @param samplingFreq    Sample rate in Hz
         * @param lowCutoff       Low cutoff frequency of the stopband in Hz
         * @param highCutoff      High cutoff frequency of the stopband in Hz
         * @param transitionWidth Relative transition width
         * @return This builder for chaining
         */
        public Builder bandReject(double samplingFreq, double lowCutoff, double highCutoff, double transitionWidth) {
            this.taps = FilterDesign.bandReject(this.gain, samplingFreq, lowCutoff, highCutoff, transitionWidth,
                    this.windowType);
            return this;
        }

        /**
         * Design Root Raised Cosine (RRC) pulse shaping filter taps.
         * @param samplingFreq Sample rate in Hz
         * @param symbolRate   Symbol rate in symbols/sec
         * @param excessBw     Excess bandwidth (rolloff)
         * @param ntaps        Number of filter taps
         * @return This builder for chaining
         */
        public Builder rootRaisedCosine(double samplingFreq, double symbolRate, double excessBw, int ntaps) {
            this.taps = FilterDesign.rootRaisedCosine(this.gain, samplingFreq, symbolRate, excessBw, ntaps);
            return this;
        }

        /**
         * Build and return the configured {@link FirFilter} instance.
         * @return New FirFilter instance
         */
        public FirFilter build() {
            if (taps == null || taps.length == 0) {
                taps = new float[]{1.0f};
            }
            return new FirFilter(type, taps, decimation, interpolation, name);
        }
    }

    private static native long nativeCreateFirFilter(int type, float[] taps, int decimation, int interpolation,
                                                     String name);

    private native void nativeSetTaps(long handle, float[] taps);
}
