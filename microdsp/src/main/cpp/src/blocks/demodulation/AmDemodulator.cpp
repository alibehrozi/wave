#include "AmDemodulator.h"
#include <cmath>
#include <vector>
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "AmDemodulator"

constexpr size_t MAX_INPUT_AM_CHUNK = 4096;

AmDemodulator::AmDemodulator(bool dc_block, const std::string& name)
    : Demodulator(name), dc_block_(dc_block), last_in_(0.0f), last_out_(0.0f), alpha_(0.995f) {
    in_buf_.resize(MAX_INPUT_AM_CHUNK);
    out_buf_.resize(MAX_INPUT_AM_CHUNK);
}

AmDemodulator::~AmDemodulator() {
    stop();
}

void AmDemodulator::set_dc_blocking(bool enable) {
    std::lock_guard<std::mutex> lock(mutex_);
    dc_block_ = enable;
}

void AmDemodulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    last_in_ = 0.0f;
    last_out_ = 0.0f;
}

void AmDemodulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_AM_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < nitems; ++i) {
            // Envelope detection: abs(sample)
            float env = std::abs(in_buf_[i]);

            if (dc_block_) {
                // DC blocking single-pole filter: y[n] = x[n] - x[n-1] + alpha * y[n-1]
                float y = env - last_in_ + alpha_ * last_out_;
                last_in_ = env;
                last_out_ = y;
                out_buf_[i] = y;
            } else {
                out_buf_[i] = env;
            }
        }
        out->write(out_buf_.data(), nitems);
    }
}
