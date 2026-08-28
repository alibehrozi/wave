#include "HilbertFilter.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_HILBERT_CHUNK = 4096;

HilbertFilter::HilbertFilter(int ntaps, const std::string& name)
    : Block(name) {

    add_input_port("in", DataType::FLOAT);
    add_output_port("out", DataType::COMPLEX_FLOAT);

    design_taps(ntaps);

    size_t nt = taps_.size();
    history_.assign(nt + MAX_HILBERT_CHUNK, 0.0f);
    in_buf_.resize(MAX_HILBERT_CHUNK);
    out_buf_.resize(MAX_HILBERT_CHUNK);
}

HilbertFilter::~HilbertFilter() {
    stop();
}

void HilbertFilter::design_taps(int ntaps) {
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
        // Apply Hamming window
        double window = 0.54 - 0.46 * std::cos(2.0 * M_PI * i / (ntaps - 1));
        taps_[i] *= static_cast<float>(window);
    }
}

void HilbertFilter::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    std::fill(history_.begin(), history_.end(), 0.0f);
}

bool HilbertFilter::is_ready() {
    Port* in = get_input_port(0);
    return is_active() && in && in->get_buffer() && in->get_buffer()->read_available() > 0;
}

void HilbertFilter::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_HILBERT_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);
        size_t ntaps = taps_.size();
        size_t m = (ntaps - 1) / 2;
        size_t prev_samples = ntaps - 1;

        // Copy input to history
        std::copy(in_buf_.begin(), in_buf_.begin() + nitems, history_.begin() + prev_samples);

        for (size_t i = 0; i < nitems; ++i) {
            float h_real = history_[i + prev_samples - m];
            float h_imag = 0.0f;
            const float* hist_ptr = &history_[i];

            for (size_t j = 0; j < ntaps; ++j) {
                if (taps_[j] != 0.0f) {
                    h_imag += hist_ptr[ntaps - 1 - j] * taps_[j];
                }
            }

            out_buf_[i] = std::complex<float>(h_real, h_imag);
        }

        // Shift history for next chunk
        std::copy(history_.begin() + nitems, history_.begin() + nitems + prev_samples, history_.begin());

        out->write(out_buf_.data(), nitems);
    }
}
