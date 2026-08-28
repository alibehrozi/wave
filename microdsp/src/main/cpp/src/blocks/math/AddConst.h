#ifndef ADD_CONST_H
#define ADD_CONST_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class AddConst
 * @brief DSP block that adds a constant scalar or complex offset to the input stream.
 *
 * y[n] = x[n] + k
 */
class AddConst : public Block {
public:
    /**
     * @brief Create a new AddConst block with real constant
     * @param type Data type to process
     * @param constant Real constant to add
     * @param name Unique block name
     */
    AddConst(DataType type, float constant = 0.0f, const std::string& name = "add_const");

    /**
     * @brief Create a new AddConst block with complex constant
     * @param type Data type to process (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param const_real Real part of constant
     * @param const_imag Imaginary part of constant
     * @param name Unique block name
     */
    AddConst(DataType type, float const_real, float const_imag, const std::string& name = "add_const");

    virtual ~AddConst();

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

#endif // ADD_CONST_H
