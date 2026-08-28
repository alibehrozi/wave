#include "FskModulator.h"
#include <complex>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_INPUT_FSK_CHUNK = 4096;

FskModulator::FskModulator(double sample_rate, double freq_mark, double freq_space, const std::string& name)
    : Modulator(name), sample_rate_(sample_rate), freq_mark_(freq_mark), freq_space_(freq_space), phase_(0.0) {
    in_buf_.resize(MAX_INPUT_FSK_CHUNK);
    out_buf_.resize(MAX_INPUT_FSK_CHUNK);
}

FskModulator::~FskModulator() {
    stop();
}

void FskModulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    phase_ = 0.0;
}

void FskModulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_FSK_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < nitems; ++i) {
            double freq = (in_buf_[i] >= 0.5f) ? freq_mark_ : freq_space_;
            double phase_inc = 2.0 * M_PI * freq / sample_rate_;

            out_buf_[i] = std::polar(1.0f, static_cast<float>(phase_));

            phase_ += phase_inc;
            if (phase_ >= 2.0 * M_PI) phase_ -= 2.0 * M_PI;
        }
        out->write(out_buf_.data(), nitems);
    }
}
