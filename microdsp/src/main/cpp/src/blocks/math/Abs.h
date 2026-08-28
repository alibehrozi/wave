#ifndef ABS_H
#define ABS_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class Abs
 * @brief DSP block that computes the absolute value of real signals.
 *
 * y[n] = |x[n]|
 */
class Abs : public Block {
public:
    /**
     * @brief Create a new Abs block
     * @param type Data type to process (FLOAT, DOUBLE, INT32, SHORT)
     * @param name Unique block name
     */
    Abs(DataType type, const std::string& name = "abs");

    virtual ~Abs();

    void work() override;
    void reset() override;

    DataType get_data_type() const { return type_; }

private:
    DataType type_;

    std::vector<float> buf_f_;
    std::vector<double> buf_d_;
    std::vector<int32_t> buf_i32_;
    std::vector<int16_t> buf_i16_;

    template<typename T>
    void process(T* data, size_t nitems);
};

#endif // ABS_H
