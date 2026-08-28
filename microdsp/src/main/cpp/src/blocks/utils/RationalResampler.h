#ifndef RATIONAL_RESAMPLER_H
#define RATIONAL_RESAMPLER_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class RationalResampler
 * @brief DSP block for rational resampling (interpolation L then decimation M).
 *
 * Uses an internal FIR filter to prevent aliasing during decimation and
 * imaging during interpolation.
 */
class RationalResampler : public Block {
public:
    /**
     * @brief Create a new RationalResampler
     * @param type Data type to process (FLOAT or COMPLEX_FLOAT)
     * @param interpolation Interpolation factor (L)
     * @param decimation Decimation factor (M)
     * @param taps Anti-aliasing filter coefficients
     * @param name Unique block name
     */
    RationalResampler(DataType type, int interpolation, int decimation, const std::vector<float>& taps, const std::string& name = "rational_resampler");

    virtual ~RationalResampler();

    void work() override;
    void reset() override;
    bool is_ready() override;

private:
    DataType type_;
    int interpolation_;
    int decimation_;
    std::vector<float> taps_;
    int phase_acc_{0};

    // Polyphase filter banks (L phases)
    std::vector<std::vector<float>> taps_polyphase_;

    // Internal state
    std::vector<float> history_f_;
    std::vector<std::complex<float>> history_cf_;

    // Pre-allocated buffers
    std::vector<float> in_buf_f_;
    std::vector<float> out_buf_f_;
    std::vector<std::complex<float>> in_buf_cf_;
    std::vector<std::complex<float>> out_buf_cf_;

    void build_polyphase_taps();

    template<typename T>
    size_t process_polyphase(const T* input, T* output, size_t nitems, std::vector<T>& history);
};

#endif // RATIONAL_RESAMPLER_H
