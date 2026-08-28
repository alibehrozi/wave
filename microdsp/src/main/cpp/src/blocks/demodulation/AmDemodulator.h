#ifndef AM_DEMODULATOR_H
#define AM_DEMODULATOR_H

#include "Demodulator.h"

/**
 * @class AmDemodulator
 * @brief Amplitude Modulation (AM) demodulation block.
 *
 * Performs envelope detection on a COMPLEX_FLOAT IQ signal to recover
 * the original FLOAT audio signal.
 * Formula: output = abs(IQ)
 */
class AmDemodulator : public Demodulator {
public:
    /**
     * @brief Create a new AmDemodulator
     * @param name Unique block name
     */
    explicit AmDemodulator(const std::string& name = "am_demod")
        : AmDemodulator(true, name) {}

    /**
     * @brief Create a new AmDemodulator
     * @param dc_block Whether to filter out carrier DC offset
     * @param name Unique block name
     */
    AmDemodulator(bool dc_block, const std::string& name);

    virtual ~AmDemodulator();

    void work() override;
    void reset() override;

    void set_dc_blocking(bool enable);

private:
    bool dc_block_;
    float last_in_{0.0f};
    float last_out_{0.0f};
    float alpha_{0.995f};

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<std::complex<float>> in_buf_;
    std::vector<float> out_buf_;
};

#endif // AM_DEMODULATOR_H
