#include "Abs.h"
#include <algorithm>
#include <cmath>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Abs"

constexpr size_t MAX_MATH_CHUNK = 4096;

Abs::Abs(DataType type, const std::string& name)
    : Block(name), type_(type) {

    add_input_port("in", type);
    add_output_port("out", type);

    switch (type_) {
        case DataType::FLOAT: buf_f_.resize(MAX_MATH_CHUNK); break;
        case DataType::DOUBLE: buf_d_.resize(MAX_MATH_CHUNK); break;
        case DataType::INT32: buf_i32_.resize(MAX_MATH_CHUNK); break;
        case DataType::SHORT: buf_i16_.resize(MAX_MATH_CHUNK); break;
        default: buf_f_.resize(MAX_MATH_CHUNK); break;
    }
}

Abs::~Abs() {
    stop();
}

void Abs::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void Abs::work() {
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
void Abs::process(T* data, size_t nitems) {
    for (size_t i = 0; i < nitems; ++i) {
        data[i] = std::abs(data[i]);
    }
}
