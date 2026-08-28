#ifndef COMPLEX_TO_MAG_SQUARED_H
#define COMPLEX_TO_MAG_SQUARED_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class ComplexToMagSquared
 * @brief DSP block that computes the magnitude squared (power) of a complex IQ stream.
 *
 * Input: COMPLEX_FLOAT (or COMPLEX_DOUBLE)
 * Output: FLOAT (or DOUBLE)
 * y[n] = |x[n]|^2 = I[n]^2 + Q[n]^2
 */
class ComplexToMagSquared : public Block {
public:
    /**
     * @brief Create a new ComplexToMagSquared block
     * @param type Input complex data type (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Unique block name
     */
    ComplexToMagSquared(DataType type = DataType::COMPLEX_FLOAT, const std::string& name = "complex_to_mag_squared");

    virtual ~ComplexToMagSquared();

    void work() override;
    void reset() override;

    DataType get_data_type() const { return in_type_; }

private:
    DataType in_type_;
    DataType out_type_;

    std::vector<std::complex<float>> in_cf_;
    std::vector<float> out_f_;

    std::vector<std::complex<double>> in_cd_;
    std::vector<double> out_d_;
};

#endif // COMPLEX_TO_MAG_SQUARED_H
