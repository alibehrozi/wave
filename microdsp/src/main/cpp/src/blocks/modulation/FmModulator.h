#ifndef FM_MODULATOR_H
#define FM_MODULATOR_H

#include "Modulator.h"
#include <cmath>

/**
 * @class FmModulator
 * @brief Frequency Modulation (FM) block.
 *
 * Takes a FLOAT audio signal as input and produces a COMPLEX_FLOAT IQ signal.
 * Modulation: exp(j * integral(sensitivity * audio))
 */
class FmModulator : public Modulator {
public:
    /**
     * @brief Create a new FmModulator
     * @param sensitivity Modulation sensitivity (deviation / max_input)
     * @param name Unique block name
     */
    FmModulator(float sensitivity = 1.0f, const std::string& name = "fm_mod");

    virtual ~FmModulator();

    void work() override;
    void reset() override;

    void set_sensitivity(float sensitivity);

private:
    float sensitivity_;
    float phase_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> in_buf_;
    std::vector<std::complex<float>> out_buf_;
};

#endif // FM_MODULATOR_H
