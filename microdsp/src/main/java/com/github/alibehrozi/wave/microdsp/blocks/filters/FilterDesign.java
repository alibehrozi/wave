package com.github.alibehrozi.wave.microdsp.blocks.filters;

/**
 * Complete mathematical synthesis utility for designing FIR and IIR filter coefficients.
 */
public final class FilterDesign {

    /**
     * Windowing functions supported for windowed-sinc FIR filter synthesis.
     */
    public enum WindowType {
        /**
         * Hamming window
         */
        HAMMING(0),
        /**
         * Hann (Hanning) window
         */
        HANN(1),
        /**
         * Blackman window
         */
        BLACKMAN(2),
        /**
         * Rectangular (no window / Dirichlet)
         */
        RECTANGULAR(3),
        /**
         * Blackman-Harris 4-term window
         */
        BLACKMAN_HARRIS(4),
        /**
         * Bartlett (triangular) window
         */
        BARTLETT(5),
        /**
         * Flat-Top window
         */
        FLAT_TOP(6),
        /**
         * Kaiser window
         */
        KAISER(7);

        public final int value;

        WindowType(int value) {
            this.value = value;
        }
    }

    private FilterDesign() {
    }

    // FIR Design Methods

    /**
     * Design a low-pass FIR filter.
     * @param gain            Overall filter gain
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @param windowType      Windowing function to apply
     * @return Array of computed FIR tap coefficients
     */
    public static float[] lowPass(double gain,
                                  double samplingFreq,
                                  double cutoffFreq,
                                  double transitionWidth,
                                  WindowType windowType) {
        return nativeLowPass(gain, samplingFreq, cutoffFreq, transitionWidth, windowType.value);
    }

    /**
     * Design a low-pass FIR filter with unity gain and default Hamming window.
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Array of computed FIR tap coefficients
     */
    public static float[] lowPass(double samplingFreq, double cutoffFreq, double transitionWidth) {
        return lowPass(1.0, samplingFreq, cutoffFreq, transitionWidth, WindowType.HAMMING);
    }

    /**
     * Design a high-pass FIR filter.
     * @param gain            Overall filter gain
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @param windowType      Windowing function to apply
     * @return Array of computed FIR tap coefficients
     */
    public static float[] highPass(double gain,
                                   double samplingFreq,
                                   double cutoffFreq,
                                   double transitionWidth,
                                   WindowType windowType) {
        return nativeHighPass(gain, samplingFreq, cutoffFreq, transitionWidth, windowType.value);
    }

    /**
     * Design a high-pass FIR filter with unity gain and default Hamming window.
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param cutoffFreq      Cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Array of computed FIR tap coefficients
     */
    public static float[] highPass(double samplingFreq, double cutoffFreq, double transitionWidth) {
        return highPass(1.0, samplingFreq, cutoffFreq, transitionWidth, WindowType.HAMMING);
    }

    /**
     * Design a band-pass FIR filter.
     * @param gain            Overall filter gain
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param lowCutoff       Low cutoff frequency in Hz
     * @param highCutoff      High cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @param windowType      Windowing function to apply
     * @return Array of computed FIR tap coefficients
     */
    public static float[] bandPass(double gain,
                                   double samplingFreq,
                                   double lowCutoff,
                                   double highCutoff,
                                   double transitionWidth,
                                   WindowType windowType) {
        return nativeBandPass(gain,
                samplingFreq,
                lowCutoff,
                highCutoff,
                transitionWidth,
                windowType.value);
    }

    /**
     * Design a band-pass FIR filter with unity gain and default Hamming window.
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param lowCutoff       Low cutoff frequency in Hz
     * @param highCutoff      High cutoff frequency in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Array of computed FIR tap coefficients
     */
    public static float[] bandPass(double samplingFreq,
                                   double lowCutoff,
                                   double highCutoff,
                                   double transitionWidth) {
        return bandPass(1.0,
                samplingFreq,
                lowCutoff,
                highCutoff,
                transitionWidth,
                WindowType.HAMMING);
    }

    /**
     * Design a band-reject (notch) FIR filter.
     * @param gain            Overall filter gain
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param lowCutoff       Low cutoff frequency of stopband in Hz
     * @param highCutoff      High cutoff frequency of stopband in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @param windowType      Windowing function to apply
     * @return Array of computed FIR tap coefficients
     */
    public static float[] bandReject(double gain,
                                     double samplingFreq,
                                     double lowCutoff,
                                     double highCutoff,
                                     double transitionWidth,
                                     WindowType windowType) {
        return nativeBandReject(gain,
                samplingFreq,
                lowCutoff,
                highCutoff,
                transitionWidth,
                windowType.value);
    }

    /**
     * Design a band-reject (notch) FIR filter with unity gain and default Hamming window.
     * @param samplingFreq    Audio/signal sample rate in Hz
     * @param lowCutoff       Low cutoff frequency of stopband in Hz
     * @param highCutoff      High cutoff frequency of stopband in Hz
     * @param transitionWidth Relative transition width (0.0 to 1.0)
     * @return Array of computed FIR tap coefficients
     */
    public static float[] bandReject(double samplingFreq,
                                     double lowCutoff,
                                     double highCutoff,
                                     double transitionWidth) {
        return bandReject(1.0,
                samplingFreq,
                lowCutoff,
                highCutoff,
                transitionWidth,
                WindowType.HAMMING);
    }

    /**
     * Design a Root Raised Cosine (RRC) pulse shaping FIR filter.
     * @param gain         Overall filter gain
     * @param samplingFreq Sampling frequency in Hz
     * @param symbolRate   Symbol rate in symbols/sec
     * @param excessBw     Excess bandwidth (rolloff factor, 0.0 to 1.0)
     * @param ntaps        Number of filter taps
     * @return Array of computed FIR tap coefficients
     */
    public static float[] rootRaisedCosine(double gain,
                                           double samplingFreq,
                                           double symbolRate,
                                           double excessBw,
                                           int ntaps) {
        return nativeRootRaisedCosine(gain, samplingFreq, symbolRate, excessBw, ntaps);
    }

    /**
     * Design a Root Raised Cosine (RRC) pulse shaping FIR filter with unity gain.
     * @param samplingFreq Sampling frequency in Hz
     * @param symbolRate   Symbol rate in symbols/sec
     * @param excessBw     Excess bandwidth (rolloff factor, 0.0 to 1.0)
     * @param ntaps        Number of filter taps
     * @return Array of computed FIR tap coefficients
     */
    public static float[] rootRaisedCosine(double samplingFreq,
                                           double symbolRate,
                                           double excessBw,
                                           int ntaps) {
        return rootRaisedCosine(1.0, samplingFreq, symbolRate, excessBw, ntaps);
    }

    /**
     * Design a Raised Cosine (RC) FIR filter.
     * @param gain         Overall filter gain
     * @param samplingFreq Sampling frequency in Hz
     * @param symbolRate   Symbol rate in symbols/sec
     * @param excessBw     Excess bandwidth (rolloff factor, 0.0 to 1.0)
     * @param ntaps        Number of filter taps
     * @return Array of computed FIR tap coefficients
     */
    public static float[] raisedCosine(double gain,
                                       double samplingFreq,
                                       double symbolRate,
                                       double excessBw,
                                       int ntaps) {
        return nativeRaisedCosine(gain, samplingFreq, symbolRate, excessBw, ntaps);
    }

    /**
     * Design a Gaussian pulse shaping filter.
     * @param gain         Overall filter gain
     * @param samplingFreq Sampling frequency in Hz
     * @param symbolRate   Symbol rate in symbols/sec
     * @param bt           Bandwidth-time product
     * @param ntaps        Number of filter taps
     * @return Array of computed FIR tap coefficients
     */
    public static float[] gaussian(double gain,
                                   double samplingFreq,
                                   double symbolRate,
                                   double bt,
                                   int ntaps) {
        return nativeGaussian(gain, samplingFreq, symbolRate, bt, ntaps);
    }

    /**
     * Design a Hilbert transform FIR filter.
     * @param ntaps      Number of taps (must be odd)
     * @param windowType Window function to apply
     * @return Array of computed FIR tap coefficients
     */
    public static float[] hilbert(int ntaps, WindowType windowType) {
        return nativeHilbert(ntaps, windowType.value);
    }

    /**
     * Design a Hilbert transform FIR filter with default Hamming window.
     * @param ntaps Number of taps (must be odd)
     * @return Array of computed FIR tap coefficients
     */
    public static float[] hilbert(int ntaps) {
        return hilbert(ntaps, WindowType.HAMMING);
    }

    // IIR / Biquad Design Methods

    /**
     * Design single Biquad Low-Pass filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param q            Quality factor Q
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadLowPass(double samplingFreq, double cutoffFreq, double q) {
        return nativeBiquadLowPass(samplingFreq, cutoffFreq, q);
    }

    /**
     * Design single Biquad Low-Pass filter coefficients with default Butterworth Q (0.7071).
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadLowPass(double samplingFreq, double cutoffFreq) {
        return biquadLowPass(samplingFreq, cutoffFreq, 0.70710678);
    }

    /**
     * Design single Biquad High-Pass filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param q            Quality factor Q
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadHighPass(double samplingFreq, double cutoffFreq, double q) {
        return nativeBiquadHighPass(samplingFreq, cutoffFreq, q);
    }

    /**
     * Design single Biquad High-Pass filter coefficients with default Butterworth Q (0.7071).
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadHighPass(double samplingFreq, double cutoffFreq) {
        return biquadHighPass(samplingFreq, cutoffFreq, 0.70710678);
    }

    /**
     * Design single Biquad Band-Pass filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param centerFreq   Center frequency in Hz
     * @param q            Quality factor Q (centerFreq / bandwidth)
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadBandPass(double samplingFreq, double centerFreq, double q) {
        return nativeBiquadBandPass(samplingFreq, centerFreq, q);
    }

    /**
     * Design single Biquad Notch / Band-Reject filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param centerFreq   Center notch frequency in Hz
     * @param q            Quality factor Q
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadNotch(double samplingFreq, double centerFreq, double q) {
        return nativeBiquadNotch(samplingFreq, centerFreq, q);
    }

    /**
     * Design single Biquad Peaking Parametric EQ filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param centerFreq   Center frequency in Hz
     * @param q            Quality factor Q
     * @param gainDb       Gain or boost/cut in dB
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadPeaking(double samplingFreq,
                                        double centerFreq,
                                        double q,
                                        double gainDb) {
        return nativeBiquadPeaking(samplingFreq, centerFreq, q, gainDb);
    }

    /**
     * Design single Biquad Low-Shelf filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Corner cutoff frequency in Hz
     * @param q            Quality factor Q
     * @param gainDb       Shelf boost/cut in dB
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadLowShelf(double samplingFreq,
                                         double cutoffFreq,
                                         double q,
                                         double gainDb) {
        return nativeBiquadLowShelf(samplingFreq, cutoffFreq, q, gainDb);
    }

    /**
     * Design single Biquad High-Shelf filter coefficients [b0, b1, b2, a1, a2].
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Corner cutoff frequency in Hz
     * @param q            Quality factor Q
     * @param gainDb       Shelf boost/cut in dB
     * @return Array of 5 SOS coefficients [b0, b1, b2, a1, a2]
     */
    public static float[] biquadHighShelf(double samplingFreq,
                                          double cutoffFreq,
                                          double q,
                                          double gainDb) {
        return nativeBiquadHighShelf(samplingFreq, cutoffFreq, q, gainDb);
    }

    /**
     * Design cascaded Butterworth Low-Pass filter coefficients.
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param order        Filter order (even integer &ge; 2)
     * @return Flat array of 5 * (order / 2) SOS coefficients
     */
    public static float[] butterworthLowPass(double samplingFreq, double cutoffFreq, int order) {
        return nativeButterworthLowPass(samplingFreq, cutoffFreq, order);
    }

    /**
     * Design cascaded Butterworth High-Pass filter coefficients.
     * @param samplingFreq Sampling frequency in Hz
     * @param cutoffFreq   Cutoff frequency in Hz
     * @param order        Filter order (even integer &ge; 2)
     * @return Flat array of 5 * (order / 2) SOS coefficients
     */
    public static float[] butterworthHighPass(double samplingFreq, double cutoffFreq, int order) {
        return nativeButterworthHighPass(samplingFreq, cutoffFreq, order);
    }

    // Native declarations

    private static native float[] nativeLowPass(double gain, double samplingFreq, double cutoffFreq, double transitionWidth, int windowType);
    private static native float[] nativeHighPass(double gain, double samplingFreq, double cutoffFreq, double transitionWidth, int windowType);
    private static native float[] nativeBandPass(double gain, double samplingFreq, double lowCutoff, double highCutoff, double transitionWidth, int windowType);
    private static native float[] nativeBandReject(double gain, double samplingFreq, double lowCutoff, double highCutoff, double transitionWidth, int windowType);
    private static native float[] nativeRootRaisedCosine(double gain, double samplingFreq, double symbolRate, double excessBw, int ntaps);
    private static native float[] nativeRaisedCosine(double gain, double samplingFreq, double symbolRate, double excessBw, int ntaps);
    private static native float[] nativeGaussian(double gain, double samplingFreq, double symbolRate, double bt, int ntaps);
    private static native float[] nativeHilbert(int ntaps, int windowType);
    private static native float[] nativeBiquadLowPass(double samplingFreq, double cutoffFreq, double q);
    private static native float[] nativeBiquadHighPass(double samplingFreq, double cutoffFreq, double q);
    private static native float[] nativeBiquadBandPass(double samplingFreq, double centerFreq, double q);
    private static native float[] nativeBiquadNotch(double samplingFreq, double centerFreq, double q);
    private static native float[] nativeBiquadPeaking(double samplingFreq, double centerFreq, double q, double gainDb);
    private static native float[] nativeBiquadLowShelf(double samplingFreq, double cutoffFreq, double q, double gainDb);
    private static native float[] nativeBiquadHighShelf(double samplingFreq, double cutoffFreq, double q, double gainDb);
    private static native float[] nativeButterworthLowPass(double samplingFreq, double cutoffFreq, int order);
    private static native float[] nativeButterworthHighPass(double samplingFreq, double cutoffFreq, int order);
}
