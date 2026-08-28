#include "AddConst.h"
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "AddConst"

constexpr size_t MAX_MATH_CHUNK = 4096;

AddConst::AddConst(DataType type, float constant, const std::string& name)
    : Block(name), type_(type), const_real_(constant), const_imag_(0.0f) {

    add_input_port("in", type);
    add_output_port("out", type);

    switch (type_) {
        case DataType::FLOAT: buf_f_.resize(MAX_MATH_CHUNK); break;
        case DataType::COMPLEX_FLOAT: buf_cf_.resize(MAX_MATH_CHUNK); break;
        case DataType::DOUBLE: buf_d_.resize(MAX_MATH_CHUNK); break;
        case DataType::COMPLEX_DOUBLE: buf_cd_.resize(MAX_MATH_CHUNK); break;
        case DataType::INT32: buf_i32_.resize(MAX_MATH_CHUNK); break;
        case DataType::SHORT: buf_i16_.resize(MAX_MATH_CHUNK); break;
        default: buf_f_.resize(MAX_MATH_CHUNK); break;
    }
}

AddConst::AddConst(DataType type, float const_real, float const_imag, const std::string& name)
    : Block(name), type_(type), const_real_(const_real), const_imag_(const_imag) {

    add_input_port("in", type);
    add_output_port("out", type);

    switch (type_) {
        case DataType::FLOAT: buf_f_.resize(MAX_MATH_CHUNK); break;
        case DataType::COMPLEX_FLOAT: buf_cf_.resize(MAX_MATH_CHUNK); break;
        case DataType::DOUBLE: buf_d_.resize(MAX_MATH_CHUNK); break;
        case DataType::COMPLEX_DOUBLE: buf_cd_.resize(MAX_MATH_CHUNK); break;
        case DataType::INT32: buf_i32_.resize(MAX_MATH_CHUNK); break;
        case DataType::SHORT: buf_i16_.resize(MAX_MATH_CHUNK); break;
        default: buf_f_.resize(MAX_MATH_CHUNK); break;
    }
}

AddConst::~AddConst() {
    stop();
}

void AddConst::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void AddConst::set_constant(float constant) {
    std::lock_guard<std::mutex> lock(mutex_);
    const_real_ = constant;
    const_imag_ = 0.0f;
}

void AddConst::set_constant(float const_real, float const_imag) {
    std::lock_guard<std::mutex> lock(mutex_);
    const_real_ = const_real;
    const_imag_ = const_imag;
}

void AddConst::work() {
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
    } else if (type_ == DataType::COMPLEX_FLOAT) {
        if (in->read(buf_cf_.data(), nitems)) {
            process(buf_cf_.data(), nitems);
            out->write(buf_cf_.data(), nitems);
        }
    } else if (type_ == DataType::DOUBLE) {
        if (in->read(buf_d_.data(), nitems)) {
            process(buf_d_.data(), nitems);
            out->write(buf_d_.data(), nitems);
        }
    } else if (type_ == DataType::COMPLEX_DOUBLE) {
        if (in->read(buf_cd_.data(), nitems)) {
            process(buf_cd_.data(), nitems);
            out->write(buf_cd_.data(), nitems);
        }
    } else if (type_ == DataType::INT32) {
        if (in->read(buf_i32_.data(), nitems)) {
            process(buf_i32_.data(), nitems);
            out->write(buf_i32_.data(), nitems);
        }
    } else if (type_ == DataType::SHORT) {
        if (in->read(buf_i16_.data(), nitems)) {
            process(buf_i16_.data(), nitems);
            out->write(buf_i16_.data(), nitems);
        }
    }
}

template<typename T>
void AddConst::process(T* data, size_t nitems) {
    std::lock_guard<std::mutex> lock(mutex_);
    if constexpr (std::is_same_v<T, std::complex<float>>) {
        std::complex<float> c(const_real_, const_imag_);
        for (size_t i = 0; i < nitems; ++i) {
            data[i] += c;
        }
    } else if constexpr (std::is_same_v<T, std::complex<double>>) {
        std::complex<double> c(const_real_, const_imag_);
        for (size_t i = 0; i < nitems; ++i) {
            data[i] += c;
        }
    } else {
        T c = static_cast<T>(const_real_);
        for (size_t i = 0; i < nitems; ++i) {
            data[i] += c;
        }
    }
}
