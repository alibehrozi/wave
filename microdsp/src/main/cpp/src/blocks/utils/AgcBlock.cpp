#include "AgcBlock.h"
#include <cmath>
#include <algorithm>
#include <vector>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "AgcBlock"

constexpr size_t MAX_AGC_CHUNK = 4096;

AgcBlock::AgcBlock(DataType type, float target_level, float attack_rate, float decay_rate, float max_gain, const std::string& name)
    : Block(name), type_(type), target_level_(target_level), attack_rate_(attack_rate), decay_rate_(decay_rate), max_gain_(max_gain), current_gain_(1.0f) {

    add_input_port("in", type);
    add_output_port("out", type);

    if (type == DataType::FLOAT) {
        buf_f_.resize(MAX_AGC_CHUNK);
    } else if (type == DataType::COMPLEX_FLOAT) {
        buf_cf_.resize(MAX_AGC_CHUNK);
    }
}

AgcBlock::~AgcBlock() {
    stop();
}

void AgcBlock::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    current_gain_ = 1.0f;
}

void AgcBlock::set_target_level(float level) {
    std::lock_guard<std::mutex> lock(mutex_);
    target_level_ = level;
}

void AgcBlock::set_attack_rate(float rate) {
    std::lock_guard<std::mutex> lock(mutex_);
    attack_rate_ = rate;
}

void AgcBlock::set_decay_rate(float rate) {
    std::lock_guard<std::mutex> lock(mutex_);
    decay_rate_ = rate;
}

void AgcBlock::set_max_gain(float gain) {
    std::lock_guard<std::mutex> lock(mutex_);
    max_gain_ = gain;
}

void AgcBlock::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_AGC_CHUNK);

    if (type_ == DataType::FLOAT) {
        if (in->read(buf_f_.data(), nitems)) {
            process(buf_f_.data(), buf_f_.data(), nitems);
            out->write(buf_f_.data(), nitems);
        }
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        if (in->read(buf_cf_.data(), nitems)) {
            process(buf_cf_.data(), buf_cf_.data(), nitems);
            out->write(buf_cf_.data(), nitems);
        }
    }
}

template<typename T>
void AgcBlock::process(T* input, T* output, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);

    for (size_t i = 0; i < nitems; ++i) {
        float power;
        if constexpr (std::is_same_v<T, std::complex<float>>) {
            power = std::abs(input[i]);
        } else {
            power = std::abs(static_cast<float>(input[i]));
        }

        output[i] = input[i] * current_gain_;

        // Adjust gain based on power
        if (power * current_gain_ > target_level_) {
            current_gain_ -= decay_rate_ * (power * current_gain_ - target_level_);
        } else {
            current_gain_ += attack_rate_ * (target_level_ - power * current_gain_);
        }

        current_gain_ = std::clamp(current_gain_, 0.0f, max_gain_);
    }
}
