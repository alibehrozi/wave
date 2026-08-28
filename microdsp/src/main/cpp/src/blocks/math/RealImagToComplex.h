#ifndef REAL_IMAG_TO_COMPLEX_H
#define REAL_IMAG_TO_COMPLEX_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class RealImagToComplex
 * @brief DSP block that combines separate Real (I) and Imaginary (Q) float streams into a complex IQ stream.
 *
 * Input 0: real (FLOAT or DOUBLE)
 * Input 1: imag (FLOAT or DOUBLE)
 * Output: out (COMPLEX_FLOAT or COMPLEX_DOUBLE)
 */
class RealImagToComplex : public Block {
public:
    /**
     * @brief Create a new RealImagToComplex block
     * @param type Output complex data type (COMPLEX_FLOAT or COMPLEX_DOUBLE)
     * @param name Unique block name
     */
    RealImagToComplex(DataType type = DataType::COMPLEX_FLOAT, const std::string& name = "real_imag_to_complex");

    virtual ~RealImagToComplex();

    void work() override;
    void reset() override;

    DataType get_data_type() const { return out_type_; }

private:
    DataType in_type_;
    DataType out_type_;

    std::vector<float> real_f_, imag_f_;
    std::vector<std::complex<float>> out_cf_;

    std::vector<double> real_d_, imag_d_;
    std::vector<std::complex<double>> out_cd_;
};

#endif // REAL_IMAG_TO_COMPLEX_H
