#ifndef SQUELCH_BLOCK_H
#define SQUELCH_BLOCK_H

#include "core/Block.h"
#include <atomic>

/**
 * @class SquelchBlock
 * @brief Gates the signal based on average power level.
 *
 * If the input power is below the threshold, the output is zeroed out.
 * This prevents loud background noise when no signal is being received.
 */
class SquelchBlock : public Block {
public:
    SquelchBlock(DataType type, float threshold_db = -40.0f, const std::string& name = "squelch");

    void work() override;
    void reset() override;

    void set_threshold(float db);
    bool is_open() const { return open_.load(); }

private:
    DataType type_;
    float threshold_linear_;
    std::atomic<bool> open_{false};
    float alpha_ = 0.01f; // Smoothing factor for power estimation
    float current_power_ = 0.0f;

    std::vector<uint8_t> work_buf_;
};

#endif // SQUELCH_BLOCK_H
