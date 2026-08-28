#ifndef COMPLEX_TO_REAL_IMAG_H
#define COMPLEX_TO_REAL_IMAG_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class ComplexToRealImag
 * @brief DSP block that splits a complex IQ stream into separate Real (I) and Imaginary (Q) float streams.
 *
 * Input: in (COMPLEX_FLOAT or COMPLEX_DOUBLE)
 * Output 0: real (FLOAT or DOUBLE)
 * Output 1: imag (FLOAT or DOUBLE)
 */
class ComplexToRealImag : public Block {
public:
    /**
     * @brief Create a new ComplexToRealImag block
     * @param type Input complex data type (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Unique block name
     */
    ComplexToRealImag(DataType type = DataType::COMPLEX_FLOAT, const std::string& name = "complex_to_real_imag");

    virtual ~ComplexToRealImag();

    void work() override;
    void reset() override;

    DataType get_data_type() const { return in_type_; }

private:
    DataType in_type_;
    DataType out_type_;

    std::vector<std::complex<float>> in_cf_;
    std::vector<float> real_f_, imag_f_;

    std::vector<std::complex<double>> in_cd_;
    std::vector<double> real_d_, imag_d_;
};

#endif // COMPLEX_TO_REAL_IMAG_H
