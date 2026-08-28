#include "WfmDemodulator.h"
#include <cmath>
#include <vector>
#include <algorithm>

constexpr size_t MAX_INPUT_WFM_DEMOD_CHUNK = 4096;

WfmDemodulator::WfmDemodulator(double sample_rate, double tau, const std::string& name)
    : Demodulator(name), sample_rate_(sample_rate), tau_(tau), last_output_(0.0f), last_sample_(0.0f, 0.0f) {
    in_buf_.resize(MAX_INPUT_WFM_DEMOD_CHUNK);
    out_buf_.resize(MAX_INPUT_WFM_DEMOD_CHUNK);
    update_alpha();
}

WfmDemodulator::~WfmDemodulator() {
    stop();
}

void WfmDemodulator::update_alpha() {
    alpha_ = std::exp(-1.0 / (sample_rate_ * tau_));
}

void WfmDemodulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    last_output_ = 0.0f;
    last_sample_ = std::complex<float>(0.0f, 0.0f);
}

void WfmDemodulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_WFM_DEMOD_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < nitems; ++i) {
            // Quadrature demodulation
            std::complex<float> product = in_buf_[i] * std::conj(last_sample_);
            float raw_fm = std::arg(product);
            last_sample_ = in_buf_[i];

            // De-emphasis filter: y[n] = (1-alpha)*x[n] + alpha*y[n-1]
            float de_emphasized = static_cast<float>((1.0 - alpha_) * raw_fm + alpha_ * last_output_);
            out_buf_[i] = de_emphasized;
            last_output_ = de_emphasized;
        }
        out->write(out_buf_.data(), nitems);
    }
}
