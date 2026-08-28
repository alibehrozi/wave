#ifndef DIVIDE_H
#define DIVIDE_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class Divide
 * @brief DSP block that performs element-wise division between two input streams.
 *
 * y[n] = in0[n] / (in1[n] + eps)
 */
class Divide : public Block {
public:
    /**
     * @brief Create a new Divide block
     * @param type Data type to process
     * @param eps Epsilon value to prevent division by zero (default 1e-12)
     * @param name Unique block name
     */
    Divide(DataType type, float eps = 1e-12f, const std::string& name = "divide");

    virtual ~Divide();

    void work() override;
    void reset() override;

    DataType get_data_type() const { return type_; }
    float get_eps() const { return eps_; }
    void set_eps(float eps) { eps_ = eps; }

private:
    DataType type_;
    float eps_;

    std::vector<float> in0_f_, in1_f_, out_f_;
    std::vector<std::complex<float>> in0_cf_, in1_cf_, out_cf_;
    std::vector<double> in0_d_, in1_d_, out_d_;
    std::vector<std::complex<double>> in0_cd_, in1_cd_, out_cd_;
    std::vector<int32_t> in0_i32_, in1_i32_, out_i32_;
    std::vector<int16_t> in0_i16_, in1_i16_, out_i16_;

    template<typename T>
    void process(const T* in0, const T* in1, T* out, size_t nitems);
};

#endif // DIVIDE_H
