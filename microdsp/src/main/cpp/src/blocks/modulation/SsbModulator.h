#ifndef SSB_MODULATOR_H
#define SSB_MODULATOR_H

#include "Modulator.h"
#include <vector>
#include <complex>

/**
 * @class SsbModulator
 * @brief Single Sideband (SSB) modulation block.
 *
 * Takes a FLOAT audio signal and produces a COMPLEX_FLOAT IQ analytic signal
 * representing either the Upper Sideband (USB) or Lower Sideband (LSB).
 * Uses a Hilbert transform FIR filter.
 */
class SsbModulator : public Modulator {
public:
    /**
     * @enum Sideband
     * @brief Specifies which sideband to generate
     */
    enum class Sideband {
        LSB = 0, /**< Lower Sideband */
        USB = 1  /**< Upper Sideband */
    };

    /**
     * @brief Create a new SsbModulator
     * @param sideband USB or LSB
     * @param ntaps Number of taps for the Hilbert filter (must be odd)
     * @param name Unique block name
     */
    SsbModulator(Sideband sideband = Sideband::USB, int ntaps = 65, const std::string& name = "ssb_mod");

    virtual ~SsbModulator();

    void work() override;
    void reset() override;

    void set_sideband(Sideband sideband);

private:
    Sideband sideband_;
    std::vector<float> taps_;
    std::vector<float> history_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> in_buf_;
    std::vector<std::complex<float>> out_buf_;

    void design_taps(int ntaps);
};

#endif // SSB_MODULATOR_H
