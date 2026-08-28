#include "BpskDemodulator.h"
#include <vector>
#include <complex>
#include <algorithm>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_INPUT_BPSK_DEMOD_CHUNK = 4096;

BpskDemodulator::BpskDemodulator(float loop_bandwidth, const std::string& name)
    : Demodulator(name), phase_(0.0f), freq_(0.0f) {
    set_loop_bandwidth(loop_bandwidth);
    in_buf_.resize(MAX_INPUT_BPSK_DEMOD_CHUNK);
    out_buf_.resize(MAX_INPUT_BPSK_DEMOD_CHUNK);
}

BpskDemodulator::~BpskDemodulator() {
    stop();
}

void BpskDemodulator::set_loop_bandwidth(float bw) {
    std::lock_guard<std::mutex> lock(mutex_);
    // Standard critically damped 2nd order loop filter coefficients
    float damping = 0.7071f;
    float denom = 1.0f + 2.0f * damping * bw + bw * bw;
    alpha_ = (4.0f * damping * bw) / denom;
    beta_ = (4.0f * bw * bw) / denom;
}

void BpskDemodulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    phase_ = 0.0f;
    freq_ = 0.0f;
}

void BpskDemodulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_BPSK_DEMOD_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < nitems; ++i) {
            // 1. De-rotate input sample by current estimated phase
            std::complex<float> rot = std::polar(1.0f, -phase_);
            std::complex<float> sample = in_buf_[i] * rot;

            // 2. Decision
            float bit = (sample.real() >= 0.0f) ? 1.0f : 0.0f;
            out_buf_[i] = bit;

            // 3. BPSK Costas phase error detector: sgn(I) * Q
            float sgn_i = (sample.real() >= 0.0f) ? 1.0f : -1.0f;
            float error = sgn_i * sample.imag();

            // 4. Update frequency and phase estimates
            freq_ += beta_ * error;
            phase_ += alpha_ * error + freq_;

            // Wrap phase to [-pi, pi]
            while (phase_ > static_cast<float>(M_PI)) phase_ -= 2.0f * static_cast<float>(M_PI);
            while (phase_ < -static_cast<float>(M_PI)) phase_ += 2.0f * static_cast<float>(M_PI);
        }
        out->write(out_buf_.data(), nitems);
    }
}
