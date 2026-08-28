#include "Log10.h"
#include <algorithm>
#include <cmath>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Log10"

constexpr size_t MAX_MATH_CHUNK = 4096;

Log10::Log10(DataType type, float n, float k, float eps, const std::string& name)
    : Block(name), type_(type), n_(n), k_(k), eps_(eps) {

    add_input_port("in", type_);
    add_output_port("out", type_);

    if (type_ == DataType::DOUBLE) {
        buf_d_.resize(MAX_MATH_CHUNK);
    } else {
        type_ = DataType::FLOAT;
        buf_f_.resize(MAX_MATH_CHUNK);
    }
}

Log10::~Log10() {
    stop();
}

void Log10::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void Log10::set_parameters(float n, float k) {
    std::lock_guard<std::mutex> lock(mutex_);
    n_ = n;
    k_ = k;
}

void Log10::set_n(float n) {
    std::lock_guard<std::mutex> lock(mutex_);
    n_ = n;
}

void Log10::set_k(float k) {
    std::lock_guard<std::mutex> lock(mutex_);
    k_ = k;
}

void Log10::set_eps(float eps) {
    std::lock_guard<std::mutex> lock(mutex_);
    eps_ = eps;
}

void Log10::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_MATH_CHUNK);

    if (type_ == DataType::FLOAT) {
        if (in->read(buf_f_.data(), nitems)) {
            process(buf_f_.data(), nitems);
            out->write(buf_f_.data(), nitems);
        }
    } else if (type_ == DataType::DOUBLE) {
        if (in->read(buf_d_.data(), nitems)) {
            process(buf_d_.data(), nitems);
            out->write(buf_d_.data(), nitems);
        }
    }
}

template<typename T>
void Log10::process(T* data, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);
    for (size_t i = 0; i < nitems; ++i) {
        T val = std::max(data[i], static_cast<T>(eps_));
        data[i] = static_cast<T>(n_) * std::log10(val) + static_cast<T>(k_);
    }
}
