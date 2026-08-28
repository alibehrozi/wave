#ifndef CONJUGATE_H
#define CONJUGATE_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class Conjugate
 * @brief DSP block that computes the complex conjugate of an IQ stream.
 *
 * Input: in (COMPLEX_FLOAT or COMPLEX_DOUBLE)
 * Output: out (COMPLEX_FLOAT or COMPLEX_DOUBLE)
 * y[n] = conj(x[n]) = I[n] - j*Q[n]
 */
class Conjugate : public Block {
public:
    /**
     * @brief Create a new Conjugate block
     * @param type Data type to process (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Unique block name
     */
    Conjugate(DataType type = DataType::COMPLEX_FLOAT, const std::string& name = "conjugate");

    virtual ~Conjugate();

    void work() override;
    void reset() override;

    DataType get_data_type() const { return type_; }

private:
    DataType type_;

    std::vector<std::complex<float>> buf_cf_;
    std::vector<std::complex<double>> buf_cd_;
};

#endif // CONJUGATE_H
