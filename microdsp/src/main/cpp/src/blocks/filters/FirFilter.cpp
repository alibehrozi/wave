#include "FirFilter.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "FirFilter"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

constexpr size_t MAX_INPUT_CHUNK = 4096;

FirFilter::FirFilter(DataType type, const std::vector<float>& taps, int decimation, int interpolation, const std::string& name)
    : Block(name), type_(type), taps_(taps), decimation_(std::max(1, decimation)), interpolation_(std::max(1, interpolation)), phase_acc_(0) {

    add_input_port("in", type);
    add_output_port("out", type);

    build_polyphase_taps();

    size_t taps_per_phase = taps_polyphase_[0].size();
    if (type == DataType::FLOAT) {
        history_f_.assign(taps_per_phase + MAX_INPUT_CHUNK, 0.0f);
        in_buf_f_.resize(MAX_INPUT_CHUNK);
        out_buf_f_.resize((MAX_INPUT_CHUNK * interpolation_) / decimation_ + interpolation_ + 16);
    } else if (type == DataType::COMPLEX_FLOAT) {
        history_cf_.assign(taps_per_phase + MAX_INPUT_CHUNK, std::complex<float>(0.0f, 0.0f));
        in_buf_cf_.resize(MAX_INPUT_CHUNK);
        out_buf_cf_.resize((MAX_INPUT_CHUNK * interpolation_) / decimation_ + interpolation_ + 16);
    }

    LOGI("FirFilter created: %s, type: %d, taps: %zu, decim: %d, interp: %d, taps/phase: %zu",
         name.c_str(), static_cast<int>(type), taps_.size(), decimation_, interpolation_, taps_per_phase);
}

FirFilter::~FirFilter() {
    stop();
}

void FirFilter::build_polyphase_taps() {
    taps_polyphase_.assign(interpolation_, std::vector<float>());
    size_t ntaps = taps_.size();
    size_t taps_per_phase = (ntaps + interpolation_ - 1) / interpolation_;
    if (taps_per_phase == 0) taps_per_phase = 1;

    for (int p = 0; p < interpolation_; ++p) {
        taps_polyphase_[p].assign(taps_per_phase, 0.0f);
        for (size_t m = 0; m < taps_per_phase; ++m) {
            size_t idx = p + m * interpolation_;
            if (idx < ntaps) {
                // Scale by interpolation factor to preserve amplitude through interpolation
                taps_polyphase_[p][m] = taps_[idx] * static_cast<float>(interpolation_);
            }
        }
    }
}

void FirFilter::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    phase_acc_ = 0;
    std::fill(history_f_.begin(), history_f_.end(), 0.0f);
    std::fill(history_cf_.begin(), history_cf_.end(), std::complex<float>(0.0f, 0.0f));
}

bool FirFilter::is_ready() {
    Port* in = get_input_port(0);
    return is_active() && in && in->get_buffer() && in->get_buffer()->read_available() >= static_cast<size_t>(decimation_);
}

void FirFilter::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available < static_cast<size_t>(decimation_)) return;

    size_t n_to_read = std::min(available, MAX_INPUT_CHUNK);
    n_to_read = (n_to_read / decimation_) * decimation_;
    if (n_to_read == 0) return;

    if (type_ == DataType::FLOAT) {
        in->read(in_buf_f_.data(), n_to_read);
        size_t n_out = process_polyphase(in_buf_f_.data(), out_buf_f_.data(), n_to_read, history_f_);
        if (n_out > 0) {
            out->write(out_buf_f_.data(), n_out);
        }
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        in->read(in_buf_cf_.data(), n_to_read);
        size_t n_out = process_polyphase(in_buf_cf_.data(), out_buf_cf_.data(), n_to_read, history_cf_);
        if (n_out > 0) {
            out->write(out_buf_cf_.data(), n_out);
        }
    }
}

void FirFilter::set_taps(const std::vector<float>& taps) {
    std::lock_guard<std::mutex> lock(mutex_);
    taps_ = taps;
    build_polyphase_taps();
    size_t taps_per_phase = taps_polyphase_[0].size();
    if (type_ == DataType::FLOAT) {
        history_f_.assign(taps_per_phase + MAX_INPUT_CHUNK, 0.0f);
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        history_cf_.assign(taps_per_phase + MAX_INPUT_CHUNK, std::complex<float>(0.0f, 0.0f));
    }
}

template<typename T>
size_t FirFilter::process_polyphase(const T* input, T* output, size_t nitems, std::vector<T>& history) {
    std::lock_guard<std::mutex> lock(mutex_);

    size_t taps_per_phase = taps_polyphase_[0].size();
    size_t prev_samples = (taps_per_phase > 0) ? (taps_per_phase - 1) : 0;

    // 1. Copy new input into history buffer after previous samples
    std::copy(input, input + nitems, history.begin() + prev_samples);

    size_t out_idx = 0;
    size_t in_idx = 0;

    // 2. Polyphase filtering
    while (in_idx < nitems) {
        const std::vector<float>& phase_taps = taps_polyphase_[phase_acc_];
        T acc = 0;
        const T* hist_ptr = &history[in_idx];
        for (size_t j = 0; j < taps_per_phase; ++j) {
            acc += hist_ptr[taps_per_phase - 1 - j] * phase_taps[j];
        }
        output[out_idx++] = acc;

        phase_acc_ += decimation_;
        in_idx += phase_acc_ / interpolation_;
        phase_acc_ %= interpolation_;
    }

    // 3. Update history for next chunk
    if (prev_samples > 0) {
        std::copy(history.begin() + nitems, history.begin() + nitems + prev_samples, history.begin());
    }

    return out_idx;
}
