#ifndef NOISE_SOURCE_H
#define NOISE_SOURCE_H

#include "../../core/Block.h"
#include <random>

/**
 * Supported noise types
 */
enum class NoiseType {
    GAUSSIAN = 0,
    UNIFORM = 1
};

/**
 * DSP block that generates random noise
 */
class NoiseSource : public Block {
public:
    /**
     * Create a new NoiseSource
     * @param type Data type to output (FLOAT or COMPLEX_FLOAT)
     * @param amplitude Noise amplitude
     * @param noise_type Type of noise to generate
     * @param name Block name
     */
    NoiseSource(DataType type, float amplitude = 1.0f, NoiseType noise_type = NoiseType::GAUSSIAN, const std::string& name = "noise_source");

    virtual ~NoiseSource();

    /**
     * Perform noise generation
     */
    void work() override;

    /**
     * Check if block is ready to work
     */
    bool is_ready() override;

    /**
     * Set noise amplitude
     */
    void set_amplitude(float amplitude);

    /**
     * Set noise type
     */
    void set_noise_type(NoiseType noise_type);

private:
    DataType type_;
    float amplitude_;
    NoiseType noise_type_;

    std::mt19937 generator_;
    std::normal_distribution<float> gaussian_dist_;
    std::uniform_real_distribution<float> uniform_dist_;

    /**
     * Internal generation helper
     */
    template<typename T>
    void generate(T* output, size_t nitems);
};

#endif // NOISE_SOURCE_H
