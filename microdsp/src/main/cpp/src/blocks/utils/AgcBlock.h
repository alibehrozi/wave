#ifndef AGC_BLOCK_H
#define AGC_BLOCK_H

#include "../../core/Block.h"

/**
 * @class AgcBlock
 * @brief Automatic Gain Control (AGC) block.
 *
 * Maintains a constant output level by adjusting the gain based on the estimated
 * input power. Supports both FLOAT and COMPLEX_FLOAT types.
 */
class AgcBlock : public Block {
public:
    /**
     * @brief Create a new AgcBlock
     * @param type Data type to process (FLOAT or COMPLEX_FLOAT)
     * @param target_level Target output amplitude (0.0 to 1.0)
     * @param attack_rate Rate at which gain increases when signal is low
     * @param decay_rate Rate at which gain decreases when signal is high
     * @param max_gain Maximum allowable gain factor
     * @param name Unique block name
     */
    AgcBlock(DataType type, float target_level = 0.5f, float attack_rate = 1e-3f, float decay_rate = 1e-4f, float max_gain = 1000.0f, const std::string& name = "agc");

    virtual ~AgcBlock();

    void work() override;
    void reset() override;

    void set_target_level(float level);
    void set_attack_rate(float rate);
    void set_decay_rate(float rate);
    void set_max_gain(float gain);

private:
    DataType type_;
    float target_level_;
    float attack_rate_;
    float decay_rate_;
    float max_gain_;
    float current_gain_;

    // Pre-allocated buffers to avoid allocations in work()
    std::vector<float> buf_f_;
    std::vector<std::complex<float>> buf_cf_;

    template<typename T>
    void process(T* input, T* output, size_t nitems);
};

#endif // AGC_BLOCK_H
