#include "IirFilter.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "IirFilter"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

constexpr size_t MAX_CHUNK_SIZE = 4096;

IirFilter::IirFilter(DataType type, const std::vector<float>& sos_coeffs, const std::string& name)
    : Block(name), type_(type) {

    add_input_port("in", type);
    add_output_port("out", type);

    set_coefficients(sos_coeffs);

    if (type == DataType::FLOAT) {
        in_buf_f_.resize(MAX_CHUNK_SIZE);
        out_buf_f_.resize(MAX_CHUNK_SIZE);
    } else if (type == DataType::COMPLEX_FLOAT) {
        in_buf_cf_.resize(MAX_CHUNK_SIZE);
        out_buf_cf_.resize(MAX_CHUNK_SIZE);
    }

    LOGI("IirFilter created: %s, type: %d, sections: %zu", name.c_str(), static_cast<int>(type), sos_.size());
}

IirFilter::IirFilter(DataType type, const std::vector<BiquadCoeffs>& sos, const std::string& name)
    : Block(name), type_(type) {

    add_input_port("in", type);
    add_output_port("out", type);

    set_coefficients(sos);

    if (type == DataType::FLOAT) {
        in_buf_f_.resize(MAX_CHUNK_SIZE);
        out_buf_f_.resize(MAX_CHUNK_SIZE);
    } else if (type == DataType::COMPLEX_FLOAT) {
        in_buf_cf_.resize(MAX_CHUNK_SIZE);
        out_buf_cf_.resize(MAX_CHUNK_SIZE);
    }

    LOGI("IirFilter created: %s, type: %d, sections: %zu", name.c_str(), static_cast<int>(type), sos_.size());
}

IirFilter::~IirFilter() {
    stop();
}

void IirFilter::init_state() {
    state_real_.assign(sos_.size(), SectionStateReal{0.0f, 0.0f});
    state_complex_.assign(sos_.size(), SectionStateComplex{{0.0f, 0.0f}, {0.0f, 0.0f}});
}

void IirFilter::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    init_state();
}

bool IirFilter::is_ready() {
    Port* in = get_input_port(0);
    return is_active() && in && in->get_buffer() && in->get_buffer()->read_available() > 0;
}

void IirFilter::set_coefficients(const std::vector<float>& sos_coeffs) {
    std::lock_guard<std::mutex> lock(mutex_);
    sos_.clear();
    size_t num_sections = sos_coeffs.size() / 5;
    sos_.reserve(num_sections);

    for (size_t i = 0; i < num_sections; ++i) {
        BiquadCoeffs c;
        c.b0 = sos_coeffs[i * 5 + 0];
        c.b1 = sos_coeffs[i * 5 + 1];
        c.b2 = sos_coeffs[i * 5 + 2];
        c.a1 = sos_coeffs[i * 5 + 3];
        c.a2 = sos_coeffs[i * 5 + 4];
        sos_.push_back(c);
    }

    if (sos_.empty()) {
        BiquadCoeffs passthrough;
        sos_.push_back(passthrough);
    }

    init_state();
}

void IirFilter::set_coefficients(const std::vector<BiquadCoeffs>& sos) {
    std::lock_guard<std::mutex> lock(mutex_);
    sos_ = sos;
    if (sos_.empty()) {
        BiquadCoeffs passthrough;
        sos_.push_back(passthrough);
    }
    init_state();
}

void IirFilter::process_real(const float* input, float* output, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Copy input into output array for in-place cascading across sections
    std::memcpy(output, input, nitems * sizeof(float));

    for (size_t s = 0; s < sos_.size(); ++s) {
        const auto& c = sos_[s];
        auto& st = state_real_[s];

        float b0 = c.b0;
        float b1 = c.b1;
        float b2 = c.b2;
        float a1 = c.a1;
        float a2 = c.a2;
        float s1 = st.s1;
        float s2 = st.s2;

        for (size_t i = 0; i < nitems; ++i) {
            float x = output[i];
            float y = b0 * x + s1;
            s1 = b1 * x - a1 * y + s2;
            s2 = b2 * x - a2 * y;
            output[i] = y;
        }

        st.s1 = s1;
        st.s2 = s2;
    }
}

void IirFilter::process_complex(const std::complex<float>* input, std::complex<float>* output, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);

    std::memcpy(output, input, nitems * sizeof(std::complex<float>));

    for (size_t s = 0; s < sos_.size(); ++s) {
        const auto& c = sos_[s];
        auto& st = state_complex_[s];

        float b0 = c.b0;
        float b1 = c.b1;
        float b2 = c.b2;
        float a1 = c.a1;
        float a2 = c.a2;
        std::complex<float> s1 = st.s1;
        std::complex<float> s2 = st.s2;

        for (size_t i = 0; i < nitems; ++i) {
            std::complex<float> x = output[i];
            std::complex<float> y = b0 * x + s1;
            s1 = b1 * x - a1 * y + s2;
            s2 = b2 * x - a2 * y;
            output[i] = y;
        }

        st.s1 = s1;
        st.s2 = s2;
    }
}

void IirFilter::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t n_to_read = std::min(available, MAX_CHUNK_SIZE);

    if (type_ == DataType::FLOAT) {
        in->read(in_buf_f_.data(), n_to_read);
        process_real(in_buf_f_.data(), out_buf_f_.data(), n_to_read);
        out->write(out_buf_f_.data(), n_to_read);
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        in->read(in_buf_cf_.data(), n_to_read);
        process_complex(in_buf_cf_.data(), out_buf_cf_.data(), n_to_read);
        out->write(out_buf_cf_.data(), n_to_read);
    }
}
