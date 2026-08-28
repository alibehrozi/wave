#ifndef MULTIPLY_CONST_H
#define MULTIPLY_CONST_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class MultiplyConst
 * @brief DSP block that multiplies the input stream by a scalar or complex constant factor (gain/attenuation).
 *
 * y[n] = x[n] * k
 */
class MultiplyConst : public Block {
public:
    /**
     * @brief Create a new MultiplyConst block with real constant factor
     * @param type Data type to process
     * @param constant Real constant factor
     * @param name Unique block name
     */
    MultiplyConst(DataType type, float constant = 1.0f, const std::string& name = "multiply_const");

    /**
     * @brief Create a new MultiplyConst block with complex constant factor
     * @param type Data type to process (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param const_real Real part of constant factor
     * @param const_imag Imaginary part of constant factor
     * @param name Unique block name
     */
    MultiplyConst(DataType type, float const_real, float const_imag, const std::string& name = "multiply_const");

    virtual ~MultiplyConst();

    void work() override;
    void reset() override;

    void set_constant(float constant);
    void set_constant(float const_real, float const_imag);
    float get_const_real() const { return const_real_; }
    float get_const_imag() const { return const_imag_; }

private:
    DataType type_;
    float const_real_;
    float const_imag_;

    std::vector<float> buf_f_;
    std::vector<std::complex<float>> buf_cf_;
    std::vector<double> buf_d_;
    std::vector<std::complex<double>> buf_cd_;
    std::vector<int32_t> buf_i32_;
    std::vector<int16_t> buf_i16_;

    template<typename T>
    void process(T* data, size_t nitems);
};

#endif // MULTIPLY_CONST_H
