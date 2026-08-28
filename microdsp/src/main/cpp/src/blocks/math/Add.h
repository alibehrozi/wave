#ifndef ADD_H
#define ADD_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class Add
 * @brief DSP block that performs element-wise addition of multiple input streams.
 *
 * y[n] = in0[n] + in1[n] + ... + in{N-1}[n]
 */
class Add : public Block {
public:
    /**
     * @brief Create a new Add block
     * @param type Data type to process (FLOAT, COMPLEX_FLOAT, DOUBLE, etc.)
     * @param num_inputs Number of input streams to add (minimum 1, typically 2+)
     * @param name Unique block name
     */
    Add(DataType type, size_t num_inputs = 2, const std::string& name = "add");

    virtual ~Add();

    void work() override;
    void reset() override;

    size_t get_num_inputs() const { return num_inputs_; }
    DataType get_data_type() const { return type_; }

private:
    DataType type_;
    size_t num_inputs_;

    // Pre-allocated buffers to prevent heap allocations during work()
    std::vector<std::vector<float>> bufs_f_;
    std::vector<std::vector<std::complex<float>>> bufs_cf_;
    std::vector<std::vector<double>> bufs_d_;
    std::vector<std::vector<std::complex<double>>> bufs_cd_;
    std::vector<std::vector<int32_t>> bufs_i32_;
    std::vector<std::vector<int16_t>> bufs_i16_;

    std::vector<float> out_f_;
    std::vector<std::complex<float>> out_cf_;
    std::vector<double> out_d_;
    std::vector<std::complex<double>> out_cd_;
    std::vector<int32_t> out_i32_;
    std::vector<int16_t> out_i16_;

    template<typename T>
    void process(const std::vector<std::vector<T>>& in_bufs, T* out, size_t nitems);
};

#endif // ADD_H
