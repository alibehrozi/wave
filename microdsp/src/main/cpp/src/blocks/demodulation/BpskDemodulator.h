#ifndef BPSK_DEMODULATOR_H
#define BPSK_DEMODULATOR_H

#include "Demodulator.h"

/**
 * @class BpskDemodulator
 * @brief Binary Phase Shift Keying (BPSK) demodulation block.
 *
 * Takes COMPLEX_FLOAT IQ samples and produces FLOAT symbols (0.0 or 1.0).
 * Note: Assumes carrier and timing recovery is already performed.
 */
class BpskDemodulator : public Demodulator {
public:
    /**
     * @brief Create a new BpskDemodulator
     * @param name Unique block name
     */
    explicit BpskDemodulator(const std::string& name = "bpsk_demod")
        : BpskDemodulator(0.05f, name) {}

    /**
     * @brief Create a new BpskDemodulator
     * @param loop_bandwidth Costas loop bandwidth (e.g. 0.05)
     * @param name Unique block name
     */
    BpskDemodulator(float loop_bandwidth, const std::string& name);
    virtual ~BpskDemodulator();
    void work() override;
    void reset() override;

    void set_loop_bandwidth(float bw);

private:
    float alpha_{0.05f};
    float beta_{0.001f};
    float phase_{0.0f};
    float freq_{0.0f};

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<std::complex<float>> in_buf_;
    std::vector<float> out_buf_;
};

#endif // BPSK_DEMODULATOR_H
