#ifndef IIR_FILTER_H
#define IIR_FILTER_H

#include "../../core/Block.h"
#include "FilterDesign.h"
#include <vector>
#include <complex>
#include <mutex>

/**
 * DSP block that performs Infinite Impulse Response (IIR) filtering
 * using cascaded Biquad Second-Order Sections (SOS) in Transposed Direct Form II.
 *
 * Supports both DataType::FLOAT (real) and DataType::COMPLEX_FLOAT (complex IQ).
 */
class IirFilter : public Block {
public:
    /**
     * Create an IirFilter with flat SOS coefficients [b0, b1, b2, a1, a2, ...].
     */
    IirFilter(DataType type, const std::vector<float>& sos_coeffs, const std::string& name = "iir_filter");

    /**
     * Create an IirFilter from a vector of BiquadCoeffs.
     */
    IirFilter(DataType type, const std::vector<BiquadCoeffs>& sos, const std::string& name = "iir_filter");

    virtual ~IirFilter();

    void work() override;
    void reset() override;
    bool is_ready() override;

    /**
     * Update filter coefficients dynamically at runtime.
     */
    void set_coefficients(const std::vector<float>& sos_coeffs);
    void set_coefficients(const std::vector<BiquadCoeffs>& sos);

private:
    struct SectionStateReal {
        float s1 = 0.0f;
        float s2 = 0.0f;
    };

    struct SectionStateComplex {
        std::complex<float> s1 = {0.0f, 0.0f};
        std::complex<float> s2 = {0.0f, 0.0f};
    };

    DataType type_;
    std::vector<BiquadCoeffs> sos_;
    std::vector<SectionStateReal> state_real_;
    std::vector<SectionStateComplex> state_complex_;

    std::vector<float> in_buf_f_;
    std::vector<float> out_buf_f_;
    std::vector<std::complex<float>> in_buf_cf_;
    std::vector<std::complex<float>> out_buf_cf_;

    std::mutex mutex_;

    void init_state();
    void process_real(const float* input, float* output, size_t nitems);
    void process_complex(const std::complex<float>* input, std::complex<float>* output, size_t nitems);
};

#endif // IIR_FILTER_H
