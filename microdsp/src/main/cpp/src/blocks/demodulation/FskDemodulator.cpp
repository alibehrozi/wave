#include "FskDemodulator.h"
#include <cmath>
#include <vector>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_INPUT_FSK_DEMOD_CHUNK = 4096;

FskDemodulator::FskDemodulator(double sample_rate, double freq_mark, double freq_space, const std::string& name)
    : Demodulator(name), sample_rate_(sample_rate), freq_mark_(freq_mark), freq_space_(freq_space), last_sample_(0.0f, 0.0f) {
    in_buf_.resize(MAX_INPUT_FSK_DEMOD_CHUNK);
    out_buf_.resize(MAX_INPUT_FSK_DEMOD_CHUNK);
}

FskDemodulator::~FskDemodulator() {
    stop();
}

void FskDemodulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    last_sample_ = std::complex<float>(0.0f, 0.0f);
}

void FskDemodulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_FSK_DEMOD_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        double threshold = (freq_mark_ + freq_space_) / 2.0;

        for (size_t i = 0; i < nitems; ++i) {
            std::complex<float> product = in_buf_[i] * std::conj(last_sample_);
            double freq = std::arg(product) * sample_rate_ / (2.0 * M_PI);
            last_sample_ = in_buf_[i];

            out_buf_[i] = (freq >= threshold) ? 1.0f : 0.0f;
        }
        out->write(out_buf_.data(), nitems);
    }
}
