#include "FilterDesign.h"
#include <algorithm>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Helper for Bessel function I0 for Kaiser window
static double bessel_i0(double x) {
    double sum = 1.0;
    double term = 1.0;
    double half_x = x / 2.0;
    for (int k = 1; k < 25; ++k) {
        term *= (half_x / k) * (half_x / k);
        sum += term;
        if (term < 1e-12 * sum) break;
    }
    return sum;
}

std::vector<float> FilterDesign::low_pass(double gain, double sampling_freq, double cutoff_freq,
                                         double transition_width, WindowType window_type) {
    int ntaps = compute_ntaps(sampling_freq, transition_width, window_type);
    if (ntaps % 2 == 0) ntaps++;

    std::vector<float> taps(ntaps);
    double m = (ntaps - 1) / 2.0;
    double w_c = 2.0 * M_PI * cutoff_freq / sampling_freq;

    for (int n = 0; n < ntaps; ++n) {
        double val;
        if (std::abs(n - m) < 1e-9) {
            val = w_c / M_PI;
        } else {
            val = std::sin(w_c * (n - m)) / (M_PI * (n - m));
        }
        taps[n] = static_cast<float>(val * compute_window(window_type, n, ntaps) * gain);
    }

    return taps;
}

std::vector<float> FilterDesign::high_pass(double gain, double sampling_freq, double cutoff_freq,
                                          double transition_width, WindowType window_type) {
    std::vector<float> lp = low_pass(1.0, sampling_freq, cutoff_freq, transition_width, window_type);
    int ntaps = static_cast<int>(lp.size());
    std::vector<float> taps(ntaps);
    int m = (ntaps - 1) / 2;

    for (int n = 0; n < ntaps; ++n) {
        taps[n] = -lp[n];
    }
    taps[m] += 1.0f;

    for (int n = 0; n < ntaps; ++n) {
        taps[n] *= static_cast<float>(gain);
    }

    return taps;
}

std::vector<float> FilterDesign::band_pass(double gain, double sampling_freq, double low_cutoff,
                                         double high_cutoff, double transition_width,
                                         WindowType window_type) {
    int ntaps = compute_ntaps(sampling_freq, transition_width, window_type);
    if (ntaps % 2 == 0) ntaps++;

    std::vector<float> taps(ntaps);
    double m = (ntaps - 1) / 2.0;
    double w1 = 2.0 * M_PI * low_cutoff / sampling_freq;
    double w2 = 2.0 * M_PI * high_cutoff / sampling_freq;

    for (int n = 0; n < ntaps; ++n) {
        double val;
        if (std::abs(n - m) < 1e-9) {
            val = (w2 - w1) / M_PI;
        } else {
            val = (std::sin(w2 * (n - m)) - std::sin(w1 * (n - m))) / (M_PI * (n - m));
        }
        taps[n] = static_cast<float>(val * compute_window(window_type, n, ntaps) * gain);
    }

    return taps;
}

std::vector<float> FilterDesign::band_reject(double gain, double sampling_freq, double low_cutoff,
                                           double high_cutoff, double transition_width,
                                           WindowType window_type) {
    std::vector<float> bp = band_pass(1.0, sampling_freq, low_cutoff, high_cutoff, transition_width, window_type);
    int ntaps = static_cast<int>(bp.size());
    std::vector<float> taps(ntaps);
    int m = (ntaps - 1) / 2;

    for (int n = 0; n < ntaps; ++n) {
        taps[n] = -bp[n];
    }
    taps[m] += 1.0f;

    for (int n = 0; n < ntaps; ++n) {
        taps[n] *= static_cast<float>(gain);
    }

    return taps;
}

std::vector<float> FilterDesign::root_raised_cosine(double gain, double sampling_freq, double symbol_rate,
                                                   double excess_bw, int ntaps) {
    if (ntaps % 2 == 0) ntaps++;
    std::vector<float> taps(ntaps);
    double sps = sampling_freq / symbol_rate;
    double m = (ntaps - 1) / 2.0;
    double beta = excess_bw;

    double energy = 0.0;

    for (int n = 0; n < ntaps; ++n) {
        double t = (n - m) / sps;
        double val;

        if (std::abs(t) < 1e-9) {
            val = 1.0 - beta + 4.0 * beta / M_PI;
        } else if (std::abs(4.0 * beta * t - 1.0) < 1e-6 || std::abs(4.0 * beta * t + 1.0) < 1e-6) {
            val = (beta / std::sqrt(2.0)) * (((1.0 + 2.0 / M_PI) * std::sin(M_PI / (4.0 * beta))) +
                                            ((1.0 - 2.0 / M_PI) * std::cos(M_PI / (4.0 * beta))));
        } else {
            double denom = M_PI * t * (1.0 - 16.0 * beta * beta * t * t);
            double num = std::sin(M_PI * t * (1.0 - beta)) + 4.0 * beta * t * std::cos(M_PI * t * (1.0 + beta));
            val = num / denom;
        }

        taps[n] = static_cast<float>(val);
        energy += val * val;
    }

    // Normalize unit energy and apply gain
    if (energy > 0.0) {
        float norm = static_cast<float>(gain / std::sqrt(energy));
        for (int n = 0; n < ntaps; ++n) {
            taps[n] *= norm;
        }
    }

    return taps;
}

std::vector<float> FilterDesign::raised_cosine(double gain, double sampling_freq, double symbol_rate,
                                             double excess_bw, int ntaps) {
    if (ntaps % 2 == 0) ntaps++;
    std::vector<float> taps(ntaps);
    double sps = sampling_freq / symbol_rate;
    double m = (ntaps - 1) / 2.0;
    double beta = excess_bw;

    for (int n = 0; n < ntaps; ++n) {
        double t = (n - m) / sps;
        double val;

        if (std::abs(t) < 1e-9) {
            val = 1.0;
        } else if (std::abs(2.0 * beta * t - 1.0) < 1e-6 || std::abs(2.0 * beta * t + 1.0) < 1e-6) {
            val = (M_PI / 4.0) * (std::sin(M_PI * t) / (M_PI * t));
        } else {
            double sinc = std::sin(M_PI * t) / (M_PI * t);
            double cos_term = std::cos(M_PI * beta * t);
            double denom = 1.0 - 4.0 * beta * beta * t * t;
            val = sinc * (cos_term / denom);
        }

        taps[n] = static_cast<float>(val * gain);
    }

    return taps;
}

std::vector<float> FilterDesign::gaussian(double gain, double sampling_freq, double symbol_rate,
                                        double bt, int ntaps) {
    if (ntaps % 2 == 0) ntaps++;
    std::vector<float> taps(ntaps);
    double sps = sampling_freq / symbol_rate;
    double m = (ntaps - 1) / 2.0;
    double alpha = std::sqrt(std::log(2.0)) / (std::sqrt(2.0) * bt);

    double sum = 0.0;
    for (int n = 0; n < ntaps; ++n) {
        double t = (n - m) / sps;
        double val = (std::sqrt(M_PI) / alpha) * std::exp(-std::pow(M_PI * t / alpha, 2.0));
        taps[n] = static_cast<float>(val);
        sum += val;
    }

    if (sum > 0.0) {
        float norm = static_cast<float>(gain / sum);
        for (int n = 0; n < ntaps; ++n) {
            taps[n] *= norm;
        }
    }

    return taps;
}

std::vector<float> FilterDesign::hilbert(int ntaps, WindowType window_type) {
    if (ntaps % 2 == 0) ntaps++;
    std::vector<float> taps(ntaps, 0.0f);
    int m = (ntaps - 1) / 2;

    for (int i = 0; i < ntaps; ++i) {
        int k = i - m;
        if (k % 2 != 0) { // odd terms
            double val = 2.0 / (M_PI * k);
            taps[i] = static_cast<float>(val * compute_window(window_type, i, ntaps));
        }
    }

    return taps;
}

// ==========================================
// IIR Biquad Functions
// ==========================================

BiquadCoeffs FilterDesign::biquad_low_pass(double sampling_freq, double cutoff_freq, double q) {
    if (q <= 0.0) q = 0.70710678;
    double w0 = 2.0 * M_PI * cutoff_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double alpha = sin_w0 / (2.0 * q);

    double b0 = (1.0 - cos_w0) / 2.0;
    double b1 = 1.0 - cos_w0;
    double b2 = (1.0 - cos_w0) / 2.0;
    double a0 = 1.0 + alpha;
    double a1 = -2.0 * cos_w0;
    double a2 = 1.0 - alpha;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

BiquadCoeffs FilterDesign::biquad_high_pass(double sampling_freq, double cutoff_freq, double q) {
    if (q <= 0.0) q = 0.70710678;
    double w0 = 2.0 * M_PI * cutoff_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double alpha = sin_w0 / (2.0 * q);

    double b0 = (1.0 + cos_w0) / 2.0;
    double b1 = -(1.0 + cos_w0);
    double b2 = (1.0 + cos_w0) / 2.0;
    double a0 = 1.0 + alpha;
    double a1 = -2.0 * cos_w0;
    double a2 = 1.0 - alpha;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

BiquadCoeffs FilterDesign::biquad_band_pass(double sampling_freq, double center_freq, double q) {
    if (q <= 0.0) q = 1.0;
    double w0 = 2.0 * M_PI * center_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double alpha = sin_w0 / (2.0 * q);

    double b0 = sin_w0 / 2.0;
    double b1 = 0.0;
    double b2 = -sin_w0 / 2.0;
    double a0 = 1.0 + alpha;
    double a1 = -2.0 * cos_w0;
    double a2 = 1.0 - alpha;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

BiquadCoeffs FilterDesign::biquad_notch(double sampling_freq, double center_freq, double q) {
    if (q <= 0.0) q = 10.0;
    double w0 = 2.0 * M_PI * center_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double alpha = sin_w0 / (2.0 * q);

    double b0 = 1.0;
    double b1 = -2.0 * cos_w0;
    double b2 = 1.0;
    double a0 = 1.0 + alpha;
    double a1 = -2.0 * cos_w0;
    double a2 = 1.0 - alpha;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

BiquadCoeffs FilterDesign::biquad_peaking(double sampling_freq, double center_freq, double q, double gain_db) {
    if (q <= 0.0) q = 1.0;
    double w0 = 2.0 * M_PI * center_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double a = std::pow(10.0, gain_db / 40.0);
    double alpha = sin_w0 / (2.0 * q);

    double b0 = 1.0 + alpha * a;
    double b1 = -2.0 * cos_w0;
    double b2 = 1.0 - alpha * a;
    double a0 = 1.0 + alpha / a;
    double a1 = -2.0 * cos_w0;
    double a2 = 1.0 - alpha / a;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

BiquadCoeffs FilterDesign::biquad_low_shelf(double sampling_freq, double cutoff_freq, double q, double gain_db) {
    if (q <= 0.0) q = 0.70710678;
    double w0 = 2.0 * M_PI * cutoff_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double a = std::pow(10.0, gain_db / 40.0);
    double alpha = sin_w0 / (2.0 * q);
    double two_sqrt_a_alpha = 2.0 * std::sqrt(a) * alpha;

    double b0 = a * ((a + 1.0) - (a - 1.0) * cos_w0 + two_sqrt_a_alpha);
    double b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos_w0);
    double b2 = a * ((a + 1.0) - (a - 1.0) * cos_w0 - two_sqrt_a_alpha);
    double a0 = (a + 1.0) + (a - 1.0) * cos_w0 + two_sqrt_a_alpha;
    double a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos_w0);
    double a2 = (a + 1.0) + (a - 1.0) * cos_w0 - two_sqrt_a_alpha;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

BiquadCoeffs FilterDesign::biquad_high_shelf(double sampling_freq, double cutoff_freq, double q, double gain_db) {
    if (q <= 0.0) q = 0.70710678;
    double w0 = 2.0 * M_PI * cutoff_freq / sampling_freq;
    double cos_w0 = std::cos(w0);
    double sin_w0 = std::sin(w0);
    double a = std::pow(10.0, gain_db / 40.0);
    double alpha = sin_w0 / (2.0 * q);
    double two_sqrt_a_alpha = 2.0 * std::sqrt(a) * alpha;

    double b0 = a * ((a + 1.0) + (a - 1.0) * cos_w0 + two_sqrt_a_alpha);
    double b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cos_w0);
    double b2 = a * ((a + 1.0) + (a - 1.0) * cos_w0 - two_sqrt_a_alpha);
    double a0 = (a + 1.0) - (a - 1.0) * cos_w0 + two_sqrt_a_alpha;
    double a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cos_w0);
    double a2 = (a + 1.0) - (a - 1.0) * cos_w0 - two_sqrt_a_alpha;

    BiquadCoeffs c;
    c.b0 = static_cast<float>(b0 / a0);
    c.b1 = static_cast<float>(b1 / a0);
    c.b2 = static_cast<float>(b2 / a0);
    c.a1 = static_cast<float>(a1 / a0);
    c.a2 = static_cast<float>(a2 / a0);
    return c;
}

std::vector<BiquadCoeffs> FilterDesign::butterworth_low_pass(double sampling_freq, double cutoff_freq, int order) {
    if (order < 2) order = 2;
    int num_sections = (order + 1) / 2;
    std::vector<BiquadCoeffs> sos;
    sos.reserve(num_sections);

    for (int k = 1; k <= num_sections; ++k) {
        double theta = (2.0 * k - 1.0) * M_PI / (2.0 * order);
        double q_k = 1.0 / (2.0 * std::cos(theta));
        if (q_k < 0.5) q_k = 0.5;
        sos.push_back(biquad_low_pass(sampling_freq, cutoff_freq, q_k));
    }
    return sos;
}

std::vector<BiquadCoeffs> FilterDesign::butterworth_high_pass(double sampling_freq, double cutoff_freq, int order) {
    if (order < 2) order = 2;
    int num_sections = (order + 1) / 2;
    std::vector<BiquadCoeffs> sos;
    sos.reserve(num_sections);

    for (int k = 1; k <= num_sections; ++k) {
        double theta = (2.0 * k - 1.0) * M_PI / (2.0 * order);
        double q_k = 1.0 / (2.0 * std::cos(theta));
        if (q_k < 0.5) q_k = 0.5;
        sos.push_back(biquad_high_pass(sampling_freq, cutoff_freq, q_k));
    }
    return sos;
}

std::vector<float> FilterDesign::sos_to_flat_vector(const std::vector<BiquadCoeffs>& sos) {
    std::vector<float> flat;
    flat.reserve(sos.size() * 5);
    for (const auto& c : sos) {
        flat.push_back(c.b0);
        flat.push_back(c.b1);
        flat.push_back(c.b2);
        flat.push_back(c.a1);
        flat.push_back(c.a2);
    }
    return flat;
}

double FilterDesign::compute_window(WindowType type, int n, int ntaps) {
    if (ntaps <= 1) return 1.0;
    double arg = 2.0 * M_PI * n / (ntaps - 1);

    switch (type) {
        case WindowType::HAMMING:
            return 0.54 - 0.46 * std::cos(arg);
        case WindowType::HANN:
            return 0.5 * (1.0 - std::cos(arg));
        case WindowType::BLACKMAN:
            return 0.42 - 0.5 * std::cos(arg) + 0.08 * std::cos(2.0 * arg);
        case WindowType::BLACKMAN_HARRIS:
            return 0.35875 - 0.48829 * std::cos(arg) + 0.14128 * std::cos(2.0 * arg) - 0.01168 * std::cos(3.0 * arg);
        case WindowType::BARTLETT:
            return 1.0 - 2.0 * std::abs(n - (ntaps - 1) / 2.0) / (ntaps - 1);
        case WindowType::FLAT_TOP:
            return 0.21557895 - 0.41663158 * std::cos(arg) + 0.277263158 * std::cos(2.0 * arg) -
                   0.083578947 * std::cos(3.0 * arg) + 0.006947368 * std::cos(4.0 * arg);
        case WindowType::KAISER: {
            double beta = 6.76; // ~60 dB stopband attenuation
            double alpha = (ntaps - 1) / 2.0;
            double r = (n - alpha) / alpha;
            if (std::abs(r) > 1.0) r = 1.0;
            return bessel_i0(beta * std::sqrt(1.0 - r * r)) / bessel_i0(beta);
        }
        case WindowType::RECTANGULAR:
        default:
            return 1.0;
    }
}

int FilterDesign::compute_ntaps(double sampling_freq, double transition_width, WindowType window_type) {
    double a_stop = 53.0; // Default for Hamming
    switch (window_type) {
        case WindowType::HAMMING: a_stop = 53.0; break;
        case WindowType::HANN: a_stop = 44.0; break;
        case WindowType::BLACKMAN: a_stop = 74.0; break;
        case WindowType::BLACKMAN_HARRIS: a_stop = 92.0; break;
        case WindowType::BARTLETT: a_stop = 26.0; break;
        case WindowType::FLAT_TOP: a_stop = 93.0; break;
        case WindowType::KAISER: a_stop = 60.0; break;
        case WindowType::RECTANGULAR: a_stop = 21.0; break;
    }

    if (transition_width <= 0.0) transition_width = sampling_freq * 0.05;
    int ntaps = static_cast<int>(std::ceil(a_stop * sampling_freq / (22.0 * transition_width)));
    if (ntaps < 3) ntaps = 3;
    return ntaps;
}
