#include "SignalSource.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "SignalSource"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

SignalSource::SignalSource(DataType type, double sample_rate, double frequency, double amplitude, SignalType signal_type, const std::string& name)
    : Block(name), type_(type), sample_rate_(sample_rate), frequency_(frequency), amplitude_(amplitude), signal_type_(signal_type), phase_(0.0) {

    add_output_port("out", type);
    update_phase_inc();
    LOGI("SignalSource created: %s, type: %d, freq: %f, amp: %f, signal_type: %d", name.c_str(), static_cast<int>(type), frequency, amplitude, static_cast<int>(signal_type));
}

SignalSource::~SignalSource() {
    stop();
}

void SignalSource::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    phase_ = 0.0;
}

bool SignalSource::is_ready() {
    return is_active();
}

void SignalSource::work() {
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

void SignalSource::set_frequency(double frequency) {
    std::lock_guard<std::mutex> lock(mutex_);
    frequency_ = frequency;
    update_phase_inc();
}

void SignalSource::set_amplitude(double amplitude) {
    std::lock_guard<std::mutex> lock(mutex_);
    amplitude_ = amplitude;
}

void SignalSource::set_signal_type(SignalType signal_type) {
    std::lock_guard<std::mutex> lock(mutex_);
    signal_type_ = signal_type;
}

void SignalSource::update_phase_inc() {
    phase_inc_ = 2.0 * M_PI * frequency_ / sample_rate_;
}

template<typename T>
void SignalSource::generate(T* output, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);

    for (size_t i = 0; i < nitems; ++i) {
        double val = 0.0;
        double current_phase = phase_ / (2.0 * M_PI);
        if (current_phase > 1.0) current_phase -= std::floor(current_phase);

        switch (signal_type_) {
            case SignalType::SINE:
                val = std::sin(phase_);
                break;
            case SignalType::SQUARE:
                val = (std::sin(phase_) >= 0) ? 1.0 : -1.0;
                break;
            case SignalType::TRIANGLE:
                val = 2.0 * std::abs(2.0 * (current_phase - std::floor(current_phase + 0.5))) - 1.0;
                break;
            case SignalType::SAWTOOTH:
                val = 2.0 * (current_phase - std::floor(current_phase + 0.5));
                break;
        }

        val *= amplitude_;

        if constexpr (std::is_same_v<T, std::complex<float>>) {
            output[i] = std::complex<float>(static_cast<float>(val), 0.0f);
        } else {
            output[i] = static_cast<T>(val);
        }

        phase_ += phase_inc_;
        if (phase_ >= 2.0 * M_PI) phase_ -= 2.0 * M_PI;
    }
}
