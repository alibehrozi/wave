#ifndef HILBERT_FILTER_H
#define HILBERT_FILTER_H

#include "../../core/Block.h"
#include <vector>

/**
 * DSP block that performs a Hilbert transform.
 * Converts a real signal (FLOAT) into an analytic signal (COMPLEX_FLOAT).
 */
class HilbertFilter : public Block {
public:
    /**
     * Create a new HilbertFilter
     * @param ntaps Number of taps for the internal FIR filter (should be odd)
     * @param name Block name
     */
    HilbertFilter(int ntaps = 65, const std::string& name = "hilbert_filter");

    virtual ~HilbertFilter();

    void work() override;
    void reset() override;
    bool is_ready() override;

private:
    std::vector<float> taps_;
    std::vector<float> history_;

    // Pre-allocated scratch buffers
    std::vector<float> in_buf_;
    std::vector<std::complex<float>> out_buf_;

    void design_taps(int ntaps);
};

#endif // HILBERT_FILTER_H
