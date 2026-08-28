#include "Divide.h"
#include <algorithm>
#include <cmath>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Divide"

constexpr size_t MAX_MATH_CHUNK = 4096;

Divide::Divide(DataType type, float eps, const std::string& name)
    : Block(name), type_(type), eps_(eps) {

    add_input_port("in0", type);
    add_input_port("in1", type);
    add_output_port("out", type);

    switch (type_) {
        case DataType::FLOAT:
            in0_f_.resize(MAX_MATH_CHUNK);
            in1_f_.resize(MAX_MATH_CHUNK);
            out_f_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::COMPLEX_FLOAT:
            in0_cf_.resize(MAX_MATH_CHUNK);
            in1_cf_.resize(MAX_MATH_CHUNK);
            out_cf_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::DOUBLE:
            in0_d_.resize(MAX_MATH_CHUNK);
            in1_d_.resize(MAX_MATH_CHUNK);
            out_d_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::COMPLEX_DOUBLE:
            in0_cd_.resize(MAX_MATH_CHUNK);
            in1_cd_.resize(MAX_MATH_CHUNK);
            out_cd_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::INT32:
            in0_i32_.resize(MAX_MATH_CHUNK);
            in1_i32_.resize(MAX_MATH_CHUNK);
            out_i32_.resize(MAX_MATH_CHUNK);
            break;
        case DataType::SHORT:
            in0_i16_.resize(MAX_MATH_CHUNK);
            in1_i16_.resize(MAX_MATH_CHUNK);
            out_i16_.resize(MAX_MATH_CHUNK);
            break;
        default:
            in0_f_.resize(MAX_MATH_CHUNK);
            in1_f_.resize(MAX_MATH_CHUNK);
            out_f_.resize(MAX_MATH_CHUNK);
            break;
    }
}

Divide::~Divide() {
    stop();
}

void Divide::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void Divide::work() {
    if (!is_active()) return;

    Port* in0 = get_input_port(0);
    Port* in1 = get_input_port(1);
    Port* out = get_output_port(0);
    if (!in0 || !in1 || !out || !in0->get_buffer() || !in1->get_buffer()) return;

    size_t avail0 = in0->get_buffer()->read_available();
    size_t avail1 = in1->get_buffer()->read_available();
    size_t available = std::min(avail0, avail1);
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_MATH_CHUNK);

    if (type_ == DataType::FLOAT) {
        if (in0->read(in0_f_.data(), nitems) && in1->read(in1_f_.data(), nitems)) {
            process(in0_f_.data(), in1_f_.data(), out_f_.data(), nitems);
            out->write(out_f_.data(), nitems);
        }
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        if (in0->read(in0_cf_.data(), nitems) && in1->read(in1_cf_.data(), nitems)) {
            process(in0_cf_.data(), in1_cf_.data(), out_cf_.data(), nitems);
            out->write(out_cf_.data(), nitems);
        }
    } else if (type_ == DataType::DOUBLE) {
        if (in0->read(in0_d_.data(), nitems) && in1->read(in1_d_.data(), nitems)) {
            process(in0_d_.data(), in1_d_.data(), out_d_.data(), nitems);
            out->write(out_d_.data(), nitems);
        }
    } else if (type_ == DataType::COMPLEX_DOUBLE) {
        if (in0->read(in0_cd_.data(), nitems) && in1->read(in1_cd_.data(), nitems)) {
            process(in0_cd_.data(), in1_cd_.data(), out_cd_.data(), nitems);
            out->write(out_cd_.data(), nitems);
        }
    } else if (type_ == DataType::INT32) {
        if (in0->read(in0_i32_.data(), nitems) && in1->read(in1_i32_.data(), nitems)) {
            process(in0_i32_.data(), in1_i32_.data(), out_i32_.data(), nitems);
            out->write(out_i32_.data(), nitems);
        }
    } else if (type_ == DataType::SHORT) {
        if (in0->read(in0_i16_.data(), nitems) && in1->read(in1_i16_.data(), nitems)) {
            process(in0_i16_.data(), in1_i16_.data(), out_i16_.data(), nitems);
            out->write(out_i16_.data(), nitems);
        }
    }
}

template<typename T>
void Divide::process(const T* in0, const T* in1, T* out, size_t nitems) {
    if constexpr (std::is_same_v<T, std::complex<float>> || std::is_same_v<T, std::complex<double>>) {
        for (size_t i = 0; i < nitems; ++i) {
            auto mag_sq = std::norm(in1[i]);
            if (mag_sq < static_cast<decltype(mag_sq)>(eps_ * eps_)) {
                out[i] = T(0);
            } else {
                out[i] = in0[i] / in1[i];
            }
        }
    } else if constexpr (std::is_integral_v<T>) {
        for (size_t i = 0; i < nitems; ++i) {
            if (in1[i] == 0) {
                out[i] = 0;
            } else {
                out[i] = in0[i] / in1[i];
            }
        }
    } else {
        for (size_t i = 0; i < nitems; ++i) {
            if (std::abs(in1[i]) < static_cast<T>(eps_)) {
                out[i] = 0;
            } else {
                out[i] = in0[i] / in1[i];
            }
        }
    }
}
