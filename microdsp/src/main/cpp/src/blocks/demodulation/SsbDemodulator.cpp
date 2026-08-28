#include "SsbDemodulator.h"
#include <cmath>
#include <vector>
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "SsbDemodulator"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_INPUT_SSB_DEMOD_CHUNK = 4096;

SsbDemodulator::SsbDemodulator(Sideband sideband, const std::string& name)
    : Demodulator(name), sideband_(sideband) {

    design_taps(65);

    size_t nt = taps_.size();
    history_i_.assign(nt + MAX_INPUT_SSB_DEMOD_CHUNK, 0.0f);
    history_q_.assign(nt + MAX_INPUT_SSB_DEMOD_CHUNK, 0.0f);
    in_buf_.resize(MAX_INPUT_SSB_DEMOD_CHUNK);
    out_buf_.resize(MAX_INPUT_SSB_DEMOD_CHUNK);
}

SsbDemodulator::~SsbDemodulator() {
    stop();
}

void SsbDemodulator::design_taps(int ntaps) {
    if (ntaps % 2 == 0) ntaps++;
    taps_.resize(ntaps);
    int m = (ntaps - 1) / 2;

    for (int i = 0; i < ntaps; ++i) {
        int n = i - m;
        if (n == 0 || n % 2 == 0) {
            taps_[i] = 0.0f;
        } else {
            taps_[i] = static_cast<float>(2.0 / (M_PI * n));
        }
        double window = 0.54 - 0.46 * std::cos(2.0 * M_PI * i / (ntaps - 1));
        taps_[i] *= static_cast<float>(window);
    }
}

void SsbDemodulator::set_sideband(Sideband sideband) {
    std::lock_guard<std::mutex> lock(mutex_);
    sideband_ = sideband;
}

void SsbDemodulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    std::fill(history_i_.begin(), history_i_.end(), 0.0f);
    std::fill(history_q_.begin(), history_q_.end(), 0.0f);
}

void SsbDemodulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_SSB_DEMOD_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);

        size_t ntaps = taps_.size();
        size_t m = (ntaps - 1) / 2;
        size_t prev_samples = ntaps - 1;

        // Separate I and Q into history buffers
        for (size_t i = 0; i < nitems; ++i) {
            history_i_[prev_samples + i] = in_buf_[i].real();
            history_q_[prev_samples + i] = in_buf_[i].imag();
        }

        for (size_t i = 0; i < nitems; ++i) {
            float i_delayed = history_i_[i + prev_samples - m];

            // Hilbert transform of Q component
            float h_q = 0.0f;
            const float* q_ptr = &history_q_[i];
            for (size_t j = 0; j < ntaps; ++j) {
                if (taps_[j] != 0.0f) {
                    h_q += q_ptr[ntaps - 1 - j] * taps_[j];
                }
            }

            // Phasing method: USB = (I - H{Q}) * 0.5, LSB = (I + H{Q}) * 0.5
            if (sideband_ == Sideband::USB) {
                out_buf_[i] = (i_delayed - h_q) * 0.5f;
            } else {
                out_buf_[i] = (i_delayed + h_q) * 0.5f;
            }
        }

        // Shift history buffers for next chunk
        std::copy(history_i_.begin() + nitems, history_i_.begin() + nitems + prev_samples, history_i_.begin());
        std::copy(history_q_.begin() + nitems, history_q_.begin() + nitems + prev_samples, history_q_.begin());

        out->write(out_buf_.data(), nitems);
    }
}
