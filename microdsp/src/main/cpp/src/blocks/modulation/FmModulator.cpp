#include "FmModulator.h"
#include <complex>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "FmModulator"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_INPUT_FM_CHUNK = 4096;

FmModulator::FmModulator(float sensitivity, const std::string& name)
    : Modulator(name), sensitivity_(sensitivity), phase_(0.0f) {
    in_buf_.resize(MAX_INPUT_FM_CHUNK);
    out_buf_.resize(MAX_INPUT_FM_CHUNK);
}

FmModulator::~FmModulator() {
    stop();
}

void FmModulator::set_sensitivity(float sensitivity) {
    std::lock_guard<std::mutex> lock(mutex_);
    sensitivity_ = sensitivity;
}

void FmModulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    phase_ = 0.0f;
}

void FmModulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_FM_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < nitems; ++i) {
            // FM: exp(j * integral(sensitivity * audio))
            phase_ += sensitivity_ * in_buf_[i];
            if (phase_ > static_cast<float>(M_PI)) phase_ -= 2.0f * static_cast<float>(M_PI);
            if (phase_ < -static_cast<float>(M_PI)) phase_ += 2.0f * static_cast<float>(M_PI);

            out_buf_[i] = std::polar(1.0f, phase_);
        }
        out->write(out_buf_.data(), nitems);
    }
}
