#ifndef WFM_DEMODULATOR_H
#define WFM_DEMODULATOR_H

#include "Demodulator.h"
#include <complex>

/**
 * @class WfmDemodulator
 * @brief Wideband FM (WFM) demodulation block.
 *
 * Performs FM demodulation followed by a de-emphasis filter to recover
 * high-fidelity audio from broadcast FM signals.
 */
class WfmDemodulator : public Demodulator {
public:
    /**
     * @brief Create a new WfmDemodulator
     * @param sample_rate Audio sample rate
     * @param tau De-emphasis time constant (e.g. 75e-6 for US, 50e-6 for EU)
     * @param name Unique block name
     */
    WfmDemodulator(double sample_rate = 48000.0, double tau = 75e-6, const std::string& name = "wfm_demod");

    virtual ~WfmDemodulator();

    void work() override;
    void reset() override;

private:
    double sample_rate_;
    double tau_;
    double alpha_;
    float last_output_;
    std::complex<float> last_sample_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<std::complex<float>> in_buf_;
    std::vector<float> out_buf_;

    void update_alpha();
};

#endif // WFM_DEMODULATOR_H
