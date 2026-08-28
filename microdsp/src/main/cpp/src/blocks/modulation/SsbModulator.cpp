#include "SsbModulator.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

constexpr size_t MAX_INPUT_SSB_CHUNK = 4096;

SsbModulator::SsbModulator(Sideband sideband, int ntaps, const std::string& name)
    : Modulator(name), sideband_(sideband) {

    design_taps(ntaps);

    size_t nt = taps_.size();
    history_.assign(nt + MAX_INPUT_SSB_CHUNK, 0.0f);
    in_buf_.resize(MAX_INPUT_SSB_CHUNK);
    out_buf_.resize(MAX_INPUT_SSB_CHUNK);
}

SsbModulator::~SsbModulator() {
    stop();
}

void SsbModulator::set_sideband(Sideband sideband) {
    std::lock_guard<std::mutex> lock(mutex_);
    sideband_ = sideband;
}

void SsbModulator::design_taps(int ntaps) {
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

void SsbModulator::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    std::fill(history_.begin(), history_.end(), 0.0f);
}

void SsbModulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_SSB_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        std::lock_guard<std::mutex> lock(mutex_);

        size_t ntaps = taps_.size();
        size_t m = (ntaps - 1) / 2;
        size_t prev_samples = ntaps - 1;

        // Copy new input into history
        std::copy(in_buf_.begin(), in_buf_.begin() + static_cast<ptrdiff_t>(nitems), history_.begin() + static_cast<ptrdiff_t>(prev_samples));

        for (size_t i = 0; i < nitems; ++i) {
            // Real part is the center sample (delayed by m)
            float h_real = history_[i + prev_samples - m];

            // Imaginary part is the Hilbert transform
            float h_imag = 0.0f;
            const float* hist_ptr = &history_[i];
            for (size_t j = 0; j < ntaps; ++j) {
                h_imag += hist_ptr[j] * taps_[ntaps - 1 - j];
            }

            if (sideband_ == Sideband::USB) {
                out_buf_[i] = std::complex<float>(h_real, h_imag);
            } else {
                out_buf_[i] = std::complex<float>(h_real, -h_imag);
            }
        }

        // Update history
        std::copy(history_.begin() + static_cast<ptrdiff_t>(nitems), history_.begin() + static_cast<ptrdiff_t>(nitems + prev_samples), history_.begin());

        out->write(out_buf_.data(), nitems);
    }
}
