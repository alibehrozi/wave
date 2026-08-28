#ifndef SSB_DEMODULATOR_H
#define SSB_DEMODULATOR_H

#include "Demodulator.h"
#include <complex>

/**
 * @class SsbDemodulator
 * @brief Single Sideband (SSB) demodulation block.
 *
 * Recovers a FLOAT audio signal from a COMPLEX_FLOAT IQ analytic signal.
 * Supports both USB and LSB.
 */
class SsbDemodulator : public Demodulator {
public:
    /**
     * @enum Sideband
     * @brief Specifies which sideband to demodulate
     */
    enum class Sideband {
        LSB = 0, /**< Lower Sideband */
        USB = 1  /**< Upper Sideband */
    };

    /**
     * @brief Create a new SsbDemodulator
     * @param sideband Sideband to demodulate (LSB or USB)
     * @param name Unique block name
     */
    SsbDemodulator(Sideband sideband = Sideband::USB, const std::string& name = "ssb_demod");

    virtual ~SsbDemodulator();

    void work() override;
    void reset() override;

    void set_sideband(Sideband sideband);

private:
    Sideband sideband_;

    std::vector<float> taps_;
    std::vector<float> history_i_;
    std::vector<float> history_q_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<std::complex<float>> in_buf_;
    std::vector<float> out_buf_;

    void design_taps(int ntaps = 65);
};

#endif // SSB_DEMODULATOR_H