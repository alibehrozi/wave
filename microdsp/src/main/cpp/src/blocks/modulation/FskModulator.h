#ifndef FSK_MODULATOR_H
#define FSK_MODULATOR_H

#include "Modulator.h"
#include <cmath>

/**
 * @class FskModulator
 * @brief Frequency Shift Keying (FSK) modulation block.
 *
 * Takes a FLOAT stream of symbols (0.0 or 1.0) and produces a COMPLEX_FLOAT
 * IQ signal alternating between Mark and Space frequencies.
 */
class FskModulator : public Modulator {
public:
    /**
     * @brief Create a new FskModulator
     * @param sample_rate Sample rate in Hz
     * @param freq_mark Frequency for symbol 1.0 (Hz)
     * @param freq_space Frequency for symbol 0.0 (Hz)
     * @param name Unique block name
     */
    FskModulator(double sample_rate = 48000.0, double freq_mark = 2200.0, double freq_space = 1200.0, const std::string& name = "fsk_mod");

    virtual ~FskModulator();

    void work() override;
    void reset() override;

private:
    double sample_rate_;
    double freq_mark_;
    double freq_space_;
    double phase_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> in_buf_;
    std::vector<std::complex<float>> out_buf_;
};

#endif // FSK_MODULATOR_H
