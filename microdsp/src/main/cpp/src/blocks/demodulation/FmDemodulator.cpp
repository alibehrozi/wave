#include "FmDemodulator.h"
#include <cmath>
#include <vector>
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "FmDemodulator"

constexpr size_t MAX_INPUT_FM_DEMOD_CHUNK = 4096;

FmDemodulator::FmDemodulator(float gain, const std::string& name)
    : Demodulator(name), gain_(gain), last_sample_(0.0f, 0.0f) {
    in_buf_.resize(MAX_INPUT_FM_DEMOD_CHUNK);
    out_buf_.resize(MAX_INPUT_FM_DEMOD_CHUNK);
}

FmDemodulator::~FmDemodulator() {
    stop();
}

void FmDemodulator::set_gain(float gain) {
    std::lock_guard<std::mutex> lock(mutex_);
    gain_ = gain;
}

void FmDemodulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    last_sample_ = std::complex<float>(0.0f, 0.0f);
}

void FmDemodulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_FM_DEMOD_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < nitems; ++i) {
            // Quadrature demodulation: angle(current * conj(last))
            std::complex<float> product = in_buf_[i] * std::conj(last_sample_);
            out_buf_[i] = gain_ * std::arg(product);
            last_sample_ = in_buf_[i];
        }
        out->write(out_buf_.data(), nitems);
    }
}
