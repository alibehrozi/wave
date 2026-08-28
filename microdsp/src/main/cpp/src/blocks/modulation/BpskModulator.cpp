#include "BpskModulator.h"
#include <vector>
#include <complex>
#include <algorithm>

constexpr size_t MAX_INPUT_BPSK_CHUNK = 4096;

BpskModulator::BpskModulator(const std::string& name) : Modulator(name) {
    in_buf_.resize(MAX_INPUT_BPSK_CHUNK);
    out_buf_.resize(MAX_INPUT_BPSK_CHUNK);
}

BpskModulator::~BpskModulator() {
    stop();
}

void BpskModulator::reset() {
    // Basic BPSK modulator is stateless
}

void BpskModulator::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_INPUT_BPSK_CHUNK);

    if (in->read(in_buf_.data(), nitems)) {
        for (size_t i = 0; i < nitems; ++i) {
            out_buf_[i] = std::complex<float>((in_buf_[i] >= 0.5f) ? 1.0f : -1.0f, 0.0f);
        }
        out->write(out_buf_.data(), nitems);
    }
}
