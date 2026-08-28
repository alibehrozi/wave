#include "AmModulator.h"
#include <complex>
#include <vector>
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "AmModulator"

constexpr size_t MAX_INPUT_AM_MOD_CHUNK = 4096;

AmModulator::AmModulator(const std::string& name) : Modulator(name) {
    in_buf_.resize(MAX_INPUT_AM_MOD_CHUNK);
    out_buf_.resize(MAX_INPUT_AM_MOD_CHUNK);
}

AmModulator::~AmModulator() {
    stop();
}

void AmModulator::reset() {
    // Basic AM modulator is stateless
}

void AmModulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_AM_MOD_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        for (size_t i = 0; i < nitems; ++i) {
            // Basic AM: 1.0 + audio
            out_buf_[i] = std::complex<float>(1.0f + in_buf_[i], 0.0f);
        }
        out->write(out_buf_.data(), nitems);
    }
}
