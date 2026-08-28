package com.github.alibehrozi.wave.microdsp.blocks.filters;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.microdsp.core.Block;
import com.github.alibehrozi.wave.microdsp.core.DataType;

/**
 * DSP block that performs Infinite Impulse Response (IIR) filtering
 * using cascaded Biquad Second-Order Sections (SOS) in Transposed Direct Form II.
 */
public class IirFilter extends Block {

    private float[] currentCoeffs;

    /**
     * Create an IirFilter with custom Second-Order Section (SOS) coefficients and custom name.
     * @param type      Data type to process (e.g., FLOAT or COMPLEX_FLOAT)
     * @param sosCoeffs Flat array of 5 * N coefficients [b0, b1, b2, a1, a2, ...]
     * @param name      Block name
     */
    public IirFilter(@NonNull DataType type, float[] sosCoeffs, String name) {
        super(name, nativeCreateIirFilter(type.ordinal(), sosCoeffs, name));
        this.currentCoeffs = sosCoeffs != null ? sosCoeffs.clone() : new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    }

    /**
     * Create an IirFilter with default name.
     * @param type      Data type to process
     * @param sosCoeffs Flat array of 5 * N coefficients [b0, b1, b2, a1, a2, ...]
     */
    public IirFilter(@NonNull DataType type, float[] sosCoeffs) {
        this(type, sosCoeffs, "iir_filter");
    }

    /**
     * Update filter coefficients dynamically at runtime.
     * @param sosCoeffs Flat array of 5 * N coefficients [b0, b1, b2, a1, a2, ...]
     */
    public void setCoefficients(float[] sosCoeffs) {
        this.currentCoeffs = sosCoeffs != null ? sosCoeffs.clone() : new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        nativeSetCoefficients(nativeHandle, currentCoeffs);
    }

    /**
     * Get a copy of the current SOS coefficients.
     * @return Copy of the current SOS coefficient array
     */
    public float[] getCoefficients() {
        return currentCoeffs != null ? currentCoeffs.clone() : new float[0];
    }

    // Static Convenience Factory Methods

    /**
     * Create a single Biquad Low-Pass IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param q            Quality factor Q (0.7071 for Butterworth response)
     * @return Configured IirFilter instance
     */
    public static IirFilter createLowPass(@NonNull DataType type, double samplingFreq, double cutoffFreq, double q) {
        float[] coeffs = FilterDesign.biquadLowPass(samplingFreq, cutoffFreq, q);
        return new IirFilter(type, coeffs, "iir_lpf");
    }

    /**
     * Create a single Biquad Low-Pass IIR filter with default Q (0.7071).
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @return Configured IirFilter instance
     */
    public static IirFilter createLowPass(@NonNull DataType type, double samplingFreq, double cutoffFreq) {
        return createLowPass(type, samplingFreq, cutoffFreq, 0.70710678);
    }

    /**
     * Create a single Biquad High-Pass IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param q            Quality factor Q (0.7071 for Butterworth response)
     * @return Configured IirFilter instance
     */
    public static IirFilter createHighPass(@NonNull DataType type, double samplingFreq, double cutoffFreq, double q) {
        float[] coeffs = FilterDesign.biquadHighPass(samplingFreq, cutoffFreq, q);
        return new IirFilter(type, coeffs, "iir_hpf");
    }

    /**
     * Create a single Biquad High-Pass IIR filter with default Q (0.7071).
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @return Configured IirFilter instance
     */
    public static IirFilter createHighPass(@NonNull DataType type, double samplingFreq, double cutoffFreq) {
        return createHighPass(type, samplingFreq, cutoffFreq, 0.70710678);
    }

    /**
     * Create a single Biquad Band-Pass IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param centerFreq   Center passband frequency in Hz
     * @param q            Quality factor Q (centerFreq / bandwidth)
     * @return Configured IirFilter instance
     */
    public static IirFilter createBandPass(@NonNull DataType type, double samplingFreq, double centerFreq, double q) {
        float[] coeffs = FilterDesign.biquadBandPass(samplingFreq, centerFreq, q);
        return new IirFilter(type, coeffs, "iir_bpf");
    }

    /**
     * Create a single Biquad Notch / Band-Reject IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param centerFreq   Center notch frequency in Hz
     * @param q            Quality factor Q
     * @return Configured IirFilter instance
     */
    public static IirFilter createNotch(@NonNull DataType type, double samplingFreq, double centerFreq, double q) {
        float[] coeffs = FilterDesign.biquadNotch(samplingFreq, centerFreq, q);
        return new IirFilter(type, coeffs, "iir_notch");
    }

    /**
     * Create a single Biquad Peaking Parametric Equalizer IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param centerFreq   Center frequency in Hz
     * @param q            Quality factor Q
     * @param gainDb       Gain or attenuation in dB
     * @return Configured IirFilter instance
     */
    public static IirFilter createPeakingEq(@NonNull DataType type, double samplingFreq, double centerFreq, double q, double gainDb) {
        float[] coeffs = FilterDesign.biquadPeaking(samplingFreq, centerFreq, q, gainDb);
        return new IirFilter(type, coeffs, "iir_peaking");
    }

    /**
     * Create a cascaded Butterworth Low-Pass IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param order        Filter order (even integer &ge; 2)
     * @return Configured IirFilter instance
     */
    public static IirFilter createButterworthLowPass(@NonNull DataType type, double samplingFreq, double cutoffFreq, int order) {
        float[] coeffs = FilterDesign.butterworthLowPass(samplingFreq, cutoffFreq, order);
        return new IirFilter(type, coeffs, "iir_butterworth_lpf");
    }

    /**
     * Create a cascaded Butterworth High-Pass IIR filter.
     * @param type         Data type to process
     * @param samplingFreq Sample rate in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param order        Filter order (even integer &ge; 2)
     * @return Configured IirFilter instance
     */
    public static IirFilter createButterworthHighPass(@NonNull DataType type, double samplingFreq, double cutoffFreq, int order) {
        float[] coeffs = FilterDesign.butterworthHighPass(samplingFreq, cutoffFreq, order);
        return new IirFilter(type, coeffs, "iir_butterworth_hpf");
    }

    // Fluent Builder

    /**
     * Create a fluent builder for configuring and constructing an {@link IirFilter}.
     * @param type Data type to process
     * @return Builder instance
     */
    public static Builder builder(@NonNull DataType type) {
        return new Builder(type);
    }

    /**
     * Fluent Builder for {@link IirFilter}.
     */
    public static class Builder {
        private final DataType type;
        private float[] sosCoeffs;
        private String name = "iir_filter";

        /**
         * Create a new Builder.
         * @param type Data type to process
         */
        public Builder(@NonNull DataType type) {
            this.type = type;
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
         * Set custom Second-Order Section (SOS) coefficients directly.
         * @param sosCoeffs Flat array of 5 * N coefficients [b0, b1, b2, a1, a2, ...]
         * @return This builder for chaining
         */
        public Builder coefficients(float[] sosCoeffs) {
            this.sosCoeffs = sosCoeffs;
            return this;
        }

        /**
         * Design single Biquad Low-Pass filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param cutoffFreq   Cutoff frequency in Hz
         * @param q            Quality factor Q
         * @return This builder for chaining
         */
        public Builder lowPass(double samplingFreq, double cutoffFreq, double q) {
            this.sosCoeffs = FilterDesign.biquadLowPass(samplingFreq, cutoffFreq, q);
            return this;
        }

        /**
         * Design single Biquad High-Pass filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param cutoffFreq   Cutoff frequency in Hz
         * @param q            Quality factor Q
         * @return This builder for chaining
         */
        public Builder highPass(double samplingFreq, double cutoffFreq, double q) {
            this.sosCoeffs = FilterDesign.highPass(samplingFreq, cutoffFreq, q);
            return this;
        }

        /**
         * Design single Biquad Band-Pass filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param centerFreq   Center frequency in Hz
         * @param q            Quality factor Q
         * @return This builder for chaining
         */
        public Builder bandPass(double samplingFreq, double centerFreq, double q) {
            this.sosCoeffs = FilterDesign.biquadBandPass(samplingFreq, centerFreq, q);
            return this;
        }

        /**
         * Design single Biquad Notch filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param centerFreq   Center notch frequency in Hz
         * @param q            Quality factor Q
         * @return This builder for chaining
         */
        public Builder notch(double samplingFreq, double centerFreq, double q) {
            this.sosCoeffs = FilterDesign.biquadNotch(samplingFreq, centerFreq, q);
            return this;
        }

        /**
         * Design single Biquad Peaking EQ filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param centerFreq   Center frequency in Hz
         * @param q            Quality factor Q
         * @param gainDb       Gain or boost/cut in dB
         * @return This builder for chaining
         */
        public Builder peaking(double samplingFreq, double centerFreq, double q, double gainDb) {
            this.sosCoeffs = FilterDesign.biquadPeaking(samplingFreq, centerFreq, q, gainDb);
            return this;
        }

        /**
         * Design single Biquad Low-Shelf filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param cutoffFreq   Corner cutoff frequency in Hz
         * @param q            Quality factor Q
         * @param gainDb       Shelf gain in dB
         * @return This builder for chaining
         */
        public Builder lowShelf(double samplingFreq, double cutoffFreq, double q, double gainDb) {
            this.sosCoeffs = FilterDesign.biquadLowShelf(samplingFreq, cutoffFreq, q, gainDb);
            return this;
        }

        /**
         * Design single Biquad High-Shelf filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param cutoffFreq   Corner cutoff frequency in Hz
         * @param q            Quality factor Q
         * @param gainDb       Shelf gain in dB
         * @return This builder for chaining
         */
        public Builder highShelf(double samplingFreq, double cutoffFreq, double q, double gainDb) {
            this.sosCoeffs = FilterDesign.biquadHighShelf(samplingFreq, cutoffFreq, q, gainDb);
            return this;
        }

        /**
         * Design cascaded Butterworth Low-Pass filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param cutoffFreq   Cutoff frequency in Hz
         * @param order        Filter order (even integer &ge; 2)
         * @return This builder for chaining
         */
        public Builder butterworthLowPass(double samplingFreq, double cutoffFreq, int order) {
            this.sosCoeffs = FilterDesign.butterworthLowPass(samplingFreq, cutoffFreq, order);
            return this;
        }

        /**
         * Design cascaded Butterworth High-Pass filter coefficients.
         * @param samplingFreq Sample rate in Hz
         * @param cutoffFreq   Cutoff frequency in Hz
         * @param order        Filter order (even integer &ge; 2)
         * @return This builder for chaining
         */
        public Builder butterworthHighPass(double samplingFreq, double cutoffFreq, int order) {
            this.sosCoeffs = FilterDesign.butterworthHighPass(samplingFreq, cutoffFreq, order);
            return this;
        }

        /**
         * Build and return the configured {@link IirFilter} instance.
         * @return New IirFilter instance
         */
        public IirFilter build() {
            if (sosCoeffs == null || sosCoeffs.length == 0) {
                sosCoeffs = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            }
            return new IirFilter(type, sosCoeffs, name);
        }
    }

    private static native long nativeCreateIirFilter(int type, float[] sosCoeffs, String name);
    private native void nativeSetCoefficients(long handle, float[] sosCoeffs);
}
