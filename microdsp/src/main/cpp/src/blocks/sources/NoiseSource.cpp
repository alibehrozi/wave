#include "NoiseSource.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "NoiseSource"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

NoiseSource::NoiseSource(DataType type, float amplitude, NoiseType noise_type, const std::string& name)
    : Block(name), type_(type), amplitude_(amplitude), noise_type_(noise_type),
      generator_(std::random_device{}()), gaussian_dist_(0.0f, 1.0f), uniform_dist_(-1.0f, 1.0f) {

    add_output_port("out", type);
    LOGI("NoiseSource created: %s, type: %d, noise_type: %d", name.c_str(), static_cast<int>(type), static_cast<int>(noise_type));
}

NoiseSource::~NoiseSource() {
    stop();
}

bool NoiseSource::is_ready() {
    return is_active();
}

void NoiseSource::work() {
    if (!is_active()) return;

    Port* out = get_output_port(0);
    if (!out) return;

    // Generate in chunks
    constexpr size_t nitems = 1024;

    if (type_ == DataType::FLOAT) {
        std::vector<float> buffer(nitems);
        generate(buffer.data(), nitems);
        out->write(buffer.data(), nitems);
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        std::vector<std::complex<float>> buffer(nitems);
        generate(buffer.data(), nitems);
        out->write(buffer.data(), nitems);
    }
}

void NoiseSource::set_amplitude(float amplitude) {
    std::lock_guard<std::mutex> lock(mutex_);
    amplitude_ = amplitude;
}

void NoiseSource::set_noise_type(NoiseType noise_type) {
    std::lock_guard<std::mutex> lock(mutex_);
    noise_type_ = noise_type;
}

template<typename T>
void NoiseSource::generate(T* output, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);

    for (size_t i = 0; i < nitems; ++i) {
        float val;
        if (noise_type_ == NoiseType::GAUSSIAN) {
            val = gaussian_dist_(generator_) * amplitude_;
        } else {
            val = uniform_dist_(generator_) * amplitude_;
        }

        if constexpr (std::is_same_v<T, std::complex<float>>) {
            float val_q;
            if (noise_type_ == NoiseType::GAUSSIAN) {
                val_q = gaussian_dist_(generator_) * amplitude_;
            } else {
                val_q = uniform_dist_(generator_) * amplitude_;
            }
            output[i] = std::complex<float>(val, val_q);
        } else {
            output[i] = static_cast<T>(val);
        }
    }
}
