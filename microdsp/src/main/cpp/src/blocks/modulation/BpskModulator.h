#ifndef BPSK_MODULATOR_H
#define BPSK_MODULATOR_H

#include "Modulator.h"

/**
 * @class BpskModulator
 * @brief Binary Phase Shift Keying (BPSK) modulation block.
 *
 * Takes a FLOAT stream of symbols (0.0 or 1.0) and produces a COMPLEX_FLOAT
 * IQ signal where 1.0 maps to 1+0j and 0.0 maps to -1+0j.
 */
class BpskModulator : public Modulator {
public:
    /**
     * @brief Create a new BpskModulator
     * @param name Unique block name
     */
    BpskModulator(const std::string& name = "bpsk_mod");
    virtual ~BpskModulator();
    void work() override;
    void reset() override;

private:
    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> in_buf_;
    std::vector<std::complex<float>> out_buf_;
};

#endif // BPSK_MODULATOR_H
