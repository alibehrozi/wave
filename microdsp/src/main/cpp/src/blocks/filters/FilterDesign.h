#ifndef FILTER_DESIGN_H
#define FILTER_DESIGN_H

#include <vector>
#include <cmath>
#include <string>

/**
 * Structure representing a Biquad Second-Order Section (SOS).
 * Transfer function: H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
 */
struct BiquadCoeffs {
    float b0 = 1.0f;
    float b1 = 0.0f;
    float b2 = 0.0f;
    float a1 = 0.0f;
    float a2 = 0.0f;
};

/**
 * Complete utility class for designing FIR and IIR (Biquad/SOS) filter coefficients.
 */
class FilterDesign {
public:
    enum class WindowType {
        HAMMING = 0,
        HANN = 1,
        BLACKMAN = 2,
        RECTANGULAR = 3,
        BLACKMAN_HARRIS = 4,
        BARTLETT = 5,
        FLAT_TOP = 6,
        KAISER = 7
    };

    /**
     * Design a low-pass FIR filter using the windowed sinc method.
     */
    static std::vector<float> low_pass(double gain, double sampling_freq, double cutoff_freq,
                                       double transition_width, WindowType window_type = WindowType::HAMMING);

    /**
     * Design a high-pass FIR filter using spectral inversion.
     */
    static std::vector<float> high_pass(double gain, double sampling_freq, double cutoff_freq,
                                        double transition_width, WindowType window_type = WindowType::HAMMING);

    /**
     * Design a band-pass FIR filter.
     */
    static std::vector<float> band_pass(double gain, double sampling_freq, double low_cutoff,
                                        double high_cutoff, double transition_width,
                                        WindowType window_type = WindowType::HAMMING);

    /**
     * Design a band-reject (notch) FIR filter.
     */
    static std::vector<float> band_reject(double gain, double sampling_freq, double low_cutoff,
                                          double high_cutoff, double transition_width,
                                          WindowType window_type = WindowType::HAMMING);

    /**
     * Design a Root Raised Cosine (RRC) pulse shaping FIR filter.
     */
    static std::vector<float> root_raised_cosine(double gain, double sampling_freq, double symbol_rate,
                                                  double excess_bw, int ntaps);

    /**
     * Design a Raised Cosine (RC) FIR filter.
     */
    static std::vector<float> raised_cosine(double gain, double sampling_freq, double symbol_rate,
                                            double excess_bw, int ntaps);

    /**
     * Design a Gaussian pulse shaping FIR filter (e.g. for GMSK).
     */
    static std::vector<float> gaussian(double gain, double sampling_freq, double symbol_rate,
                                       double bt, int ntaps);

    /**
     * Design a Hilbert Transform FIR filter.
     */
    static std::vector<float> hilbert(int ntaps, WindowType window_type = WindowType::HAMMING);

    /**
     * Single Biquad Low-Pass Filter (RBJ Audio EQ).
     */
    static BiquadCoeffs biquad_low_pass(double sampling_freq, double cutoff_freq, double q = 0.70710678);

    /**
     * Single Biquad High-Pass Filter.
     */
    static BiquadCoeffs biquad_high_pass(double sampling_freq, double cutoff_freq, double q = 0.70710678);

    /**
     * Single Biquad Band-Pass Filter (constant skirt gain).
     */
    static BiquadCoeffs biquad_band_pass(double sampling_freq, double center_freq, double q = 1.0);

    /**
     * Single Biquad Notch / Band-Reject Filter.
     */
    static BiquadCoeffs biquad_notch(double sampling_freq, double center_freq, double q = 10.0);

    /**
     * Single Biquad Peaking EQ Filter.
     */
    static BiquadCoeffs biquad_peaking(double sampling_freq, double center_freq, double q, double gain_db);

    /**
     * Single Biquad Low-Shelf Filter.
     */
    static BiquadCoeffs biquad_low_shelf(double sampling_freq, double cutoff_freq, double q, double gain_db);

    /**
     * Single Biquad High-Shelf Filter.
     */
    static BiquadCoeffs biquad_high_shelf(double sampling_freq, double cutoff_freq, double q, double gain_db);

    /**
     * Cascaded Butterworth Low-Pass Filter (arbitrary even order 2, 4, 6, 8...).
     */
    static std::vector<BiquadCoeffs> butterworth_low_pass(double sampling_freq, double cutoff_freq, int order = 4);

    /**
     * Cascaded Butterworth High-Pass Filter.
     */
    static std::vector<BiquadCoeffs> butterworth_high_pass(double sampling_freq, double cutoff_freq, int order = 4);

    /**
     * Window function generator.
     */
    static double compute_window(WindowType type, int n, int ntaps);

    /**
     * Estimate number of taps for a given transition bandwidth and window.
     */
    static int compute_ntaps(double sampling_freq, double transition_width, WindowType window_type);

    /**
     * Helper to serialize a vector of BiquadCoeffs to flat float array [b0, b1, b2, a1, a2, ...]
     */
    static std::vector<float> sos_to_flat_vector(const std::vector<BiquadCoeffs>& sos);
};

#endif // FILTER_DESIGN_H
