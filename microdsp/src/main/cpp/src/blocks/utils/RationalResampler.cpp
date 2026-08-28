#include "RationalResampler.h"
#include <android/log.h>
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "RationalResampler"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

constexpr size_t MAX_RESAMPLE_CHUNK = 4096;

RationalResampler::RationalResampler(DataType type, int interpolation, int decimation, const std::vector<float>& taps, const std::string& name)
    : Block(name), type_(type), interpolation_(std::max(1, interpolation)), decimation_(std::max(1, decimation)), taps_(taps), phase_acc_(0) {

    add_input_port("in", type);
    add_output_port("out", type);

    build_polyphase_taps();

    size_t taps_per_phase = taps_polyphase_[0].size();
    if (type == DataType::FLOAT) {
        history_f_.assign(taps_per_phase + MAX_RESAMPLE_CHUNK, 0.0f);
        in_buf_f_.resize(MAX_RESAMPLE_CHUNK);
        out_buf_f_.resize((MAX_RESAMPLE_CHUNK * interpolation_) / decimation_ + interpolation_ + 16);
    } else if (type == DataType::COMPLEX_FLOAT) {
        history_cf_.assign(taps_per_phase + MAX_RESAMPLE_CHUNK, std::complex<float>(0.0f, 0.0f));
        in_buf_cf_.resize(MAX_RESAMPLE_CHUNK);
        out_buf_cf_.resize((MAX_RESAMPLE_CHUNK * interpolation_) / decimation_ + interpolation_ + 16);
    }

    LOGI("RationalResampler created: %s, type: %d, L: %d, M: %d, taps: %zu, taps/phase: %zu",
         name.c_str(), static_cast<int>(type), interpolation_, decimation_, taps_.size(), taps_per_phase);
}

RationalResampler::~RationalResampler() {
    stop();
}

void RationalResampler::build_polyphase_taps() {
    taps_polyphase_.assign(interpolation_, std::vector<float>());
    size_t ntaps = taps_.size();
    size_t taps_per_phase = (ntaps + interpolation_ - 1) / interpolation_;
    if (taps_per_phase == 0) taps_per_phase = 1;

    for (int p = 0; p < interpolation_; ++p) {
        taps_polyphase_[p].assign(taps_per_phase, 0.0f);
        for (size_t m = 0; m < taps_per_phase; ++m) {
            size_t idx = p + m * interpolation_;
            if (idx < ntaps) {
                taps_polyphase_[p][m] = taps_[idx] * static_cast<float>(interpolation_);
            }
        }
    }
}

void RationalResampler::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    phase_acc_ = 0;
    std::fill(history_f_.begin(), history_f_.end(), 0.0f);
    std::fill(history_cf_.begin(), history_cf_.end(), std::complex<float>(0.0f, 0.0f));
}

bool RationalResampler::is_ready() {
    Port* in = get_input_port(0);
    return is_active() && in && in->get_buffer() && in->get_buffer()->read_available() >= static_cast<size_t>(decimation_);
}

void RationalResampler::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available < static_cast<size_t>(decimation_)) return;

    size_t n_to_read = std::min(available, MAX_RESAMPLE_CHUNK);
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

template<typename T>
size_t RationalResampler::process_polyphase(const T* input, T* output, size_t nitems, std::vector<T>& history) {
    std::lock_guard<std::mutex> lock(mutex_);

    size_t taps_per_phase = taps_polyphase_[0].size();
    size_t prev_samples = (taps_per_phase > 0) ? (taps_per_phase - 1) : 0;

    std::copy(input, input + nitems, history.begin() + prev_samples);

    size_t out_idx = 0;
    size_t in_idx = 0;

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

    if (prev_samples > 0) {
        std::copy(history.begin() + nitems, history.begin() + nitems + prev_samples, history.begin());
    }

    return out_idx;
}
