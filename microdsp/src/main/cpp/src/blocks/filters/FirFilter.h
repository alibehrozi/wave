#ifndef FIR_FILTER_H
#define FIR_FILTER_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class FirFilter
 * @brief DSP block that performs Finite Impulse Response (FIR) filtering.
 *
 * This block supports decimation and interpolation, making it useful as a resampler.
 * It uses a linear history buffer and pre-allocated memory for high performance.
 */
class FirFilter : public Block {
public:
    /**
     * @brief Create a new FirFilter
     * @param type Data type to process (FLOAT or COMPLEX_FLOAT)
     * @param taps Filter coefficients
     * @param decimation Decimation factor (M)
     * @param interpolation Interpolation factor (L)
     * @param name Unique block name
     */
    FirFilter(DataType type, const std::vector<float>& taps, int decimation = 1, int interpolation = 1, const std::string& name = "fir_filter");

    virtual ~FirFilter();

    /**
     * Perform filtering
     */
    void work() override;

    /**
     * Check if block is ready to work
     */
    bool is_ready() override;

    /**
     * Set new filter taps
     */
    void set_taps(const std::vector<float>& taps);

    /**
     * Reset filter state
     */
    void reset() override;

private:
    DataType type_;
    std::vector<float> taps_;
    int decimation_;
    int interpolation_;
    int phase_acc_{0};

    // Polyphase filter banks (L phases)
    std::vector<std::vector<float>> taps_polyphase_;

    // Internal state for filtering
    std::vector<float> history_f_;
    std::vector<std::complex<float>> history_cf_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> in_buf_f_;
    std::vector<float> out_buf_f_;
    std::vector<std::complex<float>> in_buf_cf_;
    std::vector<std::complex<float>> out_buf_cf_;

    void build_polyphase_taps();

    template<typename T>
    size_t process_polyphase(const T* input, T* output, size_t nitems, std::vector<T>& history);
};

#endif // FIR_FILTER_H
