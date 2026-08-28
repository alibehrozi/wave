#ifndef FM_DEMODULATOR_H
#define FM_DEMODULATOR_H

#include "Demodulator.h"
#include <complex>

/**
 * @class FmDemodulator
 * @brief Frequency Modulation (FM) demodulation block.
 *
 * Uses quadrature differentiation to recover the FLOAT audio signal
 * from a COMPLEX_FLOAT IQ signal.
 */
class FmDemodulator : public Demodulator {
public:
    /**
     * @brief Create a new FmDemodulator
     * @param gain Demodulation gain (deviation / frequency)
     * @param name Unique block name
     */
    FmDemodulator(float gain = 1.0f, const std::string& name = "fm_demod");

    virtual ~FmDemodulator();

    void work() override;
    void reset() override;

    void set_gain(float gain);

private:
    float gain_;
    std::complex<float> last_sample_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<std::complex<float>> in_buf_;
    std::vector<float> out_buf_;
};

#endif // FM_DEMODULATOR_H
