#ifndef LOG10_H
#define LOG10_H

#include "../../core/Block.h"
#include <vector>

/**
 * @class Log10
 * @brief DSP block that computes scaled base-10 logarithm with offset: y[n] = n * log10(max(x[n], eps)) + k
 *
 * Commonly used for decibel (dB) scaling:
 * - Power to dB: n = 10.0, k = 0.0
 * - Amplitude to dB: n = 20.0, k = 0.0
 */
class Log10 : public Block {
public:
    /**
     * @brief Create a new Log10 block
     * @param type Data type to process (FLOAT or DOUBLE)
     * @param n Multiplier factor (default 1.0f, use 10.0f for power dB, 20.0f for amplitude dB)
     * @param k Offset added to the result (default 0.0f)
     * @param eps Minimum floor to prevent log(0) (default 1e-12f)
     * @param name Unique block name
     */
    Log10(DataType type = DataType::FLOAT, float n = 1.0f, float k = 0.0f, float eps = 1e-12f, const std::string& name = "log10");

    virtual ~Log10();

    void work() override;
    void reset() override;

    void set_parameters(float n, float k);
    void set_n(float n);
    void set_k(float k);
    void set_eps(float eps);

    float get_n() const { return n_; }
    float get_k() const { return k_; }
    float get_eps() const { return eps_; }
    DataType get_data_type() const { return type_; }

private:
    DataType type_;
    float n_;
    float k_;
    float eps_;

    std::vector<float> buf_f_;
    std::vector<double> buf_d_;

    template<typename T>
    void process(T* data, size_t nitems);
};

#endif // LOG10_H
