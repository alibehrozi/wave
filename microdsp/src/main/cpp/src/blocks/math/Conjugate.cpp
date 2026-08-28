#include "Conjugate.h"
#include <algorithm>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "Conjugate"

constexpr size_t MAX_MATH_CHUNK = 4096;

Conjugate::Conjugate(DataType type, const std::string& name)
    : Block(name), type_(type) {

    add_input_port("in", type_);
    add_output_port("out", type_);

    if (type_ == DataType::COMPLEX_DOUBLE) {
        buf_cd_.resize(MAX_MATH_CHUNK);
    } else {
        type_ = DataType::COMPLEX_FLOAT;
        buf_cf_.resize(MAX_MATH_CHUNK);
    }
}

Conjugate::~Conjugate() {
    stop();
}

void Conjugate::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void Conjugate::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_MATH_CHUNK);

    if (type_ == DataType::COMPLEX_FLOAT) {
        if (in->read(buf_cf_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                buf_cf_[i] = std::conj(buf_cf_[i]);
            }
            out->write(buf_cf_.data(), nitems);
        }
    } else if (type_ == DataType::COMPLEX_DOUBLE) {
        if (in->read(buf_cd_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                buf_cd_[i] = std::conj(buf_cd_[i]);
            }
            out->write(buf_cd_.data(), nitems);
        }
    }
}
