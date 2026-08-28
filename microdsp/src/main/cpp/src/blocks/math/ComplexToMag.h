#ifndef COMPLEX_TO_MAG_H
#define COMPLEX_TO_MAG_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class ComplexToMag
 * @brief DSP block that computes the magnitude (absolute value) of a complex IQ stream.
 *
 * Input: COMPLEX_FLOAT (or COMPLEX_DOUBLE)
 * Output: FLOAT (or DOUBLE)
 * y[n] = |x[n]| = sqrt(I[n]^2 + Q[n]^2)
 */
class ComplexToMag : public Block {
public:
    /**
     * @brief Create a new ComplexToMag block
     * @param type Input complex data type (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Unique block name
     */
    ComplexToMag(DataType type = DataType::COMPLEX_FLOAT, const std::string& name = "complex_to_mag");

    virtual ~ComplexToMag();

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

#endif // COMPLEX_TO_MAG_H
