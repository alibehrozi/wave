#ifndef FSK_DEMODULATOR_H
#define FSK_DEMODULATOR_H

#include "Demodulator.h"
#include <complex>

/**
 * @class FskDemodulator
 * @brief Frequency Shift Keying (FSK) demodulation block.
 *
 * Takes COMPLEX_FLOAT IQ samples and produces FLOAT symbols (0.0 or 1.0)
 * based on instantaneous frequency estimation.
 */
class FskDemodulator : public Demodulator {
public:
    /**
     * @brief Create a new FskDemodulator
     * @param sample_rate Sample rate in Hz
     * @param freq_mark Frequency for symbol 1.0 (Hz)
     * @param freq_space Frequency for symbol 0.0 (Hz)
     * @param name Unique block name
     */
    FskDemodulator(double sample_rate = 48000.0, double freq_mark = 2200.0, double freq_space = 1200.0, const std::string& name = "fsk_demod");

    virtual ~FskDemodulator();

    void work() override;
    void reset() override;

private:
    double sample_rate_;
    double freq_mark_;
    double freq_space_;
    std::complex<float> last_sample_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<std::complex<float>> in_buf_;
    std::vector<float> out_buf_;
};

#endif // FSK_DEMODULATOR_H
