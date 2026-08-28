#include "ComplexToMag.h"
#include <algorithm>
#include <cmath>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "ComplexToMag"

constexpr size_t MAX_MATH_CHUNK = 4096;

ComplexToMag::ComplexToMag(DataType type, const std::string& name)
    : Block(name), in_type_(type) {

    if (in_type_ == DataType::COMPLEX_DOUBLE) {
        out_type_ = DataType::DOUBLE;
        in_cd_.resize(MAX_MATH_CHUNK);
        out_d_.resize(MAX_MATH_CHUNK);
    } else {
        in_type_ = DataType::COMPLEX_FLOAT;
        out_type_ = DataType::FLOAT;
        in_cf_.resize(MAX_MATH_CHUNK);
        out_f_.resize(MAX_MATH_CHUNK);
    }

    add_input_port("in", in_type_);
    add_output_port("out", out_type_);
}

ComplexToMag::~ComplexToMag() {
    stop();
}

void ComplexToMag::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
}

void ComplexToMag::work() {
    if (!is_active()) return;

    Port* in = get_input_port(0);
    Port* out = get_output_port(0);
    if (!in || !out || !in->get_buffer()) return;

    size_t available = in->get_buffer()->read_available();
    if (available == 0) return;

    size_t nitems = std::min(available, MAX_MATH_CHUNK);

    if (in_type_ == DataType::COMPLEX_FLOAT) {
        if (in->read(in_cf_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                out_f_[i] = std::abs(in_cf_[i]);
            }
            out->write(out_f_.data(), nitems);
        }
    } else if (in_type_ == DataType::COMPLEX_DOUBLE) {
        if (in->read(in_cd_.data(), nitems)) {
            for (size_t i = 0; i < nitems; ++i) {
                out_d_[i] = std::abs(in_cd_[i]);
            }
            out->write(out_d_.data(), nitems);
        }
    }
}
