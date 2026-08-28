#ifndef AM_MODULATOR_H
#define AM_MODULATOR_H

#include "Modulator.h"

/**
 * @class AmModulator
 * @brief Amplitude Modulation (AM) block.
 *
 * Takes a FLOAT audio signal as input and produces a COMPLEX_FLOAT IQ signal.
 * The output is calculated as: IQ = (1.0 + audio) + 0j.
 */
class AmModulator : public Modulator {
public:
    /**
     * @brief Construct a new AmModulator
     * @param name Unique name for this block
     */
    AmModulator(const std::string& name = "am_mod");

    virtual ~AmModulator();

    void work() override;
    void reset() override;

private:
    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> in_buf_;
    std::vector<std::complex<float>> out_buf_;
};

#endif // AM_MODULATOR_H
